@file:Suppress(
    // This file contains coffee region / variety / process reference data —
    // long lines are alias tables and prompt strings whose readability is
    // hurt, not helped, by wrapping. Detekt rule applied per-line is wrong
    // for data tables; suppress at file scope.
    "MaxLineLength",
    // The reference data is naturally a single object with many tables.
    // Splitting into multiple objects/files would scatter the canonical
    // dictionary across the codebase.
    "LargeClass",
    "TooManyFunctions",
)

package com.adsamcik.starlitcoffee.util

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.CoffeeOrigin
import com.adsamcik.starlitcoffee.data.model.CoffeeProcessType
import com.adsamcik.starlitcoffee.data.model.CoffeeRegion
import com.adsamcik.starlitcoffee.data.model.CoffeeRoastLevel
import com.adsamcik.starlitcoffee.data.model.CoffeeVariety
import com.adsamcik.starlitcoffee.data.model.FlavorDescriptor
import java.text.Normalizer
import java.util.Locale

enum class CoffeeMetadataMatchStrategy {
    EXACT_ALIAS,
    CONTAINS_ALIAS,
    RELATION_INFERENCE,
    RAW_FALLBACK,
}

data class NormalizedCoffeeField(
    val value: String,
    val rawValue: String,
    val canonicalKey: String? = null,
    val matchStrategy: CoffeeMetadataMatchStrategy = CoffeeMetadataMatchStrategy.RAW_FALLBACK,
    val relatedCanonicalKeys: Map<String, String> = emptyMap(),
)

data class NormalizedCoffeeBagMetadata(
    val origin: String? = null,
    val originId: String? = null,
    val region: String? = null,
    val regionId: String? = null,
    val roastLevel: String? = null,
    val roastLevelIds: String? = null,
    val processType: String? = null,
    val processTypeId: String? = null,
    val variety: String? = null,
    val varietyIds: String? = null,
    val tastingNotes: String? = null,
    val tasteNoteIds: String? = null,
)

/** What [CoffeeMetadataNormalizer.sanitizeExtraction] did to a single field. */
enum class ScanFieldCorrectionAction {
    /** Value moved to the field it canonically belongs to (its slot was wrong). */
    RELOCATED,

    /** Value removed — it duplicated/conflicted with another field or didn't parse. */
    DROPPED,

    /** Value rewritten to a clean form (e.g. weight token recovered from noise). */
    NORMALIZED,

    /** A bare decaf marker was moved out of a content field into the isDecaf flag. */
    FLAGGED_DECAF,
}

/**
 * One field-level correction applied while sanitising a raw LLM/OCR extraction.
 * Carried in [SanitizedExtraction.corrections] so callers can log/telemeter why
 * a detected value changed (the scan pipeline otherwise persists no per-field
 * provenance).
 */
data class ScanFieldCorrection(
    val field: String,
    val action: ScanFieldCorrectionAction,
    val from: String?,
    val to: String?,
    val reason: String,
)

/**
 * Result of running raw extracted fields through the field contracts. Mirrors
 * the controlled-vocabulary + format fields of an extraction; free-text fields
 * (name, roaster, farm, …) are intentionally not touched here.
 */
data class SanitizedExtraction(
    val origin: String? = null,
    val region: String? = null,
    val processType: String? = null,
    val roastLevel: String? = null,
    val variety: String? = null,
    val weight: String? = null,
    val isDecaf: Boolean? = null,
    val corrections: List<ScanFieldCorrection> = emptyList(),
)

object CoffeeMetadataNormalizer {
    private enum class MetadataFieldType(
        val fieldName: String,
        val multiValue: Boolean = false,
    ) {
        ORIGIN("origin"),
        REGION("region"),
        PROCESS_TYPE("processType"),
        ROAST_LEVEL("roastLevel", multiValue = true),
        VARIETY("variety", multiValue = true),
        TASTE_NOTE("tastingNotes", multiValue = true),
        ;

        companion object {
            fun fromFieldName(fieldName: String): MetadataFieldType? = entries.firstOrNull { it.fieldName == fieldName }
        }
    }

    private data class CanonicalEntry(
        val id: String,
        val defaultLabel: String,
        val localizedLabels: Map<String, String> = emptyMap(),
        val aliases: Set<String> = emptySet(),
        val relatedCanonicalKeys: Map<String, String> = emptyMap(),
    ) {
        val normalizedAliases: Set<String> = aliases
            .map(::normalizeSearch)
            .filter(String::isNotBlank)
            .toSet()
    }

    private val supportedLocales = listOf(
        Locale.ENGLISH,
        Locale.forLanguageTag("cs"),
        Locale.GERMAN,
        Locale.forLanguageTag("es"),
        Locale.ITALIAN,
        Locale.forLanguageTag("da"),
    )

    // Token-boundary patterns compiled against normalized text (lowercased, diacritics stripped,
    // non-alphanumerics collapsed to single spaces). `\b` reliably delimits tokens in this form.
    // Patterns use prefix stems (e.g. decaf\\w*) so variants like "decaffeinated" or "descafeinada"
    // match without being listed individually.
    private val decafMarkerPatterns: List<Regex> = listOf(
        // English
        "\\bdecaf\\w*\\b",                   // decaf, decaffeinated, decafs
        "\\bcaffeine\\s+free\\b",
        "\\bwithout\\s+caffeine\\b",
        "\\bno\\s+caffeine\\b",
        // Spanish / Portuguese / Catalan
        "\\bdescaf\\w*\\b",                  // descafeinado/a, descafeinat
        "\\bsin\\s+cafeina\\b",
        "\\bsem\\s+cafeina\\b",
        // French
        "\\bdecafein\\w*\\b",                // décaféiné(e), décaféination
        "\\bsans\\s+cafeine\\b",
        // Italian
        "\\bdecaffeinat\\w*\\b",
        "\\bsenza\\s+caffeina\\b",
        // German
        "\\bentkoffein\\w*\\b",              // entkoffeiniert, entkoffeinierter
        "\\bkoffeinfrei\\w*\\b",
        "\\bohne\\s+koffein\\b",
        // Dutch
        "\\bcafeinevrij\\w*\\b",
        "\\bzonder\\s+cafeine\\b",
        // Czech / Slovak
        "\\bbez\\s+kofeinu\\b",
        "\\bbezkofein\\w*\\b",               // bezkofeinová/-ový/-ové
        "\\bdekofein\\w*\\b",                // dekofeinizovaná
        // Polish
        "\\bbezkofeinow\\w*\\b",
        "\\bbez\\s+kofeiny\\b",
        // Hungarian
        "\\bkoffeinmentes\\w*\\b",
        // Romanian
        "\\bdecofein\\w*\\b",                // decofeinizat(ă)
        "\\bfara\\s+cofeina\\b",
        // Nordic (Danish/Norwegian/Swedish)
        "\\bkoffeinfri\\w*\\b",
        "\\buden\\s+koffein\\b",
        "\\butan\\s+koffein\\b",
        // Finnish
        "\\bkofeiiniton\\w*\\b",
        // Turkish
        "\\bkafeinsiz\\w*\\b",
        // Russian (transliterated)
        "\\bbez\\s+kofeina\\b",
    ).map { it.toRegex(RegexOption.IGNORE_CASE) }

