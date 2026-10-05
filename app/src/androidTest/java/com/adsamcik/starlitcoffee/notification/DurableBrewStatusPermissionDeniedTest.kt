package com.adsamcik.starlitcoffee.notification

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSession
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionClockReading
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionReducer
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.CalcUiState
import com.adsamcik.starlitcoffee.viewmodel.CalculatorBrewSessionStartFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Run separately after the host revokes access; revoking inside instrumentation can kill its own UID. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class DurableBrewStatusPermissionDeniedTest {
    @Test
    fun notificationDenialDoesNotEndOrAdvanceAnyCalculatorBrew() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        val manager = context.getSystemService(NotificationManager::class.java)
        val now = System.currentTimeMillis()
        val notifier = DurableBrewSessionStatusNotifier(context, { now }, { false })
        BrewMethod.entries.forEach { method ->
            val request = requireNotNull(CalculatorBrewSessionStartFactory().create(
                CalcUiState(brewMethod = method, tokens = listOf(CalcToken.Number("20")), previewDoseG = 20f,
                    previewWaterMl = 20f * method.defaultRatio, ratio = method.defaultRatio, hasValidExpression = true),
                BrewUiState(method = method), null))
            val runtime = SessionReducer.reduce(SessionRuntimeState.create(request.sessionId, request.stagePlan),
                SessionEvent.Start(), SessionClockReading(1_000L, now)).state
            val session = ActiveBrewSession(request.recipe, runtime, request.executionContext)
            notifier.publish(session)
            assertFalse(manager.activeNotifications.any {
                it.tag == durableBrewStatusNotificationTag(runtime.sessionId.value)
            })
            assertEquals(runtime, session.runtime)
            assertEquals(BrewSessionStatus.RUNNING, session.runtime.status)
            val progressed = SessionReducer.reduce(runtime, SessionEvent.Tick(), SessionClockReading(6_000L, now + 5_000L))
            assertEquals(5_000L, progressed.state.totalActiveElapsedMillis)
        }
    }
}
