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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

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
    /** Auto-start WAV + JSONL recording when monitoring begins. */
    private val autoRecord: Boolean = false,
) {
    private val captureSession = AudioCaptureSession(config)
    private val recorder = AudioRecorder(config)
    private val preProcessor = AudioPreProcessor(sampleRate = config.sampleRate)
    private val spectralAnalyzer = SpectralAnalyzer()
    private val eventDetector = BrewEventDetector(detectorConfig)
    private val ambientBaseline= AmbientBaseline()
    private val trajectoryMatcher = BrewTrajectoryMatcher()
    private val flightRecorder = FlightRecorder()
    private val userLabelsRecorder = UserLabelsRecorder()
    private val shadowLog = ShadowComparisonLog()

    // Thread-safe event accumulation for flight recorder
    private val pendingEvents = ConcurrentLinkedQueue<BrewAudioEvent>()

    // Guards against concurrent start/stop races
    private val monitoringLock = AtomicBoolean(false)

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
    private var brewContext = BrewContext()
    private var ambientRmsCaptured = false

    val isMonitoring: Boolean get() = monitoringLock.get()
    val isRecording: Boolean get() = recorder.isOpen

    /**
     * Starts shadow comparison logging for A/B testing.
     * Call AFTER startMonitoring() so brewTimestamp is set.
     */
    fun startShadowLog(outputDirectory: File) {
        val file = File(outputDirectory, "brew_${brewTimestamp}_shadow.jsonl")
        shadowLog.open(file)
    }

    /**
     * Records a user-initiated phase transition for shadow comparison.
     */
    fun recordUserTap(phaseIndex: Int, phaseLabel: String) {
        shadowLog.recordUserTap(phaseIndex, phaseLabel)
    }

    /**
     * Sets the brew context for metadata export. Call before startRecording()
     * with the current BrewUiState values.
     */
    fun setBrewContext(
        method: String,
        filterType: String,
        doseG: Float,
        waterG: Float,
        ratio: Float,
        grinderId: String? = null,
        grinderSetting: String? = null,
        placement: String = "unknown",
        environment: String = "unknown",
        notes: String = "",
    ) {
        brewContext = BrewContext(method, filterType, doseG, waterG, ratio,
            grinderId, grinderSetting, placement, environment, notes)
    }

    /**
     * Starts capturing audio from the microphone and running real-time analysis.
     * Does NOT start recording to files — call [startRecording] separately.
     */
    fun startMonitoring() {
        if (!monitoringLock.compareAndSet(false, true)) return

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope
        brewTimestamp = System.currentTimeMillis().toString()

        // Reset pipeline state for new session
        preProcessor.reset()
        spectralAnalyzer.reset()
        eventDetector.reset()
        ambientBaseline.reset()
        trajectoryMatcher.reset()
        silenceStartTimeMs = System.currentTimeMillis()
        wasSilent = true
        ambientRmsCaptured = false
        captureJob = newScope.launch {
            captureSession.audioBufferFlow().collect { buffer ->
                processBuffer(buffer)
            }
        }

        _analysisState.update {
            it.copy(isMonitoring = true)
        }

        // Auto-start recording if enabled (captures WAV + JSONL for debugging)
        if (autoRecord && outputDirectory != null) {
            startRecording()
        }
    }

    /** The current brew session timestamp. Available after [startMonitoring]. */
    val currentBrewTimestamp: String get() = brewTimestamp

    /**
     * Stops audio capture and analysis. Also stops recording if active.
     */
    fun stopMonitoring() {
        if (!monitoringLock.compareAndSet(true, false)) return
        if (isRecording) stopRecording()
        shadowLog.close()
        // Cancel capture joband wait for in-flight processBuffer to complete
        captureJob?.cancel()
        captureJob = null
        scope?.cancel()
        scope = null

        _analysisState.update {
            AudioAnalysisState() // reset to defaults
        }
        silenceStartTimeMs = 0L
        wasSilent = true
        brewContext = BrewContext()
    }

    /**
     * Starts recording audio to WAV files. Requires [startMonitoring] first.
     * Each phase gets its own file via [onPhaseChanged].
     */
    fun startRecording() {
        if (!isMonitoring) return
        if (isRecording) return

        openNewRecordingFile()

        // Open flight recorder sidecar (same name, .jsonl extension)
        recorder.outputFile?.let { wavFile ->
            val parent = wavFile.parentFile ?: return@let
            val jsonlFile = File(parent, wavFile.nameWithoutExtension + ".jsonl")
            flightRecorder.open(jsonlFile)
        }

        // Write recording session metadata sidecar
        outputDirectory?.let { dir ->
            val ambientRms = _analysisState.value.rmsDb
            RecordingMetadata.fromCurrentSession(
                brewTimestamp = brewTimestamp.toLongOrNull() ?: System.currentTimeMillis(),
                method = brewContext.method,
                filterType = brewContext.filterType,
                doseG = brewContext.doseG,
                waterG = brewContext.waterG,
                ratio = brewContext.ratio,
                grinderId = brewContext.grinderId,
                grinderSetting = brewContext.grinderSetting,
                placement = brewContext.placement,
                environment = brewContext.environment,
                ambientRmsDb = ambientRms,
                sampleRate = config.sampleRate,
                notes = brewContext.notes,
            ).writeToFile(dir)
        }

        // Open user labels recorder for ground truth marking
        outputDirectory?.let { dir ->
            val labelsFile = File(dir, "brew_${brewTimestamp}_user_labels.txt")
            userLabelsRecorder.open(labelsFile, System.currentTimeMillis())
        }

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
        userLabelsRecorder.close()
        flightRecorder.close()
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
            flightRecorder.close()
            recorder.close()
            openNewRecordingFile()
            recorder.outputFile?.let { wavFile ->
                val parent = wavFile.parentFile ?: return@let
                val jsonlFile = File(parent, wavFile.nameWithoutExtension + ".jsonl")
                flightRecorder.open(jsonlFile)
            }
            _analysisState.update {
                it.copy(recordingFilePath = recorder.outputFile?.absolutePath)
            }
        }
    }

    /**
     * Records a user-marked event label (ground truth, ±2-3s accuracy).
     */
    fun markUserLabel(label: String) {
        userLabelsRecorder.markEvent(label)
    }

    /**
     * Records a problem marker during a brew experiment.
     */
    fun markUserProblem(description: String) {
        userLabelsRecorder.markProblem(description)
    }

    private fun processBuffer(samples: ShortArray) {
        val timeFeatures = AudioAnalyzer.analyze(samples, samples.size, config.sampleRate)

        // Write raw audio to file if recording
        if (recorder.isOpen) {
            recorder.write(samples)
        }

        // Spectral pipeline: PreProcessor → SpectralAnalyzer → Baseline → EventDetector
        val frames = preProcessor.process(samples)
        var latestSpectral = _analysisState.value.spectralFeatures
        var latestBrewEvent: BrewAudioEvent? = null

        for (frame in frames) {
            // Always include power spectrum for baseline subtraction
            val spectral = spectralAnalyzer.analyze(frame, includePowerSpectrum = true)
            val rawPower = spectral.powerSpectrum ?: continue

            // Ambient baseline: calibrate during first ~5s, then subtract
            if (!ambientBaseline.isCalibrated) {
                val justCalibrated = ambientBaseline.feedCalibrationFrame(rawPower)
                // Capture ambient RMS once calibration completes, rewrite metadata
                if (justCalibrated && !ambientRmsCaptured) {
                    ambientRmsCaptured = true
                    val ambientRms = timeFeatures.rmsDb
                    outputDirectory?.let { dir ->
                        RecordingMetadata.fromCurrentSession(
                            brewTimestamp = brewTimestamp.toLongOrNull() ?: System.currentTimeMillis(),
                            method = brewContext.method,
                            filterType = brewContext.filterType,
                            doseG = brewContext.doseG,
                            waterG = brewContext.waterG,
                            ratio = brewContext.ratio,
                            grinderId = brewContext.grinderId,
                            grinderSetting = brewContext.grinderSetting,
                            placement = brewContext.placement,
                            environment = brewContext.environment,
                            ambientRmsDb = ambientRms,
                            sampleRate = config.sampleRate,
                            notes = brewContext.notes,
                        ).writeToFile(dir)
                    }
                }
            }
            val residualPower = ambientBaseline.subtract(rawPower)

            // Enrich spectral features with subtraction results
            val enrichedSpectral = spectral.copy(
                isBaselineCalibrated = ambientBaseline.isCalibrated,
            )
            latestSpectral = enrichedSpectral

            // Feed trajectory matcher (1-second downsampled)
            trajectoryMatcher.feedFrame(
                pourEnergyDb = spectral.bandEnergyDb[com.adsamcik.starlitcoffee.data.model.FrequencyBand.POUR] ?: -96f,
                spectralFlatness = spectral.spectralFlatness,
                bandCoincidence = spectral.bandCoincidenceCount,
            )

            val events = eventDetector.processFrame(enrichedSpectral)
            for (event in events) {
                _brewEvents.tryEmit(event)
                latestBrewEvent = event
                if (shadowLog.isOpen) {
                    shadowLog.recordDetectorEvent(event)
                }
            }

            // Flight recorder: accumulate events, write snapshot at interval
            if (flightRecorder.isOpen) {
                events.forEach { pendingEvents.offer(it) }
                // Drain queue into snapshot
                val snapshotEvents = mutableListOf<BrewAudioEvent>()
                while (true) {
                    snapshotEvents.add(pendingEvents.poll() ?: break)
                }
                val wrote = flightRecorder.recordSnapshot(
                    FlightRecorder.Snapshot(
                        spectralFeatures = enrichedSpectral,
                        detectorState = eventDetector.state,
                        noiseFloorDb = eventDetector.noiseFloorDb,
                        dripRate = eventDetector.dripRate,
                        rmsDb = timeFeatures.rmsDb,
                        brewPhaseLabel = currentPhaseLabel,
                        trajectoryPhase = trajectoryMatcher.trajectoryPhase.displayName,
                        brewConfidence = trajectoryMatcher.brewConfidence,
                        baselineCalibrated = ambientBaseline.isCalibrated,
                        events = snapshotEvents,
                    )
                )
                // If not written (throttled), put events back for next snapshot
                if (!wrote) {
                    snapshotEvents.forEach { pendingEvents.offer(it) }
                }
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
                baselineCalibrated = ambientBaseline.isCalibrated,
                trajectoryPhase = trajectoryMatcher.trajectoryPhase.displayName,
                brewConfidence = trajectoryMatcher.brewConfidence,
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

    private data class BrewContext(
        val method: String = "",
        val filterType: String = "",
        val doseG: Float = 0f,
        val waterG: Float = 0f,
        val ratio: Float = 0f,
        val grinderId: String? = null,
        val grinderSetting: String? = null,
        val placement: String = "unknown",
        val environment: String = "unknown",
        val notes: String = "",
    )
}