    // Tokens that, when appearing immediately before a decaf marker, indicate negation
    // (e.g. "not decaf", "non-decaf", "no decaf"). Keep this list conservative — only English
    // negations, since ambiguous tokens in other languages ("sem", "bez") are themselves
    // part of legitimate decaf phrases.
    private val decafNegationTokens = setOf("not", "non", "no", "never")

    // Bare botanical species names ("Arabica", "Robusta") are never a valid
    // origin (they are not a country) and never a meaningfully distinguishing
    // variety either — nearly all specialty coffee is Arabica, so the bare
    // word carries no information a real cultivar name ("Bourbon", "Geisha")
    // would. The prompt already asks the model to translate concept fields to
    // canonical English, so this check is intentionally English-only.
    private val bareSpeciesTokens = setOf("arabica", "robusta", "liberica", "excelsa")

    // Bean-form / roast-form descriptors are not a processing METHOD — they
    // describe how the coffee is packaged (whole bean vs ground), not how the
    // green coffee was processed (washed, natural, honey, ...). Prompt-side
    // exclusion exists (VISION_SYSTEM_PROMPT / SYSTEM_PROMPT_14) but was
    // observed leaking through on real bags, so this is a deterministic
    // backstop. English-only for the same reason as [bareSpeciesTokens].
    private val beanFormOnlyTokens = setOf(
        "whole bean", "whole beans", "bean", "beans",
        "ground", "ground coffee", "coffee beans", "roasted beans", "roasted coffee",
    )

    private val originCountryCodes = mapOf(
        CoffeeOrigin.Known.ETHIOPIA.name to "ET",
        CoffeeOrigin.Known.COLOMBIA.name to "CO",
        CoffeeOrigin.Known.BRAZIL.name to "BR",
        CoffeeOrigin.Known.KENYA.name to "KE",
        CoffeeOrigin.Known.GUATEMALA.name to "GT",
        CoffeeOrigin.Known.COSTA_RICA.name to "CR",
        CoffeeOrigin.Known.HONDURAS.name to "HN",
        CoffeeOrigin.Known.PERU.name to "PE",
        CoffeeOrigin.Known.RWANDA.name to "RW",
        CoffeeOrigin.Known.BURUNDI.name to "BI",
        CoffeeOrigin.Known.INDONESIA.name to "ID",
        CoffeeOrigin.Known.PAPUA_NEW_GUINEA.name to "PG",
        CoffeeOrigin.Known.YEMEN.name to "YE",
        CoffeeOrigin.Known.PANAMA.name to "PA",
        CoffeeOrigin.Known.EL_SALVADOR.name to "SV",
        CoffeeOrigin.Known.MEXICO.name to "MX",
        CoffeeOrigin.Known.NICARAGUA.name to "NI",
        CoffeeOrigin.Known.TANZANIA.name to "TZ",
        CoffeeOrigin.Known.UGANDA.name to "UG",
        CoffeeOrigin.Known.DR_CONGO.name to "CD",
        CoffeeOrigin.Known.INDIA.name to "IN",
        CoffeeOrigin.Known.VIETNAM.name to "VN",
        CoffeeOrigin.Known.MYANMAR.name to "MM",
        CoffeeOrigin.Known.LAOS.name to "LA",
        CoffeeOrigin.Known.THAILAND.name to "TH",
        CoffeeOrigin.Known.CHINA.name to "CN",
        CoffeeOrigin.Known.ECUADOR.name to "EC",
        CoffeeOrigin.Known.BOLIVIA.name to "BO",
        CoffeeOrigin.Known.MALAWI.name to "MW",
        CoffeeOrigin.Known.ZAMBIA.name to "ZM",
    )

    private val processLocalizedLabels = mapOf(
        CoffeeProcessType.Known.WASHED.name to mapOf("cs" to "Praný", "de" to "Gewaschen", "es" to "Lavado", "it" to "Lavato", "da" to "Vasket"),
        CoffeeProcessType.Known.NATURAL.name to mapOf("cs" to "Přírodní", "de" to "Natur", "es" to "Natural", "it" to "Naturale", "da" to "Naturlig"),
        CoffeeProcessType.Known.HONEY.name to mapOf("cs" to "Medový", "de" to "Honey", "es" to "Miel", "it" to "Miele", "da" to "Honey"),
        CoffeeProcessType.Known.ANAEROBIC.name to mapOf("cs" to "Anaerobní", "de" to "Anaerob", "es" to "Anaeróbico", "it" to "Anaerobico", "da" to "Anaerob"),
        CoffeeProcessType.Known.CARBONIC_MACERATION.name to mapOf("cs" to "Karbonická macerace", "de" to "Kohlensäuremaischung", "es" to "Maceración carbónica", "it" to "Macerazione carbonica", "da" to "Kulsyremaceration"),
        CoffeeProcessType.Known.SEMI_WASHED.name to mapOf("cs" to "Polopraný", "de" to "Semi-Washed", "es" to "Semi lavado", "it" to "Semi-lavato", "da" to "Semi-vasket"),
        CoffeeProcessType.Known.WET_HULLED.name to mapOf("cs" to "Mokré loupání", "de" to "Wet-Hulled", "es" to "Descascarado húmedo", "it" to "Wet hulled", "da" to "Wet hulled"),
        CoffeeProcessType.Known.PULPED_NATURAL.name to mapOf("cs" to "Pulped Natural", "de" to "Pulped Natural", "es" to "Natural despulpado", "it" to "Pulped natural", "da" to "Pulped natural"),
        CoffeeProcessType.Known.DOUBLE_FERMENTED.name to mapOf("cs" to "Dvojitě fermentovaný", "de" to "Doppelt fermentiert", "es" to "Doble fermentación", "it" to "Doppia fermentazione", "da" to "Dobbeltfermenteret"),
        CoffeeProcessType.Known.THERMAL_SHOCK.name to mapOf("cs" to "Thermal Shock", "de" to "Thermal Shock", "es" to "Choque térmico", "it" to "Shock termico", "da" to "Thermal Shock"),
    )

