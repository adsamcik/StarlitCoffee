package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
