package com.adsamcik.starlitcoffee.data.network.ocr

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException

/** Uses [primary] when ready and falls back without exposing providers to UI. */
class FallbackOcrService(
    private val primary: OcrService,
    private val fallback: OcrService,
) : OcrService {
    override fun close() {
        primary.close()
        fallback.close()
    }

    override suspend fun isAvailable(): Boolean = safelyAvailable(primary) || safelyAvailable(fallback)

    override suspend fun recognize(bitmap: Bitmap): RecognizedText? = runWithFallback(
        primaryAvailable = primary::isAvailable,
        primaryCall = { primary.recognize(bitmap) },
        fallbackCall = { fallback.recognize(bitmap) },
    )

    private suspend fun safelyAvailable(service: OcrService): Boolean = try {
        service.isAvailable()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }
}

internal suspend fun <T> runWithFallback(
    primaryAvailable: suspend () -> Boolean,
    primaryCall: suspend () -> T?,
    fallbackCall: suspend () -> T?,
): T? {
    val primaryResult = try {
        if (primaryAvailable()) primaryCall() else null
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
    if (primaryResult != null) return primaryResult
    return try {
        fallbackCall()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}
