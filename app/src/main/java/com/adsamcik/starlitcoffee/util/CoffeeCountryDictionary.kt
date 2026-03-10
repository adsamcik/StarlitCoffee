package com.adsamcik.starlitcoffee.util

import java.util.Locale

/**
 * Per-country OCR vocabulary for coffee bag label recognition.
 *
 * Each dictionary provides the section-label keywords that roasters in a given
 * GS1 region tend to print on their bags (e.g. "Původ" for origin on Czech bags).
 * These are used to **prioritize** — not exclude — language-specific matches when
 * a barcode hints at the product's registration country.
 *
 * To add a new country: create a new [CoffeeCountryDictionary] instance and register
 * it in [CoffeeCountryDictionaries.ALL] and [CoffeeCountryDictionaries.byGs1Prefix].
 */
data class CoffeeCountryDictionary(
    val countryCode: String,
    val countryName: String,
    val locale: Locale,
    val gs1PrefixRange: IntRange,
    val sectionLabels: CountrySectionLabels,
)

/**
 * Section-label keywords that appear on coffee bags in a specific language.
 * Each list contains the raw label strings (without trailing colon/punctuation)
 * as they would appear on a physical bag. OCR matching strips punctuation separately.
 */
data class CountrySectionLabels(
    val origin: List<String> = emptyList(),
    val roaster: List<String> = emptyList(),
    val tastingNotes: List<String> = emptyList(),
    val processType: List<String> = emptyList(),
    val roastLevel: List<String> = emptyList(),
    val variety: List<String> = emptyList(),
    val farm: List<String> = emptyList(),
    val weight: List<String> = emptyList(),
    val roastDate: List<String> = emptyList(),
    val expiryDate: List<String> = emptyList(),
)

object CoffeeCountryDictionaries {

