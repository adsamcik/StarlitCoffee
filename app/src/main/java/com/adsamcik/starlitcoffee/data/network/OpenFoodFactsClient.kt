package com.adsamcik.starlitcoffee.data.network

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for Open Food Facts free product lookup API.
 * No API key required. Rate limit: ~100 req/min.
 */
object OpenFoodFactsClient {
    private const val TAG = "OpenFoodFactsClient"
    private const val BASE_URL = "https://world.openfoodfacts.org/api/v0/product"
    private const val USER_AGENT = "StarlitCoffee/1.0 (Android)"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun lookupBarcode(barcode: String): ProductResult? = lookupBarcode(
        barcode = barcode,
        connectionFactory = { url -> url.openConnection() as HttpURLConnection },
    )

    internal fun lookupBarcode(
        barcode: String,
        connectionFactory: (URL) -> HttpURLConnection,
    ): ProductResult? {
        return try {
            val connection = connectionFactory(URL("$BASE_URL/$barcode.json"))
            connection.useAndDisconnect { activeConnection ->
                activeConnection.requestMethod = "GET"
                activeConnection.setRequestProperty("User-Agent", USER_AGENT)
                activeConnection.connectTimeout = 5000
                activeConnection.readTimeout = 5000

                if (activeConnection.responseCode != HttpURLConnection.HTTP_OK) {
                    activeConnection.errorStream?.use { }
                    return@useAndDisconnect null
                }

                val responseText = activeConnection.inputStream
                    .bufferedReader()
                    .use { reader -> reader.readText() }
                val response = json.decodeFromString<OffResponse>(responseText)
                val product = response.product
                    ?.takeIf { response.status == 1 }
                    ?: return@useAndDisconnect null

                ProductResult(
                    name = product.productName?.takeIf { it.isNotBlank() },
                    brand = product.brands?.takeIf { it.isNotBlank() },
                    categories = product.categories?.takeIf { it.isNotBlank() },
                    imageUrl = product.imageUrl?.takeIf { it.isNotBlank() },
                    quantity = product.quantity?.takeIf { it.isNotBlank() },
                    origins = product.origins?.takeIf { it.isNotBlank() },
                    countriesTags = product.countriesTags?.takeIf { it.isNotEmpty() },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch product info from OpenFoodFacts", e)
            null
        }
    }

    private inline fun <T> HttpURLConnection.useAndDisconnect(
        block: (HttpURLConnection) -> T,
    ): T {
        val connection = this
        return AutoCloseable { connection.disconnect() }.use {
            block(connection)
        }
    }
}

data class ProductResult(
    val name: String?,
    val brand: String?,
    val categories: String?,
    val imageUrl: String?,
    val quantity: String?,
    val origins: String?,
    val countriesTags: List<String>?,
)

@Serializable
private data class OffResponse(
    val status: Int = 0,
    val product: OffProduct? = null,
)

@Serializable
private data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    val categories: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val quantity: String? = null,
    val origins: String? = null,
    @SerialName("countries_tags") val countriesTags: List<String>? = null,
)
