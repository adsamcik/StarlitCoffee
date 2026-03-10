package com.adsamcik.starlitcoffee.util

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import java.util.Locale
import kotlin.math.abs

data class Gs1IssuerRegionInsight(
    val prefix: String,
    val regionName: String,
)

data class ObservedBarcodeStemMatch(
    val stem: String,
    val brands: List<String>,
    val roasterCandidate: String? = null,
    val candidateConfidence: BagFieldConfidence? = null,
    val gs1Region: String? = null,
    val note: String,
)

object BarcodeInsights {
    fun normalizeBarcode(barcode: String?): String? {
        val normalized = barcode
            ?.filter(Char::isDigit)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return normalized.takeIf { it.length in 8..14 }
    }

    fun gs1IssuerRegion(barcode: String?): Gs1IssuerRegionInsight? {
        val normalized = normalizeBarcode(barcode) ?: return null
        if (normalized.length != 13) return null

        val prefix = normalized.take(3)
        val prefixNumber = prefix.toIntOrNull() ?: return null
        val regionName = when {
            prefix == "859" -> "Czech Republic"
            prefix == "590" -> "Poland"
            prefixNumber in 400..440 -> "Germany"
            prefixNumber in 800..839 -> "Italy"
            else -> return null
        }

        return Gs1IssuerRegionInsight(prefix = prefix, regionName = regionName)
    }

    fun findObservedStemMatch(barcode: String?): ObservedBarcodeStemMatch? {
        val barcodeVariants = normalizedBarcodeVariants(barcode)
        if (barcodeVariants.isEmpty()) return null

        return barcodeVariants
            .asSequence()
            .flatMap { digits ->
                OBSERVED_STEMS.asSequence().filter { digits.startsWith(it.stem) }
            }
            .firstOrNull()
            ?.toMatch()
    }

    fun buildLocalMatchCandidates(
        matchedBag: CoffeeBagEntity,
        locale: Locale = Locale.getDefault(),
    ): List<BagFieldCandidate> {
        val localizedMetadata = CoffeeMetadataNormalizer.resolveBagMetadata(matchedBag, locale)
        val supportingText = buildString {
            append("Matched your saved bag")
            if (matchedBag.name.isNotBlank()) {
                append(": ${matchedBag.name}")
            }
            matchedBag.roaster?.takeIf { it.isNotBlank() }?.let { append(" by $it") }
        }

        return buildList {
            addLocalMatchCandidate("name", matchedBag.name, supportingText)
            addLocalMatchCandidate("roaster", matchedBag.roaster, supportingText)
            addLocalMatchCandidate(
                fieldName = "origin",
                value = localizedMetadata.origin,
                supportingText = supportingText,
                rawValue = matchedBag.origin,
                canonicalKey = localizedMetadata.originId,
            )
            addLocalMatchCandidate(
                fieldName = "region",
                value = localizedMetadata.region,
                supportingText = supportingText,
                rawValue = matchedBag.region,
                canonicalKey = localizedMetadata.regionId,
            )
            addLocalMatchCandidate(
                fieldName = "roastLevel",
                value = localizedMetadata.roastLevel,
                supportingText = supportingText,
                rawValue = matchedBag.roastLevel,
                canonicalKey = localizedMetadata.roastLevelIds,
            )
            addLocalMatchCandidate(
                fieldName = "processType",
                value = localizedMetadata.processType,
                supportingText = supportingText,
                rawValue = matchedBag.processType,
                canonicalKey = localizedMetadata.processTypeId,
            )
            addLocalMatchCandidate(
                fieldName = "variety",
                value = localizedMetadata.variety,
                supportingText = supportingText,
                rawValue = matchedBag.variety,
                canonicalKey = localizedMetadata.varietyIds,
            )
            addLocalMatchCandidate(
                fieldName = "tastingNotes",
                value = localizedMetadata.tastingNotes,
                supportingText = supportingText,
                rawValue = matchedBag.tastingNotes,
                canonicalKey = localizedMetadata.tasteNoteIds,
            )
            matchedBag.weightG
                ?.takeIf { it > 0f }
                ?.let { addLocalMatchCandidate("weight", formatWeight(it), supportingText) }
        }
    }

