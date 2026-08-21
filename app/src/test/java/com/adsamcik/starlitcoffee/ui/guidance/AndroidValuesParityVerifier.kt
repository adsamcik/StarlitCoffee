package com.adsamcik.starlitcoffee.ui.guidance

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Verifies translated string-like resources across every XML file in each
 * `values*` directory. Android permits a locale's resources to be split into
 * files such as `strings.xml` and `release_1_4_strings.xml`, so comparing only
 * one file silently misses untranslated additions.
 */
internal object AndroidValuesParityVerifier {

    fun verify(
        resourceDirectory: File,
        localeConfigFile: File = File(resourceDirectory, "xml/locales_config.xml"),
    ): AndroidValuesParityReport {
        require(resourceDirectory.isDirectory) {
            "Resource directory does not exist: ${resourceDirectory.absolutePath}"
        }
        require(localeConfigFile.isFile) {
            "Locale config does not exist: ${localeConfigFile.absolutePath}"
        }

        val supportedLocales = readSupportedLocales(localeConfigFile)
        require(SOURCE_LOCALE in supportedLocales) {
            "Locale config must declare source locale '$SOURCE_LOCALE'"
        }

        val source = parseValueResources(File(resourceDirectory, SOURCE_VALUES_DIRECTORY))
        val localeReports = supportedLocales
            .asSequence()
            .filterNot { it == SOURCE_LOCALE }
            .map { locale -> compareLocale(locale, source, resourceDirectory) }
            .toList()

        return AndroidValuesParityReport(
            sourceLocale = SOURCE_LOCALE,
            sourceResourceCount = source.resources.size,
            sourceDuplicateKeys = source.duplicateKeys,
            localeReports = localeReports,
        )
    }

    private fun compareLocale(
        locale: String,
        source: ParsedValueResources,
        resourceDirectory: File,
    ): LocaleValuesParityReport {
        val directory = File(resourceDirectory, valuesDirectoryName(locale))
        if (!directory.isDirectory) {
            return LocaleValuesParityReport(
                locale = locale,
                directoryExists = false,
                missingKeys = source.resources.keys,
            )
        }

        val translated = parseValueResources(directory)
        val missingKeys = source.resources.keys - translated.resources.keys
        val unexpectedKeys = translated.resources.keys - source.resources.keys
        val formatMismatches = source.resources.keys
            .intersect(translated.resources.keys)
            .mapNotNull { key ->
                val sourceSignature = checkNotNull(source.resources[key])
                val translatedSignature = checkNotNull(translated.resources[key])
                if (sourceSignature == translatedSignature) {
                    null
                } else {
                    ValueFormatMismatch(key, sourceSignature, translatedSignature)
                }
            }

        return LocaleValuesParityReport(
            locale = locale,
            directoryExists = true,
            missingKeys = missingKeys,
            unexpectedKeys = unexpectedKeys,
            formatMismatches = formatMismatches,
            duplicateKeys = translated.duplicateKeys,
        )
    }

    private fun readSupportedLocales(localeConfigFile: File): List<String> {
        val document = parseXml(localeConfigFile)
        return document.documentElement.childNodes
            .asElementSequence()
            .filter { it.tagName == LOCALE_TAG }
            .mapNotNull { element ->
                element.getAttributeNS(ANDROID_NAMESPACE, NAME_ATTRIBUTE)
                    .ifBlank { element.getAttribute("android:$NAME_ATTRIBUTE") }
                    .takeIf(String::isNotBlank)
            }
            .toList()
            .also { locales -> require(locales.distinct().size == locales.size) { "Duplicate locale config entry" } }
    }

