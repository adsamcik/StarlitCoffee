package com.adsamcik.starlitcoffee.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.adsamcik.starlitcoffee.R
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun brewTimerRemoteViews(
    context: Context,
    layout: Int,
    title: String,
    stage: String,
    presentation: DurableBrewStatusPresentation,
): RemoteViews {
    val hasTime = presentation.chronometerWallClockMillis != null
    val showSeconds = presentation.clock == DurableBrewStatusClock.COUNTDOWN &&
        presentation.timeMillis < BREW_NOTIFICATION_SECONDS_THRESHOLD_MILLIS
    val text = formatBrewNotificationTime(presentation.timeMillis, showSeconds)
    val styled = SpannableString(text)
    text.forEachIndexed { index, character ->
        if (character.isLetter()) styled.setSpan(RelativeSizeSpan(0.55f), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return RemoteViews(context.packageName, layout).apply {
        setTextViewText(R.id.brew_timer_title, title)
        setTextViewText(R.id.brew_timer_stage, stage)
        setTextViewText(R.id.brew_timer_time, styled)
        setViewVisibility(R.id.brew_timer_time, if (hasTime) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.brew_timer_divider, if (hasTime) View.VISIBLE else View.GONE)
        setContentDescription(R.id.brew_timer_time, spokenBrewNotificationTime(context, presentation.timeMillis, showSeconds))
        setImageViewBitmap(R.id.brew_timer_cup, renderBrewNotificationCup(context, presentation.cupFillFraction))
    }
}

private fun spokenBrewNotificationTime(context: Context, millis: Long, secondsVisible: Boolean): String {
    val seconds = (millis.coerceAtLeast(0L) / 1_000).let {
        if (millis > 0 && secondsVisible) it.coerceAtLeast(1) else it
    }
    val measures = buildList {
        if (seconds >= 3_600) add(Measure(seconds / 3_600, MeasureUnit.HOUR))
        if (seconds / 60 % 60 > 0 || !secondsVisible) add(Measure(seconds / 60 % 60, MeasureUnit.MINUTE))
        if (secondsVisible) add(Measure(seconds % 60, MeasureUnit.SECOND))
    }
    val formatter = MeasureFormat.getInstance(context.resources.configuration.locales[0], MeasureFormat.FormatWidth.WIDE)
    return if (measures.size == 1) formatter.formatMeasures(measures[0]) else formatter.formatMeasures(measures[0], measures[1])
}

/** Both layers use the same traced viewport, with equal top/bottom inset in a square bitmap. */
internal fun renderBrewNotificationCup(context: Context, fillFraction: Float?): Bitmap {
    val size = (48 * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val outline = requireNotNull(ContextCompat.getDrawable(context, R.drawable.notification_cup_outline)).mutate()
    val height = (size.toFloat() * outline.intrinsicHeight / outline.intrinsicWidth).roundToInt()
    val top = (size - height) / 2
    outline.setBounds(0, top, size, top + height)
    outline.setTint(context.getColor(R.color.notification_brew_cup_outline))
    val fill = fillFraction?.coerceIn(0f, 1f) ?: 0f
    if (fill > 0f) {
        val bowl = requireNotNull(ContextCompat.getDrawable(context, R.drawable.notification_cup_bowl)).mutate()
        bowl.bounds = outline.bounds
        bowl.setTint(context.getColor(R.color.notification_brew_cup_fill))
        canvas.withSave {
            if (fill < 1f) {
                clipPath(liquidClip(size, top + height * (1f - fill)))
            }
            bowl.draw(this)
        }
    }
    outline.draw(canvas)
    return bitmap
}

private fun liquidClip(size: Int, waterline: Float): Path = Path().apply {
    moveTo(0f, size.toFloat())
    for (x in 0..size) {
        val y = waterline + sin(x.toDouble() / size * 2 * Math.PI).toFloat() * size / 48f
        lineTo(x.toFloat(), y)
    }
    lineTo(size.toFloat(), size.toFloat())
    close()
}
