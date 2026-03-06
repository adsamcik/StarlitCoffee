package com.adsamcik.starlitcoffee.data.network

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

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

    fun lookupBarcode(barcode: String): ProductResult? {
        return try {
            val url = URL("$BASE_URL/$barcode.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) return null

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val response = json.decodeFromString<OffResponse>(responseText)

            if (response.status != 1 || response.product == null) return null

            val product = response.product
            ProductResult(
                name = product.productName?.takeIf { it.isNotBlank() },
                brand = product.brands?.takeIf { it.isNotBlank() },
                categories = product.categories?.takeIf { it.isNotBlank() },
                imageUrl = product.imageUrl?.takeIf { it.isNotBlank() },
                quantity = product.quantity?.takeIf { it.isNotBlank() },
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch product info from OpenFoodFacts", e)
            null
        }
    }
}

data class ProductResult(
    val name: String?,
    val brand: String?,
    val categories: String?,
    val imageUrl: String?,
    val quantity: String?,
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
)