    private fun parseValueResources(directory: File): ParsedValueResources {
        if (!directory.isDirectory) {
            return ParsedValueResources(emptyMap(), emptySet())
        }

        val resources = linkedMapOf<ValueResourceKey, ValueFormatSignature>()
        val duplicateKeys = linkedSetOf<ValueResourceKey>()
        directory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { file -> file.isFile && file.extension.equals("xml", ignoreCase = true) }
            .sortedBy(File::getName)
            .forEach { file ->
                parseXml(file).documentElement.childNodes
                    .asElementSequence()
                    .filter { element ->
                        element.tagName in STRING_LIKE_RESOURCE_TAGS &&
                            element.getAttribute(TRANSLATABLE_ATTRIBUTE).lowercase() != TRANSLATABLE_FALSE
                    }
                    .forEach { element ->
                        val name = element.getAttribute(NAME_ATTRIBUTE)
                        if (name.isBlank()) return@forEach
                        val key = ValueResourceKey(element.tagName, name)
                        val previous = resources.put(key, element.toFormatSignature())
                        if (previous != null) duplicateKeys += key
                    }
            }
        return ParsedValueResources(resources, duplicateKeys)
    }

    private fun Element.toFormatSignature(): ValueFormatSignature = when (tagName) {
        STRING_TAG -> ValueFormatSignature(
            containerType = tagName,
            itemSignatures = listOf(textContent.toStringFormatSignature(isFormatted())),
        )

        STRING_ARRAY_TAG -> ValueFormatSignature(
            containerType = tagName,
            itemSignatures = childElements(ITEM_TAG).map { item ->
                item.textContent.toStringFormatSignature(item.isFormatted())
            },
        )

        PLURALS_TAG -> ValueFormatSignature(
            containerType = tagName,
            itemSignatures = childElements(ITEM_TAG)
                .map { item -> item.textContent.toStringFormatSignature(item.isFormatted()) }
                .distinct()
                .sortedBy(StringFormatSignature::canonicalForm),
        )

        else -> error("Unsupported string-like resource tag: $tagName")
    }

    private fun Element.isFormatted(): Boolean =
        getAttribute(FORMATTED_ATTRIBUTE).lowercase() != FORMATTED_FALSE

    private fun Element.childElements(tagName: String): List<Element> = childNodes
        .asElementSequence()
        .filter { it.tagName == tagName }
        .toList()

    private fun String.toStringFormatSignature(formatted: Boolean): StringFormatSignature {
        if (!formatted) return StringFormatSignature.EMPTY

        val indexedArguments = linkedMapOf<Int, MutableList<String>>()
        val positionalArguments = mutableListOf<String>()
        ANDROID_FORMAT_TOKEN.findAll(this).forEach { match ->
            val argumentIndex = match.groupValues[1].toIntOrNull()
            val conversion = match.groupValues[2]
            if (conversion == ESCAPED_PERCENT || conversion == NEWLINE_CONVERSION) return@forEach
            if (argumentIndex == null) {
                positionalArguments += conversion
            } else {
                indexedArguments.getOrPut(argumentIndex) { mutableListOf() } += conversion
            }
        }
        return StringFormatSignature(
            indexedArguments = indexedArguments.mapValues { (_, value) -> value.toList() },
            positionalArguments = positionalArguments,
        )
    }

    private fun parseXml(file: File) = newSecureDocumentBuilder().parse(file)

    private fun newSecureDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        runCatching { setFeature(DISALLOW_DOCTYPE_DECLARATION, true) }
        runCatching { setFeature(EXTERNAL_GENERAL_ENTITIES, false) }
        runCatching { setFeature(EXTERNAL_PARAMETER_ENTITIES, false) }
    }.newDocumentBuilder()

    private fun valuesDirectoryName(locale: String): String = "values-$locale"

    private const val SOURCE_LOCALE = "en"
    private const val SOURCE_VALUES_DIRECTORY = "values"
    private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    private const val NAME_ATTRIBUTE = "name"
    private const val FORMATTED_ATTRIBUTE = "formatted"
    private const val FORMATTED_FALSE = "false"
    private const val TRANSLATABLE_ATTRIBUTE = "translatable"
    private const val TRANSLATABLE_FALSE = "false"
    private const val LOCALE_TAG = "locale"
    private const val STRING_TAG = "string"
    private const val STRING_ARRAY_TAG = "string-array"
    private const val PLURALS_TAG = "plurals"
    private const val ITEM_TAG = "item"
    private const val ESCAPED_PERCENT = "%"
    private const val NEWLINE_CONVERSION = "n"
    private const val DISALLOW_DOCTYPE_DECLARATION = "http://apache.org/xml/features/disallow-doctype-decl"
    private const val EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities"
    private const val EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities"
    private val STRING_LIKE_RESOURCE_TAGS = setOf(STRING_TAG, STRING_ARRAY_TAG, PLURALS_TAG)
    private val ANDROID_FORMAT_TOKEN = Regex(
        "%(?:(\\d+)\\$)?([-#+ 0,(<]*\\d*(?:\\.\\d+)?(?:[tT])?[a-zA-Z]|%)",
    )
}

