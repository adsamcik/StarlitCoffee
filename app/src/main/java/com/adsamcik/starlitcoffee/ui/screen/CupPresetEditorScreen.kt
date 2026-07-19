package com.adsamcik.starlitcoffee.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.ui.component.DestructiveActionDialog
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.util.PresetIcon
import com.adsamcik.starlitcoffee.ui.util.availablePresetIcons
import com.adsamcik.starlitcoffee.ui.util.presetColorPalette
import com.adsamcik.starlitcoffee.viewmodel.CupPresetEditorOperation
import com.adsamcik.starlitcoffee.viewmodel.CupPresetEditorFailure
import com.adsamcik.starlitcoffee.viewmodel.CupPresetEditorViewModel

private val ScreenHorizontalPadding = 16.dp
private val ScreenTopPadding = 24.dp
private val ScreenBottomPadding = 24.dp
private val SectionGap = 20.dp
private val CardPadding = 16.dp
private val FieldGap = 12.dp
private val PreviewIconSize = 56.dp
private val PreviewBadgeSize = 16.dp
private val PreviewBorderWidth = 2.dp
private val IconTileSize = 64.dp
private val IconGlyphSize = 44.dp
private val IconTileGap = 8.dp
private val ColorTileSize = 48.dp
private val ColorTileGap = 8.dp
private val ColorCheckSize = 22.dp
private val ColorBorderWidth = 3.dp
private val IconTileBorderWidth = 2.dp
private const val LightLuminanceThreshold = 0.5f
private const val DefaultIconName = "mug"
private val TileCornerRadius = 16.dp

