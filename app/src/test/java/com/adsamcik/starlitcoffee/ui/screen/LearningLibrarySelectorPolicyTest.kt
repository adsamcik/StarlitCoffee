package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningLibrarySelectorPolicyTest {
    @Test
    fun `every packaged brewer receives its intended silhouette`() {
        val expected = mapOf(
            "v60_02" to BrewerProfileIconKind.CONE,
            "v60_unspecified" to BrewerProfileIconKind.CONE,
            "manual_wave_185" to BrewerProfileIconKind.FLAT_BOTTOM,
            "manual_wedge_generic" to BrewerProfileIconKind.WEDGE,
            "manual_thick_paper_carafe" to BrewerProfileIconKind.CARAFE,
            "manual_conical_generic" to BrewerProfileIconKind.CONE,
            "clever_style" to BrewerProfileIconKind.IMMERSION,
            "hario_switch" to BrewerProfileIconKind.IMMERSION,
            "cezve_generic" to BrewerProfileIconKind.CEZVE,
            "automatic_batch_generic" to BrewerProfileIconKind.BATCH_MACHINE,
            "automatic_single_cup_generic" to BrewerProfileIconKind.SINGLE_CUP_MACHINE,
            "vietnamese_phin" to BrewerProfileIconKind.PHIN,
            "pulsar_standard" to BrewerProfileIconKind.PULSAR,
        )

        expected.forEach { (profileId, iconKind) ->
            assertEquals(iconKind, brewerProfileIconKind(BrewerProfileId(profileId)))
        }
    }

    @Test
    fun `every packaged brewer receives a dedicated generated asset`() {
        val expected = mapOf(
            "v60_02" to R.drawable.learn_brewer_icon_v60_02,
            "v60_unspecified" to R.drawable.learn_brewer_icon_v60,
            "manual_wave_185" to R.drawable.learn_brewer_icon_wave_185,
            "manual_wedge_generic" to R.drawable.learn_brewer_icon_wedge,
            "manual_thick_paper_carafe" to R.drawable.learn_brewer_icon_carafe,
            "manual_conical_generic" to R.drawable.learn_brewer_icon_conical,
            "clever_style" to R.drawable.learn_brewer_icon_clever,
            "hario_switch" to R.drawable.learn_brewer_icon_switch,
            "cezve_generic" to R.drawable.learn_brewer_icon_cezve,
            "automatic_batch_generic" to R.drawable.learn_brewer_icon_batch,
            "automatic_single_cup_generic" to R.drawable.learn_brewer_icon_single_cup,
            "vietnamese_phin" to R.drawable.learn_brewer_icon_phin,
            "pulsar_standard" to R.drawable.learn_brewer_icon_pulsar,
        )

        expected.forEach { (profileId, drawable) ->
            assertEquals(drawable, brewerProfileIconDrawable(BrewerProfileId(profileId)))
        }
        assertEquals(expected.size, expected.values.toSet().size)
    }

    @Test
    fun `related brewers remain grouped under one readable method heading`() {
        assertEquals(
            BrewerMethodGroup.MANUAL_GRAVITY,
            brewerMethodGroup(BrewerProfileId("manual_wave_185")),
        )
        assertEquals(
            BrewerMethodGroup.STEEP_AND_RELEASE,
            brewerMethodGroup(BrewerProfileId("hario_switch")),
        )
        assertEquals(
            BrewerMethodGroup.AUTOMATIC,
            brewerMethodGroup(BrewerProfileId("automatic_single_cup_generic")),
        )
        assertEquals(
            BrewerMethodGroup.VALVE_CONTROLLED,
            brewerMethodGroup(BrewerProfileId("pulsar_standard")),
        )
    }

    @Test
    fun `recipe label separates name from quantities for scanning`() {
        assertEquals(
            LearningRecipeLabelParts(
                title = "Tetsu Kasuya 4:6",
                details = "20 g / 300 g",
            ),
            splitLearningRecipeLabel("Tetsu Kasuya 4:6 · 20 g / 300 g"),
        )
    }

    @Test
    fun `recipe label without metadata remains intact`() {
        assertEquals(
            LearningRecipeLabelParts(title = "Custom recipe", details = null),
            splitLearningRecipeLabel("Custom recipe"),
        )
    }
}
