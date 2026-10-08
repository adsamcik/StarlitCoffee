package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.session.LongSessionScheduler
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.LongSessionWorkCanceller
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect

/** Delivers a stage alert without teaching the coordinator about Android notifications. */
fun interface BrewSessionStageAlertNotifier {
    suspend fun deliver(
        effect: PendingSessionEffect.StageAlert,
        session: ActiveBrewSession,
    ): SessionEffectDelivery

    suspend fun deliverTimer(
        effect: PendingSessionEffect.TimerAlert,
        session: ActiveBrewSession,
    ): SessionEffectDelivery = SessionEffectDelivery.Delivered

    /** Withdraw posted cues whose stage/revision is no longer relevant. */
    fun synchronize(session: ActiveBrewSession) = Unit
}

object NoOpBrewSessionStageAlertNotifier : BrewSessionStageAlertNotifier {
    override suspend fun deliver(
        effect: PendingSessionEffect.StageAlert,
        session: ActiveBrewSession,
    ): SessionEffectDelivery = SessionEffectDelivery.Delivered
}

/**
 * Production effect router. It deliberately lets adapter failures escape: a
 * worker can retry a transient database or WorkManager failure, while an
 * adapter that cannot currently fulfill an effect returns [SessionEffectDelivery.Deferred].
 */
class DefaultBrewSessionEffectHandler(
    private val scheduler: LongSessionScheduler,
    private val finalizer: BrewSessionFinalizer,
    private val stageAlertNotifier: BrewSessionStageAlertNotifier = NoOpBrewSessionStageAlertNotifier,
    private val statusNotifier: BrewSessionStatusNotifier = NoOpBrewSessionStatusNotifier,
    private val workCanceller: LongSessionWorkCanceller? = null,
) : BrewSessionEffectHandler {

    override fun synchronize(session: ActiveBrewSession) {
        stageAlertNotifier.synchronize(session)
    }

    override suspend fun deliver(
        effect: PendingSessionEffect,
        session: ActiveBrewSession,
    ): SessionEffectDelivery {
        stageAlertNotifier.synchronize(session)
        if (session.runtime.status != BrewSessionStatus.RUNNING) {
            statusNotifier.clear(session.runtime.sessionId)
        }
        return when (effect) {
            is PendingSessionEffect.TimerAlert -> stageAlertNotifier.deliverTimer(effect, session)
            is PendingSessionEffect.ScheduleTimerDeadline -> {
                scheduler.schedule(effect.sessionId, effect.stageInstanceId, effect.scheduleToken,
                    effect.dueAtWallClockMillis, effect.effectId)
                SessionEffectDelivery.Delivered
            }
            is PendingSessionEffect.StageAlert -> if (session.runtime.isGuidancePaused) {
                SessionEffectDelivery.Delivered
            } else {
                stageAlertNotifier.deliver(effect, session)
            }
            is PendingSessionEffect.ScheduleStageDeadline -> {
                scheduler.schedule(
                    sessionId = effect.sessionId,
                    stageInstanceId = effect.stageInstanceId,
                    scheduleToken = effect.scheduleToken,
                    dueAtWallClockMillis = effect.dueAtWallClockMillis,
                    effectId = effect.effectId,
                )
                SessionEffectDelivery.Delivered
            }

            is PendingSessionEffect.CancelStageDeadline -> {
                scheduler.cancel(
                    sessionId = effect.sessionId,
                    scheduleToken = effect.scheduleToken,
                    effectId = effect.effectId,
                )
                SessionEffectDelivery.Delivered
            }

            is PendingSessionEffect.FinalizeBrewLog -> finalizer.deliver(effect, session)
            is PendingSessionEffect.CancelSessionWork -> {
                workCanceller?.cancelAllForSession(effect.sessionId, effect.effectId)
                SessionEffectDelivery.Delivered
            }
        }
    }
}
