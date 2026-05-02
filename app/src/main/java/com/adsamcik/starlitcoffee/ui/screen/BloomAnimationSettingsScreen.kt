package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.component.BloomSpritesheetFinalFramePreview
import com.adsamcik.starlitcoffee.ui.component.BloomSpritesheetOption
import com.adsamcik.starlitcoffee.ui.component.BloomSpritesheetOptions
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import kotlinx.coroutines.launch

private val SettingsScreenHorizontalPadding = 16.dp
private val SettingsScreenTopPadding = 24.dp
private val SettingsListTopPadding = 16.dp
private val SettingsListBottomPadding = 24.dp
private val BloomRowContentPadding = 16.dp
private val BloomRowGap = 16.dp
private val BloomRowTextGap = 4.dp
private val BloomPreviewSize = 64.dp

@Composable
fun BloomAnimationSettingsScreen(
    userPreferencesRepository: UserPreferencesRepository,
    onBack: () -> Unit,
) {
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(
        initialValue = UserPreferences(),
    )
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SettingsScreenHorizontalPadding)
            .padding(top = SettingsScreenTopPadding),
    ) {
        ScreenTopBar(
            title = stringResource(R.string.screen_bloom_animation_settings_title),
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = SettingsListTopPadding,
                bottom = SettingsListBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(BloomRowGap),
        ) {
            item {
                Text(
                    text = stringResource(R.string.msg_bloom_animation_settings_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(
                items = BloomSpritesheetOptions,
                key = { option -> option.id },
            ) { option ->
                BloomSpritesheetRow(
                    option = option,
                    weight = prefs.bloomSpritesheetWeights[option.id] ?: 1,
                    onCycleWeight = {
                        val nextWeight = nextBloomSpritesheetWeight(
                            prefs.bloomSpritesheetWeights[option.id] ?: 1,
                        )
                        scope.launch {
                            userPreferencesRepository.updateBloomSpritesheetWeights(
                                prefs.bloomSpritesheetWeights + (option.id to nextWeight),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BloomSpritesheetRow(
    option: BloomSpritesheetOption,
    weight: Int,
    onCycleWeight: () -> Unit,
) {
    val label = stringResource(option.labelRes)
    val stateLabel = bloomSpritesheetWeightLabel(weight)
    val contentDescription = stringResource(
        R.string.format_bloom_spritesheet_weight_cd,
        label,
        stateLabel,
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(BloomRowContentPadding),
            horizontalArrangement = Arrangement.spacedBy(BloomRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BloomSpritesheetFinalFramePreview(
                option = option,
                contentDescription = null,
                modifier = Modifier
                    .size(BloomPreviewSize)
                    .clip(MaterialTheme.shapes.medium),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = BloomRowGap),
                verticalArrangement = Arrangement.spacedBy(BloomRowTextGap),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(option.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onCycleWeight,
                modifier = Modifier.semantics {
                    this.contentDescription = contentDescription
                },
            ) {
                Text(stateLabel)
            }
        }
    }
}

@Composable
private fun bloomSpritesheetWeightLabel(weight: Int): String {
    return when (weight.coerceIn(0, 2)) {
        2 -> stringResource(R.string.label_bloom_weight_2x)
        0 -> stringResource(R.string.label_bloom_weight_0x)
        else -> stringResource(R.string.label_bloom_weight_1x)
    }
}

private fun nextBloomSpritesheetWeight(weight: Int): Int {
    return when (weight.coerceIn(0, 2)) {
        1 -> 2
        2 -> 0
        else -> 1
    }
}
