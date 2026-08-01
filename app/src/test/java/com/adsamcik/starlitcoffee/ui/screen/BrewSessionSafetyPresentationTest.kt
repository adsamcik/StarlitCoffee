package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import org.junit.Assert.assertEquals
import org.junit.Test

class BrewSessionSafetyPresentationTest {

    @Test
    fun `each severity has a distinct localized safety label`() {
        assertEquals(
            R.string.label_brew_safety_critical,
            StageSafetySeverity.CRITICAL.labelRes(),
        )
        assertEquals(
            R.string.label_brew_safety_warning,
            StageSafetySeverity.WARNING.labelRes(),
        )
        assertEquals(
            R.string.label_brew_safety_note,
            StageSafetySeverity.ADVICE.labelRes(),
        )
    }
}
