package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidTriadGeometryTest {
    private val targets = CalculatorQuantityTarget.entries

    @Test
    fun `interpolation endpoints exactly match profiles`() {
        targets.forEach { sourceTarget ->
            targets.forEach { targetTarget ->
                val source = FluidTriadGeometry.profileFor(sourceTarget)
                val target = FluidTriadGeometry.profileFor(targetTarget)
                assertEquals(source, FluidTriadGeometry.interpolate(source, target, 0f))
                assertEquals(target, FluidTriadGeometry.interpolate(source, target, 1f))
            }
        }
    }

    @Test
    fun `all direct transitions remain finite and inside drawing envelope`() {
        val resolved = MutableFluidTriadVisualProfile()
        val points = FloatArray(FluidTriadGeometry.PathPointCount * 2)

        targets.forEach { sourceTarget ->
            targets.forEach { targetTarget ->
                if (sourceTarget == targetTarget) return@forEach
                val source = FluidTriadGeometry.profileFor(sourceTarget)
                val target = FluidTriadGeometry.profileFor(targetTarget)
                for (step in 0..100) {
                    val progress = step / 100f
                    FluidTriadGeometry.resolve(source, target, progress, resolved)
                    FluidTriadGeometry.writePathPoints(resolved, waterWave = 0.8f, out = points)
                    points.forEachIndexed { index, coordinate ->
                        assertTrue(
                            "$sourceTarget -> $targetTarget produced non-finite coordinate $index at $progress",
                            coordinate.isFinite(),
                        )
                        assertTrue(
                            "$sourceTarget -> $targetTarget exceeded drawing envelope at $progress: $coordinate",
                            coordinate in -0.02f..1.02f,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `interrupted transition rebases from current rendered profile`() {
        val coffee = FluidTriadGeometry.profileFor(CalculatorQuantityTarget.COFFEE)
        val water = FluidTriadGeometry.profileFor(CalculatorQuantityTarget.WATER_IN)
        val cup = FluidTriadGeometry.profileFor(CalculatorQuantityTarget.IN_CUP)

        val rendered = FluidTriadGeometry.interpolate(coffee, water, 0.43f)
        val restarted = FluidTriadGeometry.interpolate(rendered, cup, 0f)

        assertEquals(rendered, restarted)
    }
}
