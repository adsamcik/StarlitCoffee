package com.adsamcik.starlitcoffee.domain

import com.adsamcik.starlitcoffee.data.model.GrindContext
import com.adsamcik.starlitcoffee.data.model.GrindSaveScope
import com.adsamcik.starlitcoffee.data.model.GrindSettingSource
import com.adsamcik.starlitcoffee.data.model.Grinder
import com.adsamcik.starlitcoffee.data.model.GrinderScaleType
import com.adsamcik.starlitcoffee.data.model.RememberedGrind
import com.adsamcik.starlitcoffee.data.model.RememberedGrindValue
import java.math.BigDecimal

object GrindMemoryResolver {
    data class Resolved(val value: RememberedGrindValue, val source: GrindSettingSource)

    fun resolve(
        settings: List<RememberedGrind>,
        context: GrindContext,
        grinder: Grinder,
        packId: Long?,
        coffeeId: Long?,
        isDecaf: Boolean,
        temporary: RememberedGrind? = null,
    ): Resolved? {
        temporary?.takeIf { it.context == context && compatible(it.value, grinder) }?.let {
            return Resolved(it.value, GrindSettingSource.BREW)
        }
        val owners = listOf(
            Triple(GrindSaveScope.PACK, packId?.toString(), GrindSettingSource.PACK),
            Triple(GrindSaveScope.COFFEE, coffeeId?.toString(), GrindSettingSource.COFFEE),
            Triple(GrindSaveScope.TYPE, typeOwner(isDecaf), GrindSettingSource.TYPE),
        )
        owners.forEach { (scope, owner, source) ->
            settings.firstOrNull {
                owner != null && it.scope == scope && it.owner == owner &&
                    it.context == context && compatible(it.value, grinder)
            }?.let { return Resolved(it.value, source) }
        }
        return null
    }

    fun typeOwner(isDecaf: Boolean): String = if (isDecaf) "DECAF" else "REGULAR"

    fun parse(input: String, grinder: Grinder, now: Long): RememberedGrindValue? {
        val raw = input.trim().replace(',', '.')
        if (raw.length > 24 || !raw.matches(Regex("[0-9]+(?:\\.[0-9]+)?"))) return null
        return when (grinder.scaleType) {
            GrinderScaleType.DIAL_CLICKS -> dialClicks(raw, grinder, now)
            GrinderScaleType.PURE_CLICKS -> raw.toIntOrNull()?.takeIf { it in 0..MAX_CLICKS }?.let {
                RememberedGrindValue(grinder.scaleType.name, null, it, null, now)
            }
            GrinderScaleType.NUMBERED_DIAL -> raw.toBigDecimalOrNull()?.takeIf {
                it >= BigDecimal.ZERO && it <= BigDecimal(MAX_CLICKS)
            }?.let {
                RememberedGrindValue(grinder.scaleType.name, null, null, it.stripTrailingZeros().toPlainString(), now)
            }
        }
    }

    private fun dialClicks(raw: String, grinder: Grinder, now: Long): RememberedGrindValue? {
        val perRotation = grinder.clicksPerRotation?.takeIf { it > 0 } ?: return null
        val pieces = raw.split('.')
        val rotations = pieces[0].toLongOrNull() ?: return null
        val clicks = pieces.getOrNull(1)?.toIntOrNull() ?: 0
        if (rotations > MAX_CLICKS / perRotation || clicks !in 0 until perRotation) return null
        val total = rotations * perRotation + clicks
        if (total !in 0..MAX_CLICKS.toLong()) return null
        return RememberedGrindValue(grinder.scaleType.name, perRotation, total.toInt(), null, now)
    }

    fun compatible(value: RememberedGrindValue, grinder: Grinder): Boolean =
        value.scaleType == grinder.scaleType.name && when (grinder.scaleType) {
            GrinderScaleType.DIAL_CLICKS -> value.clicksPerRotation == grinder.clicksPerRotation &&
                (value.clicksPerRotation ?: 0) > 0 && value.totalClicks?.let { it in 0..MAX_CLICKS } == true
            GrinderScaleType.PURE_CLICKS -> value.totalClicks?.let { it in 0..MAX_CLICKS } == true
            GrinderScaleType.NUMBERED_DIAL -> value.dialValue?.toBigDecimalOrNull()?.let {
                it >= BigDecimal.ZERO && it <= BigDecimal(MAX_CLICKS)
            } == true
        }

    fun display(value: RememberedGrindValue): String = when (value.scaleType) {
        GrinderScaleType.DIAL_CLICKS.name -> {
            val perRotation = requireNotNull(value.clicksPerRotation)
            val clicks = requireNotNull(value.totalClicks)
            "${clicks / perRotation}.${clicks % perRotation}"
        }
        GrinderScaleType.PURE_CLICKS.name -> requireNotNull(value.totalClicks).toString()
        else -> requireNotNull(value.dialValue)
    }

    private const val MAX_CLICKS = 1_000_000
}
