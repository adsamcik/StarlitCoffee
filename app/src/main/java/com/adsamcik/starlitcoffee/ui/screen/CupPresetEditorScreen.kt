package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.util.PresetIcon
import com.adsamcik.starlitcoffee.ui.util.availablePresetIcons
import com.adsamcik.starlitcoffee.ui.util.presetColorPalette
import kotlinx.coroutines.launch

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
    cupPresetRepository: CupPresetRepository,
    presetId: Long?,
    onBack: () -> Unit,
) {
    val presets by cupPresetRepository.presets.collectAsStateWithLifecycle(initialValue = emptyList())
    val existingPreset = remember(presetId, presets) {
        presetId?.let { id -> presets.find { it.id == id } }
    }
    val isEditMode = presetId != null
    val scope = rememberCoroutineScope()

    var name by rememberSaveable(presetId) { mutableStateOf("") }
    var waterMl by rememberSaveable(presetId) { mutableStateOf("") }
    var selectedIcon by rememberSaveable(presetId) { mutableStateOf(DefaultIconName) }
    var selectedColor by rememberSaveable(presetId) { mutableStateOf<String?>(null) }
    var hydrated by rememberSaveable(presetId) { mutableStateOf(false) }
    var showValidation by rememberSaveable(presetId) { mutableStateOf(false) }

    LaunchedEffect(existingPreset?.id) {
        val preset = existingPreset
        if (preset != null && !hydrated) {
            name = preset.name
            waterMl = preset.waterMl.toInt().toString()
            selectedIcon = preset.iconName
            selectedColor = preset.colorHex
            hydrated = true
        }
    }

    val parsedWater = waterMl.toFloatOrNull()
    val nameValid = name.isNotBlank()
    val waterValid = parsedWater != null && parsedWater > 0f
    val canSave = nameValid && waterValid

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
            onBack = onBack,
            actions = {
                TextButton(
                    onClick = {
                        if (!canSave) {
                            showValidation = true
                            return@TextButton
                        }
                        val water = checkNotNull(parsedWater)
                        scope.launch {
                            if (existingPreset != null) {
                                cupPresetRepository.updatePreset(
                                    existingPreset.copy(
                                        name = name.trim(),
                                        waterMl = water,
                                        iconName = selectedIcon,
                                        colorHex = selectedColor,
                                    ),
                                )
                            } else {
                                cupPresetRepository.addPreset(
                                    CupPreset(
                                        name = name.trim(),
                                        iconName = selectedIcon,
                                        doseG = 0f,
                                        waterMl = water,
                                        sortOrder = presets.size,
                                        colorHex = selectedColor,
                                    ),
                                )
                            }
                            onBack()
                        }
                    },
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
            )

            IconPickerCard(
                selectedIcon = selectedIcon,
                onSelect = { selectedIcon = it },
            )

            ColorPickerCard(
                selectedColor = selectedColor,
                onSelect = { selectedColor = it },
            )

            if (existingPreset != null) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            cupPresetRepository.deletePreset(existingPreset)
                            onBack()
                        }
                    },
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
) {
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
                availablePresetIcons.forEach { (iconName, label) ->
                    IconTile(
                        iconName = iconName,
                        label = label,
                        isSelected = iconName == selectedIcon,
                        onClick = { onSelect(iconName) },
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
            .clickable(onClick = onClick)
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
) {
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
                presetColorPalette.forEach { (hex, label) ->
                    ColorTile(
                        hex = hex,
                        label = label,
                        isSelected = hex == selectedColor,
                        onSelect = { onSelect(hex) },
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
            .clickable(onClick = onSelect)
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
