package com.adsamcik.starlitcoffee.ui.component

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareableBrewCard(
    brew: BrewLogEntity,
    bagName: String?,
    flavorTags: List<String>,
    modifier: Modifier = Modifier,
) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val ratingEmoji = when {
        brew.rating == null -> ""
        brew.rating >= 4.5f -> "🔥"
        brew.rating >= 3.0f -> "👍"
        else -> "☕"
    }
    val methodName = brew.method.lowercase().replaceFirstChar { it.uppercase() }
    val filterLabel = when (brew.filterType) {
        "PAPER" -> "Paper"
        "METAL_19K" -> "19K Metal"
        "METAL_40K" -> "40K Metal"
        else -> null
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            // Header: emoji + rating
            if (brew.rating != null && brew.rating > 0f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ratingEmoji,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StarRatingRow(rating = brew.rating, starSize = 20.dp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Method + bean name
            val decafLabel = stringResource(R.string.label_decaf)
            Text(
                text = if (brew.isDecaf) "$methodName · $decafLabel" else methodName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (bagName != null) {
                Text(
                    text = bagName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (brew.isDecaf) {
                Text(
                    text = stringResource(R.string.label_decaf),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recipe details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RecipeDetail(label = stringResource(R.string.label_coffee), value = "${"%.0f".format(brew.doseG)}g")
                RecipeDetail(label = stringResource(R.string.label_water), value = "${"%.0f".format(brew.waterG)}g")
                RecipeDetail(label = stringResource(R.string.label_ratio), value = "1:${"%.0f".format(brew.ratio)}")
                if (filterLabel != null) {
                    RecipeDetail(label = stringResource(R.string.label_filter), value = filterLabel)
                }
            }

            // Flavor tags
            if (flavorTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    flavorTags.take(4).forEach { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                    CircleShape,
                                )
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // Footer: date + branding
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dateFormat.format(Date(brew.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.label_starlit_coffee_brand),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecipeDetail(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shares brew details as a nicely formatted text message via the system share sheet.
 * Uses text sharing (universal, no bitmap issues) with a rich format.
 */
fun shareBrewCard(
    context: Context,
    brew: BrewLogEntity,
    bagName: String?,
    flavorTags: List<String>,
) {
    val methodName = brew.method.lowercase().replaceFirstChar { it.uppercase() }
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val dateStr = dateFormat.format(Date(brew.createdAt))
    val ratingEmoji = when {
        brew.rating == null -> ""
        brew.rating >= 4.5f -> "🔥"
        brew.rating >= 3.0f -> "👍"
        else -> "☕"
    }
    val stars = brew.rating?.let { r ->
        "★".repeat(r.toInt()) + if (r % 1 >= 0.5f) "½" else ""
    } ?: ""
    val filterLabel = when (brew.filterType) {
        "PAPER" -> "Paper"
        "METAL_19K" -> "19K Metal"
        "METAL_40K" -> "40K Metal"
        else -> null
    }

    val text = buildString {
        if (ratingEmoji.isNotEmpty()) append("$ratingEmoji ")
        append("$methodName brew")
        if (brew.isDecaf) append(" · Decaf")
        if (bagName != null) append(" · $bagName")
        appendLine()
        appendLine()
        append("☕ ${"%.0f".format(brew.doseG)}g  💧 ${"%.0f".format(brew.waterG)}g  📐 1:${"%.0f".format(brew.ratio)}")
        if (filterLabel != null) append("  🔽 $filterLabel")
        appendLine()
        if (stars.isNotEmpty()) {
            appendLine("$stars (${"%.1f".format(brew.rating)})")
        }
        if (flavorTags.isNotEmpty()) {
            appendLine(flavorTags.take(4).joinToString(" · "))
        }
        appendLine()
        append("$dateStr · Starlit Coffee ☕")
    }

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(
            Intent.EXTRA_SUBJECT,
            "$ratingEmoji My ${if (brew.isDecaf) "decaf " else ""}$methodName brew",
        )
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share your brew"))
}
