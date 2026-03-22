package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.data.model.RatioPreset

@Composable
fun RatioPresetRow(
    presets: List<RatioPreset>,
    selectedIndex: Int,
    onSelectPreset: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        presets.forEachIndexed { index, preset ->
            SegmentedButton(
                selected = selectedIndex == index,
                onClick = { onSelectPreset(index) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = presets.size,
                ),
            ) {
                Text(preset.label)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RatioPresetRowPreview() {
    StarlitCoffeeTheme {
        RatioPresetRow(
            presets = listOf(
                RatioPreset(ratio = 16f, label = "Bright · 1:16"),
                RatioPreset(ratio = 17f, label = "Balanced · 1:17", isDefault = true),
                RatioPreset(ratio = 18f, label = "Rich · 1:18"),
            ),
            selectedIndex = 1,
            onSelectPreset = {},
        )
    }
}
