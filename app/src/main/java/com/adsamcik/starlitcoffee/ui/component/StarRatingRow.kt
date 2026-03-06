package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme

@Composable
fun StarRatingRow(
    rating: Float,
    modifier: Modifier = Modifier,
    starSize: Dp = 16.dp,
) {
    Row(modifier = modifier) {
        for (i in 1..5) {
            val icon = when {
                rating >= i -> Icons.Filled.Star
                rating >= i - 0.5f -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (rating >= i - 0.5f) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(starSize),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StarRatingRowPreview() {
    StarlitCoffeeTheme {
        StarRatingRow(rating = 3.5f)
    }
}
