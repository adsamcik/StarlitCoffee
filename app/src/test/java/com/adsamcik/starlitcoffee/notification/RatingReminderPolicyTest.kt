package com.adsamcik.starlitcoffee.notification

import android.app.PendingIntent
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingReminderPolicyTest {

    @Test
    fun `scheduled and cancelled alarms use immutable PendingIntent identity`() {
        val flags = ratingReminderPendingIntentFlags()

        assertTrue(flags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertFalse(flags and PendingIntent.FLAG_MUTABLE != 0)
    }

    @Test
    fun `receiver posts only for an existing unrated brew log`() {
        val unrated = brewLog(rating = null)
        val rated = brewLog(rating = 4f)

        assertTrue(shouldPostRatingReminder(unrated))
        assertFalse(shouldPostRatingReminder(rated))
        assertFalse(shouldPostRatingReminder(null))
    }

    @Test
    fun `rating reminder notifications have a finite expiry`() {
        assertTrue(RatingReminderReceiver.RATING_NOTIFICATION_TIMEOUT_MILLIS > 0L)
    }

    private fun brewLog(rating: Float?): BrewLogEntity = BrewLogEntity(
        id = 7L,
        method = "PULSAR",
        doseG = 20f,
        waterG = 340f,
        ratio = 17f,
        rating = rating,
    )
}
