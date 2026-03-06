package com.adsamcik.starlitcoffee.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Singleton managing the Gemma 3n E2B model lifecycle — download, storage, and readiness.
 *
 * The model file is stored at `context.filesDir/models/[MODEL_FILENAME]`.
 * AI-enabled preference is persisted in SharedPreferences.
 */
object ModelManager {

    private const val MODEL_URL =
        "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm"
    private const val MODEL_FILENAME = "gemma-3n-e2b.litertlm"
    private const val MODELS_DIR = "models"
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

    /** Check current file state and update [_state] accordingly. */
    fun refreshState(context: Context) {
        if (isModelDownloaded(context)) {
            _state.value = ModelState.DOWNLOADED
        } else if (_state.value != ModelState.DOWNLOADING) {
            _state.value = ModelState.NOT_DOWNLOADED
        }
    }

    /**
     * Downloads the model from HuggingFace to local storage.
     * Supports resume via HTTP Range header when a partial file already exists.
     */
    suspend fun downloadModel(context: Context) {
        if (_state.value == ModelState.DOWNLOADING) return
        _state.value = ModelState.DOWNLOADING
        _downloadProgress.value = 0f
        _errorMessage.value = null

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
                        // Server supports resume
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
                    _state.value = if (isModelDownloaded(context)) {
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

    /** Cancels an in-progress download. */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        if (_state.value == ModelState.DOWNLOADING) {
            _state.value = ModelState.NOT_DOWNLOADED
        }
    }

    /** Stores the download [Job] so it can be cancelled. */
    fun setDownloadJob(job: Job) {
        downloadJob = job
    }

    /** Deletes the model file and resets state. */
    suspend fun deleteModel(context: Context) {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "$MODELS_DIR/$MODEL_FILENAME")
            if (file.exists()) file.delete()
        }
        _downloadProgress.value = 0f
        _state.value = ModelState.NOT_DOWNLOADED
    }

    /** Returns the model file path if the file exists, null otherwise. */
    fun getModelPath(context: Context): String? {
        val file = File(context.filesDir, "$MODELS_DIR/$MODEL_FILENAME")
        return if (file.exists()) file.absolutePath else null
    }

    /** True when the model file exists on disk. */
    fun isModelDownloaded(context: Context): Boolean {
        return File(context.filesDir, "$MODELS_DIR/$MODEL_FILENAME").exists()
    }

    /** Size of the model file in bytes, or 0 if not present. */
    fun getModelSizeBytes(context: Context): Long {
        val file = File(context.filesDir, "$MODELS_DIR/$MODEL_FILENAME")
        return if (file.exists()) file.length() else 0L
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
