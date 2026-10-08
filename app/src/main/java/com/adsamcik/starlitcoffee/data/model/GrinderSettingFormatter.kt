package com.adsamcik.starlitcoffee.data.model

import java.util.Locale
import kotlin.math.roundToInt

data class FormattedGrinderSetting(val primary: String, val unit: String? = null, val breakdown: String? = null)

/** Shared by preparation, summaries, favorites and brew logs so model notation cannot drift. */
object GrinderSettingFormatter {
    fun format(grinder: Grinder, value: Float): FormattedGrinderSetting {
        val dial = grinder.dial
        val setting = dial?.normalize(value) ?: value
        val notation = dial?.notation ?: when (grinder.scaleType) {
            GrinderScaleType.DIAL_CLICKS -> GrinderDialNotation.DECIMAL_CLICKS
            GrinderScaleType.PURE_CLICKS -> GrinderDialNotation.CLICK_COUNT
            GrinderScaleType.NUMBERED_DIAL -> GrinderDialNotation.NUMBERED
        }
        return when (notation) {
            GrinderDialNotation.DECIMAL_CLICKS -> decimalClicks(setting, dial?.numbersPerRotation ?: 9)
            GrinderDialNotation.CLICK_COUNT -> {
                val clicks = setting.roundToInt()
                FormattedGrinderSetting(clicks.toString(), clickLabel(clicks), "from zero")
            }
            GrinderDialNotation.FELLOW_ODE -> {
                val index = ((setting - 1f) * 3f).roundToInt()
                val number = 1 + index / 3
                val extra = index % 3
                FormattedGrinderSetting(
                    primary = if (extra == 0) "$number" else "$number.$extra",
                    breakdown = if (extra == 0) "on dial" else "$number + $extra ${clickLabel(extra)}",
                )
            }
            GrinderDialNotation.NUMBERED -> FormattedGrinderSetting(number(setting), breakdown = "on dial")
        }
    }

    fun label(grinder: Grinder, value: Float): String = format(grinder, value).let {
        listOfNotNull(it.primary, it.unit).joinToString(" ")
    }

    fun range(grinder: Grinder, recommendation: GrindRecommendation): String {
        val start = label(grinder, recommendation.rangeStart)
        val end = label(grinder, recommendation.rangeEnd)
        return if (recommendation.rangeStart == recommendation.rangeEnd) start else "$start–$end"
    }

    fun adjustment(grinder: Grinder, amount: Float): String {
        val step = grinder.dial?.stepSize
        return if (step != null) {
            val clicks = (amount / step).roundToInt()
            "$clicks ${clickLabel(clicks)}"
        } else {
            number(amount)
        }
    }

    private fun decimalClicks(value: Float, numbersPerRotation: Int): FormattedGrinderSetting {
        val total = (value * 10f).roundToInt()
        val clicksPerRotation = numbersPerRotation * 10
        val rotations = total / clicksPerRotation
        val remainder = total % clicksPerRotation
        val mark = "${remainder / 10}.${remainder % 10}"
        return FormattedGrinderSetting(
            primary = if (rotations == 0) mark else "$rotations × 360° · $mark",
            breakdown = "$total ${clickLabel(total)} from zero",
        )
    }

    private fun clickLabel(count: Int): String = if (count == 1) "click" else "clicks"

    private fun number(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.ROOT, "%.1f", value)
}
