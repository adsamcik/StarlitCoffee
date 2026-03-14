package com.adsamcik.starlitcoffee.ui.component

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.util.CoffeeBagInsights
import com.adsamcik.starlitcoffee.util.ImagePreprocessor
import java.text.SimpleDateFormat
import java.util.Date

private const val TAG = "BagCard"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BagCard(
    bag: CoffeeBagEntity,
    dateFormat: SimpleDateFormat,
    onTap: () -> Unit,
    isRecommended: Boolean = false,
    brewsRemaining: Int? = null,
) {
    val freshness = remember(bag.roastDate) {
        CoffeeBagInsights.freshnessInsight(bag.roastDate)
    }
    val summary = remember(bag, freshness) {
        buildBagCardSummary(
            bag = bag,
            freshness = freshness,
        )
    }
    val subtitle = bag.roaster ?: bag.origin ?: bag.region
    val supportingText = bag.roastDate?.let { roastDate ->
        "Roasted ${dateFormat.format(Date(roastDate))} - ${summary.freshnessSupportingText}"
    }

    ElevatedCard(
        onClick = onTap,
        shape = MaterialTheme.shapes.large,
        colors = if (isRecommended) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        } else {
            CardDefaults.elevatedCardColors()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // "Brew next" badge for recommended bag
            if (isRecommended) {
                Text(
                    text = "☕ Brew this next",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                bag.photoUri?.let { uri ->
                    val bitmap = remember(uri) {
                        try {
                            val file = java.io.File(android.net.Uri.parse(uri).path ?: return@remember null)
                            val raw = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                ?: return@remember null
                            ImagePreprocessor.applyExifRotation(raw, file.absolutePath)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to load bag thumbnail", e)
                            null
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Bag photo",
                            modifier = Modifier
                                .size(68.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = bag.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InsightChip(
                            label = summary.statusLabel,
                            emphasis = summary.statusEmphasis,
                        )
                        InsightChip(
                            label = summary.freshnessLabel,
                            emphasis = summary.freshnessEmphasis,
                        )
                        if (bag.isDecaf) {
                            InsightChip(
                                label = "Decaf",
                                emphasis = ChipEmphasis.NEUTRAL,
                            )
                        }
                    }
                }
            }

            summary.warningText?.let { warningText ->
                Surface(
                    color = when (summary.stockEmphasis) {
                        ChipEmphasis.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                    contentColor = when (summary.stockEmphasis) {
                        ChipEmphasis.CRITICAL -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = warningText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.stockLabel,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = summary.stockSupportingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledTonalButton(onClick = onTap) {
                        Text(summary.primaryActionLabel)
                    }
                }
                summary.stockProgress?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = when (summary.stockEmphasis) {
                            ChipEmphasis.CRITICAL -> MaterialTheme.colorScheme.error
                            ChipEmphasis.WARNING -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                supportingText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (brewsRemaining != null) {
                    Text(
                        text = "~$brewsRemaining brews remaining at your usual dose",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}
