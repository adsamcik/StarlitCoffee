package com.adsamcik.starlitcoffee.ui.guidance

import android.content.Context
import java.util.Locale

/**
 * Resolves the locale used by the exact-guidance raw resources.
 *
 * Android serves the unqualified English resource when none of the user's
 * preferred languages is supported. The exact-guidance release gate must use
 * that resolved language too; tagging the English bytes with an unsupported
 * device locale would otherwise make released content fail
 * closed.
 */
internal object P1ExactGuidanceLocaleResolver {
    const val DEFAULT_LANGUAGE_TAG = "en"

    val supportedLanguageTags: Set<String> = linkedSetOf(
        "en",
        "bg",
        "cs",
        "da",
        "de",
        "el",
        "es",
        "et",
        "fi",
        "fr",
        "hr",
        "hu",
        "it",
        "lt",
        "lv",
        "nl",
        "pl",
        "pt",
        "ro",
        "sk",
        "sl",
        "sv",
        "zh",
    )

    fun resolve(preferredLocaleTags: List<String>): String = preferredLocaleTags
        .asSequence()
        .mapNotNull(::languageOf)
        .firstOrNull(supportedLanguageTags::contains)
        ?: DEFAULT_LANGUAGE_TAG

    fun resolve(context: Context): String {
        val locales = context.resources.configuration.locales
        return resolve(
            preferredLocaleTags = List(locales.size()) { index ->
                locales[index].toLanguageTag()
            },
        )
    }

    private fun languageOf(localeTag: String): String? = Locale.forLanguageTag(localeTag)
        .language
        .lowercase(Locale.ROOT)
        .takeIf(String::isNotBlank)
}
