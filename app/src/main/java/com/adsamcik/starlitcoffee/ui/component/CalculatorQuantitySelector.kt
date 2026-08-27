package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget

/**
 * Three independent, friendly quantity cards for choosing what the calculator edits.
 *
 * This intentionally uses ordinary Material surfaces. Each card owns its visual and interaction
 * state; there is no shared rail, moving overlay, custom canvas, shader, or idle animation.
 */
@Composable
fun CalculatorQuantitySelector(
    items: List<CalculatorQuantityCardItem>,
    selected: CalculatorQuantityTarget,
    onSelect: (CalculatorQuantityTarget) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    require(items.map { it.target } == CalculatorQuantityTargetOrder)

    val fontScale = LocalDensity.current.fontScale
    val useAccessibleStack = fontScale >= StackedLayoutFontScale
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SelectorTestTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (useAccessibleStack) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(CardSpacing),
            ) {
                items.forEach { item ->
                    QuantityCard(
                        item = item,
                        selected = item.target == selected,
                        onSelect = onSelect,
                        compact = compact,
                        horizontalContent = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AccessibleCardMinimumHeight),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) CompactCardHeight else RegularCardHeight)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(CardSpacing),
            ) {
                items.forEach { item ->
                    QuantityCard(
                        item = item,
                        selected = item.target == selected,
                        onSelect = onSelect,
                        compact = compact,
                        horizontalContent = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }

    }
}

@Composable
private fun QuantityCard(
    item: CalculatorQuantityCardItem,
    selected: Boolean,
    onSelect: (CalculatorQuantityTarget) -> Unit,
    compact: Boolean,
    horizontalContent: Boolean,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = quantityCardPalette(item.target, scheme)
    val activeSelection = selected && item.enabled
    val targetContainer = if (activeSelection) {
        palette.activeContainer
    } else {
        scheme.surfaceContainerLow
    }
    val targetPrimaryContent = when {
        !item.enabled -> scheme.onSurface.copy(alpha = DisabledContentAlpha)
        activeSelection -> palette.activeContent
        else -> scheme.onSurface
    }
    val targetSecondaryContent = when {
        !item.enabled -> scheme.onSurfaceVariant.copy(alpha = DisabledContentAlpha)
        activeSelection -> palette.activeContent
        else -> scheme.onSurfaceVariant
    }
    val targetIcon = when {
        !item.enabled -> scheme.onSurfaceVariant.copy(alpha = DisabledContentAlpha)
        activeSelection -> palette.activeContent
        else -> palette.activeContainer
    }
    val targetBorder = when {
        !item.enabled -> scheme.outlineVariant.copy(alpha = DisabledContentAlpha)
        activeSelection -> palette.activeContent.copy(alpha = SelectedBorderAlpha)
        else -> scheme.outlineVariant
    }

    val container by androidx.compose.animation.animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(CardColorDurationMillis, easing = FastOutSlowInEasing),
        label = "quantity_card_container",
    )
    val primaryContent by androidx.compose.animation.animateColorAsState(
        targetValue = targetPrimaryContent,
        animationSpec = tween(CardColorDurationMillis, easing = FastOutSlowInEasing),
        label = "quantity_card_primary_content",
    )
    val secondaryContent by androidx.compose.animation.animateColorAsState(
        targetValue = targetSecondaryContent,
        animationSpec = tween(CardColorDurationMillis, easing = FastOutSlowInEasing),
        label = "quantity_card_secondary_content",
    )
    val iconColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetIcon,
        animationSpec = tween(CardColorDurationMillis, easing = FastOutSlowInEasing),
        label = "quantity_card_icon",
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(CardColorDurationMillis, easing = FastOutSlowInEasing),
        label = "quantity_card_border",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (activeSelection) SelectedCornerRadius else RestingCornerRadius,
        animationSpec = tween(CardShapeDurationMillis, easing = FastOutSlowInEasing),
        label = "quantity_card_corner",
    )
    val elevation by animateDpAsState(
        targetValue = if (activeSelection) SelectedElevation else 0.dp,
        animationSpec = tween(CardShapeDurationMillis, easing = FastOutSlowInEasing),
        label = "quantity_card_elevation",
    )
    val shape = RoundedCornerShape(cornerRadius)

    Card(
        modifier = modifier
            .clip(shape)
            .testTag(calculatorQuantityCardTestTag(item.target))
            .selectable(
                selected = selected,
                enabled = item.enabled,
                role = Role.RadioButton,
                onClick = {
                    if (!selected) onSelect(item.target)
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${item.label}, ${item.spokenValue}"
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(CardBorderWidth, borderColor),
    ) {
        if (horizontalContent) {
            HorizontalCardContent(
                item = item,
                primaryContent = primaryContent,
                secondaryContent = secondaryContent,
                iconColor = iconColor,
            )
        } else {
            VerticalCardContent(
                item = item,
                compact = compact,
                primaryContent = primaryContent,
                secondaryContent = secondaryContent,
                iconColor = iconColor,
            )
        }
    }
}

@Composable
private fun VerticalCardContent(
    item: CalculatorQuantityCardItem,
    compact: Boolean,
    primaryContent: Color,
    secondaryContent: Color,
    iconColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(
                horizontal = if (compact) CompactHorizontalPadding else RegularHorizontalPadding,
                vertical = if (compact) CompactVerticalPadding else RegularVerticalPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CalculationQuantityIcon(
            icon = item.icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(if (compact) CompactIconSize else RegularIconSize),
        )
        Spacer(modifier = Modifier.height(if (compact) CompactIconGap else RegularIconGap))
        Text(
            text = item.label,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
            color = primaryContent,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(LabelValueGap))
        QuantityValue(
            item = item,
            color = secondaryContent,
            compact = compact,
        )
    }
}

@Composable
private fun HorizontalCardContent(
    item: CalculatorQuantityCardItem,
    primaryContent: Color,
    secondaryContent: Color,
    iconColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalculationQuantityIcon(
            icon = item.icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(AccessibleIconSize),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleSmall,
                color = primaryContent,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            QuantityValue(
                item = item,
                color = secondaryContent,
                compact = true,
            )
        }
    }
}

