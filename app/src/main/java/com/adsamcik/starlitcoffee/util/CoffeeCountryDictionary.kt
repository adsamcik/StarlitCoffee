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
            origin = listOf("Původ", "Země původu", "Země", "Oblast"),
            roaster = listOf("Pražírna", "Praženo", "Praženo v", "Výrobce", "Praženo u"),
            tastingNotes = listOf("Chuťové poznámky", "Chuťový profil", "Chuť", "Senzorický profil", "Degustační poznámky"),
            processType = listOf("Zpracování", "Metoda zpracování", "Process", "Způsob zpracování"),
            roastLevel = listOf("Stupeň pražení", "Pražení", "Profil pražení"),
            variety = listOf("Odrůda", "Odrůda kávovníku", "Varieta", "Kultivar"),
            farm = listOf("Farma", "Plantáž", "Statek", "Producent", "Pěstitel"),
            weight = listOf("Hmotnost", "Čistá hmotnost", "Obsah"),
            roastDate = listOf("Datum pražení", "Praženo", "Upražili jsme", "Upraženo", "Praženo dne"),
            expiryDate = listOf("Spotřebujte do", "Nejlépe do", "Datum minimální trvanlivosti", "Minimální trvanlivost"),
        ),
    )

    val GERMAN = CoffeeCountryDictionary(
        countryCode = "DE",
        countryName = "Germany",
        locale = Locale.GERMAN,
        gs1PrefixRange = 400..440,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Herkunft", "Ursprung", "Herkunftsland", "Anbaugebiet", "Anbauland", "Anbauregion"),
            roaster = listOf("Rösterei", "Kaffeerösterei", "Geröstet von", "Geröstet in", "Hersteller", "Geröstet bei", "Produzent"),
            tastingNotes = listOf("Geschmacksnoten", "Geschmack", "Aroma", "Geschmacksprofil", "Aromanoten", "Sensorik", "Sensorisches Profil"),
            processType = listOf("Aufbereitung", "Verarbeitung", "Aufbereitungsmethode", "Verarbeitungsmethode"),
            roastLevel = listOf("Röstgrad", "Röstung", "Röstprofil"),
            variety = listOf("Sorte", "Varietät", "Kaffeesorte", "Kultivar"),
            farm = listOf("Farm", "Finca", "Gut", "Erzeuger", "Produzent", "Kaffeefarm", "Kooperative"),
            weight = listOf("Nettogewicht", "Inhalt", "Gewicht", "Füllmenge", "Nettoinhalt"),
            roastDate = listOf("Röstdatum", "Geröstet am", "Geröstet", "Röstung am"),
            expiryDate = listOf("Mindestens haltbar bis", "MHD", "Haltbar bis", "Verfallsdatum"),
        ),
    )

    val ITALIAN = CoffeeCountryDictionary(
        countryCode = "IT",
        countryName = "Italy",
        locale = Locale.ITALIAN,
        gs1PrefixRange = 800..839,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Origine", "Provenienza", "Paese di origine", "Paese", "Regione di origine"),
            roaster = listOf("Torrefazione", "Tostatura", "Tostato da", "Prodotto da", "Produttore", "Torrefattore"),
            tastingNotes = listOf("Note di degustazione", "Note sensoriali", "Gusto", "Profilo sensoriale", "Profilo aromatico", "Note aromatiche"),
            processType = listOf("Lavorazione", "Metodo di lavorazione", "Processo", "Metodo di processo"),
            roastLevel = listOf("Grado di tostatura", "Tostatura", "Livello di tostatura", "Profilo di tostatura"),
            variety = listOf("Varietà", "Cultivar", "Specie"),
            farm = listOf("Fattoria", "Finca", "Fazenda", "Produttore", "Cooperativa", "Piantagione"),
            weight = listOf("Peso netto", "Contenuto", "Peso", "Contenuto netto"),
            roastDate = listOf("Data di tostatura", "Tostato il", "Data tostatura", "Tostato"),
            expiryDate = listOf("Da consumarsi preferibilmente entro", "Scadenza", "TMC", "Da consumarsi entro"),
        ),
    )

    val POLISH = CoffeeCountryDictionary(
        countryCode = "PL",
        countryName = "Poland",
        locale = Locale("pl"),
        gs1PrefixRange = 590..590,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Pochodzenie", "Kraj pochodzenia", "Kraj", "Region"),
            roaster = listOf("Palarnia", "Palarnią", "Prażone przez", "Producent", "Wypalane przez", "Palono przez"),
            tastingNotes = listOf("Nuty smakowe", "Smak", "Profil smakowy", "Profil sensoryczny", "Notatki degustacyjne"),
            processType = listOf("Obróbka", "Metoda obróbki", "Przetwarzanie", "Proces", "Metoda przetwarzania"),
            roastLevel = listOf("Stopień palenia", "Palenie", "Stopień wypalenia", "Profil palenia"),
            variety = listOf("Odmiana", "Varietal", "Gatunek", "Kultywar"),
            farm = listOf("Farma", "Finca", "Producent", "Plantacja", "Gospodarstwo", "Kooperatywa"),
            weight = listOf("Masa netto", "Waga", "Waga netto"),
            roastDate = listOf("Data palenia", "Data wypalenia", "Wypalono", "Palono"),
            expiryDate = listOf("Najlepiej spożyć przed", "Termin przydatności", "Data ważności", "Najlepiej przed"),
        ),
    )

    val SPANISH = CoffeeCountryDictionary(
        countryCode = "ES",
        countryName = "Spain",
        locale = Locale("es"),
        gs1PrefixRange = 840..849,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Origen", "País de origen", "Procedencia", "Región"),
            roaster = listOf("Tostador", "Tostado por", "Torrefactor", "Fabricante", "Tostadero", "Tostado en"),
            tastingNotes = listOf("Notas de cata", "Sabor", "Perfil sensorial", "Perfil de sabor", "Notas de sabor", "Perfil de taza"),
            processType = listOf("Procesamiento", "Método de procesamiento", "Beneficio", "Proceso", "Método de beneficio"),
            roastLevel = listOf("Grado de tueste", "Tueste", "Nivel de tueste", "Perfil de tueste"),
            variety = listOf("Variedad", "Cultivar", "Especie"),
            farm = listOf("Finca", "Productor", "Hacienda", "Granja", "Caficultor", "Cooperativa"),
            weight = listOf("Peso neto", "Contenido", "Peso", "Contenido neto"),
            roastDate = listOf("Fecha de tueste", "Tostado el", "Tostado", "Fecha de tostado"),
            expiryDate = listOf("Consumir preferentemente antes de", "Caducidad", "Fecha de caducidad", "Consumir antes de"),
        ),
    )

    val DANISH = CoffeeCountryDictionary(
        countryCode = "DK",
        countryName = "Denmark",
        locale = Locale("da"),
        gs1PrefixRange = 570..579,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Oprindelse", "Oprindelsesland", "Land", "Region"),
            roaster = listOf("Risteri", "Brændt af", "Kafferisteri", "Producent", "Brændt i"),
            tastingNotes = listOf("Smagsnoter", "Smag", "Smagsprofil", "Sensorisk profil"),
            processType = listOf("Forarbejdning", "Forarbejdningsmetode", "Proces"),
            roastLevel = listOf("Ristningsgrad", "Ristning", "Risteprofil"),
            variety = listOf("Sort", "Varietet", "Kaffevariant"),
            farm = listOf("Farm", "Finca", "Producent", "Gård", "Kooperativ"),
            weight = listOf("Nettovægt", "Indhold", "Vægt"),
            roastDate = listOf("Ristningsdato", "Ristet", "Brændt den", "Ristet den"),
            expiryDate = listOf("Bedst før", "Mindst holdbar til", "Holdbar til"),
        ),
    )

    val FRENCH = CoffeeCountryDictionary(
        countryCode = "FR",
        countryName = "France",
        locale = Locale.FRENCH,
        gs1PrefixRange = 300..379,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Origine", "Pays d'origine", "Provenance", "Région"),
            roaster = listOf("Torréfacteur", "Torréfié par", "Brûlerie", "Fabricant", "Producteur", "Torréfié à"),
            tastingNotes = listOf("Notes de dégustation", "Notes aromatiques", "Profil aromatique", "Arôme", "Saveur", "Profil sensoriel"),
            processType = listOf("Traitement", "Méthode de traitement", "Processus", "Procédé"),
            roastLevel = listOf("Torréfaction", "Degré de torréfaction", "Niveau de torréfaction", "Profil de torréfaction"),
            variety = listOf("Variété", "Cultivar", "Espèce"),
            farm = listOf("Ferme", "Finca", "Fazenda", "Producteur", "Plantation", "Coopérative"),
            weight = listOf("Poids net", "Contenu", "Poids", "Contenu net"),
            roastDate = listOf("Date de torréfaction", "Torréfié le"),
            expiryDate = listOf("À consommer de préférence avant", "À consommer avant", "DLUO", "DDM", "Date limite"),
        ),
    )

    val PORTUGUESE = CoffeeCountryDictionary(
        countryCode = "PT",
        countryName = "Portugal",
        locale = Locale("pt"),
        gs1PrefixRange = 560..569,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Origem", "País de origem", "Procedência", "Região"),
            roaster = listOf("Torrefação", "Torrado por", "Fabricante", "Produtor", "Torrefactor"),
            tastingNotes = listOf("Notas de degustação", "Notas sensoriais", "Sabor", "Perfil sensorial", "Perfil aromático"),
            processType = listOf("Processamento", "Método de processamento", "Processo"),
            roastLevel = listOf("Grau de torra", "Torra", "Nível de torra", "Perfil de torra"),
            variety = listOf("Variedade", "Cultivar", "Espécie"),
            farm = listOf("Fazenda", "Finca", "Produtor", "Plantação", "Cooperativa"),
            weight = listOf("Peso líquido", "Conteúdo", "Peso", "Conteúdo líquido"),
            roastDate = listOf("Data de torra", "Torrado em"),
            expiryDate = listOf("Consumir de preferência antes de", "Validade", "Data de validade", "Consumir antes de"),
        ),
    )

    val DUTCH = CoffeeCountryDictionary(
        countryCode = "NL",
        countryName = "Netherlands",
        locale = Locale("nl"),
        gs1PrefixRange = 870..879,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Herkomst", "Land van herkomst", "Oorsprong"),
            roaster = listOf("Brander", "Branderij", "Koffiebranderij", "Gebrand door", "Producent", "Fabrikant"),
            tastingNotes = listOf("Smaaknotities", "Smaak", "Smaakprofiel", "Aroma"),
            processType = listOf("Verwerking", "Verwerkingsmethode", "Proces"),
            roastLevel = listOf("Brandgraad", "Branding", "Brandprofiel"),
            variety = listOf("Variëteit", "Cultivar", "Soort"),
            farm = listOf("Boerderij", "Finca", "Fazenda", "Producent", "Plantage", "Coöperatie"),
            weight = listOf("Nettogewicht", "Inhoud", "Gewicht"),
            roastDate = listOf("Branddatum", "Gebrand op"),
            expiryDate = listOf("Ten minste houdbaar tot", "THT", "Houdbaar tot"),
        ),
    )

    val SWEDISH = CoffeeCountryDictionary(
        countryCode = "SE",
        countryName = "Sweden",
        locale = Locale("sv"),
        gs1PrefixRange = 730..739,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Ursprung", "Ursprungsland", "Region"),
            roaster = listOf("Rosteri", "Kafferosteri", "Rostad av", "Tillverkare", "Producent", "Rostad i"),
            tastingNotes = listOf("Smaknoter", "Smak", "Smakprofil", "Sensorisk profil", "Arom"),
            processType = listOf("Bearbetning", "Bearbetningsmetod", "Process"),
            roastLevel = listOf("Rostningsgrad", "Rostning", "Rostprofil"),
            variety = listOf("Sort", "Varietet", "Kultivar"),
            farm = listOf("Farm", "Finca", "Producent", "Gård", "Kooperativ"),
            weight = listOf("Nettovikt", "Innehåll", "Vikt"),
            roastDate = listOf("Rostningsdatum", "Rostad", "Rostad den"),
            expiryDate = listOf("Bäst före", "Bäst före datum", "Hållbar till"),
        ),
    )

    val NORWEGIAN = CoffeeCountryDictionary(
        countryCode = "NO",
        countryName = "Norway",
        locale = Locale("no"),
        gs1PrefixRange = 700..709,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Opprinnelse", "Opprinnelsesland", "Region"),
            roaster = listOf("Brenneri", "Kaffebrenneriet", "Brent av", "Produsent", "Brent i"),
            tastingNotes = listOf("Smaksnotater", "Smak", "Smaksprofil", "Aroma"),
            processType = listOf("Bearbeiding", "Bearbeidingsmetode", "Prosess"),
            roastLevel = listOf("Brennegrad", "Brenning", "Brennprofil"),
            variety = listOf("Sort", "Varietet", "Kultivar"),
            farm = listOf("Farm", "Finca", "Produsent", "Gård", "Kooperativ"),
            weight = listOf("Nettovekt", "Innhold", "Vekt"),
            roastDate = listOf("Brennedato", "Brent", "Brent den"),
            expiryDate = listOf("Best før", "Holdbar til"),
        ),
    )

    val FINNISH = CoffeeCountryDictionary(
        countryCode = "FI",
        countryName = "Finland",
        locale = Locale("fi"),
        gs1PrefixRange = 640..649,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Alkuperä", "Alkuperämaa", "Alue"),
            roaster = listOf("Paahtimo", "Kahvipaahtimo", "Paahdettu", "Valmistaja", "Tuottaja"),
            tastingNotes = listOf("Makumuistiinpanot", "Maku", "Makuprofiili", "Aromi"),
            processType = listOf("Käsittely", "Käsittelymenetelmä", "Prosessi"),
            roastLevel = listOf("Paahtoaste", "Paahtoprofiili"),
            variety = listOf("Lajike", "Kultivaari"),
            farm = listOf("Tila", "Finca", "Tuottaja", "Osuuskunta"),
            weight = listOf("Nettopaino", "Sisältö", "Paino"),
            roastDate = listOf("Paahtopäivä", "Paahdettu"),
            expiryDate = listOf("Parasta ennen", "Viimeinen käyttöpäivä"),
        ),
    )

    val SLOVAK = CoffeeCountryDictionary(
        countryCode = "SK",
        countryName = "Slovakia",
        locale = Locale("sk"),
        gs1PrefixRange = 858..858,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Pôvod", "Krajina pôvodu", "Krajina", "Oblasť"),
            roaster = listOf("Pražiareň", "Pražené", "Pražené v", "Výrobca"),
            tastingNotes = listOf("Chuťové poznámky", "Chuťový profil", "Chuť", "Senzorický profil"),
            processType = listOf("Spracovanie", "Metóda spracovania"),
            roastLevel = listOf("Stupeň praženia", "Praženie"),
            variety = listOf("Odroda", "Varieta", "Kultivar"),
            farm = listOf("Farma", "Plantáž", "Producent", "Pestovateľ"),
            weight = listOf("Hmotnosť", "Čistá hmotnosť", "Obsah"),
            roastDate = listOf("Dátum praženia", "Pražené"),
            expiryDate = listOf("Spotrebujte do", "Najlepšie do", "Minimálna trvanlivosť", "Dátum minimálnej trvanlivosti"),
        ),
    )

    val JAPANESE = CoffeeCountryDictionary(
        countryCode = "JP",
        countryName = "Japan",
        locale = Locale.JAPANESE,
        gs1PrefixRange = 450..459,
        sectionLabels = CountrySectionLabels(
            origin = listOf("原産地", "生産国", "産地"),
            roaster = listOf("焙煎所", "ロースター", "焙煎", "製造者"),
            tastingNotes = listOf("テイスティングノート", "フレーバー", "味わい", "風味"),
            processType = listOf("精製方法", "精製", "プロセス"),
            roastLevel = listOf("焙煎度", "ローストレベル", "焙煎レベル"),
            variety = listOf("品種"),
            farm = listOf("農園", "農場", "生産者"),
            weight = listOf("内容量", "正味重量"),
            roastDate = listOf("焙煎日"),
            expiryDate = listOf("賞味期限", "消費期限"),
        ),
    )

    val KOREAN = CoffeeCountryDictionary(
        countryCode = "KR",
        countryName = "South Korea",
        locale = Locale.KOREAN,
        gs1PrefixRange = 880..889,
        sectionLabels = CountrySectionLabels(
            origin = listOf("원산지", "생산국"),
            roaster = listOf("로스터리", "로스터", "제조원", "제조자"),
            tastingNotes = listOf("테이스팅 노트", "향미", "풍미"),
            processType = listOf("가공방법", "프로세스"),
            roastLevel = listOf("로스팅", "배전도"),
            variety = listOf("품종"),
            farm = listOf("농장", "생산자"),
            weight = listOf("내용량", "중량"),
            roastDate = listOf("로스팅일", "배전일"),
            expiryDate = listOf("유통기한", "소비기한"),
        ),
    )

    val ENGLISH_FALLBACK = CoffeeCountryDictionary(
        countryCode = "EN",
        countryName = "International (English)",
        locale = Locale.ENGLISH,
        gs1PrefixRange = IntRange.EMPTY,
        sectionLabels = CountrySectionLabels(
            origin = listOf("Origin", "Country", "Country of origin", "Single origin", "Source", "Growing region"),
            roaster = listOf("Roaster", "Roastery", "Coffee roasters", "Roasted by", "Produced by", "Made by", "Roasted at"),
            tastingNotes = listOf("Tasting notes", "Cupping notes", "Notes", "Flavor", "Flavour", "Tastes like", "Flavor notes", "Flavour notes", "Flavor profile", "Flavour profile", "Cup profile", "Sensory notes"),
            processType = listOf("Process", "Processing", "Processing method", "Process method", "Post-harvest"),
            roastLevel = listOf("Roast", "Roast level", "Roast profile", "Roast degree", "Roast type"),
            variety = listOf("Variety", "Varietal", "Cultivar", "Coffee variety", "Bean variety"),
            farm = listOf("Farm", "Finca", "Producer", "Estate", "Fazenda", "Hacienda", "Grower", "Cooperative", "Co-op", "Washing station"),
            weight = listOf("Net weight", "Weight", "Net wt", "Contents", "Net contents", "Net content"),
            roastDate = listOf("Roast date", "Roasted on", "Roasted", "Date roasted"),
            expiryDate = listOf("Best before", "Use by", "Expiry", "Expiration", "BB", "EXP", "Best by", "Consume by", "Consume before", "Shelf life"),
        ),
    )

    val ALL = listOf(
        CZECH, SLOVAK, GERMAN, ITALIAN, POLISH, SPANISH, FRENCH, PORTUGUESE,
        DANISH, SWEDISH, NORWEGIAN, FINNISH, DUTCH,
        JAPANESE, KOREAN,
        ENGLISH_FALLBACK,
    )

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
        "Slovakia" -> SLOVAK
        "Germany" -> GERMAN
        "Italy" -> ITALIAN
        "Poland" -> POLISH
        "Spain" -> SPANISH
        "France" -> FRENCH
        "Portugal" -> PORTUGUESE
        "Denmark" -> DANISH
        "Sweden" -> SWEDISH
        "Norway" -> NORWEGIAN
        "Finland" -> FINNISH
        "Netherlands" -> DUTCH
        "Japan" -> JAPANESE
        "South Korea" -> KOREAN
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