internal data class AndroidValuesParityReport(
    val sourceLocale: String,
    val sourceResourceCount: Int,
    val sourceDuplicateKeys: Set<ValueResourceKey>,
    val localeReports: List<LocaleValuesParityReport>,
) {
    val isValid: Boolean
        get() = sourceDuplicateKeys.isEmpty() && localeReports.all(LocaleValuesParityReport::isValid)

    fun failureDescription(): String = buildString {
        if (sourceDuplicateKeys.isNotEmpty()) {
            append("Source duplicate keys: ")
            append(sourceDuplicateKeys)
        }
        localeReports.filterNot(LocaleValuesParityReport::isValid).forEach { report ->
            if (isNotEmpty()) appendLine()
            append(report.failureDescription())
        }
    }
}

internal data class LocaleValuesParityReport(
    val locale: String,
    val directoryExists: Boolean,
    val missingKeys: Set<ValueResourceKey> = emptySet(),
    val unexpectedKeys: Set<ValueResourceKey> = emptySet(),
    val formatMismatches: List<ValueFormatMismatch> = emptyList(),
    val duplicateKeys: Set<ValueResourceKey> = emptySet(),
) {
    val isValid: Boolean
        get() = directoryExists &&
            missingKeys.isEmpty() &&
            unexpectedKeys.isEmpty() &&
            formatMismatches.isEmpty() &&
            duplicateKeys.isEmpty()

    fun failureDescription(): String = buildString {
        append(locale)
        append(": ")
        if (!directoryExists) append("missing values directory; ")
        if (missingKeys.isNotEmpty()) append("missing=$missingKeys; ")
        if (unexpectedKeys.isNotEmpty()) append("unexpected=$unexpectedKeys; ")
        if (formatMismatches.isNotEmpty()) append("format=$formatMismatches; ")
        if (duplicateKeys.isNotEmpty()) append("duplicates=$duplicateKeys; ")
    }.removeSuffix("; ")
}

internal data class ValueResourceKey(
    val type: String,
    val name: String,
)

internal data class ValueFormatMismatch(
    val key: ValueResourceKey,
    val source: ValueFormatSignature,
    val translated: ValueFormatSignature,
)

internal data class ValueFormatSignature(
    val containerType: String,
    val itemSignatures: List<StringFormatSignature>,
)

internal data class StringFormatSignature(
    val indexedArguments: Map<Int, List<String>>,
    val positionalArguments: List<String>,
) {
    fun canonicalForm(): String = "${indexedArguments.toSortedMap()}|$positionalArguments"

    companion object {
        val EMPTY = StringFormatSignature(emptyMap(), emptyList())
    }
}

private data class ParsedValueResources(
    val resources: Map<ValueResourceKey, ValueFormatSignature>,
    val duplicateKeys: Set<ValueResourceKey>,
)

private fun org.w3c.dom.NodeList.asElementSequence(): Sequence<Element> = sequence {
    for (index in 0 until length) {
        val node = item(index)
        if (node.nodeType == Node.ELEMENT_NODE) yield(node as Element)
    }
}
