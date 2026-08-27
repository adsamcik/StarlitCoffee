package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import java.text.NumberFormat
import java.util.Locale

internal enum class BrewMeasurementInputIssue {
    EMPTY,
    INCOMPLETE,
    INVALID_WATER_INPUT,
    INVALID_BEVERAGE_OUTPUT,
    BEVERAGE_EXCEEDS_WATER,
}

internal data class BrewMeasurementInputValidation(
    val waterInputG: Float? = null,
    val beverageOutputG: Float? = null,
    val issue: BrewMeasurementInputIssue? = null,
) {
    val isValid: Boolean
        get() = waterInputG != null && beverageOutputG != null && issue == null
}

/** Parses locale-friendly decimal input without silently accepting partial samples. */
internal fun validateBrewMeasurements(
    waterInputText: String,
    beverageOutputText: String,
): BrewMeasurementInputValidation {
    val waterText = waterInputText.trim()
    val beverageText = beverageOutputText.trim()
    if (waterText.isEmpty() && beverageText.isEmpty()) {
        return BrewMeasurementInputValidation(issue = BrewMeasurementInputIssue.EMPTY)
    }
    if (waterText.isEmpty() || beverageText.isEmpty()) {
        return BrewMeasurementInputValidation(issue = BrewMeasurementInputIssue.INCOMPLETE)
    }

    val waterInputG = waterText.toMeasurementFloatOrNull()
    val beverageOutputG = beverageText.toMeasurementFloatOrNull()
    return when {
        waterInputG == null -> BrewMeasurementInputValidation(
            issue = BrewMeasurementInputIssue.INVALID_WATER_INPUT,
        )
        beverageOutputG == null -> BrewMeasurementInputValidation(
            issue = BrewMeasurementInputIssue.INVALID_BEVERAGE_OUTPUT,
        )
        beverageOutputG > waterInputG -> BrewMeasurementInputValidation(
            waterInputG = waterInputG,
            beverageOutputG = beverageOutputG,
            issue = BrewMeasurementInputIssue.BEVERAGE_EXCEEDS_WATER,
        )
        else -> BrewMeasurementInputValidation(
            waterInputG = waterInputG,
            beverageOutputG = beverageOutputG,
        )
    }
}

