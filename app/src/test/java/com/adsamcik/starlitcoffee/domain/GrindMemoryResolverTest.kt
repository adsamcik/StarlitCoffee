package com.adsamcik.starlitcoffee.domain

import com.adsamcik.starlitcoffee.data.model.*
import org.junit.Assert.*
import org.junit.Test

class GrindMemoryResolverTest {
    private val grinder = Grinder("dial", "Maker", "Model", true, GrinderScaleType.DIAL_CLICKS, 40)
    private val context = GrindContext("dial", "V60", "PAPER")
    private fun setting(scope: GrindSaveScope, owner: String, text: String) =
        RememberedGrind(scope, owner, context, requireNotNull(GrindMemoryResolver.parse(text, grinder, 100L)))
    private fun resolve(settings: List<RememberedGrind>, temporary: RememberedGrind? = null, decaf: Boolean = false) =
        GrindMemoryResolver.resolve(settings, context, grinder, 5, 2, decaf, temporary)

    @Test fun `dial suffix is clicks and does not round a two digit value`() {
        val value = requireNotNull(GrindMemoryResolver.parse("1.14", grinder, 12))
        assertEquals(54, value.totalClicks)
        assertEquals("1.14", GrindMemoryResolver.display(value))
        assertEquals("1.4", GrindMemoryResolver.display(requireNotNull(GrindMemoryResolver.parse("1,04", grinder, 12))))
    }

    @Test fun `invalid click suffixes nonfinite values and overflow are rejected`() {
        listOf("1.40", "NaN", "Infinity", "-1", "1.4.5", "9223372036854775807.2", "25001.0").forEach {
            assertNull(it, GrindMemoryResolver.parse(it, grinder, 0))
        }
    }

    @Test fun `precedence and removing a narrower setting reveal the next source`() {
        val type = setting(GrindSaveScope.TYPE, "REGULAR", "2.3")
        val coffee = setting(GrindSaveScope.COFFEE, "2", "2.14")
        val pack = setting(GrindSaveScope.PACK, "5", "3.9")
        val temporary = setting(GrindSaveScope.BREW, "brew", "4.7")
        assertEquals(GrindSettingSource.BREW, resolve(listOf(type, coffee, pack), temporary)?.source)
        assertEquals(GrindSettingSource.PACK, resolve(listOf(type, coffee, pack))?.source)
        assertEquals(GrindSettingSource.COFFEE, resolve(listOf(type, coffee))?.source)
        assertEquals(GrindSettingSource.TYPE, resolve(listOf(type))?.source)
        assertNull(resolve(emptyList()))
    }

    @Test fun `regular and decaf defaults stay independent without offsets`() {
        val regular = setting(GrindSaveScope.TYPE, "REGULAR", "2.4")
        val decaf = setting(GrindSaveScope.TYPE, "DECAF", "2.7")
        assertEquals("2.4", GrindMemoryResolver.display(requireNotNull(resolve(listOf(regular, decaf))).value))
        assertEquals("2.7", GrindMemoryResolver.display(requireNotNull(resolve(listOf(regular, decaf), decaf = true)).value))
    }

    @Test fun `saved settings require the exact grinder method filter and scale`() {
        val saved = setting(GrindSaveScope.PACK, "5", "1.14")
        listOf(context.copy(grinderId = "other"), context.copy(methodId = "PULSAR"), context.copy(filterKey = "METAL")).forEach {
            assertNull(GrindMemoryResolver.resolve(listOf(saved), it, grinder, 5, 2, false))
        }
        assertNull(GrindMemoryResolver.resolve(listOf(saved), context, grinder.copy(clicksPerRotation = 60), 5, 2, false))
        assertNull(GrindMemoryResolver.resolve(listOf(saved), context, grinder.copy(scaleType = GrinderScaleType.NUMBERED_DIAL), 5, 2, false))
    }

    @Test fun `pure clicks reject fractions and numbered dials preserve precision`() {
        val clicks = grinder.copy(scaleType = GrinderScaleType.PURE_CLICKS, clicksPerRotation = null)
        assertNull(GrindMemoryResolver.parse("14.5", clicks, 0))
        assertEquals(14, GrindMemoryResolver.parse("14", clicks, 0)?.totalClicks)
        val dial = clicks.copy(scaleType = GrinderScaleType.NUMBERED_DIAL)
        assertEquals("1.125", GrindMemoryResolver.parse("1.12500", dial, 0)?.dialValue)
    }
}
