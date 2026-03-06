package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.AudioAnalysisState
import com.adsamcik.starlitcoffee.data.model.AudioConfig
import com.adsamcik.starlitcoffee.data.model.AudioDetectionEvent
import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import com.adsamcik.starlitcoffee.data.model.DetectorConfig
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
 * Full pipeline: AudioCapture → PreProcessor → SpectralAnalyzer → BrewEventDetector
 *
 * Lifecycle:
 * 1. [startMonitoring] — begins capturing audio and running real-time analysis
 * 2. [startRecording] — additionally writes audio to WAV files (opt-in)
 * 3. [onPhaseChanged] — closes current recording segment, opens new one
 * 4. [stopRecording] / [stopMonitoring] — clean shutdown
 *
 * Exposes [analysisState] for UI, [detectionEvents] for legacy events,
 * and [brewEvents] for brew-specific detection events.
 */
class BrewAudioManager(
    private val config: AudioConfig = AudioConfig(),
    private val detectorConfig: DetectorConfig = DetectorConfig(),
    private val outputDirectory: File? = null,
    /** Enable the active acoustic probe (experimental). Off by default. */
    private val activeProbeEnabled: Boolean = false,
) {
    private val captureSession = AudioCaptureSession(config)
    private val recorder = AudioRecorder(config)
    private val preProcessor = AudioPreProcessor(sampleRate = config.sampleRate)
    private val spectralAnalyzer = SpectralAnalyzer()
    private val eventDetector = BrewEventDetector(detectorConfig)
    private val activeProbe: ActiveProbe? = if (activeProbeEnabled) ActiveProbe() else null

    private var scope: CoroutineScope? = null
    private var captureJob: Job? = null

    private val _analysisState = MutableStateFlow(AudioAnalysisState())
    val analysisState: StateFlow<AudioAnalysisState> = _analysisState

    private val _detectionEvents = MutableSharedFlow<AudioDetectionEvent>(extraBufferCapacity = 16)
    val detectionEvents: SharedFlow<AudioDetectionEvent> = _detectionEvents

    private val _brewEvents = MutableSharedFlow<BrewAudioEvent>(extraBufferCapacity = 16)
    val brewEvents: SharedFlow<BrewAudioEvent> = _brewEvents

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

        // Reset pipeline state for new session
        preProcessor.reset()
        spectralAnalyzer.reset()
        eventDetector.reset()
        silenceStartTimeMs = System.currentTimeMillis()
        wasSilent = true
        activeProbe?.start()

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
        activeProbe?.stop()
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
        val timeFeatures = AudioAnalyzer.analyze(samples, samples.size, config.sampleRate)

        // Write raw audio to file if recording
        if (recorder.isOpen) {
            recorder.write(samples)
        }

        // Spectral pipeline: PreProcessor → SpectralAnalyzer → EventDetector
        val frames = preProcessor.process(samples)
        var latestSpectral = _analysisState.value.spectralFeatures
        var latestBrewEvent: BrewAudioEvent? = null

        for (frame in frames) {
            val spectral = spectralAnalyzer.analyze(frame, includePowerSpectrum = activeProbe?.isActive == true)

            // Active probe: analyze the probe bin in the power spectrum
            val probeTurbulence = if (activeProbe?.isActive == true && spectral.powerSpectrum != null) {
                activeProbe.analyzeProbeResponse(spectral.powerSpectrum)
            } else {
                0f
            }

            // Attach probe turbulence to features for the detector
            val enrichedSpectral = if (probeTurbulence > 0f) {
                spectral.copy(probeTurbulence = probeTurbulence)
            } else {
                spectral
            }
            latestSpectral = enrichedSpectral

            val events = eventDetector.processFrame(enrichedSpectral)
            for (event in events) {
                _brewEvents.tryEmit(event)
                latestBrewEvent = event
            }
        }

        // Silence detection (legacy, still useful for basic monitoring)
        val now = System.currentTimeMillis()
        val isSilent = timeFeatures.rmsDb < config.silenceThresholdDb

        if (isSilent) {
            if (!wasSilent) {
                silenceStartTimeMs = now
            }
            val silenceDuration = now - silenceStartTimeMs

            if (silenceDuration >= config.silenceDurationThresholdMs && !wasSilent) {
                _detectionEvents.tryEmit(AudioDetectionEvent.SilenceDetected(silenceDuration))
            }
        } else {
            if (wasSilent && silenceStartTimeMs > 0) {
                _detectionEvents.tryEmit(AudioDetectionEvent.SoundResumed)
            }
            silenceStartTimeMs = 0L
        }

        // Update UI state with both time-domain and spectral features
        _analysisState.update { state ->
            val newHistory = (state.levelHistory + timeFeatures.rmsDb)
                .takeLast(AudioAnalysisState.LEVEL_HISTORY_SIZE)
            state.copy(
                rmsDb = timeFeatures.rmsDb,
                peakDb = timeFeatures.peakDb,
                dominantFrequencyHz = timeFeatures.dominantFrequencyHz,
                zeroCrossingRate = timeFeatures.zeroCrossingRate,
                isSilent = isSilent,
                silenceDurationMs = if (isSilent) now - silenceStartTimeMs else 0L,
                levelHistory = newHistory,
                spectralFeatures = latestSpectral,
                detectorState = eventDetector.state,
                dripRate = eventDetector.dripRate,
                noiseFloorDb = eventDetector.noiseFloorDb,
                lastBrewEvent = latestBrewEvent ?: state.lastBrewEvent,
                probeActive = activeProbe?.isActive == true,
                probeTurbulence = activeProbe?.turbulenceScore ?: 0f,
            )
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