    private val roastLocalizedLabels = mapOf(
        CoffeeRoastLevel.Known.LIGHT.name to mapOf("cs" to "Světlé", "de" to "Hell", "es" to "Claro", "it" to "Chiaro", "da" to "Lys"),
        CoffeeRoastLevel.Known.MEDIUM_LIGHT.name to mapOf("cs" to "Středně světlé", "de" to "Mittel-hell", "es" to "Medio claro", "it" to "Medio-chiaro", "da" to "Mellem-lys"),
        CoffeeRoastLevel.Known.MEDIUM.name to mapOf("cs" to "Střední", "de" to "Mittel", "es" to "Medio", "it" to "Medio", "da" to "Mellem"),
        CoffeeRoastLevel.Known.MEDIUM_DARK.name to mapOf("cs" to "Středně tmavé", "de" to "Mittel-dunkel", "es" to "Medio oscuro", "it" to "Medio-scuro", "da" to "Mellem-mørk"),
        CoffeeRoastLevel.Known.DARK.name to mapOf("cs" to "Tmavé", "de" to "Dunkel", "es" to "Oscuro", "it" to "Scuro", "da" to "Mørk"),
        CoffeeRoastLevel.Known.FILTER.name to mapOf("cs" to "Filtr", "de" to "Filter", "es" to "Filtro", "it" to "Filtro", "da" to "Filter"),
        CoffeeRoastLevel.Known.ESPRESSO.name to mapOf("cs" to "Espresso", "de" to "Espresso", "es" to "Espresso", "it" to "Espresso", "da" to "Espresso"),
        CoffeeRoastLevel.Known.OMNIROAST.name to mapOf("cs" to "Omniroast", "de" to "Omniroast", "es" to "Omniroast", "it" to "Omniroast", "da" to "Omniroast"),
        CoffeeRoastLevel.Known.CINNAMON.name to mapOf("cs" to "Skořicové", "de" to "Zimt", "es" to "Canela", "it" to "Cannella", "da" to "Kanel"),
    )

    private val tasteNoteEntries = listOf(
        note(id = "bergamot", defaultLabel = "Bergamot", descriptor = FlavorDescriptor.CITRUS, es = "Bergamota", de = "Bergamotte", it = "Bergamotto"),
        note(id = "peach", defaultLabel = "Peach", descriptor = FlavorDescriptor.FRUITY, cs = "Broskev", de = "Pfirsich", es = "Melocotón", it = "Pesca", da = "Fersken"),
        note(id = "peach_tea", defaultLabel = "Peach tea", descriptor = FlavorDescriptor.FRUITY, cs = "Broskvový čaj", de = "Pfirsichtee", es = "Té de melocotón", it = "Tè alla pesca", da = "Ferskente"),
        note(id = "raw_honey", defaultLabel = "Raw honey", descriptor = FlavorDescriptor.SWEET, cs = "Syrový med", de = "Rohhonig", es = "Miel cruda", it = "Miele grezzo", da = "Rå honning"),
        note(id = "honey", defaultLabel = "Honey", descriptor = FlavorDescriptor.SWEET, cs = "Med", de = "Honig", es = "Miel", it = "Miele", da = "Honning"),
        note(id = "yuzu", defaultLabel = "Yuzu", descriptor = FlavorDescriptor.CITRUS, cs = "Yuzu", de = "Yuzu", es = "Yuzu", it = "Yuzu", da = "Yuzu"),
        note(id = "wild_strawberry", defaultLabel = "Wild strawberry", descriptor = FlavorDescriptor.BERRY, cs = "Lesní jahoda", de = "Walderdbeere", es = "Fresa silvestre", it = "Fragolina di bosco", da = "Skovjordbær"),
        note(id = "berry", defaultLabel = "Berry", descriptor = FlavorDescriptor.BERRY, cs = "Bobule", de = "Beere", es = "Baya", it = "Bacca", da = "Bær"),
        note(id = "plum", defaultLabel = "Plum", descriptor = FlavorDescriptor.FRUITY, cs = "Švestka", de = "Pflaume", es = "Ciruela", it = "Prugna", da = "Blomme"),
        note(id = "green_tea", defaultLabel = "Green tea", descriptor = FlavorDescriptor.CLEAN, cs = "Zelený čaj", de = "Grüner Tee", es = "Té verde", it = "Tè verde", da = "Grøn te"),
        note(id = "chocolate", defaultLabel = "Chocolate", descriptor = FlavorDescriptor.CHOCOLATE, cs = "Čokoláda", de = "Schokolade", es = "Chocolate", it = "Cioccolato", da = "Chokolade"),
        note(id = "hazelnut", defaultLabel = "Hazelnut", descriptor = FlavorDescriptor.NUTTY, cs = "Lískový ořech", de = "Haselnuss", es = "Avellana", it = "Nocciola", da = "Hasselnød"),
        note(id = "caramel", defaultLabel = "Caramel", descriptor = FlavorDescriptor.CARAMEL, cs = "Karamel", de = "Karamell", es = "Caramelo", it = "Caramello", da = "Karamel"),
        note(id = "citrus", defaultLabel = "Citrus", descriptor = FlavorDescriptor.CITRUS, cs = "Citrusy", de = "Zitrus", es = "Cítricos", it = "Agrumi", da = "Citrus"),
        note(id = "floral", defaultLabel = "Floral", descriptor = FlavorDescriptor.FLORAL, cs = "Květinové", de = "Blumig", es = "Floral", it = "Floreale", da = "Blomstret"),
        note(id = "fruity", defaultLabel = "Fruity", descriptor = FlavorDescriptor.FRUITY, cs = "Ovocné", de = "Fruchtig", es = "Afrutado", it = "Fruttato", da = "Frugtig"),
        note(id = "nutty", defaultLabel = "Nutty", descriptor = FlavorDescriptor.NUTTY, cs = "Ořechové", de = "Nussig", es = "A nuez", it = "Frutta secca", da = "Nøddeagtig"),
        note(id = "sweet", defaultLabel = "Sweet", descriptor = FlavorDescriptor.SWEET, cs = "Sladké", de = "Süß", es = "Dulce", it = "Dolce", da = "Sød"),
        note(id = "smooth", defaultLabel = "Smooth", descriptor = FlavorDescriptor.SMOOTH, cs = "Hebké", de = "Sanft", es = "Suave", it = "Morbido", da = "Blød"),
        note(id = "earthy", defaultLabel = "Earthy", descriptor = FlavorDescriptor.EARTHY, cs = "Zemité", de = "Erdig", es = "Terroso", it = "Terroso", da = "Jordet"),
        note(id = "spicy", defaultLabel = "Spicy", descriptor = FlavorDescriptor.SPICY, cs = "Kořenité", de = "Würzig", es = "Especiado", it = "Speziato", da = "Krydret"),
        note(id = "wine_like", defaultLabel = "Wine-like", descriptor = FlavorDescriptor.WINE, cs = "Vínové", de = "Weinartig", es = "Avinado", it = "Vinoso", da = "Vinøs"),
        note(id = "clean", defaultLabel = "Clean", descriptor = FlavorDescriptor.CLEAN, cs = "Čisté", de = "Klar", es = "Limpio", it = "Pulito", da = "Ren"),
    )

