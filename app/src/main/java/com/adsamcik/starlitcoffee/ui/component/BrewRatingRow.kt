package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsamcik.starlitcoffee.data.model.BrewRating
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.ui.util.iconRes
import com.adsamcik.starlitcoffee.ui.util.labelRes
import com.adsamcik.starlitcoffee.ui.util.selectContentDescriptionRes

private val RatingFaceTouchTarget = 48.dp
private const val UnselectedFaceAlpha = 0.45f
private val SelectedFaceSize = 40.dp
private val UnselectedFaceSize = 34.dp

/**
 * One-tap 4-face rating selector — the single interactive rating control across
 * the app. Each custom coffee mark is its own radio-style target (min 48dp) with
 * a text content description and visible label. The selected tier is emphasized;
 * the rest are dimmed.
 */
@Composable
fun BrewRatingRow(
    selected: BrewRating?,
    onSelect: (BrewRating) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BrewRating.ordered.forEach { tier ->
            val isSelected = selected == tier
            val dimmed = selected != null && !isSelected
            val faceCd = stringResource(tier.selectContentDescriptionRes())
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(tier) },
                    )
                    .sizeIn(minWidth = RatingFaceTouchTarget, minHeight = RatingFaceTouchTarget)
                    .padding(vertical = 8.dp, horizontal = 2.dp)
                    .clearAndSetSemantics { contentDescription = faceCd },
            ) {
                Image(
                    painter = painterResource(tier.iconRes()),
                    contentDescription = null,
                    modifier = Modifier
                        .size(if (isSelected) SelectedFaceSize else UnselectedFaceSize)
                        .then(if (dimmed) Modifier.alpha(UnselectedFaceAlpha) else Modifier),
                )
                if (showLabels) {
                    Text(
                        text = stringResource(tier.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * Read-only single-face badge for a stored rating (list rows, share card,
 * detail header). Renders nothing when [rating] is null (unrated). The artwork
 * carries a text content description so screen readers announce the tier.
 */
@Composable
fun BrewRatingBadge(
    rating: BrewRating?,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    iconSize: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    rating ?: return
    val label = stringResource(rating.labelRes())
    val iconSizeDp = with(LocalDensity.current) { iconSize.toDp() }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(rating.iconRes()),
            contentDescription = null,
            modifier = Modifier
                .size(iconSizeDp)
                .then(
                    if (showLabel) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics { contentDescription = label }
                    },
                ),
        )
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Convenience overload for the stored float value. */
@Composable
fun BrewRatingBadge(
    ratingValue: Float?,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    iconSize: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    BrewRatingBadge(
        rating = BrewRating.fromStoredValue(ratingValue),
        modifier = modifier,
        showLabel = showLabel,
        iconSize = iconSize,
    )
}

/** Compact icon-and-count distribution, ordered from best rating to worst. */
@Composable
fun BrewRatingDistribution(
    counts: Map<BrewRating, Int>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrewRating.ordered.asReversed()
            .filter { counts.getOrDefault(it, 0) > 0 }
            .forEach { rating ->
                val count = counts.getValue(rating)
                val label = stringResource(rating.labelRes())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "$label, $count"
                    },
                ) {
                    Image(
                        painter = painterResource(rating.iconRes()),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "×$count",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
    }
}

@Preview(showBackground = true)
@Composable
private fun BrewRatingRowPreview() {
    StarlitCoffeeTheme {
        Surface {
            BrewRatingRow(selected = BrewRating.GOOD, onSelect = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