@Composable
fun CupPresetEditorScreen(
    viewModel: CupPresetEditorViewModel,
    presetId: Long?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val loadedPresets by viewModel.presets.collectAsStateWithLifecycle(initialValue = null)
    val operationState by viewModel.uiState.collectAsStateWithLifecycle()
    val presets = loadedPresets.orEmpty()
    val existingPreset = remember(presetId, presets) {
        presetId?.let { id -> presets.find { it.id == id } }
    }
    val isEditMode = presetId != null

    var name by rememberSaveable(presetId) { mutableStateOf("") }
    var waterMl by rememberSaveable(presetId) { mutableStateOf("") }
    var selectedIcon by rememberSaveable(presetId) { mutableStateOf(DefaultIconName) }
    var selectedColor by rememberSaveable(presetId) { mutableStateOf<String?>(null) }
    var hydrated by rememberSaveable(presetId) { mutableStateOf(false) }
    var initialName by rememberSaveable(presetId) { mutableStateOf("") }
    var initialWaterMl by rememberSaveable(presetId) { mutableStateOf("") }
    var initialIcon by rememberSaveable(presetId) { mutableStateOf(DefaultIconName) }
    var initialColor by rememberSaveable(presetId) { mutableStateOf<String?>(null) }
    var showValidation by rememberSaveable(presetId) { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable(presetId) { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable(presetId) { mutableStateOf(false) }
    val interactionsEnabled = areCupPresetEditorInteractionsEnabled(
        operation = operationState.operation,
        completionPending = operationState.completion != null,
    )
    val isBusy = !interactionsEnabled
    val presetMissing = isEditMode && loadedPresets != null && existingPreset == null
    val isLoadingPreset = shouldBlockPresetEditor(
        presetId = presetId,
        presetsLoaded = loadedPresets != null,
        presetExists = existingPreset != null,
        hydrated = hydrated,
    )

    LaunchedEffect(existingPreset?.id) {
        val preset = existingPreset
        if (preset != null && !hydrated) {
            name = preset.name
            waterMl = preset.waterMl.toInt().toString()
            selectedIcon = preset.iconName
            selectedColor = preset.colorHex
            initialName = preset.name
            initialWaterMl = preset.waterMl.toInt().toString()
            initialIcon = preset.iconName
            initialColor = preset.colorHex
            hydrated = true
        }
    }
    LaunchedEffect(presetMissing) {
        if (presetMissing) onBack()
    }
    LaunchedEffect(operationState.completion) {
        if (operationState.completion != null) {
            showDeleteDialog = false
            viewModel.consumeCompletion()
            onBack()
        }
    }
    LaunchedEffect(operationState.failure) {
        if (operationState.failure == CupPresetEditorFailure.SAVE) {
            Toast.makeText(context, R.string.msg_could_not_save_changes, Toast.LENGTH_LONG).show()
            viewModel.consumeFailure()
        }
    }

    val parsedWater = waterMl.toFloatOrNull()
    val nameValid = name.isNotBlank()
    val waterValid = parsedWater != null && parsedWater > 0f
    val canSave = nameValid && waterValid
    val hasUnsavedChanges = if (isEditMode && hydrated) {
        name != initialName ||
            waterMl != initialWaterMl ||
            selectedIcon != initialIcon ||
            selectedColor != initialColor
    } else if (isEditMode) {
        false
    } else {
        name.isNotEmpty() ||
            waterMl.isNotEmpty() ||
            selectedIcon != DefaultIconName ||
            selectedColor != null
    }
    val requestBack = {
        if (!isBusy) {
            if (hasUnsavedChanges) showDiscardDialog = true else onBack()
        }
    }

    BackHandler(onBack = requestBack)

    if (showDiscardDialog) {
        DestructiveActionDialog(
            titleRes = R.string.dialog_discard_changes_title,
            messageRes = R.string.msg_discard_changes_body,
            confirmLabelRes = R.string.action_discard,
            onConfirm = {
                showDiscardDialog = false
                onBack()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }

    if (isLoadingPreset || presetMissing) {
        CupPresetLoadingScreen(onBack = requestBack)
        return
    }

    if (showDeleteDialog && existingPreset != null) {
        DestructiveActionDialog(
            titleRes = R.string.action_delete,
            confirmLabelRes = R.string.action_delete,
            messageRes = if (operationState.failure == CupPresetEditorFailure.DELETE) {
                R.string.msg_could_not_delete
            } else {
                R.string.dialog_delete_cup_preset_message
            },
            enabled = interactionsEnabled,
            onConfirm = {
                if (isBusy) return@DestructiveActionDialog
                viewModel.deletePreset(existingPreset)
            },
            onDismiss = {
                showDeleteDialog = false
                if (operationState.failure == CupPresetEditorFailure.DELETE) {
                    viewModel.consumeFailure()
                }
            },
        )
    }

    val title = if (isEditMode) {
        stringResource(R.string.screen_edit_cup_preset_title)
    } else {
        stringResource(R.string.screen_add_cup_preset_title)
    }
    val saveActionLabel = if (isEditMode) {
        stringResource(R.string.action_save_simple)
    } else {
        stringResource(R.string.action_add)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(top = ScreenTopPadding),
    ) {
        ScreenTopBar(
            title = title,
            onBack = requestBack,
            backEnabled = interactionsEnabled,
            actions = {
                TextButton(
                    onClick = {
                        if (isBusy) return@TextButton
                        if (!canSave) {
                            showValidation = true
                            return@TextButton
                        }
                        val water = checkNotNull(parsedWater)
                        val preset = existingPreset?.copy(
                            name = name.trim(),
                            waterMl = water,
                            iconName = selectedIcon,
                            colorHex = selectedColor,
                        ) ?: CupPreset(
                            name = name.trim(),
                            iconName = selectedIcon,
                            doseG = 0f,
                            waterMl = water,
                            sortOrder = presets.size,
                            colorHex = selectedColor,
                        )
                        viewModel.savePreset(preset, isNew = existingPreset == null)
                    },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("cup_preset_save"),
                ) {
                    Text(saveActionLabel)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = SectionGap, bottom = ScreenBottomPadding),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
        ) {
            PreviewCard(
                name = name.ifBlank { stringResource(R.string.label_name) },
                iconName = selectedIcon,
                colorHex = selectedColor,
            )

            DetailsCard(
                name = name,
                onNameChange = {
                    name = it
                    if (showValidation) showValidation = false
                },
                waterMl = waterMl,
                onWaterChange = {
                    waterMl = it
                    if (showValidation) showValidation = false
                },
                showNameError = showValidation && !nameValid,
                showWaterError = showValidation && !waterValid,
                enabled = interactionsEnabled,
            )

            IconPickerCard(
                selectedIcon = selectedIcon,
                onSelect = { selectedIcon = it },
                enabled = interactionsEnabled,
            )

            ColorPickerCard(
                selectedColor = selectedColor,
                onSelect = { selectedColor = it },
                enabled = interactionsEnabled,
            )

            if (existingPreset != null) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cup_preset_delete"),
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

        }
    }
}

internal fun shouldBlockPresetEditor(
    presetId: Long?,
    presetsLoaded: Boolean,
    presetExists: Boolean,
    hydrated: Boolean = false,
): Boolean = presetId != null && (!presetsLoaded || (presetExists && !hydrated))

internal fun areCupPresetEditorInteractionsEnabled(
    operation: CupPresetEditorOperation,
    completionPending: Boolean,
): Boolean = operation == CupPresetEditorOperation.IDLE && !completionPending

@Composable
private fun CupPresetLoadingScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(top = ScreenTopPadding),
    ) {
        ScreenTopBar(
            title = stringResource(R.string.screen_edit_cup_preset_title),
            onBack = onBack,
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator()
        }
    }
}

@Composable
private fun PreviewCard(
    name: String,
    iconName: String,
    colorHex: String?,
) {
    val dotColor = colorHex?.let {
        runCatching { Color(it.toColorInt()) }.getOrNull()
    } ?: MaterialTheme.colorScheme.secondaryContainer
    val previewDescription = stringResource(R.string.cd_cup_preset_preview, name)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = previewDescription },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardPadding),
            horizontalArrangement = Arrangement.spacedBy(CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                PresetIcon(
                    iconName = iconName,
                    contentDescription = null,
                    modifier = Modifier.size(PreviewIconSize),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(PreviewBadgeSize)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(PreviewBorderWidth, MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DetailsCard(
    name: String,
    onNameChange: (String) -> Unit,
    waterMl: String,
    onWaterChange: (String) -> Unit,
    showNameError: Boolean,
    showWaterError: Boolean,
    enabled: Boolean,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(FieldGap),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.label_name)) },
                singleLine = true,
                isError = showNameError,
                enabled = enabled,
                supportingText = if (showNameError) {
                    { Text(stringResource(R.string.msg_cup_preset_name_required)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = waterMl,
                onValueChange = onWaterChange,
                label = { Text(stringResource(R.string.label_volume_ml)) },
                singleLine = true,
                isError = showWaterError,
                enabled = enabled,
                supportingText = {
                    if (showWaterError) {
                        Text(stringResource(R.string.msg_cup_preset_volume_required))
                    } else {
                        Text(stringResource(R.string.msg_dose_from_ratio))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPickerCard(
    selectedIcon: String,
    onSelect: (String) -> Unit,
    enabled: Boolean,
) {
    val iconLabels = stringArrayResource(R.array.preset_icon_labels)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding)) {
            Text(
                text = stringResource(R.string.label_icon),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(FieldGap))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(IconTileGap),
                verticalArrangement = Arrangement.spacedBy(IconTileGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                availablePresetIcons.forEachIndexed { index, iconName ->
                    IconTile(
                        iconName = iconName,
                        label = iconLabels[index],
                        isSelected = iconName == selectedIcon,
                        onClick = { onSelect(iconName) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun IconTile(
    iconName: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val background = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(IconTileSize)
            .clip(RoundedCornerShape(TileCornerRadius))
            .background(background)
            .border(IconTileBorderWidth, borderColor, RoundedCornerShape(TileCornerRadius))
            .selectable(
                selected = isSelected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        PresetIcon(
            iconName = iconName,
            contentDescription = null,
            modifier = Modifier.size(IconGlyphSize),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerCard(
    selectedColor: String?,
    onSelect: (String?) -> Unit,
    enabled: Boolean,
) {
    val colorLabels = stringArrayResource(R.array.preset_color_labels)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CardPadding)) {
            Text(
                text = stringResource(R.string.label_color),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(FieldGap))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ColorTileGap),
                verticalArrangement = Arrangement.spacedBy(ColorTileGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                presetColorPalette.forEachIndexed { index, hex ->
                    ColorTile(
                        hex = hex,
                        label = colorLabels[index],
                        isSelected = hex == selectedColor,
                        onSelect = { onSelect(hex) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorTile(
    hex: String?,
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean,
) {
    val fallback = MaterialTheme.colorScheme.secondaryContainer
    val circleColor = if (hex != null) {
        runCatching { Color(hex.toColorInt()) }.getOrDefault(fallback)
    } else {
        fallback
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(ColorTileSize)
            .clip(CircleShape)
            .background(circleColor)
            .border(ColorBorderWidth, borderColor, CircleShape)
            .selectable(
                selected = isSelected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            val checkColor = if (circleColor.luminance() > LightLuminanceThreshold) {
                Color.Black
            } else {
                Color.White
            }
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = checkColor,
                modifier = Modifier.size(ColorCheckSize),
            )
        }
    }
}