/**
 * Optional, advanced entry point for adding an actual water-in / beverage-output pair.
 * Estimates are shown as context but are never copied into the editable fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewMeasurementsBottomSheet(
    estimatedCoffeeDoseG: Float,
    estimatedWaterInputG: Float,
    estimatedBeverageOutputG: Float?,
    measuredWaterInputG: Float?,
    measuredBeverageOutputG: Float?,
    onDismissRequest: () -> Unit,
    onSaveMeasurements: (waterInputG: Float, beverageOutputG: Float) -> Unit,
    onRemoveMeasurements: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    saveError: String? = null,
) {
    var waterInputText by rememberSaveable(measuredWaterInputG) {
        mutableStateOf(formatMeasurementInput(measuredWaterInputG))
    }
    var beverageOutputText by rememberSaveable(measuredBeverageOutputG) {
        mutableStateOf(formatMeasurementInput(measuredBeverageOutputG))
    }
    val validation = validateBrewMeasurements(waterInputText, beverageOutputText)
    val hasExistingMeasurements = measuredWaterInputG != null || measuredBeverageOutputG != null
    val beverageFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val submit: () -> Unit = submit@{
        if (isSaving || !validation.isValid) return@submit
        val water = validation.waterInputG
        val beverage = validation.beverageOutputG
        if (water == null || beverage == null) return@submit
        focusManager.clearFocus()
        onSaveMeasurements(water, beverage)
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismissRequest() },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        modifier = modifier.testTag(BrewMeasurementSheetTestTag),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = BrewMeasurementSheetMaximumWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.brew_measurement_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.brew_measurement_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PlannedCoffeeEstimate(estimatedCoffeeDoseG)

                BrewMeasurementField(
                    value = waterInputText,
                    onValueChange = { waterInputText = it },
                    label = stringResource(R.string.brew_measurement_water_in),
                    supporting = waterSupportingText(validation, waterInputText, estimatedWaterInputG),
                    isError = validation.issue == BrewMeasurementInputIssue.INVALID_WATER_INPUT ||
                        validation.issue == BrewMeasurementInputIssue.INCOMPLETE && waterInputText.isBlank(),
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(
                        onNext = { beverageFocusRequester.requestFocus() },
                    ),
                    testTag = BrewMeasurementWaterInputTestTag,
                    enabled = !isSaving,
                )
                BrewMeasurementField(
                    value = beverageOutputText,
                    onValueChange = { beverageOutputText = it },
                    label = stringResource(R.string.brew_measurement_in_cup),
                    supporting = beverageSupportingText(
                        validation = validation,
                        beverageOutputText = beverageOutputText,
                        estimatedBeverageOutputG = estimatedBeverageOutputG,
                    ),
                    isError = validation.issue == BrewMeasurementInputIssue.INVALID_BEVERAGE_OUTPUT ||
                        validation.issue == BrewMeasurementInputIssue.BEVERAGE_EXCEEDS_WATER ||
                        validation.issue == BrewMeasurementInputIssue.INCOMPLETE && beverageOutputText.isBlank(),
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    testTag = BrewMeasurementBeverageOutputTestTag,
                    modifier = Modifier.focusRequester(beverageFocusRequester),
                    enabled = !isSaving,
                )

                if (saveError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Assertive },
                    ) {
                        Text(
                            text = saveError,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }

                Button(
                    onClick = submit,
                    enabled = validation.isValid && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BrewMeasurementSaveTestTag),
                ) {
                    Text(
                        text = stringResource(
                            if (isSaving) {
                                R.string.brew_measurement_saving
                            } else {
                                R.string.brew_measurement_save
                            },
                        ),
                    )
                }
                if (hasExistingMeasurements) {
                    TextButton(
                        onClick = onRemoveMeasurements,
                        enabled = !isSaving,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .testTag(BrewMeasurementRemoveTestTag),
                    ) {
                        Text(
                            text = stringResource(R.string.brew_measurement_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PlannedCoffeeEstimate(estimatedCoffeeDoseG: Float) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.brew_measurement_planned_coffee),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.brew_measurement_value_grams,
                    formatBrewQuantity(estimatedCoffeeDoseG),
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BrewMeasurementField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String,
    isError: Boolean,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions,
    testTag: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(stringResource(R.string.brew_measurement_unit_grams)) },
        supportingText = { Text(supporting) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction,
        ),
        keyboardActions = keyboardActions,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

@Composable
private fun waterSupportingText(
    validation: BrewMeasurementInputValidation,
    waterInputText: String,
    estimatedWaterInputG: Float,
): String = when {
    validation.issue == BrewMeasurementInputIssue.INVALID_WATER_INPUT ->
        stringResource(R.string.brew_measurement_error_positive_water)
    validation.issue == BrewMeasurementInputIssue.INCOMPLETE && waterInputText.isBlank() ->
        stringResource(R.string.brew_measurement_error_complete_pair)
    else -> stringResource(
        R.string.brew_measurement_planned_value,
        formatBrewQuantity(estimatedWaterInputG),
    )
}

@Composable
private fun beverageSupportingText(
    validation: BrewMeasurementInputValidation,
    beverageOutputText: String,
    estimatedBeverageOutputG: Float?,
): String = when {
    validation.issue == BrewMeasurementInputIssue.INVALID_BEVERAGE_OUTPUT ->
        stringResource(R.string.brew_measurement_error_positive_output)
    validation.issue == BrewMeasurementInputIssue.BEVERAGE_EXCEEDS_WATER ->
        stringResource(R.string.brew_measurement_error_output_exceeds_water)
    validation.issue == BrewMeasurementInputIssue.INCOMPLETE && beverageOutputText.isBlank() ->
        stringResource(R.string.brew_measurement_error_complete_pair)
    estimatedBeverageOutputG != null -> stringResource(
        R.string.brew_measurement_estimated_value,
        formatBrewQuantity(estimatedBeverageOutputG),
    )
    else -> stringResource(R.string.brew_measurement_estimate_unavailable)
}

private fun String.toMeasurementFloatOrNull(): Float? =
    replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }

internal fun formatMeasurementInput(
    value: Float?,
    locale: Locale = Locale.getDefault(),
): String = value
    ?.takeIf { it.isFinite() && it > 0f }
    ?.let { quantity ->
        NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = 3
        }.format(quantity)
    }
    .orEmpty()

internal const val BrewMeasurementSheetTestTag = "brew_measurement_sheet"
internal const val BrewMeasurementWaterInputTestTag = "brew_measurement_water_input"
internal const val BrewMeasurementBeverageOutputTestTag = "brew_measurement_beverage_output"
internal const val BrewMeasurementSaveTestTag = "brew_measurement_save"
internal const val BrewMeasurementRemoveTestTag = "brew_measurement_remove"

private val BrewMeasurementSheetMaximumWidth = 560.dp
