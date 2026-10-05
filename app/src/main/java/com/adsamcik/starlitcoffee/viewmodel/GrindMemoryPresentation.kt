package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.GrindContext
import com.adsamcik.starlitcoffee.data.model.GrindSaveScope
import com.adsamcik.starlitcoffee.data.model.GrindSettingSource
import com.adsamcik.starlitcoffee.data.model.GrinderScaleType
import com.adsamcik.starlitcoffee.data.model.RememberedGrind
import com.adsamcik.starlitcoffee.domain.GrindMemoryResolver
import java.util.Locale
import kotlin.math.roundToInt

internal fun preparedGrind(
    state: BrewUiState,
    bag: CoffeeBagEntity?,
    settings: List<RememberedGrind>,
    temporary: RememberedGrind?,
): PreparedGrind {
    val result = state.grindResult as? GrindResult.Specific ?: return state.preparedGrind.copy(
        value = null, inputText = "", displayValue = "", rotations = null, clicks = null,
        source = GrindSettingSource.GENERIC, availableScopes = emptyList(), canReset = false,
    )
    val grinder = result.grinder
    val context = GrindContext(grinder.id, state.method.name, state.filterType?.name.orEmpty())
    val resolved = GrindMemoryResolver.resolve(settings, context, grinder, bag?.id, bag?.coffeeId, state.isDecafBrew, temporary)
    val recommended = when (grinder.scaleType) {
        GrinderScaleType.DIAL_CLICKS -> String.format(Locale.US, "%.1f", result.recommendation.suggestedStart)
        GrinderScaleType.PURE_CLICKS -> result.recommendation.suggestedStart.roundToInt().toString()
        GrinderScaleType.NUMBERED_DIAL -> result.recommendation.suggestedStart.toBigDecimal().stripTrailingZeros().toPlainString()
    }
    val value = resolved?.value ?: GrindMemoryResolver.parse(recommended, grinder, 0)
    val display = value?.let(GrindMemoryResolver::display) ?: recommended
    val source = resolved?.source ?: GrindSettingSource.RECOMMENDATION
    val scopes = buildList {
        add(GrindSaveScope.BREW)
        if (bag != null) add(GrindSaveScope.PACK)
        if (bag?.coffeeId != null) add(GrindSaveScope.COFFEE)
        add(GrindSaveScope.TYPE)
    }
    return state.preparedGrind.copy(
        value = value,
        inputText = display, displayValue = display,
        rotations = if (grinder.scaleType == GrinderScaleType.DIAL_CLICKS && value != null) {
            requireNotNull(value.totalClicks) / requireNotNull(value.clicksPerRotation)
        } else null,
        clicks = when (grinder.scaleType) {
            GrinderScaleType.DIAL_CLICKS -> value?.let { requireNotNull(it.totalClicks) % requireNotNull(it.clicksPerRotation) }
            GrinderScaleType.PURE_CLICKS -> value?.totalClicks
            GrinderScaleType.NUMBERED_DIAL -> null
        },
        source = source, availableScopes = scopes,
        canReset = source in setOf(GrindSettingSource.BREW, GrindSettingSource.PACK, GrindSettingSource.COFFEE, GrindSettingSource.TYPE),
    )
}

/** Exact chosen display, including multi-digit dial-click suffixes, survives logging. */
internal fun BrewUiState.effectiveGrindLabel(): String = preparedGrind.displayValue.takeIf(String::isNotBlank)
    ?: when (val result = grindResult) {
        is GrindResult.Generic -> result.descriptor.displayName
        is GrindResult.Specific -> String.format(Locale.US, "%.1f", result.recommendation.suggestedStart)
    }

internal fun CoffeeBagEntity.isBrewable(): Boolean = status in setOf("OPEN", "SEALED") &&
    (weightG == null || weightG.isFinite() && weightG > 0)

internal fun BrewUiState.sameGrindContext(other: BrewUiState): Boolean =
    GrindContext(selectedGrinderId.orEmpty(), method.name, filterType?.name.orEmpty()) ==
        GrindContext(other.selectedGrinderId.orEmpty(), other.method.name, other.filterType?.name.orEmpty()) &&
        isDecafBrew == other.isDecafBrew
