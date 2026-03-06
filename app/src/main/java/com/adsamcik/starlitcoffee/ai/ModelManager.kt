package com.adsamcik.starlitcoffee.ai

import android.content.Context
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackStates
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Manages Gemma 3n E2B model lifecycle with dual delivery:
 * - **Play Asset Delivery** for Play Store installs (on-demand asset pack)
 * - **HTTP download** from HuggingFace for debug/sideloaded builds
 *
 * The resolved model path is always returned via [getModelPath], regardless of source.
 */
object ModelManager {

    private const val MODEL_URL =
        "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm"
    private const val MODEL_FILENAME = "gemma-3n-e2b.litertlm"
    private const val MODELS_DIR = "models"
    private const val ASSET_PACK_NAME = "ai_model_pack"
    private const val ASSET_MODEL_PATH = "gemma-3n-E2B-it-int4.litertlm"
    private const val PREFS_NAME = "ai_prefs"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val BUFFER_SIZE = 8192

    enum class ModelState {
        NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, LOADING, READY, ERROR
    }

    private val _state = MutableStateFlow(ModelState.NOT_DOWNLOADED)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var downloadJob: Job? = null

    /** Check all sources (asset pack + local file) and update state. */
    fun refreshState(context: Context) {
        if (getModelPath(context) != null) {
            _state.value = ModelState.DOWNLOADED
        } else if (_state.value != ModelState.DOWNLOADING) {
            _state.value = ModelState.NOT_DOWNLOADED
        }
    }

    /**
     * Downloads the model. Tries Play Asset Delivery first, falls back to HTTP.
     */
    suspend fun downloadModel(context: Context) {
        if (_state.value == ModelState.DOWNLOADING) return
        _state.value = ModelState.DOWNLOADING
        _downloadProgress.value = 0f
        _errorMessage.value = null

        // Try Play Asset Delivery first
        if (tryAssetPackDelivery(context)) return

        // Fallback to HTTP download
        downloadViaHttp(context)
    }

    /**
     * Attempts to fetch the model via Play Asset Delivery.
     * Returns true if the asset pack was successfully fetched or was already available.
     */
    private suspend fun tryAssetPackDelivery(context: Context): Boolean {
        return try {
            val manager = AssetPackManagerFactory.getInstance(context)
            val packStates = suspendCancellableCoroutine<AssetPackStates?> { cont ->
                manager.getPackStates(listOf(ASSET_PACK_NAME))
                    .addOnSuccessListener { state -> cont.resume(state) }
                    .addOnFailureListener { cont.resume(null) }
            } ?: return false

            val packState = packStates.packStates()[ASSET_PACK_NAME] ?: return false

            when (packState.status()) {
                AssetPackStatus.COMPLETED -> {
                    _downloadProgress.value = 1f
                    _state.value = ModelState.DOWNLOADED
                    true
                }
                AssetPackStatus.NOT_INSTALLED -> {
                    val fetchResult = suspendCancellableCoroutine<Boolean> { cont ->
                        manager.fetch(listOf(ASSET_PACK_NAME))
                            .addOnSuccessListener { cont.resume(true) }
                            .addOnFailureListener { cont.resume(false) }
                    }
                    if (!fetchResult) return false

                    monitorAssetPackProgress(context)
                }
                AssetPackStatus.DOWNLOADING, AssetPackStatus.TRANSFERRING -> {
                    monitorAssetPackProgress(context)
                }
                else -> false
            }
        } catch (_: Exception) {
            false // PAD not available, fall back to HTTP
        }
    }

