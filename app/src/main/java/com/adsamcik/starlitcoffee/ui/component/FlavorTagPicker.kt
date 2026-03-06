package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.data.model.FlavorDescriptor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlavorTagPicker(
    selectedTags: Set<FlavorDescriptor>,
    onTagToggle: (FlavorDescriptor) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        FlavorDescriptor.entries.forEach { descriptor ->
            val selected = descriptor in selectedTags
            FilterChip(
                selected = selected,
                onClick = { onTagToggle(descriptor) },
                label = {
                    Text("${descriptor.emoji} ${descriptor.displayName}")
                },
                shape = MaterialTheme.shapes.large,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FlavorTagPickerPreview() {
    StarlitCoffeeTheme {
        FlavorTagPicker(
            selectedTags = setOf(FlavorDescriptor.entries.first()),
            onTagToggle = {},
        )
    }
}
