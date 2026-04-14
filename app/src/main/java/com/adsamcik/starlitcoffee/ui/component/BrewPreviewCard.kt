package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewPreviewCard(
    coffeeG: Float,
    waterG: Float,
    ratio: Float,
    modifier: Modifier = Modifier,
    coffeeFormat: String = "%.0f",
    waterFormat: String = "%.0f",
    ratioFormat: String = "%.0f",
    bloomG: Float = 0f,
    timeTargetLowS: Int = 0,
    timeTargetHighS: Int = 0,
    predictedCupVolumeG: Float = 0f,
    retainedWaterG: Float = 0f,
) {
    var showRetentionInfo by remember { mutableStateOf(false) }

    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val coffeeFormatted = coffeeFormat.format(coffeeG)
                val coffeeCd = stringResource(R.string.cd_coffee_grams, coffeeFormatted)
                Text(
                    text = stringResource(R.string.format_coffee_grams, coffeeFormatted),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { contentDescription = coffeeCd },
                )
                val waterFormatted = waterFormat.format(waterG)
                val waterCd = stringResource(R.string.cd_water_grams, waterFormatted)
                Text(
                    text = stringResource(R.string.format_water_grams, waterFormatted),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { contentDescription = waterCd },
                )
                val ratioFormatted = ratioFormat.format(ratio)
                val ratioCd = stringResource(R.string.cd_ratio, ratioFormatted)
                Text(
                    text = stringResource(R.string.format_ratio, ratioFormatted),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = ratioCd },
                )
            }
            val showCupVolume = predictedCupVolumeG > 0f && predictedCupVolumeG < waterG
            if (bloomG > 0f || timeTargetLowS > 0 || showCupVolume) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (bloomG > 0f) {
                        Text(
                            text = stringResource(R.string.format_bloom_grams, bloomG),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (timeTargetLowS > 0 && timeTargetHighS > 0) {
                        val lowMin = timeTargetLowS / 60
                        val lowSec = timeTargetLowS % 60
                        val highMin = timeTargetHighS / 60
                        val highSec = timeTargetHighS % 60
                        Text(
                            text = stringResource(R.string.format_time_range, lowMin, lowSec, highMin, highSec),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showCupVolume) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.format_cup_volume, predictedCupVolumeG),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(
                                onClick = { showRetentionInfo = true },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = stringResource(R.string.cd_about_cup_volume),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRetentionInfo) {
        ModalBottomSheet(onDismissRequest = { showRetentionInfo = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = stringResource(R.string.label_water_retention),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.format_retention_explanation, coffeeG, retainedWaterG, waterG - retainedWaterG),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BrewPreviewCardPreview() {
    StarlitCoffeeTheme {
        BrewPreviewCard(
            coffeeG = 18f,
            waterG = 300f,
            ratio = 16.7f,
            bloomG = 50f,
            timeTargetLowS = 210,
            timeTargetHighS = 270,
            predictedCupVolumeG = 262f,
            retainedWaterG = 36f,
        )
    }
}
