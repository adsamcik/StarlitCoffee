package com.adsamcik.starlitcoffee.data.network

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsClientTest {

    @Test
    fun `success closes response stream and disconnects`() {
        val responseStream = TrackingInputStream(
            """{"status":1,"product":{"product_name":"Night Sky","brands":"North Star"}}""",
        )
        val connection = FakeHttpURLConnection(inputStreamValue = responseStream)

        val result = OpenFoodFactsClient.lookupBarcode(
            barcode = "123",
            connectionFactory = { connection },
        )

        assertEquals("Night Sky", result?.name)
        assertEquals("North Star", result?.brand)
        assertEquals(1, responseStream.closeCalls)
        assertEquals(1, connection.disconnectCalls)
        assertEquals(0, connection.errorStreamCalls)
    }

    @Test
    fun `http error closes error stream without opening response stream and disconnects`() {
        val errorStream = TrackingInputStream("service unavailable")
        val connection = FakeHttpURLConnection(
            responseCodeValue = HttpURLConnection.HTTP_UNAVAILABLE,
            errorStreamValue = errorStream,
        )

        val result = OpenFoodFactsClient.lookupBarcode(
            barcode = "123",
            connectionFactory = { connection },
        )

        assertNull(result)
        assertEquals(1, errorStream.closeCalls)
        assertEquals(0, connection.inputStreamCalls)
        assertEquals(1, connection.disconnectCalls)
    }

    @Test
    fun `parsing failure closes response stream and disconnects`() {
        val responseStream = TrackingInputStream("not-json")
        val connection = FakeHttpURLConnection(inputStreamValue = responseStream)

        val result = OpenFoodFactsClient.lookupBarcode(
            barcode = "123",
            connectionFactory = { connection },
        )

        assertNull(result)
        assertEquals(1, responseStream.closeCalls)
        assertEquals(1, connection.disconnectCalls)
    }

    @Test
    fun `read timeout closes response stream and disconnects`() {
        val timeout = SocketTimeoutException("read timed out")
        val responseStream = FailingInputStream(timeout)
        val connection = FakeHttpURLConnection(inputStreamValue = responseStream)

        val result = OpenFoodFactsClient.lookupBarcode(
            barcode = "123",
            connectionFactory = { connection },
        )

        assertNull(result)
        assertTrue(responseStream.closed)
        assertEquals(1, connection.disconnectCalls)
    }

    @Test
    fun `cancellation closes response stream disconnects and preserves cancellation`() {
        val cancellation = CancellationException("lookup cancelled")
        val disconnectFailure = IllegalStateException("disconnect failed")
        val responseStream = FailingInputStream(cancellation)
        val connection = FakeHttpURLConnection(
            inputStreamValue = responseStream,
            disconnectFailure = disconnectFailure,
        )

        val failure = runCatching {
            OpenFoodFactsClient.lookupBarcode(
                barcode = "123",
                connectionFactory = { connection },
            )
        }.exceptionOrNull()

        assertSame(cancellation, failure)
        assertTrue(responseStream.closed)
        assertEquals(1, connection.disconnectCalls)
        assertTrue(failure?.suppressedExceptions?.contains(disconnectFailure) == true)
    }

    private open class TrackingInputStream(body: String) : ByteArrayInputStream(
        body.toByteArray(StandardCharsets.UTF_8),
    ) {
        var closeCalls = 0
            private set

        override fun close() {
            closeCalls += 1
            super.close()
        }
    }

    private class FailingInputStream(
        private val failure: Exception,
    ) : InputStream() {
        var closed = false
            private set

        override fun read(): Int = throw failure

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw failure

        override fun close() {
            closed = true
        }
    }

    private class FakeHttpURLConnection(
        url: URL = URL("https://world.openfoodfacts.org/api/v0/product/123.json"),
        private val responseCodeValue: Int = HttpURLConnection.HTTP_OK,
        private val inputStreamValue: InputStream? = null,
        private val errorStreamValue: InputStream? = null,
        private val disconnectFailure: RuntimeException? = null,
    ) : HttpURLConnection(url) {
        var disconnectCalls = 0
            private set
        var inputStreamCalls = 0
            private set
        var errorStreamCalls = 0
            private set

        override fun disconnect() {
            disconnectCalls += 1
            disconnectFailure?.let { throw it }
        }

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit

        override fun getResponseCode(): Int = responseCodeValue

        override fun getInputStream(): InputStream {
            inputStreamCalls += 1
            return requireNotNull(inputStreamValue)
        }

        override fun getErrorStream(): InputStream? {
            errorStreamCalls += 1
            return errorStreamValue
        }
    }
}
