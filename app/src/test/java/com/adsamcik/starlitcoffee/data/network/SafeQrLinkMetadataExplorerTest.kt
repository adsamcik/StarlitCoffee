package com.adsamcik.starlitcoffee.data.network

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SafeQrLinkMetadataExplorerTest {

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- HTML Parsing ---

    @Test
    fun `html metadata is extracted from safe qr page`() = runTest {
        val html = """
            <html>
              <head>
                <title>Ethiopia Guji | Starlit Roasters</title>
                <meta property="og:site_name" content="Starlit Roasters" />
                <meta name="description" content="Peach, jasmine, and black tea." />
                <script type="application/ld+json">
                  {
                    "@context": "https://schema.org",
                    "@type": "Product",
                    "name": "Ethiopia Guji",
                    "brand": { "@type": "Brand", "name": "Starlit Roasters" },
                    "description": "Peach, jasmine, and black tea.",
                    "additionalProperty": [
                      { "name": "Origin", "value": "Ethiopia" },
                      { "name": "Region", "value": "Guji" },
                      { "name": "Process", "value": "Washed" },
                      { "name": "Tasting notes", "value": "Peach, jasmine" }
                    ]
                  }
                </script>
              </head>
              <body>
                <p>Origin: Ethiopia</p>
                <p>Region: Guji</p>
                <p>Process: Washed</p>
              </body>
            </html>
        """.trimIndent()

        val explorer = SafeQrLinkMetadataExplorer(
            connectionFactory = { url ->
                FakeHttpURLConnection(
                    url = url,
                    responseCodeValue = 200,
                    body = html,
                )
            },
            dnsResolver = { listOf(InetAddress.getByName("93.184.216.34")) },
        )

        val result = explorer.explore("https://roaster.example/ethiopia-guji")

        assertTrue(result is QrLinkExploreResult.Success)
        val metadata = (result as QrLinkExploreResult.Success).metadata
        assertEquals("Ethiopia Guji", metadata.name)
        assertEquals("Starlit Roasters", metadata.roaster)
        assertEquals("Ethiopia", metadata.origin)
        assertEquals("Guji", metadata.region)
        assertEquals("Washed", metadata.processType)
        assertEquals("Peach, jasmine", metadata.tastingNotes)
    }

    @Test
    fun `html parsing detects decaf markers from page content`() {
        val explorer = SafeQrLinkMetadataExplorer()
        val metadata = explorer.parseHtmlDocument(
            html = """
                <html>
                  <head>
                    <title>Colombia Tumbaga Decaf</title>
                    <meta name="description" content="Sweet decaf coffee with caramel notes." />
                  </head>
                  <body>
                    <p>Swiss Water decaf</p>
                  </body>
                </html>
            """.trimIndent(),
            sourceUrl = "https://roaster.example/tumbaga-decaf",
            finalUrl = "https://roaster.example/tumbaga-decaf",
        )

        assertTrue(metadata?.isDecaf == true)
    }

    @Test
    fun `json parsing detects decaf markers from structured payload`() {
        val explorer = SafeQrLinkMetadataExplorer()
        val metadata = explorer.parseJsonDocument(
            body = """
                {
                  "@context": "https://schema.org",
                  "@type": "Product",
                  "name": "Night Shift",
                  "description": "Sugarcane decaf coffee",
                  "additionalProperty": [
                    { "name": "Origin", "value": "Colombia" }
                  ]
                }
            """.trimIndent(),
            sourceUrl = "https://roaster.example/night-shift",
            finalUrl = "https://roaster.example/night-shift",
        )

        assertTrue(metadata?.isDecaf == true)
    }

    // --- URL Safety ---

    @Test
    fun `private network qr links are rejected before fetch`() = runTest {
        var connectionAttempted = false
        val explorer = SafeQrLinkMetadataExplorer(
            connectionFactory = { url ->
                connectionAttempted = true
                FakeHttpURLConnection(url = url)
            },
            dnsResolver = { listOf(InetAddress.getByName("93.184.216.34")) },
        )

        val result = explorer.explore("http://192.168.1.20/coffee")

        assertTrue(result is QrLinkExploreResult.Skipped)
        assertFalse(connectionAttempted)
    }

    @Test
    fun `http qr links are not fetched automatically`() = runTest {
        var connectionAttempted = false
        val explorer = SafeQrLinkMetadataExplorer(
            connectionFactory = { url ->
                connectionAttempted = true
                FakeHttpURLConnection(url = url)
            },
            dnsResolver = { listOf(InetAddress.getByName("93.184.216.34")) },
        )

        val result = explorer.explore("http://roaster.example/coffee")

        assertTrue(result is QrLinkExploreResult.Skipped)
        assertTrue((result as QrLinkExploreResult.Skipped).keepUrl)
        assertFalse(connectionAttempted)
    }

    @Test
    fun `redirects resolving to private hosts are rejected`() = runTest {
        val explorer = SafeQrLinkMetadataExplorer(
            connectionFactory = { url ->
                when (url.host) {
                    "roaster.example" -> FakeHttpURLConnection(
                        url = url,
                        responseCodeValue = 302,
                        headers = mapOf("Location" to "https://traceability.example/lot-42"),
                    )
                    else -> FakeHttpURLConnection(url = url)
                }
            },
            dnsResolver = { host ->
                when (host) {
                    "roaster.example" -> listOf(InetAddress.getByName("93.184.216.34"))
                    "traceability.example" -> listOf(InetAddress.getByName("10.0.0.20"))
                    else -> listOf(InetAddress.getByName("93.184.216.34"))
                }
            },
        )

        val result = explorer.explore("https://roaster.example/redirect")

        assertTrue(result is QrLinkExploreResult.Skipped)
        assertTrue((result as QrLinkExploreResult.Skipped).reason.contains("private", ignoreCase = true))
    }

    @Test
    fun `oversized responses are skipped safely`() = runTest {
        val explorer = SafeQrLinkMetadataExplorer(
            connectionFactory = { url ->
                FakeHttpURLConnection(
                    url = url,
                    responseCodeValue = 200,
                    body = "<html><head><title>Too Large</title></head><body></body></html>",
                    contentLengthOverride = 700_000,
                )
            },
            dnsResolver = { listOf(InetAddress.getByName("93.184.216.34")) },
        )

        val result = explorer.explore("https://roaster.example/large")

        assertTrue(result is QrLinkExploreResult.Skipped)
        assertTrue((result as QrLinkExploreResult.Skipped).reason.contains("too large", ignoreCase = true))
    }

    private class FakeHttpURLConnection(
        url: URL,
        private val responseCodeValue: Int = 200,
        private val body: String = "",
        private val contentTypeValue: String = "text/html; charset=UTF-8",
        private val headers: Map<String, String> = emptyMap(),
        private val contentLengthOverride: Long? = null,
    ) : HttpURLConnection(url) {
        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit

        override fun getResponseCode(): Int = responseCodeValue

        override fun getContentType(): String = contentTypeValue

        override fun getContentLengthLong(): Long =
            contentLengthOverride ?: body.toByteArray(StandardCharsets.UTF_8).size.toLong()

        override fun getHeaderField(name: String?): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        override fun getInputStream(): InputStream =
            ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8))
    }
}
