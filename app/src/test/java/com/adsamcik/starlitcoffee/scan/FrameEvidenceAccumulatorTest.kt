package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.scan.model.AccumulatorConfig
import com.adsamcik.starlitcoffee.scan.model.FieldStatus
import com.adsamcik.starlitcoffee.scan.model.FrameResult
import com.adsamcik.starlitcoffee.scan.model.GuidanceType
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FrameEvidenceAccumulatorTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Lifecycle ---

    @Test
    fun `initial evidence is empty`() {
        val accumulator = FrameEvidenceAccumulator()
        assertEquals(0, accumulator.evidence.value.fields.size)
        assertEquals(0, accumulator.evidence.value.totalFramesProcessed)
    }

    @Test
    fun `initial scan progress is zero`() {
        val accumulator = FrameEvidenceAccumulator()

        assertEquals(0f, accumulator.evidence.value.scanProgress, 0.01f)
        assertFalse(accumulator.evidence.value.isComplete)
    }

    // --- Adaptive Throttle ---

    @Test
    fun `throttle starts at fast interval`() {
        val accumulator = FrameEvidenceAccumulator()
        assertEquals(AccumulatorConfig.DEFAULT.throttleFastMs, accumulator.currentThrottleMs())
    }

    @Test
    fun `throttle returns slow interval when more than eighty percent of fields are resolved`() = runTest {
        val trackedFields = setOf(
            "name",
            "roaster",
            "origin",
            "region",
            "farm",
            "variety",
        )
        val config = AccumulatorConfig.DEFAULT.copy(
            consensusIntervalMs = 5_000L,
            allFields = trackedFields,
        )
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitEnrichment(
            fieldValues = mapOf(
                "name" to "Geometry",
                "roaster" to "Onyx",
                "origin" to "Ethiopia",
                "region" to "Yirgacheffe",
                "farm" to "Chelbesa",
                "variety" to "Heirloom",
            ),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            barcode = "slow-throttle",
        )
        waitForAsync()

        accumulator.userResolveField("name", "Geometry")
        accumulator.userResolveField("roaster", "Onyx")
        accumulator.userResolveField("origin", "Ethiopia")
        accumulator.userResolveField("region", "Yirgacheffe")
        accumulator.userResolveField("farm", "Chelbesa")
        waitForAsync()

        assertEquals(config.throttleSlowMs, accumulator.currentThrottleMs())

        accumulator.stop()
    }

    @Test
    fun `throttle returns maintenance interval when all fields are locked`() = runTest {
        val trackedFields = setOf("origin", "roaster")
        val config = fastConfig().copy(allFields = trackedFields)
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitEnrichment(
            fieldValues = mapOf(
                "origin" to "Ethiopia",
                "roaster" to "Onyx",
            ),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            barcode = "maintenance-throttle",
        )
        waitForAsync()

        accumulator.userResolveField("origin", "Ethiopia")
        accumulator.userResolveField("roaster", "Onyx")
        waitForAsync()

        assertEquals(config.throttleMaintenanceMs, accumulator.currentThrottleMs())

        accumulator.stop()
    }

    @Test
    fun `throttle stays fast when only a partial bag is locked`() = runTest {
        val config = fastConfig()
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitEnrichment(
            fieldValues = mapOf(
                "origin" to "Ethiopia",
                "roaster" to "Onyx",
            ),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            barcode = "partial-bag",
        )
        waitForAsync()

        accumulator.userResolveField("origin", "Ethiopia")
        accumulator.userResolveField("roaster", "Onyx")
        waitForAsync()

        assertEquals(config.throttleFastMs, accumulator.currentThrottleMs())

        accumulator.stop()
    }

    // --- User Actions ---

    @Test
    fun `user resolve field sets USER_LOCKED status`() = runTest {
        val accumulator = FrameEvidenceAccumulator()
        accumulator.start()

        repeat(5) { i ->
            accumulator.submitFrame(makeFrame(origin = "Ethiopia", frameIndex = i))
        }
        testScheduler.advanceUntilIdle()

        accumulator.userResolveField("origin", "ethiopia")

        val field = accumulator.evidence.value.fields["origin"]
        assertNotNull(field)
        assertEquals(FieldStatus.USER_LOCKED, field!!.status)

        accumulator.stop()
    }

    @Test
    fun `user reset field returns to SCANNING`() = runTest {
        val config = fastConfig()
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        repeat(5) { i ->
            accumulator.submitFrame(makeFrame(origin = "Ethiopia", frameIndex = i))
        }
        Thread.sleep(200)

        accumulator.userResolveField("origin", "ethiopia")
        assertEquals(FieldStatus.USER_LOCKED, accumulator.evidence.value.fields["origin"]?.status)

        accumulator.userResetField("origin")
        assertEquals(FieldStatus.SCANNING, accumulator.evidence.value.fields["origin"]?.status)

        accumulator.stop()
    }

    @Test
    fun `resolve field that does not exist is ignored safely`() {
        val accumulator = FrameEvidenceAccumulator()

        accumulator.userResolveField("missing", "value")

        assertTrue(accumulator.evidence.value.fields.isEmpty())
    }

    @Test
    fun `reset field that does not exist is ignored safely`() {
        val accumulator = FrameEvidenceAccumulator()

        accumulator.userResetField("missing")

        assertTrue(accumulator.evidence.value.fields.isEmpty())
    }

    @Test
    fun `resolve with a value not present in candidates leaves field unchanged`() = runTest {
        val config = AccumulatorConfig.DEFAULT.copy(consensusIntervalMs = 5_000L)
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitFrame(makeFrame(origin = "Ethiopia", frameIndex = 0))
        waitForAsync()

        accumulator.userResolveField("origin", "Colombia")

        assertTrue(accumulator.evidence.value.fields["origin"]?.status != FieldStatus.USER_LOCKED)
        assertTrue(accumulator.evidence.value.fields["origin"]?.resolvedValue != "colombia")

        accumulator.stop()
    }

    // --- Enrichment Dedup ---

    @Test
    fun `enrichment deduplicates by barcode`() = runTest {
        val config = fastConfig()
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitEnrichment(
            fieldValues = mapOf("roaster" to "Onyx"),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            barcode = "1234567890",
        )

        accumulator.submitEnrichment(
            fieldValues = mapOf("roaster" to "Different Roaster"),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            barcode = "1234567890",
        )

        waitForAsync()

        val evidence = accumulator.evidence.value
        assertEquals("1234567890", evidence.detectedBarcode)
        val roasterCandidates = evidence.fields["roaster"]?.candidates
        assertNotNull(roasterCandidates)
        assertEquals(1, roasterCandidates!!.size)

        accumulator.stop()
    }

    @Test
    fun `qr url enrichment populates qr metadata and field values`() = runTest {
        val config = fastConfig()
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitEnrichment(
            fieldValues = mapOf("origin" to "Ethiopia"),
            sourceType = BagFieldSourceType.QR_LINK_LOOKUP,
            qrUrl = "https://example.com/coffee",
        )
        waitForAsync()

        assertEquals("https://example.com/coffee", accumulator.evidence.value.detectedQrUrl)
        assertTrue(accumulator.evidence.value.fields.containsKey("origin"))

        accumulator.stop()
    }

    @Test
    fun `local barcode match gets higher weight than barcode lookup`() = runTest {
        val config = fastConfig()
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitEnrichment(
            fieldValues = mapOf("roaster" to "Lookup Roaster"),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            barcode = "lookup-1",
        )
        accumulator.submitEnrichment(
            fieldValues = mapOf("roaster" to "Local Match"),
            sourceType = BagFieldSourceType.LOCAL_BARCODE_MATCH,
            barcode = "local-1",
        )
        waitForAsync()

        val candidates = accumulator.evidence.value.fields["roaster"]!!.candidates
        assertTrue(candidates["local match"]!!.qualityWeightedVotes > candidates["lookup roaster"]!!.qualityWeightedVotes)

        accumulator.stop()
    }

    @Test
    fun `enrichment deduplicates by qr url`() = runTest {
        val config = fastConfig()
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitEnrichment(
            fieldValues = mapOf("origin" to "Ethiopia"),
            sourceType = BagFieldSourceType.QR_LINK_LOOKUP,
            qrUrl = "https://example.com/bag",
        )
        accumulator.submitEnrichment(
            fieldValues = mapOf("origin" to "Colombia"),
            sourceType = BagFieldSourceType.QR_LINK_LOOKUP,
            qrUrl = "https://example.com/bag",
        )
        waitForAsync()

        val candidates = accumulator.evidence.value.fields["origin"]!!.candidates
        assertEquals(1, candidates.size)
        assertTrue(candidates.containsKey("ethiopia"))

        accumulator.stop()
    }

    // --- Quality Admission ---

    @Test
    fun `should admit sharp frame`() {
        val accumulator = FrameEvidenceAccumulator()
        val frame = makeFrame(origin = "test", blurScore = 20f, glarePercent = 0.1f)
        assertTrue(accumulator.shouldAdmitFrame(frame))
    }

    @Test
    fun `should reject blurry frame`() {
        val accumulator = FrameEvidenceAccumulator()
        val frame = makeFrame(origin = "test", blurScore = 5f, glarePercent = 0.1f)
        assertTrue(!accumulator.shouldAdmitFrame(frame))
    }

    @Test
    fun `quality relaxation triggers after prolonged lack of admitted frames`() = runTest {
        val config = fastConfig().copy(qualityRelaxationTimeMs = 50L)
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitFrame(makeFrame(origin = "Ethiopia", frameIndex = 0))
        waitForAsync()
        Thread.sleep(80)
        accumulator.submitFrame(makeFrame(origin = "Ethiopia", frameIndex = 1, blurScore = 5f))
        waitForAsync()

        val borderlineFrame = makeFrame(origin = "Ethiopia", frameIndex = 2, blurScore = 9f)
        assertTrue(accumulator.shouldAdmitFrame(borderlineFrame))

        accumulator.stop()
    }

    @Test
    fun `quality relaxation resets after a frame is admitted`() = runTest {
        val config = fastConfig().copy(qualityRelaxationTimeMs = 50L)
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitFrame(makeFrame(origin = "Ethiopia", frameIndex = 0))
        waitForAsync()
        Thread.sleep(80)
        accumulator.submitFrame(makeFrame(origin = "Ethiopia", frameIndex = 1, blurScore = 5f))
        waitForAsync()
        assertTrue(accumulator.shouldAdmitFrame(makeFrame(origin = "Ethiopia", frameIndex = 2, blurScore = 9f)))

        accumulator.submitFrame(makeFrame(origin = "Ethiopia", frameIndex = 3, blurScore = 20f))
        waitForAsync()

        assertFalse(accumulator.shouldAdmitFrame(makeFrame(origin = "Ethiopia", frameIndex = 4, blurScore = 9f)))

        accumulator.stop()
    }

    @Test
    fun `rejected blurry frame increments rejection counter`() = runTest {
        val config = fastConfig()
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitFrame(makeFrame(origin = "Ethiopia", frameIndex = 0, blurScore = 20f))
        waitForAsync()
        accumulator.submitFrame(makeFrame(origin = "Ethiopia", frameIndex = 0, blurScore = 5f))
        waitForAsync()

        assertEquals(1, accumulator.evidence.value.totalFramesProcessed)
        assertEquals(1, accumulator.evidence.value.totalFramesRejected)

        accumulator.stop()
    }

    @Test
    fun `golden frames bypass normal quality gate`() = runTest {
        val config = fastConfig()
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitGoldenFrame(makeFrame(origin = "Ethiopia", frameIndex = 0, blurScore = 2f, glarePercent = 0.5f))
        waitForAsync()

        assertEquals(1, accumulator.evidence.value.totalFramesProcessed)
        assertTrue(accumulator.evidence.value.fields.containsKey("origin"))

        accumulator.stop()
    }

    // --- Side Flip ---

    @Test
    fun `side count starts at 1`() {
        val accumulator = FrameEvidenceAccumulator()
        assertEquals(1, accumulator.evidence.value.sideCount)
    }

    @Test
    fun `notify potential side flip does not flip immediately without text confirmation`() = runTest {
        val config = fastConfig()
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.notifyPotentialSideFlip()
        accumulator.submitFrame(makeDetailedFrame(origin = "Ethiopia", frameIndex = 0, textBlockCount = 1))
        waitForAsync()

        assertEquals(0, accumulator.evidence.value.currentSide)
        assertEquals(1, accumulator.evidence.value.sideCount)

        accumulator.stop()
    }

    @Test
    fun `pending side flip with text confirmation completes`() = runTest {
        val config = fastConfig()
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.notifyPotentialSideFlip()
        accumulator.submitFrame(makeDetailedFrame(origin = "Ethiopia", frameIndex = 0, textBlockCount = 3))
        waitForAsync()

        assertEquals(1, accumulator.evidence.value.currentSide)
        assertEquals(2, accumulator.evidence.value.sideCount)

        accumulator.stop()
    }

    @Test
    fun `pending side flip without confirmation times out`() = runTest {
        val config = fastConfig().copy(sideFlipTextConfirmMs = 50L)
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.notifyPotentialSideFlip()
        Thread.sleep(80)
        accumulator.submitFrame(makeDetailedFrame(origin = "Ethiopia", frameIndex = 0, textBlockCount = 3))
        waitForAsync()

        assertEquals(0, accumulator.evidence.value.currentSide)
        assertEquals(1, accumulator.evidence.value.sideCount)

        accumulator.stop()
    }

    @Test
    fun `cannot flip more than max sides two`() = runTest {
        val config = fastConfig()
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.notifyPotentialSideFlip()
        accumulator.submitFrame(makeDetailedFrame(origin = "Ethiopia", frameIndex = 0, textBlockCount = 3))
        waitForAsync()

        accumulator.notifyPotentialSideFlip()
        accumulator.submitFrame(makeDetailedFrame(origin = "Ethiopia", frameIndex = 1, textBlockCount = 3))
        waitForAsync()

        assertEquals(1, accumulator.evidence.value.currentSide)
        assertEquals(2, accumulator.evidence.value.sideCount)

        accumulator.stop()
    }

    // --- Guidance ---

    @Test
    fun `missing core field generates missing field guidance`() = runTest {
        val config = AccumulatorConfig.DEFAULT.copy(
            consensusIntervalMs = 5_000L,
            coreFields = setOf("name", "origin"),
            allFields = setOf("name", "origin"),
        )
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitEnrichment(
            fieldValues = mapOf("origin" to "Ethiopia"),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            barcode = "guidance-core",
        )
        waitForAsync()
        accumulator.userResetField("origin")

        val guidance = accumulator.evidence.value.guidance
        assertNotNull(guidance)
        assertEquals(GuidanceType.MISSING_FIELD, guidance!!.type)
        assertEquals("name", guidance.targetField)

        accumulator.stop()
    }

    @Test
    fun `complete scan generates scan complete guidance`() = runTest {
        val config = fastConfig().copy(
            coreFields = setOf("origin"),
            allFields = setOf("origin"),
        )
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitEnrichment(
            fieldValues = mapOf("origin" to "Ethiopia"),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            barcode = "complete-guidance",
        )
        waitForAsync()
        accumulator.userResolveField("origin", "Ethiopia")
        waitForAsync()

        val guidance = accumulator.evidence.value.guidance
        assertNotNull(guidance)
        assertEquals(GuidanceType.SCAN_COMPLETE, guidance!!.type)

        accumulator.stop()
    }

    @Test
    fun `flip suggestion appears after many frames with no back side`() = runTest {
        val config = fastConfig().copy(
            coreFields = emptySet(),
            allFields = setOf("origin"),
        )
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        repeat(16) { i ->
            val frame = makeDetailedFrame(
                origin = if (i % 2 == 0) "Ethiopia" else "Colombia",
                frameIndex = i,
                textBlockCount = 3,
                isGolden = true,
            )
            accumulator.submitGoldenFrame(frame)
        }
        Thread.sleep(400)

        val guidance = accumulator.evidence.value.guidance
        assertNotNull(guidance)
        assertEquals(GuidanceType.FLIP_SUGGESTION, guidance!!.type)

        accumulator.stop()
    }

    // --- Scan Progress ---

    @Test
    fun `scan progress increases as fields resolve`() = runTest {
        val config = AccumulatorConfig.DEFAULT.copy(
            consensusIntervalMs = 5_000L,
            coreFields = setOf("origin", "roaster"),
            allFields = setOf("origin", "roaster"),
        )
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitEnrichment(
            fieldValues = mapOf(
                "origin" to "Ethiopia",
                "roaster" to "Onyx",
            ),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            barcode = "progress-1",
        )
        waitForAsync()
        accumulator.userResolveField("origin", "Ethiopia")
        waitForAsync()

        assertEquals(0.5f, accumulator.evidence.value.scanProgress, 0.01f)

        accumulator.stop()
    }

    @Test
    fun `is complete becomes true at configured threshold`() = runTest {
        val config = fastConfig().copy(
            coreFields = setOf("origin"),
            allFields = setOf("origin"),
        )
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        accumulator.submitEnrichment(
            fieldValues = mapOf("origin" to "Ethiopia"),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            barcode = "complete-flag",
        )
        waitForAsync()
        accumulator.userResolveField("origin", "Ethiopia")
        waitForAsync()

        assertTrue(accumulator.evidence.value.isComplete)

        accumulator.stop()
    }

    // --- Deep Integration Scenarios ---

    @Test
    fun `full async convergence locks a field after repeated submit frame calls`() = runTest {
        val accumulator = FrameEvidenceAccumulator(config = fastConfig())
        accumulator.start()

        repeat(20) { i ->
            accumulator.submitFrame(makeDetailedFrame(origin = "Ethiopia", frameIndex = i))
        }
        waitForAsync(300)

        val originField = accumulator.evidence.value.fields["origin"]
        assertNotNull(originField)
        assertEquals(FieldStatus.LOCKED, originField!!.status)

        accumulator.stop()
    }

    @Test
    fun `golden frame via submit golden frame can overpower regular noisy evidence`() = runTest {
        val accumulator = FrameEvidenceAccumulator(config = fastConfig())
        accumulator.start()

        repeat(4) { i ->
            accumulator.submitFrame(
                makeDetailedFrame(
                    origin = "Colombia",
                    frameIndex = i,
                    blurScore = 12f,
                ),
            )
        }
        waitForAsync(150)

        accumulator.submitGoldenFrame(
            makeDetailedFrame(
                origin = "Ethiopia",
                frameIndex = 100,
                blurScore = 100f,
            ),
        )
        waitForAsync(250)

        val originField = accumulator.evidence.value.fields["origin"]!!
        val topCandidate = originField.topCandidate!!

        assertEquals("ethiopia", topCandidate.normalizedValue)
        assertTrue(topCandidate.qualityWeightedVotes > (originField.candidates["colombia"]?.qualityWeightedVotes ?: 0f))

        accumulator.stop()
    }

    @Test
    fun `ocr and enrichment race merge into a single candidate source set`() = runTest {
        // Use ConsensusEngine directly to avoid async timing issues
        val engine = ConsensusEngine(AccumulatorConfig.DEFAULT)

        // First: OCR frame sees "Onyx Coffee Lab"
        var fields = engine.integrateFrame(
            emptyMap(),
            FrameResult(
                ocrResult = OcrExtractionResult(
                    roaster = "Onyx Coffee Lab",
                    fieldConfidence = mapOf("roaster" to BagFieldConfidence.MEDIUM),
                ),
                quality = BagCaptureQuality(
                    blurScore = 20f, glarePercent = 0.05f,
                    overexposedPercent = 0.1f, underexposedPercent = 0.2f,
                    textBlockCount = 3, textDetected = true,
                ),
                frameIndex = 0,
                timestampMs = System.currentTimeMillis(),
            ),
        )

        // Then: barcode enrichment with same value
        fields = engine.integrateEnrichment(
            currentFields = fields,
            fieldValues = mapOf("roaster" to "Onyx Coffee Lab"),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            frameIndex = 1,
        )

        val candidate = fields["roaster"]!!.candidates["onyx coffee lab"]!!
        assertEquals(
            setOf(BagFieldSourceType.OCR, BagFieldSourceType.BARCODE_LOOKUP),
            candidate.sourceTypes,
        )
        assertEquals(2, candidate.observationCount)
    }

    @Test
    fun `rapid fire identical golden frames keep ring buffer bounded and still converge`() = runTest {
        val config = fastConfig().copy(ringBufferSize = 5)
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        repeat(100) { i ->
            accumulator.submitGoldenFrame(makeDetailedFrame(origin = "Ethiopia", frameIndex = i))
        }
        waitForAsync(350)

        assertTrue(ringBufferSize(accumulator) <= config.ringBufferSize)
        assertEquals(FieldStatus.LOCKED, accumulator.evidence.value.fields["origin"]!!.status)

        accumulator.stop()
    }

    @Test
    fun `user resolving a conflict freezes the field against later opposing frames`() = runTest {
        val accumulator = FrameEvidenceAccumulator(config = fastConfig())
        accumulator.start()

        repeat(12) { i ->
            val value = if (i % 2 == 0) "Ethiopia" else "Colombia"
            accumulator.submitGoldenFrame(makeDetailedFrame(origin = value, frameIndex = i))
        }
        waitForAsync(300)

        assertEquals(FieldStatus.CONFLICT, accumulator.evidence.value.fields["origin"]?.status)

        accumulator.userResolveField("origin", "Ethiopia")
        repeat(10) { i ->
            accumulator.submitGoldenFrame(makeDetailedFrame(origin = "Colombia", frameIndex = 100 + i))
        }
        waitForAsync(300)

        val originField = accumulator.evidence.value.fields["origin"]!!
        assertEquals(FieldStatus.USER_LOCKED, originField.status)
        assertEquals("ethiopia", originField.resolvedValue)

        accumulator.stop()
    }

    @Test
    fun `full scan lifecycle records side flip progress and completion across front and back batches`() = runTest {
        val config = fastConfig().copy(
            coreFields = setOf("origin", "roaster"),
            allFields = setOf("origin", "roaster", "processType", "altitude"),
            autoSaveThreshold = 1f,
        )
        val accumulator = FrameEvidenceAccumulator(config = config)
        accumulator.start()

        repeat(15) { i ->
            accumulator.submitGoldenFrame(
                makeLifecycleFrame(
                    origin = "Ethiopia",
                    roaster = "Onyx",
                    frameIndex = i,
                ),
            )
        }
        waitForAsync(150)

        accumulator.notifyPotentialSideFlip()
        repeat(15) { i ->
            accumulator.submitGoldenFrame(
                makeLifecycleFrame(
                    processType = "Washed",
                    altitude = "1800-2000 masl",
                    frameIndex = 100 + i,
                    textBlockCount = 3,
                ),
            )
        }
        waitForAsync(350)

        val evidence = accumulator.evidence.value
        assertEquals(1, evidence.currentSide)
        assertEquals(2, evidence.sideCount)
        assertTrue(evidence.fields.keys.containsAll(setOf("origin", "roaster", "processType", "altitude")))
        assertTrue(evidence.scanProgress >= 1f)
        assertTrue(evidence.isComplete)
        assertEquals(setOf(1), evidence.fields["processType"]!!.topCandidate!!.sides)
        assertEquals(setOf(1), evidence.fields["altitude"]!!.topCandidate!!.sides)

        accumulator.stop()
    }

    @Test
    fun `stop cancels cleanly and late submissions do not mutate evidence`() = runTest {
        val accumulator = FrameEvidenceAccumulator(config = fastConfig())
        accumulator.start()

        accumulator.submitFrame(makeDetailedFrame(origin = "Ethiopia", frameIndex = 0))
        waitForAsync()
        val beforeStop = accumulator.evidence.value

        accumulator.stop()
        accumulator.submitFrame(makeDetailedFrame(origin = "Colombia", frameIndex = 1))
        accumulator.submitGoldenFrame(makeDetailedFrame(origin = "Guatemala", frameIndex = 2))
        accumulator.submitEnrichment(
            fieldValues = mapOf("origin" to "Kenya"),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            barcode = "late-after-stop",
        )
        waitForAsync()

        assertEquals(beforeStop, accumulator.evidence.value)
    }

    @Test
    fun `multiple start stop cycles work independently across accumulator instances`() = runTest {
        val first = FrameEvidenceAccumulator(config = fastConfig())
        first.start()
        first.submitFrame(makeDetailedFrame(origin = "Ethiopia", frameIndex = 0))
        waitForAsync()
        assertTrue(first.evidence.value.fields.containsKey("origin"))
        first.stop()

        val second = FrameEvidenceAccumulator(config = fastConfig())
        second.start()
        second.submitFrame(makeDetailedFrame(roaster = "Onyx", frameIndex = 1))
        waitForAsync()
        assertTrue(second.evidence.value.fields.containsKey("roaster"))
        assertFalse(second.evidence.value.fields.containsKey("origin"))
        second.stop()
    }

    // --- Helpers ---

    private fun makeFrame(
        origin: String? = null,
        roaster: String? = null,
        frameIndex: Int = 0,
        blurScore: Float = 20f,
        glarePercent: Float = 0.05f,
    ): FrameResult {
        val confidence = mutableMapOf<String, BagFieldConfidence>()
        origin?.let { confidence["origin"] = BagFieldConfidence.MEDIUM }
        roaster?.let { confidence["roaster"] = BagFieldConfidence.MEDIUM }

        return FrameResult(
            ocrResult = OcrExtractionResult(
                origin = origin,
                roaster = roaster,
                fieldConfidence = confidence,
            ),
            quality = BagCaptureQuality(
                blurScore = blurScore,
                glarePercent = glarePercent,
                overexposedPercent = 0.1f,
                underexposedPercent = 0.2f,
                textBlockCount = 3,
                textDetected = true,
            ),
            frameIndex = frameIndex,
            timestampMs = System.currentTimeMillis(),
        )
    }

    private fun makeDetailedFrame(
        origin: String? = null,
        roaster: String? = null,
        frameIndex: Int = 0,
        blurScore: Float = 20f,
        glarePercent: Float = 0.05f,
        textBlockCount: Int = 3,
        isGolden: Boolean = false,
    ): FrameResult {
        val confidence = mutableMapOf<String, BagFieldConfidence>()
        origin?.let { confidence["origin"] = BagFieldConfidence.MEDIUM }
        roaster?.let { confidence["roaster"] = BagFieldConfidence.MEDIUM }

        return FrameResult(
            ocrResult = OcrExtractionResult(
                origin = origin,
                roaster = roaster,
                fieldConfidence = confidence,
            ),
            quality = BagCaptureQuality(
                blurScore = blurScore,
                glarePercent = glarePercent,
                overexposedPercent = 0.1f,
                underexposedPercent = 0.2f,
                textBlockCount = textBlockCount,
                textDetected = true,
            ),
            frameIndex = frameIndex,
            timestampMs = System.currentTimeMillis(),
            isGoldenFrame = isGolden,
        )
    }

    private fun makeLifecycleFrame(
        origin: String? = null,
        roaster: String? = null,
        processType: String? = null,
        altitude: String? = null,
        frameIndex: Int = 0,
        textBlockCount: Int = 3,
    ): FrameResult {
        val confidence = mutableMapOf<String, BagFieldConfidence>()
        origin?.let { confidence["origin"] = BagFieldConfidence.HIGH }
        roaster?.let { confidence["roaster"] = BagFieldConfidence.HIGH }
        processType?.let { confidence["processType"] = BagFieldConfidence.HIGH }
        altitude?.let { confidence["altitude"] = BagFieldConfidence.HIGH }

        return FrameResult(
            ocrResult = OcrExtractionResult(
                origin = origin,
                roaster = roaster,
                processType = processType,
                altitude = altitude,
                fieldConfidence = confidence,
            ),
            quality = BagCaptureQuality(
                blurScore = 25f,
                glarePercent = 0.03f,
                overexposedPercent = 0.1f,
                underexposedPercent = 0.2f,
                textBlockCount = textBlockCount,
                textDetected = true,
            ),
            frameIndex = frameIndex,
            timestampMs = System.currentTimeMillis(),
        )
    }

    private fun fastConfig(): AccumulatorConfig {
        return AccumulatorConfig.DEFAULT.copy(consensusIntervalMs = 50L)
    }

    private fun ringBufferSize(accumulator: FrameEvidenceAccumulator): Int {
        val field = FrameEvidenceAccumulator::class.java.getDeclaredField("ringBuffer")
        field.isAccessible = true
        return (field.get(accumulator) as ArrayDeque<*>).size
    }

    private fun waitForAsync(delayMs: Long = 200L) {
        Thread.sleep(delayMs)
    }
}
