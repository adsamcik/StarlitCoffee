package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun HalfStarRatingRow(
    rating: Float,
    onRatingChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        for (i in 1..5) {
            Box(
                modifier = Modifier.size(36.dp),
            ) {
                // Left half — half star
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 36.dp)
                        .align(Alignment.CenterStart)
                        .semantics { role = Role.Button }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onRatingChange(i - 0.5f) },
                )

                // Right half — full star
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 36.dp)
                        .align(Alignment.CenterEnd)
                        .semantics { role = Role.Button }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onRatingChange(i.toFloat()) },
                )

                val icon = when {
                    rating >= i -> Icons.Filled.Star
                    rating >= i - 0.5f -> Icons.AutoMirrored.Filled.StarHalf
                    else -> Icons.Outlined.Star
                }
                val tint = if (rating >= i - 0.5f) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Icon(
                    imageVector = icon,
                    contentDescription = "Star $i",
                    tint = tint,
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (rating > 0f) {
            Text(
                text = if (rating % 1f == 0f) {
                    "${rating.toInt()}.0"
                } else {
                    "%.1f".format(rating)
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
