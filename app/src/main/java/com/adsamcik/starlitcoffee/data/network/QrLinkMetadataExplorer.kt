package com.adsamcik.starlitcoffee.data.network

import com.adsamcik.starlitcoffee.data.model.CoffeeOrigin
import com.adsamcik.starlitcoffee.data.model.CoffeeProcessType
import com.adsamcik.starlitcoffee.data.model.CoffeeRegion
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.IDN
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import com.adsamcik.starlitcoffee.util.CoffeeMetadataNormalizer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

sealed interface QrLinkExploreResult {
    data class Success(val metadata: QrCoffeeMetadata) : QrLinkExploreResult

    data class Skipped(
        val reason: String,
        val keepUrl: Boolean = false,
    ) : QrLinkExploreResult
}

data class QrCoffeeMetadata(
    val sourceUrl: String,
    val finalUrl: String,
    val host: String,
    val pageTitle: String? = null,
    val pageDescription: String? = null,
    val name: String? = null,
    val roaster: String? = null,
    val origin: String? = null,
    val region: String? = null,
    val processType: String? = null,
    val tastingNotes: String? = null,
    val isDecaf: Boolean? = null,
    val supportingSnippet: String? = null,
) {
    fun hasCoffeeData(): Boolean = listOf(
        name,
        roaster,
        origin,
        region,
        processType,
        tastingNotes,
    ).any { !it.isNullOrBlank() } || isDecaf == true
}

interface QrLinkMetadataExplorer {
    suspend fun explore(url: String): QrLinkExploreResult
}

