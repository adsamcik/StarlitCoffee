package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecordSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingPersistenceMapper
import com.adsamcik.starlitcoffee.data.brewing.snapshot.StageActualSnapshotV1
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus

/**
 * Immutable work to perform after a session log is inserted for the first time.
 *
 * The caller owns database reads and writes. In particular, it must apply
 * [coffeeBagUpdates] only in the same transaction that successfully inserts
 * [brewLog] using its unique [BrewLogEntity.sourceSessionId]. Replaying a
 * duplicate insert must therefore not replay inventory consumption.
 */
data class BrewSessionCompletionPlan(
    val brewRecord: BrewRecordSnapshotV1,
    val brewLog: BrewLogEntity,
    val coffeeBagUpdates: List<CoffeeBagEntity>,
    val rotatedToCoffeeBagId: Long?,
)

/**
 * Maps a completed, restored session into its immutable historical record and
 * legacy-compatible log columns without consulting the live brewing catalogue.
 *
 * Raw method-family and brewer-profile IDs deliberately stay in the versioned
 * record and indexed log columns. This keeps an unknown future profile
 * inspectable rather than replacing it with an arbitrary supported method.
 */
object BrewSessionCompletionPlanner {

    /**
     * Creates work for one newly inserted session log.
     *
     * [currentCoffeeBag] and [nextSealedCoffeeBag] are caller-supplied snapshots
     * read inside the eventual transaction. A mismatched or ineligible bag is
     * never changed. This keeps the function pure and prevents a stale lookup
     * from decrementing a bag the user did not select for this session.
     */
    fun plan(
        session: RestoredActiveBrewSession,
        currentCoffeeBag: CoffeeBagEntity?,
        nextSealedCoffeeBag: CoffeeBagEntity? = null,
    ): BrewSessionCompletionPlan {
        val completedAtWallClockMillis = requireCompletionTime(session)
        val record = BrewRecordSnapshotV1(
            recipe = session.recipe,
            stageActuals = stageActuals(session),
            completedAtWallClockMillis = completedAtWallClockMillis,
            sourceSessionId = session.runtime.sessionId.value,
        )
        val presentation = session.executionContext.logPresentation
        val legacyLog = BrewLogEntity(
            recipeId = session.executionContext.sourceRecipeId,
            coffeeBagId = session.executionContext.coffeeBagId,
            // The legacy column remains a readable historical label. Stable IDs
            // and the full raw recipe live alongside it in the versioned record.
            method = presentation.methodLabel,
            doseG = presentation.doseG.toFloat(),
            waterG = presentation.waterG.toFloat(),
            ratio = presentation.ratio.toFloat(),
            grindSetting = presentation.grindLabel,
            filterType = presentation.filterLabel,
            isDecaf = presentation.isDecaf,
            freeformNotes = presentation.notes,
            brewTimeSeconds = legacyBrewTimeSeconds(session.runtime.totalActiveElapsedMillis),
            createdAt = completedAtWallClockMillis,
        )
        val brewLog = BrewingPersistenceMapper.withBrewRecordSnapshot(
            legacyFields = legacyLog,
            snapshot = record,
            sourceSessionId = session.runtime.sessionId.value,
        )
        val inventory = inventoryPlan(
            selectedCoffeeBagId = session.executionContext.coffeeBagId,
            currentCoffeeBag = currentCoffeeBag,
            nextSealedCoffeeBag = nextSealedCoffeeBag,
            doseG = presentation.doseG.toFloat(),
            grindSetting = presentation.grindLabel?.takeIf(String::isNotBlank),
            completedAtWallClockMillis = completedAtWallClockMillis,
        )
        return BrewSessionCompletionPlan(
            brewRecord = record,
            brewLog = brewLog,
            coffeeBagUpdates = inventory.updates,
            rotatedToCoffeeBagId = inventory.rotatedToCoffeeBagId,
        )
    }