    /** Polls asset pack download progress until complete or failed. */
    private suspend fun monitorAssetPackProgress(context: Context): Boolean {
        val manager = AssetPackManagerFactory.getInstance(context)
        while (true) {
            val states = suspendCancellableCoroutine<AssetPackStates?> { cont ->
                manager.getPackStates(listOf(ASSET_PACK_NAME))
                    .addOnSuccessListener { state -> cont.resume(state) }
                    .addOnFailureListener { cont.resume(null) }
            } ?: return false

            val packState = states.packStates()[ASSET_PACK_NAME] ?: return false
            val total = packState.totalBytesToDownload()
            val downloaded = packState.bytesDownloaded()

            when (packState.status()) {
                AssetPackStatus.COMPLETED -> {
                    _downloadProgress.value = 1f
                    _state.value = ModelState.DOWNLOADED
                    return true
                }
                AssetPackStatus.DOWNLOADING, AssetPackStatus.TRANSFERRING -> {
                    if (total > 0) {
                        _downloadProgress.value = (downloaded.toFloat() / total).coerceIn(0f, 1f)
                    }
                }
                AssetPackStatus.FAILED, AssetPackStatus.CANCELED -> return false
                else -> { /* keep waiting */ }
            }
            kotlinx.coroutines.delay(500)
        }
    }

    /** Downloads the model directly from HuggingFace via HTTP. */
    private suspend fun downloadViaHttp(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, MODELS_DIR)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, MODEL_FILENAME)

                val existingBytes = if (file.exists()) file.length() else 0L

                val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    if (existingBytes > 0) {
                        setRequestProperty("Range", "bytes=$existingBytes-")
                    }
                }

                val responseCode = connection.responseCode
                val totalBytes: Long
                val append: Boolean

                when (responseCode) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        totalBytes = existingBytes + connection.contentLengthLong
                        append = true
                    }
                    HttpURLConnection.HTTP_OK -> {
                        totalBytes = connection.contentLengthLong
                        append = false
                    }
                    else -> {
                        _errorMessage.value = "Download failed: HTTP $responseCode"
                        _state.value = ModelState.ERROR
                        return@withContext
                    }
                }

                var downloaded = if (append) existingBytes else 0L
                connection.inputStream.use { input ->
                    java.io.FileOutputStream(file, append).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalBytes > 0) {
                                _downloadProgress.value =
                                    (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                            }
                        }
                    }
                }

                _downloadProgress.value = 1f
                _state.value = ModelState.DOWNLOADED
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _state.value = if (getModelPath(context) != null) {
                        ModelState.DOWNLOADED
                    } else {
                        ModelState.NOT_DOWNLOADED
                    }
                    throw e
                }
                _errorMessage.value = e.message ?: "Download failed"
                _state.value = ModelState.ERROR
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        if (_state.value == ModelState.DOWNLOADING) {
            _state.value = ModelState.NOT_DOWNLOADED
        }
    }

    fun setDownloadJob(job: Job) {
        downloadJob = job
    }

    /** Deletes the locally stored model file and resets state. */
    suspend fun deleteModel(context: Context) {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "$MODELS_DIR/$MODEL_FILENAME")
            if (file.exists()) file.delete()
        }
        _downloadProgress.value = 0f
        _state.value = ModelState.NOT_DOWNLOADED
    }

    /**
     * Returns the model file path from either:
     * 1. Local storage (HTTP download)
     * 2. Play Asset Delivery asset pack
     * Returns null if the model is not available from any source.
     */
    fun getModelPath(context: Context): String? {
        // Check local storage first (HTTP download)
        val localFile = File(context.filesDir, "$MODELS_DIR/$MODEL_FILENAME")
        if (localFile.exists()) return localFile.absolutePath

        // Check Play Asset Delivery
        return try {
            val manager = AssetPackManagerFactory.getInstance(context)
            val packLocation = manager.getPackLocation(ASSET_PACK_NAME)
            if (packLocation != null) {
                val assetFile = File(packLocation.assetsPath(), ASSET_MODEL_PATH)
                if (assetFile.exists()) assetFile.absolutePath else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun isModelDownloaded(context: Context): Boolean = getModelPath(context) != null

    fun getModelSizeBytes(context: Context): Long {
        val path = getModelPath(context) ?: return 0L
        return File(path).length()
    }

    // --- AI-enabled preference ---

    fun isAiEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AI_ENABLED, false)
    }

    fun setAiEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AI_ENABLED, enabled)
            .apply()
    }
}