    val CZECH = CoffeeCountryDictionary(
        countryCode = "CZ",
        countryName = "Czech Republic",
        locale = Locale("cs"),
        gs1PrefixRange = 859..859,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Původ", "Země původu", "Země"),
            roaster = listOf("Pražírna", "Praženo", "Praženo v"),
            tastingNotes = listOf("Chuťové poznámky", "Chuť", "Senzorický profil"),
            processType = listOf("Zpracování", "Metoda zpracování", "Process"),
            roastLevel = listOf("Stupeň pražení", "Pražení"),
            variety = listOf("Odrůda", "Varieta"),
            farm = listOf("Farma", "Plantáž", "Statek", "Producent"),
            weight = listOf("Hmotnost", "Čistá hmotnost"),
            roastDate = listOf("Datum pražení", "Praženo"),
            expiryDate = listOf("Spotřebujte do", "Nejlépe do", "Datum minimální trvanlivosti"),
        ),
    )

    val GERMAN = CoffeeCountryDictionary(
        countryCode = "DE",
        countryName = "Germany",
        locale = Locale.GERMAN,
        gs1PrefixRange = 400..440,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Herkunft", "Ursprung", "Herkunftsland"),
            roaster = listOf("Rösterei", "Kaffeerösterei", "Geröstet von", "Geröstet in"),
            tastingNotes = listOf("Geschmacksnoten", "Geschmack", "Aroma"),
            processType = listOf("Aufbereitung", "Verarbeitung"),
            roastLevel = listOf("Röstgrad", "Röstung"),
            variety = listOf("Sorte", "Varietät"),
            farm = listOf("Farm", "Finca", "Gut", "Erzeuger"),
            weight = listOf("Nettogewicht", "Inhalt"),
            roastDate = listOf("Röstdatum", "Geröstet am"),
            expiryDate = listOf("Mindestens haltbar bis", "MHD"),
        ),
    )

    val ITALIAN = CoffeeCountryDictionary(
        countryCode = "IT",
        countryName = "Italy",
        locale = Locale.ITALIAN,
        gs1PrefixRange = 800..839,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Origine", "Provenienza", "Paese di origine"),
            roaster = listOf("Torrefazione", "Tostatura", "Tostato da"),
            tastingNotes = listOf("Note di degustazione", "Note sensoriali", "Gusto"),
            processType = listOf("Lavorazione", "Metodo di lavorazione"),
            roastLevel = listOf("Grado di tostatura", "Tostatura"),
            variety = listOf("Varietà", "Cultivar"),
            farm = listOf("Fattoria", "Finca", "Fazenda", "Produttore"),
            weight = listOf("Peso netto", "Contenuto"),
            roastDate = listOf("Data di tostatura", "Tostato il"),
            expiryDate = listOf("Da consumarsi preferibilmente entro", "Scadenza"),
        ),
    )

    val POLISH = CoffeeCountryDictionary(
        countryCode = "PL",
        countryName = "Poland",
        locale = Locale("pl"),
        gs1PrefixRange = 590..590,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Pochodzenie", "Kraj pochodzenia"),
            roaster = listOf("Palarnia", "Palarnią", "Prażone przez"),
            tastingNotes = listOf("Nuty smakowe", "Smak", "Profil smakowy"),
            processType = listOf("Obróbka", "Metoda obróbki", "Przetwarzanie"),
            roastLevel = listOf("Stopień palenia", "Palenie"),
            variety = listOf("Odmiana", "Varietal"),
            farm = listOf("Farma", "Finca", "Producent", "Plantacja"),
            weight = listOf("Masa netto", "Waga"),
            roastDate = listOf("Data palenia"),
            expiryDate = listOf("Najlepiej spożyć przed", "Termin przydatności"),
        ),
    )

    val SPANISH = CoffeeCountryDictionary(
        countryCode = "ES",
        countryName = "Spain",
        locale = Locale("es"),
        gs1PrefixRange = 840..849,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Origen", "País de origen"),
            roaster = listOf("Tostador", "Tostado por", "Torrefactor"),
            tastingNotes = listOf("Notas de cata", "Sabor", "Perfil sensorial"),
            processType = listOf("Procesamiento", "Método de procesamiento", "Beneficio"),
            roastLevel = listOf("Grado de tueste", "Tueste"),
            variety = listOf("Variedad", "Cultivar"),
            farm = listOf("Finca", "Productor", "Hacienda"),
            weight = listOf("Peso neto", "Contenido"),
            roastDate = listOf("Fecha de tueste", "Tostado el"),
            expiryDate = listOf("Consumir preferentemente antes de", "Caducidad"),
        ),
    )

    val DANISH = CoffeeCountryDictionary(
        countryCode = "DK",
        countryName = "Denmark",
        locale = Locale("da"),
        gs1PrefixRange = 570..579,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Oprindelse", "Oprindelsesland"),
            roaster = listOf("Risteri", "Brændt af", "Kafferisteri"),
            tastingNotes = listOf("Smagsnoter", "Smag", "Smagsprofil"),
            processType = listOf("Forarbejdning", "Forarbejdningsmetode"),
            roastLevel = listOf("Ristningsgrad", "Ristning"),
            variety = listOf("Sort", "Varietet"),
            farm = listOf("Farm", "Finca", "Producent"),
            weight = listOf("Nettovægt", "Indhold"),
            roastDate = listOf("Ristningsdato", "Ristet"),
            expiryDate = listOf("Bedst før", "Mindst holdbar til"),
        ),
    )

    val ENGLISH_FALLBACK = CoffeeCountryDictionary(
        countryCode = "EN",
        countryName = "International (English)",
        locale = Locale.ENGLISH,
        gs1PrefixRange = IntRange.EMPTY,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Origin", "Country", "Country of origin", "Single origin"),
            roaster = listOf("Roaster", "Roastery", "Coffee roasters", "Roasted by"),
            tastingNotes = listOf("Tasting notes", "Cupping notes", "Notes", "Flavor", "Flavour", "Tastes like"),
            processType = listOf("Process", "Processing", "Processing method"),
            roastLevel = listOf("Roast", "Roast level", "Roast profile"),
            variety = listOf("Variety", "Varietal", "Cultivar"),
            farm = listOf("Farm", "Finca", "Producer", "Estate", "Fazenda", "Hacienda"),
            weight = listOf("Net weight", "Weight", "Net wt"),
            roastDate = listOf("Roast date", "Roasted on", "Roasted"),
            expiryDate = listOf("Best before", "Use by", "Expiry", "Expiration", "BB", "EXP"),
        ),
    )

    val ALL = listOf(CZECH, GERMAN, ITALIAN, POLISH, SPANISH, DANISH, ENGLISH_FALLBACK)

    /**
     * Find the best dictionary for a GS1 prefix number (first 3 digits of EAN-13).
     * Returns null if no country-specific dictionary matches.
     */
    fun byGs1Prefix(prefixNumber: Int): CoffeeCountryDictionary? =
        ALL.firstOrNull { it.gs1PrefixRange.contains(prefixNumber) }

    /**
     * Find the best dictionary for a GS1 region name as returned by [BarcodeInsights.gs1IssuerRegion].
     */
    fun byRegionName(regionName: String?): CoffeeCountryDictionary? = when (regionName) {
        "Czech Republic" -> CZECH
        "Germany" -> GERMAN
        "Italy" -> ITALIAN
        "Poland" -> POLISH
        else -> null
    }

    /**
     * Infer a locale from a barcode's GS1 prefix. Falls back to null if
     * the prefix doesn't map to a known country dictionary.
     */
    fun localeFromBarcode(barcode: String?): Locale? {
        val region = BarcodeInsights.gs1IssuerRegion(barcode) ?: return null
        return byRegionName(region.regionName)?.locale
    }

    /**
     * Collect all section labels across all dictionaries for a given field,
     * optionally boosting (placing first) labels from a hinted country.
     */
    fun allSectionLabels(
        fieldSelector: (CountrySectionLabels) -> List<String>,
        countryHint: CoffeeCountryDictionary? = null,
    ): List<String> {
        val hinted = countryHint?.let(CoffeeCountryDictionary::sectionLabels)
            ?.let(fieldSelector)
            .orEmpty()
        val rest = ALL
            .filter { it != countryHint }
            .flatMap { fieldSelector(it.sectionLabels) }
        return (hinted + rest).distinct()
    }
}