@Composable
private fun QuantityValue(
    item: CalculatorQuantityCardItem,
    color: Color,
    compact: Boolean,
) {
    val displayValue = if (item.approximate && item.value != MissingValue) {
        "$ApproximationSign${item.value}"
    } else {
        item.value
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = displayValue,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (item.value != MissingValue) {
            Text(
                text = "$ThinSpace$UnitSuffix",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 1.dp),
            )
        }
    }
}

private data class QuantityCardPalette(
    val activeContainer: Color,
    val activeContent: Color,
)

private fun quantityCardPalette(
    target: CalculatorQuantityTarget,
    scheme: ColorScheme,
): QuantityCardPalette = when (target) {
    CalculatorQuantityTarget.COFFEE -> QuantityCardPalette(
        activeContainer = scheme.primary,
        activeContent = scheme.onPrimary,
    )
    CalculatorQuantityTarget.WATER_IN -> QuantityCardPalette(
        activeContainer = scheme.secondary,
        activeContent = scheme.onSecondary,
    )
    CalculatorQuantityTarget.IN_CUP -> QuantityCardPalette(
        activeContainer = scheme.tertiary,
        activeContent = scheme.onTertiary,
    )
}

internal fun calculatorQuantityCardTestTag(target: CalculatorQuantityTarget): String =
    "quantity_card_${target.name.lowercase()}"

internal val CalculatorQuantityTargetOrder = listOf(
    CalculatorQuantityTarget.COFFEE,
    CalculatorQuantityTarget.WATER_IN,
    CalculatorQuantityTarget.IN_CUP,
)

private const val SelectorTestTag = "quantity_card_selector"
private const val StackedLayoutFontScale = 1.3f
private const val CardColorDurationMillis = 180
private const val CardShapeDurationMillis = 240
private const val DisabledContentAlpha = 0.38f
private const val SelectedBorderAlpha = 0.18f
private const val ApproximationSign = "≈"
private const val MissingValue = "—"
private const val ThinSpace = "\u2009"
private const val UnitSuffix = "g"
private val RegularCardHeight = 136.dp
private val CompactCardHeight = 108.dp
private val AccessibleCardMinimumHeight = 80.dp
private val CardSpacing = 8.dp
private val SelectedCornerRadius = 28.dp
private val RestingCornerRadius = 20.dp
private val SelectedElevation = 3.dp
private val CardBorderWidth = 1.dp
private val RegularHorizontalPadding = 8.dp
private val CompactHorizontalPadding = 6.dp
private val RegularVerticalPadding = 12.dp
private val CompactVerticalPadding = 8.dp
private val RegularIconSize = 30.dp
private val CompactIconSize = 24.dp
private val AccessibleIconSize = 28.dp
private val RegularIconGap = 10.dp
private val CompactIconGap = 6.dp
private val LabelValueGap = 4.dp
