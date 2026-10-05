package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId

/** English source copy from the editorially reviewed refrigerated-jar guide, 2 October 2026. */
object ColdBrewGuidanceCatalog {
    val catalog: BuiltInGuidanceCatalog by lazy {
        val profile = requireNotNull(BuiltinBrewingCatalog.instance.findBrewerProfile(BrewerProfileId("cold_immersion_generic")))
        BuiltInGuidanceCatalog(steps.map { (id, text) ->
            val key = "cold_refrigerated_$id"
            BuiltInGuidanceContent(StageContentId(key), profile.familyId, profileId = profile.id,
                stageId = StageId(key), placement = BuiltInGuidancePlacement.LIVE_STAGE,
                text = text, visibility = GuidanceVisibilityPolicy())
        })
    }

    private val steps = linkedMapOf(
        "prepare" to GuidanceTextMetadata(
            "Clean hands and equipment. Check the vessel fill limit and refrigerator at 4 °C or colder.",
            "Clean the equipment; check the fridge and vessel.",
            explanation = "A long cold steep has no hot brewing step. Use clean, food-safe equipment.",
            altText = "A clean covered food-safe jar, spoon and refrigerator thermometer."),
        "combine" to GuidanceTextMetadata(
            "Add the measured grounds and all the refrigerated water. Gently stir out dry clumps and cover.",
            "Measure, wet every ground, and cover.",
            explanation = "The quantities shown are your actual calculator setup. Check the vessel's usable fill limit.",
            altText = "Cold water and measured coffee mixed evenly in a covered vessel."),
        "steep" to GuidanceTextMetadata(
            "Place the covered vessel in the refrigerator. Start timing once the coffee is fully wet, covered and cold.",
            "Keep the covered batch refrigerated.",
            explanation = "The 14-hour starting point follows Counter Culture's refrigerated concentrate guide. " +
                "A reminder never proves food safety; keep the batch at 4 °C or colder throughout.",
            tip = "Label the batch with its preparation time.",
            altText = "A covered jar of cold brew resting on a refrigerator shelf."),
        "filter" to GuidanceTextMetadata(
            "Strain into a clean server. Paper-filter in portions below the rim; serve or refrigerate promptly.",
            "Filter gently into a clean server.",
            explanation = "Confirm that useful liquid has drained and the grounds are separated. " +
                "Filtering has no fixed completion time; collected concentrate varies.",
            altText = "Cold concentrate draining through a supported filter into a clean container."),
        "dilute" to GuidanceTextMetadata(
            "For one serving, combine 100 g collected concentrate with 100 g cold water. Adjust to taste.",
            "Mix equal masses of concentrate and cold water.",
            explanation = "This app-selected serving is 200 g before ice or milk. Serving water is separate from extraction water.",
            altText = "Equal weighed portions of concentrate and water poured into a serving glass."),
        "clean" to GuidanceTextMetadata(
            "Discard the grounds and paper; wash and dry the equipment. Cover and refrigerate unused concentrate promptly.",
            "Clean up and keep unused concentrate cold.",
            explanation = "No generic storage-life or caffeine claim is established for this home jar recipe.",
            altText = "Clean brewing equipment drying beside covered refrigerated concentrate."),
    )
}
