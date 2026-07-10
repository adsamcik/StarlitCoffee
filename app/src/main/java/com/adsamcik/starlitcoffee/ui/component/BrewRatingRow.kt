package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adsamcik.starlitcoffee.data.model.BrewRating
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.ui.util.labelRes
import com.adsamcik.starlitcoffee.ui.util.selectContentDescriptionRes

private val RatingFaceTouchTarget = 48.dp
private val UnselectedFaceAlpha = 0.45f
private val SelectedFaceSize = 34.sp
private val UnselectedFaceSize = 28.sp

/**
 * One-tap 4-face rating selector — the single interactive rating control across
 * the app. Each face is its own radio-style target (min 48dp) with a text
 * content description, so it is usable and accessible without relying on emoji
 * glyphs alone. The selected tier is emphasized; the rest are dimmed.
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
                Text(
                    text = tier.emoji,
                    fontSize = if (isSelected) SelectedFaceSize else UnselectedFaceSize,
                    modifier = if (dimmed) Modifier.alpha(UnselectedFaceAlpha) else Modifier,
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
 * detail header). Renders nothing when [rating] is null (unrated). The emoji
 * carries a text content description so screen readers announce the tier.
 */
@Composable
fun BrewRatingBadge(
    rating: BrewRating?,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    emojiSize: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    rating ?: return
    val label = stringResource(rating.labelRes())
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = rating.emoji,
            fontSize = emojiSize,
            modifier = if (showLabel) Modifier else Modifier.clearAndSetSemantics { contentDescription = label },
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
    emojiSize: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    BrewRatingBadge(
        rating = BrewRating.fromStoredValue(ratingValue),
        modifier = modifier,
        showLabel = showLabel,
        emojiSize = emojiSize,
    )
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