    fun buildObservedStemCandidates(match: ObservedBarcodeStemMatch?): List<BagFieldCandidate> {
        val roasterCandidate = match?.roasterCandidate ?: return emptyList()
        val candidateConfidence = match.candidateConfidence ?: return emptyList()

        return listOf(
            BagFieldCandidate(
                fieldName = "roaster",
                value = roasterCandidate,
                sourceType = BagFieldSourceType.OBSERVED_BARCODE_STEM,
                confidenceHint = candidateConfidence,
                supportingText = match.note,
            ),
        )
    }

    fun buildBarcodeReviewHints(
        barcode: String?,
        matchedBag: CoffeeBagEntity?,
        observedStemMatch: ObservedBarcodeStemMatch? = findObservedStemMatch(barcode),
    ): List<BagPhotoReviewHint> {
        val normalized = normalizeBarcode(barcode) ?: return emptyList()

        return buildList {
            matchedBag?.let { bag ->
                val bagLabel = listOfNotNull(
                    bag.name.takeIf { it.isNotBlank() },
                    bag.roaster?.takeIf { it.isNotBlank() },
                ).joinToString(" by ").ifBlank { "your saved bag" }
                add(
                    BagPhotoReviewHint(
                        severity = BagReviewSeverity.INFO,
                        message = "Exact barcode match found for $bagLabel. We'll reuse those saved details where they still make sense.",
                    ),
                )
            }
            gs1IssuerRegion(normalized)?.let { region ->
                add(
                    BagPhotoReviewHint(
                        severity = BagReviewSeverity.INFO,
                        message = "EAN prefix ${region.prefix} suggests the barcode was issued via GS1 ${region.regionName}. That may differ from where the coffee was roasted or packed.",
                    ),
                )
            }
            if (normalized.length == 12) {
                add(
                    BagPhotoReviewHint(
                        severity = BagReviewSeverity.INFO,
                        message = "This code is 12 digits long, so treat it as a GTIN/UPC-style retail identifier in its own right. Do not assume a leading zero or convert it to EAN-13 unless the physical package confirms that format.",
                    ),
                )
            }
            observedStemMatch?.let { match ->
                add(
                    BagPhotoReviewHint(
                        severity = BagReviewSeverity.INFO,
                        message = match.note,
                    ),
                )
            }
        }
    }

    private fun normalizedBarcodeVariants(barcode: String?): List<String> {
        val normalized = normalizeBarcode(barcode) ?: return emptyList()
        return buildList {
            add(normalized)
            if (normalized.length == 14 && normalized.startsWith("0")) {
                add(normalized.drop(1))
            }
        }.distinct()
    }

    private fun MutableList<BagFieldCandidate>.addLocalMatchCandidate(
        fieldName: String,
        value: String?,
        supportingText: String,
        rawValue: String? = value,
        canonicalKey: String? = null,
    ) {
        val cleanValue = value?.trim()?.takeIf { it.isNotBlank() } ?: return
        add(
            BagFieldCandidate(
                fieldName = fieldName,
                value = cleanValue,
                rawValue = rawValue?.trim()?.takeIf { it.isNotBlank() } ?: cleanValue,
                canonicalKey = canonicalKey,
                sourceType = BagFieldSourceType.LOCAL_BARCODE_MATCH,
                confidenceHint = BagFieldConfidence.HIGH,
                supportingText = supportingText,
            ),
        )
    }

    private fun formatWeight(weightG: Float): String {
        val wholeGrams = weightG.toInt().toFloat()
        return if (abs(weightG - wholeGrams) < 0.01f) {
            wholeGrams.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", weightG)
        }
    }

    private data class ObservedBarcodeStem(
        val stem: String,
        val brands: List<String>,
        val roasterCandidate: String? = null,
        val candidateConfidence: BagFieldConfidence? = null,
        val gs1Region: String? = null,
        val note: String,
    ) {
        fun toMatch(): ObservedBarcodeStemMatch = ObservedBarcodeStemMatch(
            stem = stem,
            brands = brands,
            roasterCandidate = roasterCandidate,
            candidateConfidence = candidateConfidence,
            gs1Region = gs1Region,
            note = note,
        )
    }

