package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.AccessoryProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BasketProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeDefinition
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.FilterProfileId
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.HeatSourceClass
import com.adsamcik.starlitcoffee.domain.brewing.P1EquipmentOption
import com.adsamcik.starlitcoffee.domain.brewing.P1TemperatureBasis
import com.adsamcik.starlitcoffee.domain.brewing.P1TemperatureSemantics
import com.adsamcik.starlitcoffee.domain.brewing.P1TimeBasis
import com.adsamcik.starlitcoffee.domain.brewing.P1TimeSemantics
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileSetupOption
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileSetupUiState
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileStartSelection
import com.adsamcik.starlitcoffee.ui.component.primaryActionButtonColors
import java.text.NumberFormat
import java.util.Locale

/** Physical brewer → exact recipe → exact equipment → capacity setup for durable P1 sessions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P1BrewerProfileSetupScreen(
    state: P1BrewerProfileSetupUiState,
    onProfileSelected: (BrewerProfileId) -> Unit,
    onRecipeSelected: (BuiltInRecipeId) -> Unit,
    onEquipmentOptionSelected: (Int) -> Unit,
    onEquipmentCapacityChanged: (String) -> Unit,
    onMeasuredReservoirInputChanged: (String) -> Unit,
    onCezveSugarSelected: (Boolean) -> Unit,
    onCezveHeatSourceSelected: (HeatSourceClass) -> Unit,
    onStart: (P1BrewerProfileStartSelection) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onLearn: ((P1BrewerProfileStartSelection) -> Unit)? = null,
) {
    val selectedProfile = state.selectedProfile
    val selectedRecipe = state.selectedRecipe
    val listState = rememberLazyListState()
    var optionalChoicesExpanded by rememberSaveable(selectedProfile?.profileId?.value) {
        mutableStateOf(false)
    }
    var revealSelectedProfile by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(
        selectedProfile?.profileId,
        revealSelectedProfile,
        state.profiles.size,
    ) {
        if (selectedProfile != null && revealSelectedProfile) {
            val recipeSectionIndex = 1 + state.profiles.size
            listState.animateScrollToItem(recipeSectionIndex)
            revealSelectedProfile = false
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_brew_setup_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isStarting) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (selectedProfile != null) {
                P1BrewerProfileActionBar(state = state, onStart = onStart)
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "profile-introduction") {
                SetupSectionIntroduction(
                    title = stringResource(R.string.heading_brewer_profile_choose),
                    hint = stringResource(R.string.msg_brewer_profile_choose_hint),
                )
            }

            if (state.profiles.isEmpty()) {
                item(key = "profile-empty") { ProfileSetupEmptyState() }
            } else {
                items(state.profiles, key = { option -> option.profileId.value }) { option ->
                    BrewerProfileOptionCard(
                        option = option,
                        selected = option.profileId == state.selectedProfileId,
                        onClick = {
                            revealSelectedProfile = true
                            onProfileSelected(option.profileId)
                        },
                    )
                }
            }

            selectedProfile?.let { profile ->
                item(key = "profile-recipes:${profile.profileId.value}") {
                    ExactRecipeSelectionCard(
                        profile = profile,
                        selectedRecipeId = selectedRecipe?.id,
                        onRecipeSelected = onRecipeSelected,
                    )
                }

                selectedRecipe?.let { recipe ->
                    item(key = "recipe-details:${recipe.id.value}") {
                        ExactRecipeDetailsCard(recipe)
                    }

                    if (state.requiresMeasuredReservoirInput) {
                        item(key = "recipe-measured-reservoir:${recipe.id.value}") {
                            MeasuredReservoirInputCard(
                                input = state.selectedMeasuredReservoirInput,
                                inputIsValid = state.selectedMeasuredReservoirInputG != null,
                                onInputChanged = onMeasuredReservoirInputChanged,
                            )
                        }
                    }

                    if (state.requiresCezveSetup) {
                        item(key = "recipe-options:${recipe.id.value}") {
                            CezveOptionalChoicesCard(
                                includeSugar = state.includeCezveSugar,
                                expanded = optionalChoicesExpanded,
                                onExpandedChange = { optionalChoicesExpanded = !optionalChoicesExpanded },
                                onSugarSelected = onCezveSugarSelected,
                            )
                        }
                    }

                    item(key = "recipe-equipment:${recipe.id.value}") {
                        ExactEquipmentCard(
                            recipe = recipe,
                            selectedOption = state.selectedEquipmentOption,
                            onOptionSelected = onEquipmentOptionSelected,
                        )
                    }

                    if (state.requiresCezveSetup) {
                        item(key = "profile-heat-source:${profile.profileId.value}") {
                            CezveHeatSourceCard(
                                selectedHeatSource = state.cezveHeatSource,
                                onHeatSourceSelected = onCezveHeatSourceSelected,
                            )
                        }
                    }

                    item(key = "profile-capacity:${profile.profileId.value}") {
                        EquipmentCapacityCard(
                            capacityInput = state.selectedEquipmentCapacityInput,
                            capacityIsValid = state.selectedEquipmentCapacityG != null,
                            capacitySupportsRecipe = state.capacitySupportsSelectedRecipe,
                            requiredInputG = state.selectedRecipeInputG,
                            onCapacityChanged = onEquipmentCapacityChanged,
                        )
                    }

                    state.startSelection?.let { selection ->
                        onLearn?.let { learn ->
                            item(key = "profile-learn:${recipe.id.value}") {
                                OutlinedButton(
                                    onClick = { learn(selection) },
                                    enabled = !state.isStarting,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(text = stringResource(R.string.action_learn_this_recipe))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupSectionIntroduction(title: String, hint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun P1BrewerProfileActionBar(
    state: P1BrewerProfileSetupUiState,
    onStart: (P1BrewerProfileStartSelection) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Button(
            onClick = { state.startSelection?.let(onStart) },
            enabled = state.canStart && !state.isStarting,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = primaryActionButtonColors(),
        ) {
            if (state.isStarting) {
                LoadingIndicator(
                    modifier = Modifier.size(20.dp),
                    color = LocalContentColor.current,
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = if (state.isStarting) {
                    stringResource(R.string.label_brewer_profile_starting)
                } else {
                    stringResource(R.string.action_start_brewing)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ProfileSetupEmptyState() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.msg_brewer_profile_empty),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BrewerProfileOptionCard(
    option: P1BrewerProfileSetupOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 3.dp else 0.dp,
        ),
        shape = if (selected) {
            MaterialTheme.shapes.extraLarge
        } else {
            MaterialTheme.shapes.large
        },
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(
                modifier = Modifier.padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = localizedProfileName(option.profileId, option.displayName),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = localizedMethodFamilyName(option.profileId, option.methodFamilyName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExactRecipeSelectionCard(
    profile: P1BrewerProfileSetupOption,
    selectedRecipeId: BuiltInRecipeId?,
    onRecipeSelected: (BuiltInRecipeId) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.heading_exact_recipe_choose),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (profile.recipes.size == 1) {
                    stringResource(R.string.msg_exact_recipe_single_compatible)
                } else {
                    stringResource(R.string.msg_exact_recipe_choose_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.selectableGroup()) {
                profile.recipes.forEach { recipe ->
                    val selected = recipe.id == selectedRecipeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(
                                selected = selected,
                                onClick = { onRecipeSelected(recipe.id) },
                                enabled = profile.recipes.size > 1,
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text = exactRecipeName(recipe.id),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExactRecipeDetailsCard(recipe: BuiltInP1RecipeDefinition) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.heading_exact_recipe_details),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            DetailsRow(stringResource(R.string.label_coffee), formatMass(recipe.quantities.dryCoffeeDoseG))
            recipe.quantities.brewWaterInputG?.let { input ->
                DetailsRow(stringResource(R.string.label_brewer_profile_input_water), formatMass(input))
            }
            recipe.quantities.reservoirInputG?.let { input ->
                DetailsRow(stringResource(R.string.label_brewer_profile_input_reservoir_water), formatMass(input))
            }
            if (recipe.quantities.iceG > 0.0) {
                DetailsRow(stringResource(R.string.label_exact_recipe_brew_ice), formatMass(recipe.quantities.iceG))
            }
            recipe.ratios.forEachIndexed { index, ratio ->
                DetailsRow(
                    label = if (index == 0) {
                        stringResource(R.string.label_ratio)
                    } else {
                        stringResource(R.string.label_exact_recipe_combined_ratio)
                    },
                    value = stringResource(
                        R.string.format_exact_recipe_ratio,
                        ratio.ratioValue?.let(::formatNumber)
                            ?: stringResource(R.string.label_exact_recipe_unresolved),
                        ratioDenominatorLabel(ratio.includedDenominatorRoles),
                    ),
                )
            }
            DetailsRow(
                stringResource(R.string.label_temperature),
                temperatureLabel(recipe.temperature),
            )
            DetailsRow(stringResource(R.string.label_brew_time), timeLabel(recipe.expectedTime))
        }
    }
}

@Composable
internal fun DetailsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MeasuredReservoirInputCard(
    input: String,
    inputIsValid: Boolean,
    onInputChanged: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.label_brewer_profile_input_reservoir_water),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.msg_brewer_profile_input_reservoir_water),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                label = { Text(stringResource(R.string.label_measured_amount)) },
                suffix = { Text(stringResource(R.string.unit_grams)) },
                isError = input.isNotBlank() && !inputIsValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ExactEquipmentCard(
    recipe: BuiltInP1RecipeDefinition,
    selectedOption: P1EquipmentOption?,
    onOptionSelected: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.heading_exact_recipe_equipment),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (recipe.equipmentOptions.size == 1) {
                    stringResource(R.string.msg_exact_recipe_equipment_single)
                } else {
                    stringResource(R.string.msg_exact_recipe_equipment_choose)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(modifier = Modifier.selectableGroup()) {
                recipe.equipmentOptions.forEachIndexed { index, option ->
                    val selected = option == selectedOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(
                                selected = selected,
                                onClick = { onOptionSelected(index) },
                                enabled = recipe.equipmentOptions.size > 1,
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text = equipmentOptionLabel(option),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentCapacityCard(
    capacityInput: String,
    capacityIsValid: Boolean,
    capacitySupportsRecipe: Boolean,
    requiredInputG: Double?,
    onCapacityChanged: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.heading_brewer_profile_capacity),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.msg_brewer_profile_capacity_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            OutlinedTextField(
                value = capacityInput,
                onValueChange = onCapacityChanged,
                label = { Text(stringResource(R.string.label_brewer_profile_capacity_grams)) },
                isError = capacityInput.isNotBlank() && (!capacityIsValid || !capacitySupportsRecipe),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                !capacityIsValid -> Text(
                    text = stringResource(R.string.msg_brewer_profile_capacity_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                !capacitySupportsRecipe && requiredInputG != null -> Text(
                    text = stringResource(
                        R.string.format_exact_recipe_capacity_too_small,
                        formatMass(requiredInputG),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CezveOptionalChoicesCard(
    includeSugar: Boolean,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onSugarSelected: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            TextButton(
                onClick = onExpandedChange,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(
                    text = stringResource(R.string.heading_brewer_profile_cezve_choices),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.cd_collapse_advanced else R.string.cd_expand_advanced,
                    ),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.msg_exact_recipe_cezve_choices_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.label_brewer_profile_cezve_sugar),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(false, true).forEachIndexed { index, choice ->
                            SegmentedButton(
                                selected = includeSugar == choice,
                                onClick = { onSugarSelected(choice) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                label = {
                                    Text(
                                        stringResource(
                                            if (choice) {
                                                R.string.label_brewer_profile_cezve_with_sugar
                                            } else {
                                                R.string.label_brewer_profile_cezve_without_sugar
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CezveHeatSourceCard(
    selectedHeatSource: HeatSourceClass,
    onHeatSourceSelected: (HeatSourceClass) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.heading_brewer_profile_cezve_heat_source),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.msg_brewer_profile_cezve_heat_source_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(modifier = Modifier.selectableGroup()) {
                CEZVE_HEAT_SOURCES.forEach { heatSource ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = selectedHeatSource == heatSource,
                                onClick = { onHeatSourceSelected(heatSource) },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedHeatSource == heatSource, onClick = null)
                        Text(text = heatSourceLabel(heatSource), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (selectedHeatSource == HeatSourceClass.NONE) {
                Text(
                    text = stringResource(R.string.msg_brewer_profile_cezve_heat_source_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}


@Composable
internal fun temperatureLabel(temperature: P1TemperatureSemantics): String = when (temperature.basis) {
    P1TemperatureBasis.USER_EXACT -> stringResource(
        R.string.format_exact_recipe_temperature,
        formatNumber(requireNotNull(temperature.minimumC)),
    )
    P1TemperatureBasis.USER_RANGE -> temperatureRangeLabel(temperature, R.string.format_exact_recipe_temperature_range)
    P1TemperatureBasis.USER_APPROXIMATE_RANGE -> temperatureRangeLabel(
        temperature,
        R.string.format_exact_recipe_temperature_approximate_range,
    )
    P1TemperatureBasis.USER_STARTING_RANGE -> temperatureRangeLabel(
        temperature,
        R.string.format_exact_recipe_temperature_starting_range,
    )
    P1TemperatureBasis.HOT_UNSPECIFIED -> stringResource(R.string.label_exact_recipe_hot_unspecified)
    P1TemperatureBasis.MACHINE_CONTROLLED -> stringResource(R.string.label_exact_recipe_machine_controlled)
    P1TemperatureBasis.MACHINE_CONTROLLED_REPORTED_RANGE -> temperatureRangeLabel(
        temperature,
        R.string.format_exact_recipe_temperature_machine_range,
    )
    P1TemperatureBasis.COLD_START_OBSERVATION_CONTROLLED -> stringResource(
        R.string.label_exact_recipe_cold_observed,
    )
}

@Composable
private fun temperatureRangeLabel(temperature: P1TemperatureSemantics, resourceId: Int): String =
    stringResource(
        resourceId,
        formatNumber(requireNotNull(temperature.minimumC)),
        formatNumber(requireNotNull(temperature.maximumC)),
    )

@Composable
internal fun timeLabel(time: P1TimeSemantics): String = when (time.basis) {
    P1TimeBasis.APPROXIMATE -> stringResource(
        R.string.format_exact_recipe_time_approximate,
        formatDuration(requireNotNull(time.minimumSeconds)),
    )
    P1TimeBasis.RANGE -> stringResource(
        R.string.format_exact_recipe_time_range,
        formatDuration(requireNotNull(time.minimumSeconds)),
        formatDuration(requireNotNull(time.maximumSeconds)),
    )
    P1TimeBasis.PRACTICAL_STARTING_RANGE -> stringResource(
        R.string.format_exact_recipe_time_starting_range,
        formatDuration(requireNotNull(time.minimumSeconds)),
        formatDuration(requireNotNull(time.maximumSeconds)),
    )
    P1TimeBasis.APPROXIMATE_WITH_OBSERVATION -> stringResource(
        R.string.format_exact_recipe_time_approximate_observed,
        formatDuration(requireNotNull(time.minimumSeconds)),
    )
    P1TimeBasis.GEOMETRY_DEPENDENT -> stringResource(R.string.label_exact_recipe_time_geometry)
    P1TimeBasis.OBSERVATION_DEPENDENT -> stringResource(R.string.label_exact_recipe_time_observed)
    P1TimeBasis.MACHINE_SPECIFIC -> stringResource(R.string.label_exact_recipe_time_machine)
}

@Composable
internal fun ratioDenominatorLabel(roles: Set<QuantityRole>): String {
    val labels = mutableListOf<String>()
    for (role in roles.sortedBy(QuantityRole::ordinal)) {
        labels += when (role) {
            QuantityRole.BREW_WATER_INPUT -> stringResource(R.string.label_exact_recipe_brew_water)
            QuantityRole.RESERVOIR_INPUT -> stringResource(R.string.label_exact_recipe_reservoir_water)
            QuantityRole.ICE -> stringResource(R.string.label_exact_recipe_brew_ice)
            else -> stringResource(R.string.label_exact_recipe_unresolved)
        }
    }
    return labels.joinToString(stringResource(R.string.separator_exact_recipe_quantity_roles))
}

@Composable
internal fun equipmentOptionLabel(option: P1EquipmentOption): String {
    val parts = mutableListOf(filterSelectionLabel(option.filterSelection))
    option.basketId?.let { basketId -> parts += basketLabel(basketId) }
    for (accessoryId in option.accessoryIds.sortedBy { it.value }) {
        parts += accessoryLabel(accessoryId)
    }
    return parts.joinToString(stringResource(R.string.separator_exact_recipe_equipment_parts))
}

@Composable
private fun filterSelectionLabel(selection: FilterSelection): String = when (selection) {
    FilterSelection.IntentionallyUnfiltered -> stringResource(R.string.label_exact_equipment_unfiltered)
    FilterSelection.Unspecified -> stringResource(R.string.label_exact_recipe_unavailable)
    is FilterSelection.Stack -> {
        val labels = mutableListOf<String>()
        for (entry in selection.entries.sortedBy { it.position }) {
            labels += filterLabel(entry.filterProfileId)
        }
        labels.joinToString(stringResource(R.string.separator_exact_recipe_equipment_parts))
    }
}

@Composable
private fun filterLabel(id: FilterProfileId): String = stringResource(
    when (id.value) {
        "cone_paper" -> R.string.label_exact_equipment_cone_paper
        "moccamaster_number_four_cone_paper" -> R.string.label_exact_equipment_moccamaster_number_four_paper
        "wave_paper" -> R.string.label_exact_equipment_wave_paper
        "wedge_paper" -> R.string.label_exact_equipment_wedge_paper
        "chemex_six_cup_bonded_paper" -> R.string.label_exact_equipment_chemex_paper
        "flat_basket_paper" -> R.string.label_exact_equipment_flat_basket_paper
        "number_one_paper" -> R.string.label_exact_equipment_number_one_paper
        "phin_metal" -> R.string.label_exact_equipment_phin_metal
        else -> R.string.label_exact_recipe_unavailable
    },
)

@Composable
private fun basketLabel(id: BasketProfileId): String = stringResource(
    when (id.value) {
        "automatic_cone_basket" -> R.string.label_exact_equipment_cone_basket
        "automatic_flat_basket" -> R.string.label_exact_equipment_flat_basket
        "automatic_number_one_basket" -> R.string.label_exact_equipment_number_one_basket
        "moccamaster_kbgv_select_cone_basket" -> R.string.label_exact_equipment_moccamaster_kbgv_basket
        else -> R.string.label_exact_recipe_unavailable
    },
)

@Composable
private fun accessoryLabel(id: AccessoryProfileId): String = stringResource(
    when (id.value) {
        "phin_screw_insert" -> R.string.label_exact_equipment_phin_screw_insert
        "moccamaster_kbgv_glass_carafe" -> R.string.label_exact_equipment_moccamaster_glass_carafe
        "moccamaster_automatic_drip_stop" -> R.string.label_exact_equipment_moccamaster_drip_stop
        "moccamaster_half_full_selector" -> R.string.label_exact_equipment_moccamaster_selector
        else -> R.string.label_exact_recipe_unavailable
    },
)


@Composable
private fun heatSourceLabel(heatSource: HeatSourceClass): String = when (heatSource) {
    HeatSourceClass.HOB -> stringResource(R.string.label_brewer_profile_cezve_heat_hob)
    HeatSourceClass.OPEN_FLAME -> stringResource(R.string.label_brewer_profile_cezve_heat_open_flame)
    HeatSourceClass.PORTABLE_HEATER -> stringResource(R.string.label_brewer_profile_cezve_heat_portable_heater)
    HeatSourceClass.NONE, HeatSourceClass.ELECTRIC_MACHINE -> stringResource(
        R.string.label_exact_recipe_unavailable,
    )
}

@Composable
internal fun formatMass(value: Double): String = stringResource(
    R.string.format_brewer_profile_mass,
    formatNumber(value),
    stringResource(R.string.unit_grams),
)

private fun formatDuration(seconds: Int): String = "%d:%02d".format(
    Locale.getDefault(),
    seconds / 60,
    seconds % 60,
)

internal fun formatNumber(value: Double): String = NumberFormat.getNumberInstance(Locale.getDefault()).run {
    isGroupingUsed = false
    minimumFractionDigits = 0
    maximumFractionDigits = 3
    format(value)
}


private val CEZVE_HEAT_SOURCES = listOf(
    HeatSourceClass.HOB,
    HeatSourceClass.OPEN_FLAME,
    HeatSourceClass.PORTABLE_HEATER,
)
