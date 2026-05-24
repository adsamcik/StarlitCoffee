package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.data.model.HomeContextCard
import com.adsamcik.starlitcoffee.util.FreshnessPhase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeContextCardView(
    card: HomeContextCard,
    modifier: Modifier = Modifier,
) {
    when (card) {
        is HomeContextCard.BagAgeWisdom -> BagAgeWisdomCard(card, modifier)
        is HomeContextCard.FreshnessAlert -> FreshnessAlertCard(card, modifier)
        is HomeContextCard.CoachingTip -> CoachingTipCard(card, modifier)
        is HomeContextCard.OneTwist -> OneTwistCard(card, modifier)
        is HomeContextCard.LastBrewSummary -> LastBrewSummaryCard(card, modifier)
    }
}

@Composable
private fun BagAgeWisdomCard(
    card: HomeContextCard.BagAgeWisdom,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor, phaseEmoji) = when (card.phase) {
        FreshnessPhase.DEGASSING -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "🌱",
        )
        FreshnessPhase.MELLOWING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "🍂",
        )
        FreshnessPhase.VINTAGE -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "⏳",
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurface,
            "☕",
        )
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "$phaseEmoji ${card.bagName}",
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = card.headline,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            Text(
                text = card.grindAdvice,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = card.brewTip,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun FreshnessAlertCard(
    card: HomeContextCard.FreshnessAlert,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "☕ ${card.bagName}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = card.insight.headline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (card.insight.coachText.isNotBlank()) {
                Text(
                    text = card.insight.coachText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (card.brewsRemaining != null) {
                Text(
                    text = "~${card.brewsRemaining} brews left — use it up!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun CoachingTipCard(
    card: HomeContextCard.CoachingTip,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row {
                Text(
                    text = card.emoji,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = card.tip,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = card.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun OneTwistCard(
    card: HomeContextCard.OneTwist,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row {
                Text(
                    text = card.emoji,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = card.twistName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = card.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = card.rationale,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun LastBrewSummaryCard(
    card: HomeContextCard.LastBrewSummary,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val dateFormat = SimpleDateFormat("MMM d · h:mm a", locale)
    val timeStr = dateFormat.format(Date(card.brew.createdAt))

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "${card.ratingEmoji} Last brew was ${ratingLabel(card.brew.rating)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            val details = buildString {
                append("${"%.0f".format(card.brew.doseG)}g · 1:${"%.0f".format(card.brew.ratio)}")
                if (card.bagName != null) append(" · ${card.bagName}")
                append(" · $timeStr")
            }
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun ratingLabel(rating: Float?): String = when {
    rating == null -> "unrated"
    rating >= 4.5f -> "amazing"
    rating >= 3.5f -> "good"
    rating >= 2.5f -> "okay"
    else -> "not great"
}