    private val originEntries by lazy {
        CoffeeOrigin.Known.entries.map { entry ->
            val localizedLabels = supportedLocales.associate { locale ->
                locale.language to displayCountryFor(entry.name, locale)
            }
            CanonicalEntry(
                id = entry.name,
                defaultLabel = entry.displayName,
                localizedLabels = localizedLabels,
                aliases = buildSet {
                    add(entry.displayName)
                    addAll(entry.searchAliases)
                    addAll(localizedLabels.values)
                },
            )
        }
    }

    private val regionEntries by lazy {
        CoffeeRegion.Known.entries.map { entry ->
            val regionOriginIds = entry.countries
                .mapNotNull { country -> CoffeeOrigin.Known.entries.firstOrNull { it.displayName == country }?.name }
            CanonicalEntry(
                id = entry.name,
                defaultLabel = entry.displayName,
                aliases = buildSet {
                    add(entry.displayName)
                    addAll(entry.searchAliases)
                },
                relatedCanonicalKeys = buildMap {
                    if (regionOriginIds.size == 1) {
                        put(MetadataFieldType.ORIGIN.fieldName, regionOriginIds.first())
                    }
                },
            )
        }
    }

    private val varietyEntries by lazy {
        CoffeeVariety.Known.entries.map { entry ->
            CanonicalEntry(
                id = entry.name,
                defaultLabel = entry.displayName,
                aliases = buildSet {
                    add(entry.displayName)
                    addAll(entry.searchAliases)
                },
            )
        }
    }

    private val processEntries by lazy {
        CoffeeProcessType.Known.entries.map { entry ->
            val localizedLabels = processLocalizedLabels[entry.name].orEmpty()
            CanonicalEntry(
                id = entry.name,
                defaultLabel = entry.displayName,
                localizedLabels = localizedLabels,
                aliases = buildSet {
                    add(entry.displayName)
                    addAll(entry.searchAliases)
                    addAll(localizedLabels.values)
                },
            )
        }
    }

    private val roastEntries by lazy {
        CoffeeRoastLevel.Known.entries.map { entry ->
            val localizedLabels = roastLocalizedLabels[entry.name].orEmpty()
            CanonicalEntry(
                id = entry.name,
                defaultLabel = entry.displayName,
                localizedLabels = localizedLabels,
                aliases = buildSet {
                    add(entry.displayName)
                    addAll(entry.searchAliases)
                    addAll(localizedLabels.values)
                },
            )
        }
    }

    private val entriesByField by lazy {
        mapOf(
            MetadataFieldType.ORIGIN to originEntries,
            MetadataFieldType.REGION to regionEntries,
            MetadataFieldType.PROCESS_TYPE to processEntries,
            MetadataFieldType.ROAST_LEVEL to roastEntries,
            MetadataFieldType.VARIETY to varietyEntries,
            MetadataFieldType.TASTE_NOTE to tasteNoteEntries,
        )
    }

    private val entriesByFieldAndId by lazy {
        entriesByField.mapValues { (_, entries) -> entries.associateBy(CanonicalEntry::id) }
    }

    fun normalizeField(
        fieldName: String,
        rawValue: String?,
        locale: Locale = Locale.getDefault(),
    ): NormalizedCoffeeField? {
        val cleanValue = rawValue?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val fieldType = MetadataFieldType.fromFieldName(fieldName)
        if (fieldType == null) {
            return NormalizedCoffeeField(
                value = cleanValue,
                rawValue = cleanValue,
            )
        }
        return if (fieldType.multiValue) {
            matchMultiValue(fieldType, cleanValue, locale)
        } else {
            matchSingleValue(fieldType, cleanValue, locale)
        }
    }

    fun normalizeBagMetadata(
        origin: String?,
        region: String?,
        roastLevel: String?,
        processType: String?,
        variety: String?,
        tastingNotes: String?,
        locale: Locale = Locale.getDefault(),
    ): NormalizedCoffeeBagMetadata {
        val normalizedOrigin = normalizeField(MetadataFieldType.ORIGIN.fieldName, origin, locale)
        val normalizedRegion = normalizeField(MetadataFieldType.REGION.fieldName, region, locale)
        val inferredOriginId = normalizedOrigin?.canonicalKey
            ?: normalizedRegion?.relatedCanonicalKeys?.get(MetadataFieldType.ORIGIN.fieldName)
        val inferredOriginValue = normalizedOrigin?.value ?: inferredOriginId?.let { displayOrigin(it, origin, locale) }

        val normalizedRoast = normalizeField(MetadataFieldType.ROAST_LEVEL.fieldName, roastLevel, locale)
        val normalizedProcess = normalizeField(MetadataFieldType.PROCESS_TYPE.fieldName, processType, locale)
        val normalizedVariety = normalizeField(MetadataFieldType.VARIETY.fieldName, variety, locale)
        val normalizedNotes = normalizeField(MetadataFieldType.TASTE_NOTE.fieldName, tastingNotes, locale)

        return NormalizedCoffeeBagMetadata(
            origin = inferredOriginValue ?: origin?.trim()?.takeIf { it.isNotBlank() },
            originId = inferredOriginId,
            region = normalizedRegion?.value ?: region?.trim()?.takeIf { it.isNotBlank() },
            regionId = normalizedRegion?.canonicalKey,
            roastLevel = normalizedRoast?.value ?: roastLevel?.trim()?.takeIf { it.isNotBlank() },
            roastLevelIds = normalizedRoast?.canonicalKey,
            processType = normalizedProcess?.value ?: processType?.trim()?.takeIf { it.isNotBlank() },
            processTypeId = normalizedProcess?.canonicalKey,
            variety = normalizedVariety?.value ?: variety?.trim()?.takeIf { it.isNotBlank() },
            varietyIds = normalizedVariety?.canonicalKey,
            tastingNotes = normalizedNotes?.value ?: tastingNotes?.trim()?.takeIf { it.isNotBlank() },
            tasteNoteIds = normalizedNotes?.canonicalKey,
        )
    }

