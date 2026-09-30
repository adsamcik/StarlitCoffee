package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EquipmentLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun narrowLayoutAndDoubleTextSizeUseOneReadableColumn() {
        var selection: BrewMethod? = null
        composeRule.setContent { StarlitCoffeeTheme(dynamicColor = false) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    MethodChoiceGrid(BrewMethod.PULSAR, tagPrefix = "choice", onSelect = { selection = it })
                }
            }
        } }
        val pulsar = composeRule.onNodeWithTag("choice_PULSAR")
        val v60 = composeRule.onNodeWithTag("choice_V60")
        pulsar.assertIsSelected()
        val first = pulsar.fetchSemanticsNode().boundsInRoot
        val second = v60.fetchSemanticsNode().boundsInRoot
        assertEquals(first.left, second.left, 0.1f)
        assertTrue(second.top >= first.bottom)
        v60.performClick()
        composeRule.runOnIdle { assertEquals(BrewMethod.V60, selection) }
    }
}
