package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingPersistenceMapper
import com.adsamcik.starlitcoffee.data.brewing.snapshot.StoredBrewRecordPayload
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity

internal fun BrewLogEntity.reviewedRecipe(): BrewRecipeSnapshotV1? =
    (BrewingPersistenceMapper.brewLogRecord(this).payload as? StoredBrewRecordPayload.Versioned)
        ?.snapshot?.recipe?.takeIf { it.reviewedGuide != null }

/** Unknown fill mass and source mL must not become 0 g or a mass ratio in history. */
@androidx.compose.runtime.Composable
internal fun reviewedBrewLogQuantityLabel(recipe: BrewRecipeSnapshotV1): String = listOfNotNull(
    formatMass(recipe.quantities.dryCoffeeDoseG),
    recipe.quantities.brewWaterInputG?.let { formatMass(it) }
        ?: recipe.quantities.brewWaterInputMl?.let { "${formatNumber(it)} mL" },
    recipe.quantities.targetBeverageYieldG?.let { "→ ${formatMass(it)}" },
).joinToString(" · ")