    @Suppress("LongParameterList")
    fun applyToBagEntity(
        bag: CoffeeBagEntity,
        origin: String?,
        region: String?,
        roastLevel: String?,
        processType: String?,
        variety: String?,
        tastingNotes: String?,
        locale: Locale = Locale.getDefault(),
    ): CoffeeBagEntity {
        val normalized = normalizeBagMetadata(
            origin = origin,
            region = region,
            roastLevel = roastLevel,
            processType = processType,
            variety = variety,
            tastingNotes = tastingNotes,
            locale = locale,
        )
        return bag.copy(
            origin = origin?.trim()?.takeIf { it.isNotBlank() },
            originId = normalized.originId,
            region = region?.trim()?.takeIf { it.isNotBlank() },
            regionId = normalized.regionId,
            roastLevel = roastLevel?.trim()?.takeIf { it.isNotBlank() },
            roastLevelIds = normalized.roastLevelIds,
            processType = processType?.trim()?.takeIf { it.isNotBlank() },
            processTypeId = normalized.processTypeId,
            variety = variety?.trim()?.takeIf { it.isNotBlank() },
            varietyIds = normalized.varietyIds,
            tastingNotes = tastingNotes?.trim()?.takeIf { it.isNotBlank() },
            tasteNoteIds = normalized.tasteNoteIds,
        )
    }

    fun resolveBagMetadata(
        bag: CoffeeBagEntity,
        locale: Locale = Locale.getDefault(),
    ): NormalizedCoffeeBagMetadata {
        val normalized = normalizeBagMetadata(
            origin = bag.origin,
            region = bag.region,
            roastLevel = bag.roastLevel,
            processType = bag.processType,
            variety = bag.variety,
            tastingNotes = bag.tastingNotes,
            locale = locale,
        )

        val originId = bag.originId ?: normalized.originId
        val regionId = bag.regionId ?: normalized.regionId
        val roastLevelIds = bag.roastLevelIds ?: normalized.roastLevelIds
        val processTypeId = bag.processTypeId ?: normalized.processTypeId
        val varietyIds = bag.varietyIds ?: normalized.varietyIds
        val tasteNoteIds = bag.tasteNoteIds ?: normalized.tasteNoteIds

        return NormalizedCoffeeBagMetadata(
            origin = displayOrigin(originId, bag.origin ?: normalized.origin, locale),
            originId = originId,
            region = displayRegion(regionId, bag.region ?: normalized.region, locale),
            regionId = regionId,
            roastLevel = displayRoastLevels(roastLevelIds, bag.roastLevel ?: normalized.roastLevel, locale),
            roastLevelIds = roastLevelIds,
            processType = displayProcessType(processTypeId, bag.processType ?: normalized.processType, locale),
            processTypeId = processTypeId,
            variety = displayVarieties(varietyIds, bag.variety ?: normalized.variety, locale),
            varietyIds = varietyIds,
            tastingNotes = displayTastingNotes(tasteNoteIds, bag.tastingNotes ?: normalized.tastingNotes, locale),
            tasteNoteIds = tasteNoteIds,
        )
    }

    fun displayOrigin(
        originId: String?,
        fallbackRaw: String?,
        locale: Locale = Locale.getDefault(),
    ): String? = displaySingleValue(MetadataFieldType.ORIGIN, originId, fallbackRaw, locale)

    fun displayRegion(
        regionId: String?,
        fallbackRaw: String?,
        locale: Locale = Locale.getDefault(),
    ): String? = displaySingleValue(MetadataFieldType.REGION, regionId, fallbackRaw, locale)

    fun displayProcessType(
        processTypeId: String?,
        fallbackRaw: String?,
        locale: Locale = Locale.getDefault(),
    ): String? = displaySingleValue(MetadataFieldType.PROCESS_TYPE, processTypeId, fallbackRaw, locale)

    fun displayRoastLevels(
        roastLevelIds: String?,
        fallbackRaw: String?,
        locale: Locale = Locale.getDefault(),
    ): String? = displayMultiValue(MetadataFieldType.ROAST_LEVEL, roastLevelIds, fallbackRaw, locale)

    fun displayVarieties(
        varietyIds: String?,
        fallbackRaw: String?,
        locale: Locale = Locale.getDefault(),
    ): String? = displayMultiValue(MetadataFieldType.VARIETY, varietyIds, fallbackRaw, locale)

    fun displayTastingNotes(
        tasteNoteIds: String?,
        fallbackRaw: String?,
        locale: Locale = Locale.getDefault(),
    ): String? = displayMultiValue(MetadataFieldType.TASTE_NOTE, tasteNoteIds, fallbackRaw, locale)

    fun displayField(
        fieldName: String,
        canonicalKey: String?,
        fallbackRaw: String?,
        locale: Locale = Locale.getDefault(),
    ): String? {
        val fieldType = MetadataFieldType.fromFieldName(fieldName)
            ?: return fallbackRaw?.trim()?.takeIf { it.isNotBlank() }
        return if (fieldType.multiValue) {
            displayMultiValue(fieldType, canonicalKey, fallbackRaw, locale)
        } else {
            displaySingleValue(fieldType, canonicalKey, fallbackRaw, locale)
        }
    }

    fun regionsForOrigin(
        originValue: String?,
        locale: Locale = Locale.getDefault(),
    ): List<CoffeeRegion.Known> {
        val originId = normalizeField(MetadataFieldType.ORIGIN.fieldName, originValue, locale)?.canonicalKey
            ?: return CoffeeRegion.Known.entries.toList()
        return CoffeeRegion.Known.entries
            .filter { entry ->
                entry.countries.any { country ->
                    CoffeeOrigin.Known.entries.firstOrNull { it.displayName == country }?.name == originId
                }
            }
            .sortedBy { it.displayName }
    }