    private val OBSERVED_STEMS = listOf(
        // --- Czech Republic (GS1 prefix 859) ---
        // To add a new Czech roaster: copy any entry below, change stem/brands/note.
        // Evidence standard: at least 1 verified retail EAN-13 with the shared prefix,
        // ideally 2+ distinct products. Set roasterCandidate only when a single brand
        // owns the family with no contradicting evidence.

        ObservedBarcodeStem(
            stem = "859418332",
            brands = listOf("Rebelbean"),
            roasterCandidate = "Rebelbean",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Observed public retail listings repeatedly show stem 859418332... on Rebelbean products. Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "859420618",
            brands = listOf("Beansmith's"),
            roasterCandidate = "Beansmith's",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Observed public retail listings repeatedly show stem 859420618... on Beansmith's products. Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "859420094",
            brands = listOf("Nordbeans"),
            roasterCandidate = "Nordbeans",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Observed public retail listings repeatedly show stem 859420094... on Nordbeans products (including Horizont, Esquipulas, Fox'spresso, Naali). Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "859420557",
            brands = listOf("Kávy Pitel"),
            roasterCandidate = "Kávy Pitel",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Observed repeated official product listings show stem 859420557... on Kávy Pitel products across multiple pack sizes. Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "859402113",
            brands = listOf("Frolíkova káva"),
            roasterCandidate = "Frolíkova káva",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Observed public retail listings repeatedly show stem 859402113... on Frolíkova káva products. Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "859559210",
            brands = listOf("mamacoffee"),
            roasterCandidate = "mamacoffee",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Observed public retail listings repeatedly show stem 859559210... on mamacoffee products. Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "85956882",
            brands = listOf("Doubleshot"),
            roasterCandidate = "Doubleshot",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Three verified retail EANs share the 8-digit stem 85956882... on Doubleshot products (Extra, Era, Flirt), consistent with a GS1 CZ 5-digit company identifier. No competing brand observed. Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "859421386",
            brands = listOf("Pražírna Káva Monro"),
            roasterCandidate = "Pražírna Káva Monro",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Observed public retail listings repeatedly show stem 859421386... on Pražírna Káva Monro products; some public GTIN-14 listings also add a leading zero. Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "859421130",
            brands = listOf("Father's Coffee Roastery"),
            roasterCandidate = "Father's Coffee Roastery",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Observed one verified retail EAN (capsule format) under stem 859421130... for Father's Coffee Roastery. Bag-format barcodes have not been independently verified. Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "859421470",
            brands = listOf("1754 Roastery"),
            roasterCandidate = "1754 Roastery",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Observed public retail listings repeatedly show stem 859421470... on 1754 Roastery products. Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "859422222",
            brands = listOf("Coffeespot"),
            roasterCandidate = "Coffeespot",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Observed public retail listings repeatedly show stem 859422222... on Coffeespot products across multiple bag sizes. Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "859422686",
            brands = listOf("Fiftybeans"),
            roasterCandidate = "Fiftybeans",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Observed public retail listings repeatedly show stem 859422686... on Fiftybeans products. Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "859570580",
            brands = listOf("MOTMOT"),
            roasterCandidate = "MOTMOT",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            gs1Region = "Czech Republic",
            note = "Observed public retail listings repeatedly show stem 859570580... on MOTMOT products. Treat this as a public-listing heuristic, not an official GS1 company-prefix extract.",
        ),
        ObservedBarcodeStem(
            stem = "859572220",
            brands = listOf("Garage Coffee", "Fixi Coffee"),
            gs1Region = "Czech Republic",
            note = "Observed public retail listings show stem 859572220... on both Garage Coffee and Fixi Coffee, so treat it as a shared family hint instead of a single-brand identifier.",
        ),

        // --- Czech roasters with non-Czech GS1 registration ---
        // A Czech roaster may register through a foreign GS1 organization or use
        // GTIN-12/UPC-A. The gs1Region reflects the issuing organization, not the
        // roaster's physical location.

        ObservedBarcodeStem(
            stem = "796554985",
            brands = listOf("Upraženo"),
            roasterCandidate = "Upraženo",
            candidateConfidence = BagFieldConfidence.MEDIUM,
            note = "Observed public retail listings repeatedly show stem 796554985... on Upraženo products; it is a 12-digit GTIN family, so do not assume a Czech 859 prefix is required for Czech-market coffee.",
        ),

        // --- Future: Germany (GS1 prefix 400–440) ---
        // --- Future: Poland (GS1 prefix 590) ---
        // --- Future: Italy (GS1 prefix 800–839) ---
    ).sortedByDescending { it.stem.length }
}