@Suppress("LongMethod", "ReturnCount", "TooManyFunctions")
class SafeQrLinkMetadataExplorer(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
    private val dnsResolver: (String) -> List<InetAddress> = { host ->
        InetAddress.getAllByName(host).toList()
    },
) : QrLinkMetadataExplorer {

    override suspend fun explore(url: String): QrLinkExploreResult {
        val sanitizedUrl = sanitizePublicWebUrl(url)
            ?: return QrLinkExploreResult.Skipped(
                "QR website lookup only supports safe public http(s) links.",
            )
        if (!isHttpsUrl(sanitizedUrl)) {
            return QrLinkExploreResult.Skipped(
                reason = "QR website lookup only fetches HTTPS pages. The link was saved but not explored.",
                keepUrl = true,
            )
        }

        return try {
            fetchMetadata(sanitizedUrl)
        } catch (_: IOException) {
            QrLinkExploreResult.Skipped(
                reason = "QR website could not be reached safely.",
                keepUrl = true,
            )
        } catch (_: SecurityException) {
            QrLinkExploreResult.Skipped(
                reason = "QR website was skipped because it resolved to a private address.",
                keepUrl = false,
            )
        }
    }

    private fun fetchMetadata(initialUrl: String): QrLinkExploreResult {
        var currentUrl = initialUrl

        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            if (!isHttpsUrl(currentUrl)) {
                return QrLinkExploreResult.Skipped(
                    reason = "QR website redirected away from HTTPS. The link was saved but not explored.",
                    keepUrl = true,
                )
            }
            ensurePublicNetworkTarget(currentUrl)

            val connection = connectionFactory(URL(currentUrl))
            try {
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", ACCEPT_HEADER)
                connection.setRequestProperty("Accept-Encoding", "identity")

                when (val responseCode = connection.responseCode) {
                    in 200..299 -> return parseResponse(connection, currentUrl)
                    in 300..399 -> {
                        if (redirectIndex == MAX_REDIRECTS) {
                            return QrLinkExploreResult.Skipped(
                                reason = "QR website redirected too many times.",
                                keepUrl = true,
                            )
                        }

                        val location = connection.getHeaderField("Location")
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: return QrLinkExploreResult.Skipped(
                                reason = "QR website redirected without a valid destination.",
                                keepUrl = true,
                            )

                        val redirectedUrl = URL(URL(currentUrl), location).toExternalForm()
                        currentUrl = sanitizePublicWebUrl(redirectedUrl)
                            ?: return QrLinkExploreResult.Skipped(
                                reason = "QR website redirected to an unsafe destination.",
                                keepUrl = false,
                            )
                    }
                    else -> {
                        return QrLinkExploreResult.Skipped(
                            reason = "QR website returned HTTP $responseCode.",
                            keepUrl = true,
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

        return QrLinkExploreResult.Skipped(
            reason = "QR website redirected too many times.",
            keepUrl = true,
        )
    }

    private fun parseResponse(
        connection: HttpURLConnection,
        requestedUrl: String,
    ): QrLinkExploreResult {
        val contentType = connection.contentType.orEmpty()
        val declaredLength = connection.contentLengthLong
        if (declaredLength > MAX_RESPONSE_BYTES) {
            return QrLinkExploreResult.Skipped(
                reason = "QR website response was too large to inspect safely.",
                keepUrl = true,
            )
        }

        val finalUrl = sanitizePublicWebUrl(connection.url.toExternalForm()) ?: requestedUrl
        val body = connection.inputStream.use { input ->
            readLimitedText(
                input = input,
                charset = charsetFor(contentType),
                byteLimit = MAX_RESPONSE_BYTES,
            )
        }

        val metadata = when {
            isJsonContent(contentType, body) -> parseJsonDocument(
                body = body,
                sourceUrl = requestedUrl,
                finalUrl = finalUrl,
            )
            isHtmlContent(contentType) || contentType.isBlank() -> parseHtmlDocument(
                html = body,
                sourceUrl = requestedUrl,
                finalUrl = finalUrl,
            )
            else -> null
        }

        return if (metadata?.hasCoffeeData() == true) {
            QrLinkExploreResult.Success(metadata)
        } else {
            QrLinkExploreResult.Skipped(
                reason = "QR website was reachable, but no clear coffee details were found.",
                keepUrl = true,
            )
        }
    }

    internal fun ensurePublicNetworkTarget(url: String) {
        val host = URL(url).host.orEmpty().trim()
        if (host.isBlank()) {
            throw SecurityException("Missing host")
        }

        val resolved = dnsResolver(host)
        if (resolved.isEmpty() || resolved.any(::isNonPublicAddress)) {
            throw SecurityException("Non-public host")
        }
    }

    internal fun parseHtmlDocument(
        html: String,
        sourceUrl: String,
        finalUrl: String,
    ): QrCoffeeMetadata? {
        val pageTitle = cleanText(extractTitle(html))
        val openGraphTitle = cleanText(extractMetaValue(html, "og:title"))
        val metaDescription = cleanText(
            extractMetaValue(html, "description") ?: extractMetaValue(html, "og:description"),
        )
        val siteName = cleanText(
            extractMetaValue(html, "og:site_name") ?: extractMetaValue(html, "application-name"),
        )
        val visibleText = extractVisibleText(html)
        val structured = extractStructuredMetadataFromHtml(html)

        val name = cleanDistinctValue(
            firstNonBlank(
                structured.name,
                cleanTitleCandidate(openGraphTitle ?: pageTitle, siteName),
            ),
            siteName,
        )
        val roaster = cleanDistinctValue(
            firstNonBlank(
                structured.roaster,
                siteName,
                extractLabeledValue(visibleText, listOf("roaster", "roasted by", "brand")),
            ),
            name,
        )
        val origin = canonicalOrigin(
            firstNonBlank(
                structured.origin,
                extractLabeledValue(visibleText, listOf("origin", "country", "country of origin")),
                matchKnownTerm(visibleText, CoffeeOrigin.allSearchTerms),
            ),
        )
        val region = canonicalRegion(
            firstNonBlank(
                structured.region,
                extractLabeledValue(visibleText, listOf("region", "subregion", "area")),
                matchKnownTerm(visibleText, CoffeeRegion.allSearchTerms),
            ),
        )
        val processType = canonicalProcessType(
            firstNonBlank(
                structured.processType,
                extractLabeledValue(visibleText, listOf("process", "process type", "processing")),
                matchKnownTerm(visibleText, CoffeeProcessType.allSearchTerms),
            ),
        )
        val tastingNotes = cleanNotes(
            firstNonBlank(
                structured.tastingNotes,
                extractLabeledValue(
                    visibleText,
                    listOf("tasting notes", "flavour notes", "flavor notes", "cup profile"),
                ),
                extractNotesPhrase(metaDescription),
                extractNotesPhrase(visibleText),
            ),
        )
        val isDecaf = CoffeeMetadataNormalizer.containsDecafMarker(
            listOfNotNull(
                structured.name,
                structured.description,
                openGraphTitle,
                pageTitle,
                metaDescription,
                visibleText,
                html,
            ).joinToString("\n"),
        ).takeIf { it }

        val metadata = QrCoffeeMetadata(
            sourceUrl = sourceUrl,
            finalUrl = finalUrl,
            host = URL(finalUrl).host.lowercase(),
            pageTitle = openGraphTitle ?: pageTitle,
            pageDescription = structured.description ?: metaDescription,
            name = name,
            roaster = roaster,
            origin = origin,
            region = region,
            processType = processType,
            tastingNotes = tastingNotes,
            isDecaf = isDecaf,
            supportingSnippet = firstNonBlank(
                structured.description,
                metaDescription,
                extractLabeledValue(
                    visibleText,
                    listOf("tasting notes", "flavour notes", "flavor notes", "process", "origin"),
                ),
            )?.take(SNIPPET_CHAR_LIMIT),
        )

        return metadata.takeIf { it.hasCoffeeData() }
    }

    internal fun parseJsonDocument(
        body: String,
        sourceUrl: String,
        finalUrl: String,
    ): QrCoffeeMetadata? {
        val root = try {
            json.parseToJsonElement(body)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }

        val structured = extractStructuredMetadataFromJson(root)
        val isDecaf = CoffeeMetadataNormalizer.containsDecafMarker(
            listOfNotNull(
                structured.name,
                structured.description,
                body,
            ).joinToString("\n"),
        ).takeIf { it }
        val metadata = QrCoffeeMetadata(
            sourceUrl = sourceUrl,
            finalUrl = finalUrl,
            host = URL(finalUrl).host.lowercase(),
            pageTitle = structured.name,
            pageDescription = structured.description,
            name = cleanText(structured.name),
            roaster = cleanText(structured.roaster),
            origin = canonicalOrigin(structured.origin),
            region = canonicalRegion(structured.region),
            processType = canonicalProcessType(structured.processType),
            tastingNotes = cleanNotes(structured.tastingNotes),
            isDecaf = isDecaf,
            supportingSnippet = cleanText(structured.description)?.take(SNIPPET_CHAR_LIMIT),
        )

        return metadata.takeIf { it.hasCoffeeData() }
    }

    companion object {
        private const val USER_AGENT = "StarlitCoffee/1.0 (Android; QR Link Explorer)"
        private const val ACCEPT_HEADER = "text/html,application/xhtml+xml,application/json,text/plain;q=0.9"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val MAX_REDIRECTS = 3
        private const val MAX_RESPONSE_BYTES = 512 * 1024
        private const val MAX_URL_LENGTH = 2_048
        private const val SNIPPET_CHAR_LIMIT = 180

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private val htmlTagRegex = Regex("<[^>]+>")
        private val metaTagRegex = Regex("<meta\\b[^>]*>", setOf(RegexOption.IGNORE_CASE))
        private val titleRegex = Regex(
            "<title\\b[^>]*>(.*?)</title>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private val jsonLdRegex = Regex(
            "<script\\b[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private val scriptRegex = Regex(
            "<script\\b[^>]*>.*?</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private val styleRegex = Regex(
            "<style\\b[^>]*>.*?</style>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private val blockBreakRegex = Regex(
            "(?i)<br\\s*/?>|</p>|</div>|</li>|</section>|</article>|</h[1-6]>",
        )
        private val attributeRegex = Regex(
            "([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))",
        )
        private val titleSplitRegex = Regex("\\s+[|\\-–—•:]\\s+")
        private val notesRegex = Regex(
            "(?i)(?:tasting notes?|flavou?r notes?|notes? of|cup profile)\\s*[:\\-–]?\\s*([^\\n\\r]{3,120})",
        )
        private val blockedHostSuffixes = listOf(
            ".local",
            ".localdomain",
            ".internal",
            ".lan",
            ".home",
            ".arpa",
            ".corp",
        )

        internal fun sanitizePublicWebUrl(rawUrl: String?): String? {
            val trimmed = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
            if (trimmed.length > MAX_URL_LENGTH) return null

            val uri = try {
                URI(trimmed)
            } catch (_: URISyntaxException) {
                return null
            }

            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            if (!uri.userInfo.isNullOrBlank()) return null
            if (uri.port !in setOf(-1, 80, 443)) return null

            val asciiHost = try {
                IDN.toASCII(uri.host?.trim()?.trimEnd('.') ?: return null)
            } catch (_: IllegalArgumentException) {
                return null
            }

            if (asciiHost.isBlank() || asciiHost.length > 253) return null
            val normalizedHost = asciiHost.lowercase()
            if (isObviouslyUnsafeHost(normalizedHost)) return null

            val safeUri = try {
                URI(
                    scheme,
                    null,
                    normalizedHost,
                    uri.port,
                    uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/",
                    uri.rawQuery,
                    null,
                )
            } catch (_: URISyntaxException) {
                return null
            }

            return safeUri.toASCIIString()
        }

        private fun isHtmlContent(contentType: String): Boolean = contentType.contains("text/html", true) ||
            contentType.contains("application/xhtml+xml", true) ||
            contentType.contains("text/plain", true)

        private fun isHttpsUrl(url: String): Boolean = url.startsWith("https://", ignoreCase = true)

        private fun isJsonContent(contentType: String, body: String): Boolean {
            if (contentType.contains("json", true)) return true
            val trimmed = body.trimStart()
            return trimmed.startsWith("{") || trimmed.startsWith("[")
        }

        private fun charsetFor(contentType: String): Charset {
            val charsetName = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
                .find(contentType)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.trim('"', '\'')
                ?: return StandardCharsets.UTF_8

            return try {
                Charset.forName(charsetName)
            } catch (_: IllegalArgumentException) {
                StandardCharsets.UTF_8
            }
        }

        private fun readLimitedText(
            input: InputStream,
            charset: Charset,
            byteLimit: Int,
        ): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0

            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                totalBytes += read
                if (totalBytes > byteLimit) {
                    throw IOException("Response too large")
                }
                output.write(buffer, 0, read)
            }

            return output.toString(charset.name())
        }

        private fun isObviouslyUnsafeHost(host: String): Boolean {
            val normalized = host.lowercase()
            if (normalized == "localhost" || blockedHostSuffixes.any(normalized::endsWith)) {
                return true
            }

            if (normalized.matches(Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")) || normalized.contains(':')) {
                val literalAddress = try {
                    InetAddress.getByName(normalized)
                } catch (_: IOException) {
                    return true
                }
                return isNonPublicAddress(literalAddress)
            }

            return false
        }

        private fun isNonPublicAddress(address: InetAddress): Boolean = address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isSiteLocalAddress ||
            address.isLinkLocalAddress ||
            address.isMulticastAddress

        private fun extractTitle(html: String): String? =
            titleRegex.find(html)?.groupValues?.getOrNull(1)

        private fun extractMetaValue(
            html: String,
            key: String,
        ): String? {
            val normalizedKey = key.lowercase()
            for (match in metaTagRegex.findAll(html)) {
                val attributes = parseAttributes(match.value)
                val attributeKey = attributes["property"] ?: attributes["name"] ?: continue
                if (attributeKey.lowercase() == normalizedKey) {
                    return attributes["content"]
                }
            }
            return null
        }

        private fun parseAttributes(tag: String): Map<String, String> = buildMap {
            for (match in attributeRegex.findAll(tag)) {
                val key = match.groupValues[1].lowercase()
                val value = when {
                    match.groupValues[3].isNotEmpty() -> match.groupValues[3]
                    match.groupValues[4].isNotEmpty() -> match.groupValues[4]
                    else -> match.groupValues[5]
                }
                put(key, decodeHtmlEntities(value))
            }
        }

        private fun extractVisibleText(html: String): String {
            val withLineBreaks = blockBreakRegex.replace(html, "\n")
            val withoutScripts = scriptRegex.replace(withLineBreaks, " ")
            val withoutStyles = styleRegex.replace(withoutScripts, " ")
            val plainText = htmlTagRegex.replace(withoutStyles, " ")
            return decodeHtmlEntities(plainText)
                .lineSequence()
                .map(::cleanText)
                .filterNotNull()
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }

        private fun decodeHtmlEntities(value: String): String = value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")

        private fun cleanText(value: String?): String? = value
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        private fun cleanDistinctValue(
            value: String?,
            otherValue: String?,
        ): String? {
            val cleanValue = cleanText(value) ?: return null
            val cleanOther = cleanText(otherValue)
            return cleanValue.takeUnless { cleanOther != null && normalizeSearch(it) == normalizeSearch(cleanOther) }
        }

        private fun cleanTitleCandidate(
            title: String?,
            siteName: String?,
        ): String? {
            val cleanTitle = cleanText(title) ?: return null
            val cleanSiteName = cleanText(siteName)
            val parts = titleSplitRegex.split(cleanTitle).mapNotNull(::cleanText)

            if (parts.size > 1) {
                val siteKey = cleanSiteName?.let(::normalizeSearch)
                val candidate = parts
                    .filterNot { part -> siteKey != null && normalizeSearch(part) == siteKey }
                    .maxByOrNull { it.length }
                if (!candidate.isNullOrBlank()) return candidate
            }

            return cleanDistinctValue(cleanTitle, cleanSiteName)
        }

        private fun extractNotesPhrase(text: String?): String? =
            notesRegex.find(text.orEmpty())?.groupValues?.getOrNull(1)?.let(::cleanNotes)

        private fun extractLabeledValue(
            text: String,
            labels: List<String>,
        ): String? {
            for (label in labels) {
                val regex = Regex(
                    "(?im)^\\s*${Regex.escape(label)}\\s*[:\\-–]\\s*([^\\n\\r]{2,120})$",
                )
                val match = regex.find(text)
                val candidate = match?.groupValues?.getOrNull(1)?.let(::cleanText)
                if (!candidate.isNullOrBlank()) {
                    return candidate
                }
            }

            return null
        }

        private fun matchKnownTerm(
            text: String,
            knownTerms: List<String>,
        ): String? {
            val normalizedText = normalizeSearch(text)
            return knownTerms.firstOrNull { normalizedText.contains(normalizeSearch(it)) }
        }

        private fun canonicalOrigin(value: String?): String? {
            val matched = value?.let { matchKnownTerm(it, CoffeeOrigin.allSearchTerms) } ?: return null
            return CoffeeOrigin.fromString(matched).displayName
        }

        private fun canonicalRegion(value: String?): String? {
            val matched = value?.let { matchKnownTerm(it, CoffeeRegion.allSearchTerms) } ?: return null
            return CoffeeRegion.fromString(matched).displayName
        }

        private fun canonicalProcessType(value: String?): String? {
            val matched = value?.let { matchKnownTerm(it, CoffeeProcessType.allSearchTerms) } ?: return null
            return CoffeeProcessType.fromString(matched).displayName
        }

        private fun cleanNotes(value: String?): String? = cleanText(value)
            ?.trimEnd('.', ';', ':')
            ?.takeIf { it.isNotBlank() }
            ?.take(SNIPPET_CHAR_LIMIT)

        private fun normalizeSearch(value: String): String = value
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

        private fun firstNonBlank(vararg values: String?): String? =
            values.firstNotNullOfOrNull { value -> cleanText(value) }

        private fun extractStructuredMetadataFromHtml(html: String): StructuredMetadata {
            var merged = StructuredMetadata()
            for (match in jsonLdRegex.findAll(html)) {
                val jsonBlock = match.groupValues.getOrNull(1) ?: continue
                val parsed = try {
                    json.parseToJsonElement(jsonBlock)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
                if (parsed != null) {
                    merged = merged.merge(extractStructuredMetadataFromJson(parsed))
                }
            }
            return merged
        }

        private fun extractStructuredMetadataFromJson(root: JsonElement): StructuredMetadata {
            val objects = collectJsonObjects(root)
            val product = objects.firstOrNull { objectMatchesType(it, "Product") }
                ?: objects.firstOrNull { it["brand"] != null && it["name"] != null }
            val organization = objects.firstOrNull { objectMatchesType(it, "Organization") }
                ?: objects.firstOrNull { objectMatchesType(it, "Brand") }

            val additionalProperties = extractAdditionalProperties(product)
            return StructuredMetadata(
                name = extractString(product?.get("name")),
                roaster = extractBrandName(product?.get("brand"))
                    ?: extractBrandName(product?.get("manufacturer"))
                    ?: extractString(organization?.get("name")),
                origin = extractString(product?.get("countryOfOrigin"))
                    ?: extractString(product?.get("areaServed"))
                    ?: additionalProperties["origin"]
                    ?: additionalProperties["country"]
                    ?: additionalProperties["country of origin"],
                region = additionalProperties["region"] ?: additionalProperties["subregion"],
                processType = additionalProperties["process"]
                    ?: additionalProperties["process type"]
                    ?: additionalProperties["processing"],
                tastingNotes = additionalProperties["tasting notes"]
                    ?: additionalProperties["flavor notes"]
                    ?: additionalProperties["flavour notes"]
                    ?: additionalProperties["notes"],
                description = extractString(product?.get("description")),
            )
        }

        private fun collectJsonObjects(root: JsonElement): List<JsonObject> = buildList {
            fun visit(element: JsonElement) {
                when (element) {
                    is JsonObject -> {
                        add(element)
                        element.values.forEach(::visit)
                    }
                    is JsonArray -> element.forEach(::visit)
                    else -> Unit
                }
            }

            visit(root)
        }

        private fun objectMatchesType(
            element: JsonObject,
            expectedType: String,
        ): Boolean {
            val typeElement = element["@type"] ?: return false
            return when (typeElement) {
                is JsonPrimitive -> typeElement.contentOrNull?.contains(expectedType, ignoreCase = true) == true
                is JsonArray -> typeElement.any {
                    (it as? JsonPrimitive)?.contentOrNull?.contains(expectedType, ignoreCase = true) == true
                }
                else -> false
            }
        }

        private fun extractAdditionalProperties(product: JsonObject?): Map<String, String> {
            val element = product?.get("additionalProperty") ?: return emptyMap()
            val entries = when (element) {
                is JsonArray -> element
                is JsonObject -> JsonArray(listOf(element))
                else -> return emptyMap()
            }

            return buildMap {
                entries.forEach { entry ->
                    val property = entry as? JsonObject ?: return@forEach
                    val name = extractString(property["name"]) ?: return@forEach
                    val value = extractString(property["value"])
                        ?: extractString(property["description"])
                        ?: return@forEach
                    put(normalizeSearch(name), value)
                }
            }
        }

        private fun extractBrandName(element: JsonElement?): String? {
            val asString = extractString(element)
            if (!asString.isNullOrBlank()) return asString

            val obj = element as? JsonObject ?: return null
            return extractString(obj["name"])
        }

        private fun extractString(element: JsonElement?): String? = when (element) {
            is JsonPrimitive -> element.contentOrNull?.let(::cleanText)
            is JsonObject -> {
                extractString(element["name"])
                    ?: extractString(element["value"])
                    ?: extractString(element["@value"])
            }
            is JsonArray -> element.firstNotNullOfOrNull(::extractString)
            else -> null
        }

        private data class StructuredMetadata(
            val name: String? = null,
            val roaster: String? = null,
            val origin: String? = null,
            val region: String? = null,
            val processType: String? = null,
            val tastingNotes: String? = null,
            val description: String? = null,
        ) {
            fun merge(other: StructuredMetadata): StructuredMetadata = StructuredMetadata(
                name = name ?: other.name,
                roaster = roaster ?: other.roaster,
                origin = origin ?: other.origin,
                region = region ?: other.region,
                processType = processType ?: other.processType,
                tastingNotes = tastingNotes ?: other.tastingNotes,
                description = description ?: other.description,
            )
        }
    }
}
