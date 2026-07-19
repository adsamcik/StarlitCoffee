package com.adsamcik.starlitcoffee.navigation

import com.adsamcik.starlitcoffee.data.work.BagReviewContext
import com.adsamcik.starlitcoffee.data.work.BagReviewMode
import kotlinx.serialization.Serializable

@Serializable
object CalculatorBrew

@Serializable
object BrewTimer

@Serializable
object GrindPrep

@Serializable
object BloomTimer

@Serializable
object SavedRecipes

@Serializable
object BagInventory

@Serializable
object BrewLogList

@Serializable
data class BrewLogDetail(val logId: Long)

@Serializable
object BarcodeScanner

@Serializable
object GuidedScan

@Serializable
object OnboardingMethods

@Serializable
object OnboardingPersonalize

@Serializable
object Settings

@Serializable
object BloomAnimationSettings

@Serializable
object DisplaySettings

@Serializable
data class CupPresetEditor(val presetId: Long? = null)

@Serializable
object More

@Serializable
data class RescanBag(val bagId: Long)

internal sealed interface BagReviewDestination {
    data object AddNew : BagReviewDestination
    data class Rescan(val targetBagId: Long) : BagReviewDestination
}

internal data class BagReviewNavigationPlan(
    val routes: List<Any>,
    val newBagTransferDestination: Any? = null,
)

internal enum class RescanTargetStatus {
    LOADING,
    AVAILABLE,
    MISSING,
}

internal fun bagReviewDestination(reviewContext: BagReviewContext?): BagReviewDestination =
    if (reviewContext?.mode == BagReviewMode.RESCAN && reviewContext.targetBagId != null) {
        BagReviewDestination.Rescan(reviewContext.targetBagId)
    } else {
        BagReviewDestination.AddNew
    }

internal fun bagReviewNavigationPlan(
    destination: BagReviewDestination,
    requiresInventoryBackStack: Boolean,
): BagReviewNavigationPlan = when (destination) {
    BagReviewDestination.AddNew -> BagReviewNavigationPlan(routes = listOf(BagInventory))
    is BagReviewDestination.Rescan -> BagReviewNavigationPlan(
        routes = buildList {
            if (requiresInventoryBackStack) add(BagInventory)
            add(RescanBag(destination.targetBagId))
        },
        newBagTransferDestination = BagInventory,
    )
}

internal fun shouldSuppressBagReviewNavigation(
    hasExplicitRequest: Boolean,
    currentRescanBagId: Long?,
    destination: BagReviewDestination,
): Boolean =
    !hasExplicitRequest &&
        destination is BagReviewDestination.Rescan &&
        currentRescanBagId == destination.targetBagId

internal fun rescanTargetStatus(
    inventoryLoaded: Boolean,
    availableBagIds: Collection<Long>,
    targetBagId: Long,
): RescanTargetStatus = when {
    !inventoryLoaded -> RescanTargetStatus.LOADING
    targetBagId in availableBagIds -> RescanTargetStatus.AVAILABLE
    else -> RescanTargetStatus.MISSING
}
