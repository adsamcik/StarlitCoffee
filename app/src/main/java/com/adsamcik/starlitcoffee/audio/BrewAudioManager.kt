package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.AudioAnalysisState
import com.adsamcik.starlitcoffee.data.model.AudioConfig
import com.adsamcik.starlitcoffee.data.model.AudioDetectionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Orchestrates audio capture, analysis, and recording during a brew session.
 *
 * Lifecycle:
 * 1. [startMonitoring] — begins capturing audio and running real-time analysis
 * 2. [startRecording] — additionally writes audio to WAV files (opt-in)
 * 3. [onPhaseChanged] — closes current recording segment, opens new one
 * 4. [stopRecording] / [stopMonitoring] — clean shutdown
 *
 * Exposes [analysisState] for UI and [detectionEvents] for event-driven integration.
 */
class BrewAudioManager(
    private val config: AudioConfig = AudioConfig(),
    private val outputDirectory: File? = null,
) {
    private val captureSession = AudioCaptureSession(config)
    private val recorder = AudioRecorder(config)

    private var scope: CoroutineScope? = null
    private var captureJob: Job? = null

    private val _analysisState = MutableStateFlow(AudioAnalysisState())
    val analysisState: StateFlow<AudioAnalysisState> = _analysisState

    private val _detectionEvents = MutableSharedFlow<AudioDetectionEvent>(extraBufferCapacity = 16)
    val detectionEvents: SharedFlow<AudioDetectionEvent> = _detectionEvents

    private var silenceStartTimeMs: Long = 0L
    private var wasSilent: Boolean = true

    private var currentPhaseIndex: Int = 0
    private var currentPhaseLabel: String = ""
    private var brewTimestamp: String = ""

    val isMonitoring: Boolean get() = captureJob?.isActive == true
    val isRecording: Boolean get() = recorder.isOpen

    /**
     * Starts capturing audio from the microphone and running real-time analysis.
     * Does NOT start recording to files — call [startRecording] separately.
     */
    fun startMonitoring() {
        if (isMonitoring) return

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope
        brewTimestamp = System.currentTimeMillis().toString()

        captureJob = newScope.launch {
            captureSession.audioBufferFlow().collect { buffer ->
                processBuffer(buffer)
            }
        }

        _analysisState.update {
            it.copy(isMonitoring = true)
        }
    }

    /**
     * Stops audio capture and analysis. Also stops recording if active.
     */
    fun stopMonitoring() {
        if (isRecording) stopRecording()
        captureJob?.cancel()
        captureJob = null
        scope?.cancel()
        scope = null

        _analysisState.update {
            AudioAnalysisState() // reset to defaults
        }
        silenceStartTimeMs = 0L
        wasSilent = true
    }

    /**
     * Starts recording audio to WAV files. Requires [startMonitoring] first.
     * Each phase gets its own file via [onPhaseChanged].
     */
    fun startRecording() {
        if (!isMonitoring) return
        if (isRecording) return

        openNewRecordingFile()
        _analysisState.update {
            it.copy(
                isRecording = true,
                recordingFilePath = recorder.outputFile?.absolutePath,
            )
        }
    }

    /**
     * Stops recording to files. Monitoring continues.
     */
    fun stopRecording() {
        if (recorder.isOpen) {
            recorder.close()
        }
        _analysisState.update {
            it.copy(isRecording = false, recordingFilePath = null)
        }
    }

    /**
     * Notifies the manager that the brew phase has changed.
     * If recording, closes the current file and opens a new one for the new phase.
     */
    fun onPhaseChanged(phaseIndex: Int, phaseLabel: String) {
        currentPhaseIndex = phaseIndex
        currentPhaseLabel = phaseLabel

        _analysisState.update {
            it.copy(currentPhaseLabel = phaseLabel)
        }

        if (isRecording) {
            recorder.close()
            openNewRecordingFile()
            _analysisState.update {
                it.copy(recordingFilePath = recorder.outputFile?.absolutePath)
            }
        }
    }

    private fun processBuffer(samples: ShortArray) {
        val features = AudioAnalyzer.analyze(samples, samples.size, config.sampleRate)

        // Write to file if recording
        if (recorder.isOpen) {
            recorder.write(samples)
        }

        // Silence detection
        val now = System.currentTimeMillis()
        val isSilent = features.rmsDb < config.silenceThresholdDb

        if (isSilent) {
            if (!wasSilent) {
                silenceStartTimeMs = now
            }
            val silenceDuration = now - silenceStartTimeMs

            if (silenceDuration >= config.silenceDurationThresholdMs && wasSilent) {
                // Already reported — just update duration
            } else if (silenceDuration >= config.silenceDurationThresholdMs) {
                _detectionEvents.tryEmit(AudioDetectionEvent.SilenceDetected(silenceDuration))
            }

            _analysisState.update { state ->
                val newHistory = (state.levelHistory + features.rmsDb)
                    .takeLast(AudioAnalysisState.LEVEL_HISTORY_SIZE)
                state.copy(
                    rmsDb = features.rmsDb,
                    peakDb = features.peakDb,
                    dominantFrequencyHz = features.dominantFrequencyHz,
                    zeroCrossingRate = features.zeroCrossingRate,
                    isSilent = true,
                    silenceDurationMs = now - silenceStartTimeMs,
                    levelHistory = newHistory,
                )
            }
        } else {
            if (wasSilent && silenceStartTimeMs > 0) {
                _detectionEvents.tryEmit(AudioDetectionEvent.SoundResumed)
            }
            silenceStartTimeMs = 0L

            _analysisState.update { state ->
                val newHistory = (state.levelHistory + features.rmsDb)
                    .takeLast(AudioAnalysisState.LEVEL_HISTORY_SIZE)
                state.copy(
                    rmsDb = features.rmsDb,
                    peakDb = features.peakDb,
                    dominantFrequencyHz = features.dominantFrequencyHz,
                    zeroCrossingRate = features.zeroCrossingRate,
                    isSilent = false,
                    silenceDurationMs = 0L,
                    levelHistory = newHistory,
                )
            }
        }

        wasSilent = isSilent
    }

    private fun openNewRecordingFile() {
        val dir = outputDirectory ?: return
        val sanitizedPhase = currentPhaseLabel
            .replace(" ", "_")
            .replace("&", "and")
            .lowercase()
        val fileName = "brew_${brewTimestamp}_phase_${currentPhaseIndex}_${sanitizedPhase}.wav"
        val file = File(dir, fileName)
        recorder.open(file)
    }
}
