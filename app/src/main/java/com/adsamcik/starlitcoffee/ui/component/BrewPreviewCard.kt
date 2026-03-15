package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme

@Composable
fun BrewPreviewCard(
    coffeeG: Float,
    waterG: Float,
    ratio: Float,
    modifier: Modifier = Modifier,
    coffeeFormat: String = "%.0f",
    waterFormat: String = "%.0f",
    ratioFormat: String = "%.0f",
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                text = "☕ ${coffeeFormat.format(coffeeG)}g",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = "Coffee: ${coffeeFormat.format(coffeeG)} grams" },
            )
            Text(
                text = "💧 ${waterFormat.format(waterG)}g",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = "Water: ${waterFormat.format(waterG)} grams" },
            )
            Text(
                text = "1:${ratioFormat.format(ratio)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = "Ratio 1 to ${ratioFormat.format(ratio)}" },
            )
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
        )
    }
}