    fun aliasesForFieldValue(fieldName: String, value: String): Set<String> {
        val fieldType = MetadataFieldType.fromFieldName(fieldName) ?: return setOf(value)
        val match = normalizeField(fieldName, value, Locale.ENGLISH)
        val ids = parseCanonicalIds(match?.canonicalKey)
        if (ids.isEmpty()) return setOf(value)
        return ids.flatMapTo(linkedSetOf()) { id ->
            entriesByFieldAndId[fieldType]?.get(id)?.aliases.orEmpty()
        }
    }

    fun searchTermsForField(fieldName: String): List<String> {
        val fieldType = MetadataFieldType.fromFieldName(fieldName) ?: return emptyList()
        return entriesByField[fieldType]
            .orEmpty()
            .flatMap { entry -> entry.aliases }
            .distinct()
            .sortedByDescending { it.length }
    }

    fun containsDecafMarker(text: String?): Boolean {
        val normalized = text
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeSearch)
            ?: return false
        for (pattern in decafMarkerPatterns) {
            val match = pattern.find(normalized) ?: continue
            if (!isNegatedDecafMatch(normalized, match.range.first)) {
                return true
            }
        }
        return false
    }

    private fun isNegatedDecafMatch(normalized: String, matchStart: Int): Boolean {
        if (matchStart <= 0) return false
        val precedingText = normalized.substring(0, matchStart).trimEnd()
        if (precedingText.isEmpty()) return false
        val precedingToken = precedingText.substringAfterLast(' ')
        return precedingToken in decafNegationTokens
    }

    fun inferenceAliases(fieldName: String, value: String): Set<String> {
        if (fieldName != MetadataFieldType.ORIGIN.fieldName) return emptySet()
        val originId = normalizeField(fieldName, value, Locale.ENGLISH)?.canonicalKey ?: return emptySet()
        return regionEntries
            .filter { it.relatedCanonicalKeys[MetadataFieldType.ORIGIN.fieldName] == originId }
            .flatMapTo(linkedSetOf()) { it.aliases }
    }

    // Controlled-vocabulary slots that participate in cross-field
    // reclassification. Free-text fields (name, roaster, farm, altitude,
    // tasting notes) are deliberately excluded — they have no closed dictionary
    // to validate against, so a dictionary "match" there would be a false hit.
    private val controlledSlotTypes = listOf(
        MetadataFieldType.ORIGIN,
        MetadataFieldType.REGION,
        MetadataFieldType.PROCESS_TYPE,
        MetadataFieldType.ROAST_LEVEL,
        MetadataFieldType.VARIETY,
    )

    // When a value belongs to several fields, prefer the more specific/atomic
    // one as the relocation target. Origin first (country names are unambiguous),
    // then process/roast/variety, region last (region is the widest free-text
    // slot, so it's the least authoritative target).
    private val reclassifyTargetPriority = listOf("origin", "processType", "roastLevel", "variety", "region")

    /**
     * The controlled field(s) whose canonical dictionary contains an EXACT
     * alias for [rawValue]. Strict by design (no substring matching): this
     * drives cross-field reclassification, so a free-text value that merely
     * *contains* a dictionary word (e.g. a farm name) must not be treated as
     * belonging to that field.
     */
    fun classifyControlledValue(rawValue: String?): Set<String> {
        val normalized = rawValue?.let(::normalizeSearch)?.takeIf(String::isNotBlank) ?: return emptySet()
        return controlledSlotTypes
            .filter { type -> entriesByField[type].orEmpty().any { normalized in it.normalizedAliases } }
            .map { it.fieldName }
            .toSet()
    }

    private fun canonicalIdFor(fieldName: String, value: String?): String? =
        value?.let { normalizeField(fieldName, it, Locale.ENGLISH)?.canonicalKey }

    /**
     * Enforce per-field contracts on a raw LLM/OCR extraction before it reaches
     * the review chips and the saved bag — the extraction step itself trusts
     * whatever the on-device model emits, which on bilingual/structured labels
     * routinely lands correctly-read tokens in the wrong field.
     *
     * Three deterministic, dictionary-driven rules (all reversible by the user,
     * all preferring relocate-over-drop so nothing real is silently lost):
     *  1. Decaf displacement — a bare decaf marker is never a process/region/
     *     variety/roast value; move it to the [isDecaf] flag and clear the slot.
     *  2. Cross-field reclassification — a controlled value that exactly matches
     *     a *different* field's dictionary (e.g. a country name in `region`) is
     *     relocated when its true field is empty, else dropped as a duplicate/
     *     conflict. Unknown free-text values pass through untouched.
     *  3. Weight recovery — a weight that doesn't parse is reduced to its first
     *     valid token (OCR merges like "250gC1000g" → "250g"), else dropped.
     *
     * Espresso/Filter roast values, genuine free-text regions ("Tumbaga") and
     * already-valid weights are intentionally preserved.
     */
    @Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
    fun sanitizeExtraction(
        origin: String?,
        region: String?,
        processType: String?,
        roastLevel: String?,
        variety: String?,
        weight: String?,
        isDecaf: Boolean?,
    ): SanitizedExtraction {
        val corrections = mutableListOf<ScanFieldCorrection>()
        val values = linkedMapOf(
            "origin" to origin?.trim()?.takeIf(String::isNotBlank),
            "region" to region?.trim()?.takeIf(String::isNotBlank),
            "processType" to processType?.trim()?.takeIf(String::isNotBlank),
            "roastLevel" to roastLevel?.trim()?.takeIf(String::isNotBlank),
            "variety" to variety?.trim()?.takeIf(String::isNotBlank),
        )
        var outDecaf = isDecaf

        // Rule 0 — generic/non-values that are never valid regardless of which
        // slot they landed in: a bare botanical species name (origin/variety)
        // or a bean-form/roast-form descriptor (processType). These must be
        // dropped BEFORE Rule 2 runs, not relocated between slots — moving
        // "Arabica" from origin to variety would just move the hallucination.
        run {
            val origin = values["origin"]
            if (origin != null && normalizeSearch(origin) in bareSpeciesTokens) {
                values["origin"] = null
                corrections += ScanFieldCorrection(
                    field = "origin",
                    action = ScanFieldCorrectionAction.DROPPED,
                    from = origin,
                    to = null,
                    reason = "bare species name is not a country of origin",
                )
            }
            val variety = values["variety"]
            if (variety != null && normalizeSearch(variety) in bareSpeciesTokens) {
                values["variety"] = null
                corrections += ScanFieldCorrection(
                    field = "variety",
                    action = ScanFieldCorrectionAction.DROPPED,
                    from = variety,
                    to = null,
                    reason = "bare species name does not distinguish a cultivar",
                )
            }
            val process = values["processType"]
            if (process != null && normalizeSearch(process) in beanFormOnlyTokens) {
                values["processType"] = null
                corrections += ScanFieldCorrection(
                    field = "processType",
                    action = ScanFieldCorrectionAction.DROPPED,
                    from = process,
                    to = null,
                    reason = "bean-form/roast-form descriptor is not a processing method",
                )
            }
        }

        // Rule 1 — decaf displacement.
        for (field in listOf("processType", "region", "variety", "roastLevel")) {
            val value = values[field] ?: continue
            if (containsDecafMarker(value) && field !in classifyControlledValue(value)) {
                values[field] = null
                if (outDecaf != true) outDecaf = true
                corrections += ScanFieldCorrection(
                    field = field,
                    action = ScanFieldCorrectionAction.FLAGGED_DECAF,
                    from = value,
                    to = null,
                    reason = "decaf marker is not a $field value; moved to isDecaf flag",
                )
            }
        }

        // Rule 2 — cross-field reclassification.
        for (sourceField in listOf("region", "processType", "roastLevel", "variety", "origin")) {
            val value = values[sourceField] ?: continue
            val belongsTo = classifyControlledValue(value)
            if (belongsTo.isEmpty() || sourceField in belongsTo) continue
            val target = reclassifyTargetPriority.firstOrNull { it in belongsTo } ?: continue
            val targetValue = values[target]
            when {
                targetValue == null -> {
                    values[target] = value
                    values[sourceField] = null
                    corrections += ScanFieldCorrection(
                        field = sourceField,
                        action = ScanFieldCorrectionAction.RELOCATED,
                        from = value,
                        to = target,
                        reason = "value belongs to $target, which was empty",
                    )
                }
                canonicalIdFor(target, targetValue) != null &&
                    canonicalIdFor(target, targetValue) == canonicalIdFor(target, value) -> {
                    values[sourceField] = null
                    corrections += ScanFieldCorrection(
                        field = sourceField,
                        action = ScanFieldCorrectionAction.DROPPED,
                        from = value,
                        to = null,
                        reason = "duplicates $target",
                    )
                }
                else -> {
                    values[sourceField] = null
                    corrections += ScanFieldCorrection(
                        field = sourceField,
                        action = ScanFieldCorrectionAction.DROPPED,
                        from = value,
                        to = null,
                        reason = "belongs to $target, which already holds a different value",
                    )
                }
            }
        }

        // Rule 3 — weight format recovery.
        var outWeight = weight?.trim()?.takeIf(String::isNotBlank)
        if (outWeight != null && WeightParser.parseToGrams(outWeight) == null) {
            val recovered = WeightParser.extractFirstWeightToken(outWeight)
            corrections += if (recovered != null) {
                ScanFieldCorrection("weight", ScanFieldCorrectionAction.NORMALIZED, outWeight, recovered, "recovered weight token from noisy value")
            } else {
                ScanFieldCorrection("weight", ScanFieldCorrectionAction.DROPPED, outWeight, null, "no parseable weight token")
            }
            outWeight = recovered
        }

        return SanitizedExtraction(
            origin = values["origin"],
            region = values["region"],
            processType = values["processType"],
            roastLevel = values["roastLevel"],
            variety = values["variety"],
            weight = outWeight,
            isDecaf = outDecaf,
            corrections = corrections,
        )
    }

    // Matches YYYY-MM (bare year-month, which DateParser doesn't parse but the
    // extraction prompts explicitly allow as a valid partial date).
    private val yearMonthPattern = Regex("^\\d{4}-\\d{2}$")

    /**
     * Deterministic guard for `roastDate`/`expiryDate`: returns [value]
     * unchanged if [DateParser] can actually parse it (or it is a bare
     * `YYYY-MM`, which the extraction prompts allow but DateParser doesn't
     * parse), else `null`. Delegates to DateParser rather than a private
     * format list so this accepts every format the rest of the app already
     * recognizes (ISO, dd.MM.yyyy, "January 2026", ...) and rejects anything
     * it doesn't — including a relative/descriptive phrase like "3 months
     * from roast date" or a garbled OCR token, which is exactly how a
     * hallucinated expiry date would otherwise reach the saved bag.
     */
    fun sanitizeDate(value: String?): String? {
        val trimmed = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        val isValid = DateParser.parse(trimmed) != null || yearMonthPattern.matches(trimmed)
        return trimmed.takeIf { isValid }
    }

    fun parseCanonicalIds(ids: String?): List<String> = ids
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        .orEmpty()

    fun bagDisplayOrigin(bag: CoffeeBagEntity, locale: Locale = Locale.getDefault()): String? =
        resolveBagMetadata(bag, locale).origin

    fun bagDisplayRegion(bag: CoffeeBagEntity, locale: Locale = Locale.getDefault()): String? =
        resolveBagMetadata(bag, locale).region

    fun bagDisplayProcessType(bag: CoffeeBagEntity, locale: Locale = Locale.getDefault()): String? =
        resolveBagMetadata(bag, locale).processType

    fun bagDisplayRoastLevels(bag: CoffeeBagEntity, locale: Locale = Locale.getDefault()): String? =
        resolveBagMetadata(bag, locale).roastLevel

    fun bagDisplayVarieties(bag: CoffeeBagEntity, locale: Locale = Locale.getDefault()): String? =
        resolveBagMetadata(bag, locale).variety

    fun bagDisplayTastingNotes(bag: CoffeeBagEntity, locale: Locale = Locale.getDefault()): String? =
        resolveBagMetadata(bag, locale).tastingNotes

    private fun matchSingleValue(
        fieldType: MetadataFieldType,
        rawValue: String,
        locale: Locale,
    ): NormalizedCoffeeField {
        val entry = findBestEntry(fieldType, rawValue)
        if (entry == null) {
            return NormalizedCoffeeField(
                value = prettifyRawValue(rawValue, locale),
                rawValue = rawValue,
            )
        }
        val strategy = if (entry.normalizedAliases.contains(normalizeSearch(rawValue))) {
            CoffeeMetadataMatchStrategy.EXACT_ALIAS
        } else {
            CoffeeMetadataMatchStrategy.CONTAINS_ALIAS
        }
        return NormalizedCoffeeField(
            value = localizeEntry(entry, locale),
            rawValue = rawValue,
            canonicalKey = entry.id,
            matchStrategy = strategy,
            relatedCanonicalKeys = entry.relatedCanonicalKeys,
        )
    }

    @Suppress(
        // Token loop → match → strategy upgrade is naturally three deep.
        // Splitting per-token would lose the strategy promotion ordering
        // that the whole function exists to enforce.
        "NestedBlockDepth",
    )
    private fun matchMultiValue(
        fieldType: MetadataFieldType,
        rawValue: String,
        locale: Locale,
    ): NormalizedCoffeeField {
        val tokens = tokenizeValue(rawValue)
        if (tokens.isEmpty()) {
            return NormalizedCoffeeField(
                value = prettifyRawValue(rawValue, locale),
                rawValue = rawValue,
            )
        }

        val matchedIds = mutableListOf<String>()
        val displayTokens = mutableListOf<String>()
        var strategy: CoffeeMetadataMatchStrategy = CoffeeMetadataMatchStrategy.RAW_FALLBACK

        tokens.forEach { token ->
            val entry = findBestEntry(fieldType, token)
            if (entry == null) {
                displayTokens += prettifyRawValue(token, locale)
            } else {
                matchedIds += entry.id
                displayTokens += localizeEntry(entry, locale)
                if (strategy == CoffeeMetadataMatchStrategy.RAW_FALLBACK) {
                    strategy = if (entry.normalizedAliases.contains(normalizeSearch(token))) {
                        CoffeeMetadataMatchStrategy.EXACT_ALIAS
                    } else {
                        CoffeeMetadataMatchStrategy.CONTAINS_ALIAS
                    }
                }
            }
        }

        val canonicalKey = matchedIds
            .distinct()
            .sorted()
            .joinToString(",")
            .takeIf { it.isNotBlank() }

        return NormalizedCoffeeField(
            value = displayTokens.distinct().joinToString(", "),
            rawValue = rawValue,
            canonicalKey = canonicalKey,
            matchStrategy = strategy,
        )
    }

    private fun findBestEntry(fieldType: MetadataFieldType, rawValue: String): CanonicalEntry? {
        val normalized = normalizeSearch(rawValue)
        if (normalized.isBlank()) return null
        val entries = entriesByField[fieldType].orEmpty()
        return entries.firstOrNull { normalized in it.normalizedAliases }
            ?: entries
                .asSequence()
                .mapNotNull { entry ->
                    val bestAlias = entry.normalizedAliases
                        .filter { alias -> alias.length >= 3 && (normalized.contains(alias) || alias.contains(normalized)) }
                        .maxByOrNull(String::length)
                    if (bestAlias == null) null else bestAlias.length to entry
                }
                .maxByOrNull { it.first }
                ?.second
    }

    private fun displaySingleValue(
        fieldType: MetadataFieldType,
        canonicalKey: String?,
        fallbackRaw: String?,
        locale: Locale,
    ): String? {
        val cleanFallback = fallbackRaw?.trim()?.takeIf { it.isNotBlank() }
        val entry = canonicalKey
            ?.takeIf { it.isNotBlank() }
            ?.let { entriesByFieldAndId[fieldType]?.get(it) }
        return when {
            entry != null -> localizeEntry(entry, locale)
            cleanFallback != null -> prettifyRawValue(cleanFallback, locale)
            else -> null
        }
    }

    private fun displayMultiValue(
        fieldType: MetadataFieldType,
        canonicalIds: String?,
        fallbackRaw: String?,
        locale: Locale,
    ): String? {
        val ids = parseCanonicalIds(canonicalIds)
        val cleanFallback = fallbackRaw?.trim()?.takeIf { it.isNotBlank() }
        if (ids.isEmpty()) {
            return cleanFallback?.let { prettifyRawValue(it, locale) }
        }
        val fallbackTokenCount = tokenizeValue(cleanFallback).size
        if (cleanFallback != null && fallbackTokenCount > ids.size) {
            return prettifyRawValue(cleanFallback, locale)
        }
        val localized = ids
            .mapNotNull { entriesByFieldAndId[fieldType]?.get(it) }
            .map { localizeEntry(it, locale) }
            .distinct()
        return localized.joinToString(", ").takeIf { it.isNotBlank() } ?: cleanFallback
    }

    private fun localizeEntry(entry: CanonicalEntry, locale: Locale): String =
        entry.localizedLabels[locale.language]
            ?.takeIf { it.isNotBlank() }
            ?: entry.defaultLabel

    private fun displayCountryFor(originId: String, locale: Locale): String {
        val countryCode = originCountryCodes[originId]
        val countryName = countryCode
            ?.let { code -> Locale.Builder().setRegion(code).build().getDisplayCountry(locale) }
            ?.takeIf { it.isNotBlank() }
        return countryName ?: CoffeeOrigin.Known.valueOf(originId).displayName
    }

    @Suppress("LongParameterList")
    private fun note(
        id: String,
        defaultLabel: String,
        descriptor: FlavorDescriptor,
        cs: String = defaultLabel,
        de: String = defaultLabel,
        es: String = defaultLabel,
        it: String = defaultLabel,
        da: String = defaultLabel,
    ): CanonicalEntry {
        val localized = mapOf(
            "cs" to cs,
            "de" to de,
            "es" to es,
            "it" to it,
            "da" to da,
        )
        return CanonicalEntry(
            id = id,
            defaultLabel = defaultLabel,
            localizedLabels = localized,
            aliases = buildSet {
                add(defaultLabel)
                addAll(localized.values)
            },
            relatedCanonicalKeys = mapOf("descriptor" to descriptor.name),
        )
    }

    private fun tokenizeValue(rawValue: String?): List<String> = rawValue
        ?.split(",", ";", "·", "•", "|", "/", "\n")
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        .orEmpty()

    private fun prettifyRawValue(value: String, locale: Locale): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return ""
        val tokens = tokenizeValue(trimmed)
        if (tokens.size > 1) {
            return tokens.joinToString(", ") { prettifyRawValue(it, locale) }
        }
        return trimmed.lowercase(locale).replaceFirstChar { first ->
            if (first.isLowerCase()) {
                first.titlecase(locale)
            } else {
                first.toString()
            }
        }
    }

    internal fun normalizeSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace("[^\\p{Alnum}]+".toRegex(), " ")
        .trim()
}