    private fun requireCompletionTime(session: RestoredActiveBrewSession): Long {
        require(session.runtime.status == BrewSessionStatus.COMPLETED) {
            "Only a completed brew session can create a brew log"
        }
        require(session.entity.completedLogId == null) {
            "A brew log has already been recorded for session ${session.runtime.sessionId.value}"
        }
        return requireNotNull(session.runtime.endedAtWallClockMillis) {
            "A completed brew session must have an end timestamp"
        }
    }

    private fun stageActuals(session: RestoredActiveBrewSession): List<StageActualSnapshotV1> {
        val stages = session.runtime.stagePlan.stages
        val progress = session.runtime.stageProgress
        require(stages.size == progress.size) {
            "Compiled stage count and runtime progress count must match"
        }
        return stages.zip(progress).map { (stage, stageProgress) ->
            StageActualSnapshotV1(
                stageInstanceId = stage.instanceId.persistentKey,
                elapsedActiveMillis = stageProgress.elapsedActiveMillis,
                addedAmountG = stageProgress.actuals.addedAmountGrams,
                cumulativeAmountG = stageProgress.actuals.cumulativeAmountGrams,
                beverageYieldG = stageProgress.actuals.beverageYieldGrams,
                observationIds = stageProgress.actuals.observations.map { it.value }.sorted(),
                markerIds = stageProgress.actuals.markers.map { it.value }.sorted(),
                completionKind = stageProgress.completionKind?.name,
            )
        }
    }

    private fun legacyBrewTimeSeconds(totalActiveElapsedMillis: Long): Int? {
        require(totalActiveElapsedMillis >= 0L) { "Active brew time cannot be negative" }
        return (totalActiveElapsedMillis / MILLIS_PER_SECOND)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
            .takeIf { it > 0 }
    }

    private fun inventoryPlan(
        selectedCoffeeBagId: Long?,
        currentCoffeeBag: CoffeeBagEntity?,
        nextSealedCoffeeBag: CoffeeBagEntity?,
        doseG: Float,
        grindSetting: String?,
        completedAtWallClockMillis: Long,
    ): InventoryPlan {
        val bag = currentCoffeeBag
            ?.takeIf { selectedCoffeeBagId != null && it.id == selectedCoffeeBagId }
            ?: return InventoryPlan.EMPTY

        var updated = bag
        if (updated.status == STATUS_SEALED) {
            updated = updated.copy(
                status = STATUS_OPEN,
                openedDate = completedAtWallClockMillis,
            )
        }
        if (grindSetting != null && updated.grindSetting != grindSetting) {
            updated = updated.copy(grindSetting = grindSetting)
        }
        if (updated.weightG != null) {
            val newWeight = (updated.weightG - doseG).coerceAtLeast(0f)
            updated = updated.copy(weightG = newWeight)
            if (newWeight <= 0f && updated.status == STATUS_OPEN) {
                updated = updated.copy(status = STATUS_FINISHED)
            }
        }

        val updates = buildList {
            if (updated != bag) add(updated)
        }
        val rotated = nextSealedCoffeeBag?.takeIf { candidate ->
            updated.status == STATUS_FINISHED &&
                bag.status != STATUS_FINISHED &&
                candidate.id != bag.id &&
                candidate.status == STATUS_SEALED &&
                candidate.name == updated.name &&
                candidate.roaster == updated.roaster
        } ?: return InventoryPlan(updates, null)

        return InventoryPlan(
            updates = updates + rotated.copy(
                status = STATUS_OPEN,
                openedDate = completedAtWallClockMillis,
                grindSetting = updated.grindSetting,
            ),
            rotatedToCoffeeBagId = rotated.id,
        )
    }

    private data class InventoryPlan(
        val updates: List<CoffeeBagEntity>,
        val rotatedToCoffeeBagId: Long?,
    ) {
        companion object {
            val EMPTY = InventoryPlan(emptyList(), null)
        }
    }

    private const val MILLIS_PER_SECOND = 1_000L
    private const val STATUS_SEALED = "SEALED"
    private const val STATUS_OPEN = "OPEN"
    private const val STATUS_FINISHED = "FINISHED"
}
