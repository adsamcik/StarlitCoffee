package com.adsamcik.starlitcoffee.notification

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.createBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test

/** Native RemoteViews checks, separate from JVM policy and actual notification-shade acceptance. */
class BrewTimerNotificationRenderingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun centeredCupAndReadableTimeAcrossThemesWidthsAndFontScales() {
        instrumentation.runOnMainSync {
            for (night in listOf(false, true)) for (fontScale in listOf(1f, 1.3f, 2f)) {
                val base = instrumentation.targetContext
                val configuration = Configuration(base.resources.configuration).apply {
                    this.fontScale = fontScale
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val context = base.createConfigurationContext(configuration)
                for (layout in listOf(R.layout.notification_brew_timer, R.layout.notification_brew_timer_expanded)) {
                    for (widthDp in listOf(240, 328)) for (remaining in listOf(46_236_000L, 179_999L)) {
                        val presentation = DurableBrewStatusPresentation(BrewStageAction.STEEP,
                            DurableBrewStatusClock.COUNTDOWN, System.currentTimeMillis() + remaining, remaining, .45f)
                        val view = brewTimerRemoteViews(context, layout, "Cold brew with a longer title", "Steep · Remaining", presentation)
                            .apply(context, FrameLayout(context))
                        val width = (widthDp * context.resources.displayMetrics.density).roundToInt()
                        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        view.layout(0, 0, width, view.measuredHeight)
                        val cup = view.findViewById<ImageView>(R.id.brew_timer_cup)
                        assertTrue("Cup is not centered", abs((cup.top + cup.bottom) / 2f - view.height / 2f) <= 1f)
                        val time = view.findViewById<TextView>(R.id.brew_timer_time)
                        assertTrue("Timer is clipped at font scale $fontScale", time.layout.getLineWidth(0) <= time.width + 1f)
                        assertTrue("Title lost its column", view.findViewById<TextView>(R.id.brew_timer_title).width > 0)
                        val bitmap = createBitmap(width, view.height)
                        view.draw(Canvas(bitmap))
                        val name = "${layout}_${night}_${fontScale}_${widthDp}_$remaining.png"
                        File(context.cacheDir, "timer-notification/$name").apply {
                            parentFile?.mkdirs()
                            outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun tracedCupVisibleBoundsAreCenteredAndFillDoesNotEnterTheHandle() {
        val context = instrumentation.targetContext
        val empty = renderBrewNotificationCup(context, null)
        val full = renderBrewNotificationCup(context, 1f)
        val ys = (0 until empty.height).filter { y ->
            (0 until empty.width).any { x -> empty.getPixel(x, y) ushr 24 > 0 }
        }
        assertTrue("Artwork has unequal transparent padding", abs(ys.first() - (empty.height - 1 - ys.last())) <= 2)
        // The rightmost portion is the handle; filling the bowl must leave that whole region unchanged.
        val handleStart = (empty.width * .82f).roundToInt()
        for (y in 0 until empty.height) for (x in handleStart until empty.width) {
            assertTrue("Progress leaked into the handle", empty.getPixel(x, y) == full.getPixel(x, y))
        }
    }
}
