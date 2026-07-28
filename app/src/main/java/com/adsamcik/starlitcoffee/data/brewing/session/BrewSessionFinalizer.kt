package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.db.dao.BrewLogDao
import com.adsamcik.starlitcoffee.data.db.dao.CoffeeBagDao
import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.data.repository.TransactionRunner
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionClockReading
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionReducer

/**
 * Commits the terminal side effects of a completed session as one database
 * transaction. The unique source-session log insert is the idempotency gate:
 * coffee inventory is changed only when that insert is genuinely new.
 */
class BrewSessionFinalizer(
    private val transactionRunner: TransactionRunner,
    private val sessionRepository: ActiveBrewSessionRepository,
    private val brewLogDao: BrewLogDao,
    private val coffeeBagDao: CoffeeBagDao,
    private val nowWallClockMillis: () -> Long = System::currentTimeMillis,
) {
    /**
     * The session entity is reloaded inside the transaction rather than trusting
     * an in-memory screen or worker snapshot. That makes a retry after process
     * death and a concurrent screen action resolve through the same CAS guard.
     */
    suspend fun deliver(
        effect: PendingSessionEffect.FinalizeBrewLog,
        session: ActiveBrewSession,
    ): SessionEffectDelivery {
        if (effect.sessionId != session.runtime.sessionId) {
            return SessionEffectDelivery.Deferred("The completion effect belongs to another session")
        }
        return try {
            transactionRunner.runInTransaction {
                deliverInsideTransaction(effect)
            }
        } catch (_: SessionFinalizationConflict) {
            SessionEffectDelivery.Deferred("The session changed while completion was being recorded")
        }
    }

    private suspend fun deliverInsideTransaction(
        effect: PendingSessionEffect.FinalizeBrewLog,
    ): SessionEffectDelivery {
        val entity = sessionRepository.getSession(effect.sessionId.value)
            ?: return SessionEffectDelivery.Deferred("The session is no longer available")
        val restored = ActiveBrewSessionEntityMapper.restore(entity)
        if (restored !is ActiveBrewSessionRestoreResult.Restored) {
            return SessionEffectDelivery.Deferred("The durable session cannot be restored safely")
        }
        val current = restored.value
        val pendingEffect = current.runtime.pendingEffects
            .filterIsInstance<PendingSessionEffect.FinalizeBrewLog>()
            .firstOrNull { it.effectId == effect.effectId }
            ?: return SessionEffectDelivery.AcknowledgedAtomically
        if (pendingEffect.sessionId != current.runtime.sessionId ||
            current.runtime.status != BrewSessionStatus.COMPLETED
        ) {
            return SessionEffectDelivery.Deferred("The session is not ready to create its brew record")
        }

        val logId = entity.completedLogId ?: writeNewSessionLog(current)
        acknowledgeFinalization(
            current = current,
            effect = pendingEffect,
            completedLogId = logId,
        )
        return SessionEffectDelivery.AcknowledgedAtomically
    }

    private suspend fun writeNewSessionLog(current: RestoredActiveBrewSession): Long {
        val selectedBag = current.executionContext.coffeeBagId
            ?.let { coffeeBagId -> coffeeBagDao.getByIdOnce(coffeeBagId) }
        val nextSealedBag = selectedBag?.let { bag ->
            coffeeBagDao.findNextSealed(bag.name, bag.roaster)
        }
        val completion = try {
            BrewSessionCompletionPlanner.plan(
                session = current,
                currentCoffeeBag = selectedBag,
                nextSealedCoffeeBag = nextSealedBag,
            )
        } catch (error: IllegalArgumentException) {
            throw SessionFinalizationConflict(error)
        }
        val insertedId = brewLogDao.insertIfSourceSessionIsNew(completion.brewLog)
        if (insertedId == INSERT_IGNORED) {
            return requireNotNull(
                brewLogDao.getBySourceSessionId(current.runtime.sessionId.value),
            ) {
                "A duplicate session log insert must resolve to an existing log"
            }.id
        }
        for (bag in completion.coffeeBagUpdates) {
            coffeeBagDao.update(bag)
        }
        return insertedId
    }

    private suspend fun acknowledgeFinalization(
        current: RestoredActiveBrewSession,
        effect: PendingSessionEffect.FinalizeBrewLog,
        completedLogId: Long,
    ) {
        val now = nowWallClockMillis()
        val transition = SessionReducer.reduce(
            state = current.runtime,
            event = SessionEvent.AcknowledgeEffect(effect.effectId),
            now = SessionClockReading(monotonicMillis = 0L, wallClockMillis = now),
        )
        if (transition.wasIgnored) return
        val updated = ActiveBrewSessionEntityMapper.update(
            previous = current.entity,
            recipe = current.recipe,
            runtime = transition.state,
            executionContext = current.executionContext,
            completedLogId = completedLogId,
            nowWallClockMillis = now,
        )
        val saved = sessionRepository.saveIfCurrent(
            session = updated,
            expectedRevision = current.runtime.revision,
        )
        if (saved == null) throw SessionFinalizationConflict()
    }

    private class SessionFinalizationConflict(
        cause: Throwable? = null,
    ) : IllegalStateException(cause)

    private companion object {
        const val INSERT_IGNORED = -1L
    }
}
