package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.scan.model.AccumulatorConfig
import com.adsamcik.starlitcoffee.scan.model.CandidateState
import com.adsamcik.starlitcoffee.scan.model.FieldAccumulation
import com.adsamcik.starlitcoffee.scan.model.FieldStatus
import com.adsamcik.starlitcoffee.scan.model.FrameResult
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsensusEngineTest {

    private lateinit var engine: ConsensusEngine
    private lateinit var config: AccumulatorConfig

    @Before
    fun setUp() {
        config = AccumulatorConfig.DEFAULT
        engine = ConsensusEngine(config)
    }

    @After
    fun tearDown() {
    }

    // --- Levenshtein Distance ---

    @Test
    fun `levenshtein distance between identical strings is zero`() {
        assertEquals(0, engine.levenshteinDistance("Ethiopia", "Ethiopia"))
    }

    @Test
    fun `levenshtein distance handles single character difference`() {
        assertEquals(1, engine.levenshteinDistance("Onyx", "Onyx."))
    }

    @Test
    fun `levenshtein distance handles multiple differences`() {
        assertEquals(5, engine.levenshteinDistance("Ethiopia", "Eritrea"))
    }

    @Test
    fun `levenshtein distance handles empty strings`() {
        assertEquals(5, engine.levenshteinDistance("", "hello"))
        assertEquals(5, engine.levenshteinDistance("hello", ""))
        assertEquals(0, engine.levenshteinDistance("", ""))
    }

    @Test
    fun `levenshtein distance handles unicode accents`() {
        assertEquals(1, engine.levenshteinDistance("café", "cafe"))
    }

    @Test
    fun `levenshtein distance handles very long strings`() {
        val a = "a".repeat(120)
        val b = "a".repeat(119) + "b"
        assertEquals(1, engine.levenshteinDistance(a, b))
    }

    @Test
    fun `levenshtein distance is case sensitive in raw function`() {
        assertEquals(3, engine.levenshteinDistance("Onyx", "ONYX"))
    }

    @Test
    fun `levenshtein distance handles single character strings`() {
        assertEquals(0, engine.levenshteinDistance("a", "a"))
        assertEquals(1, engine.levenshteinDistance("a", "b"))
        assertEquals(1, engine.levenshteinDistance("a", ""))
    }

    // --- Medoid Selection ---

    @Test
    fun `medoid selects central string from cluster`() {
        val cluster = listOf("Onyx", "Onyx.", "Onyx")
        val medoid = engine.selectMedoid(cluster)
        assertEquals("Onyx", medoid)
    }

    @Test
    fun `medoid returns single element for singleton cluster`() {
        val medoid = engine.selectMedoid(listOf("Ethiopia"))
        assertEquals("Ethiopia", medoid)
    }

    @Test
    fun `medoid returns first value when two strings are equally central`() {
        val medoid = engine.selectMedoid(listOf("Kenya", "Kenia"))

        assertEquals("Kenya", medoid)
    }

    @Test
    fun `medoid ignores one distant outlier when dense core exists`() {
        val medoid = engine.selectMedoid(listOf("Onyx", "Onyx.", "Onyx", "Colombia"))

        assertEquals("Onyx", medoid)
    }

    @Test
    fun `medoid comparison is case insensitive`() {
        val medoid = engine.selectMedoid(listOf("ONYX", "Onyx", "onyx"))

        assertEquals("ONYX", medoid)
    }

    // --- Frame Integration ---

    @Test
    fun `integrate frame creates new field accumulations from OCR result`() {
        val frame = makeFrame(
            origin = "Ethiopia",
            roaster = "Onyx Coffee Lab",
        )

        val result = engine.integrateFrame(emptyMap(), frame)

        assertEquals(2, result.size)
        assertNotNull(result["origin"])
        assertNotNull(result["roaster"])
        assertEquals(1, result["origin"]!!.candidates.size)
        assertEquals(FieldStatus.SCANNING, result["origin"]!!.status)
    }

    @Test
    fun `integrate frame accumulates votes for same value`() {
        val frame1 = makeFrame(origin = "Ethiopia", frameIndex = 0)
        val frame2 = makeFrame(origin = "Ethiopia", frameIndex = 1)
        val frame3 = makeFrame(origin = "Ethiopia", frameIndex = 2)

        var fields = engine.integrateFrame(emptyMap(), frame1)
        fields = engine.integrateFrame(fields, frame2)
        fields = engine.integrateFrame(fields, frame3)

        val originField = fields["origin"]!!
        assertEquals(1, originField.candidates.size)
        val candidate = originField.candidates.values.first()
        assertEquals(3, candidate.observationCount)
    }

    @Test
    fun `integrate frame clusters near-miss OCR variants into same candidate`() {
        val frame1 = makeFrame(origin = "Onyx", frameIndex = 0)
        val frame2 = makeFrame(origin = "Onyx.", frameIndex = 1)
        val frame3 = makeFrame(origin = "Onyx", frameIndex = 2)

        var fields = engine.integrateFrame(emptyMap(), frame1)
        fields = engine.integrateFrame(fields, frame2)
        fields = engine.integrateFrame(fields, frame3)

        val originField = fields["origin"]!!
        assertEquals(1, originField.candidates.size)
        assertEquals(3, originField.candidates.values.first().observationCount)
    }

    @Test
    fun `integrate frame keeps distinct candidates for very different values`() {
        val frame1 = makeFrame(origin = "Ethiopia", frameIndex = 0)
        val frame2 = makeFrame(origin = "Colombia", frameIndex = 1)

        var fields = engine.integrateFrame(emptyMap(), frame1)
        fields = engine.integrateFrame(fields, frame2)

        assertEquals(2, fields["origin"]!!.candidates.size)
    }

    @Test
    fun `golden frames get 3x weight`() {
        val normalFrame = makeFrame(origin = "Ethiopia", frameIndex = 0)
        val goldenFrame = makeFrame(origin = "Ethiopia", frameIndex = 1, isGolden = true)

        var fields = engine.integrateFrame(emptyMap(), normalFrame)
        val normalVotes = fields["origin"]!!.candidates.values.first().qualityWeightedVotes

        fields = engine.integrateFrame(fields, goldenFrame)
        val totalVotes = fields["origin"]!!.candidates.values.first().qualityWeightedVotes

        val goldenContribution = totalVotes - normalVotes
        assertTrue(goldenContribution > normalVotes * 2.5f)
    }

    @Test
    fun `integrate frame evicts lowest-vote candidates when over cap`() {
        val customConfig = config.copy(maxCandidatesPerField = 3)
        val customEngine = ConsensusEngine(customConfig)

        var fields = emptyMap<String, FieldAccumulation>()
        for (i in 0..3) {
            val origin = "Country$i"
            fields = customEngine.integrateFrame(fields, makeFrame(origin = origin, frameIndex = i))
        }

        assertTrue(fields["origin"]!!.candidates.size <= 3)
    }

    @Test
    fun `integrate frame skips USER_LOCKED fields`() {
        val frame1 = makeFrame(origin = "Ethiopia", frameIndex = 0)
        var fields = engine.integrateFrame(emptyMap(), frame1)

        fields = fields.mapValues { (_, field) ->
            field.copy(status = FieldStatus.USER_LOCKED)
        }

        val frame2 = makeFrame(origin = "Colombia", frameIndex = 1)
        fields = engine.integrateFrame(fields, frame2)

        assertEquals(1, fields["origin"]!!.candidates.size)
        assertTrue(fields["origin"]!!.candidates.containsKey("ethiopia"))
    }

    @Test
    fun `integrate frame with no OCR fields returns existing fields unchanged`() {
        val existing = engine.integrateFrame(emptyMap(), makeFrame(origin = "Ethiopia", frameIndex = 0))
        val emptyFrame = FrameResult(
            ocrResult = OcrExtractionResult(),
            quality = makeQuality(),
            frameIndex = 1,
            timestampMs = System.currentTimeMillis(),
        )

        val result = engine.integrateFrame(existing, emptyFrame)

        assertEquals(existing, result)
    }

    @Test
    fun `integrate frame handles all extracted OCR fields at once`() {
        val frame = FrameResult(
            ocrResult = OcrExtractionResult(
                name = "Geometry",
                roaster = "Onyx Coffee Lab",
                origin = "Ethiopia",
                region = "Yirgacheffe",
                farm = "Chelbesa",
                variety = "Heirloom",
                processType = "Washed",
                altitude = "2100 masl",
                tastingNotes = "Tea, Citrus, Floral",
                roastLevel = "Light",
                roastDate = "2025-01-05",
                expiryDate = "2025-03-05",
                weight = "250 g",
                fieldConfidence = mapOf(
                    "name" to BagFieldConfidence.HIGH,
                    "roaster" to BagFieldConfidence.HIGH,
                    "origin" to BagFieldConfidence.HIGH,
                    "expiryDate" to BagFieldConfidence.MEDIUM,
                ),
            ),
            quality = makeQuality(),
            frameIndex = 0,
            timestampMs = System.currentTimeMillis(),
        )

        val result = engine.integrateFrame(emptyMap(), frame)

        assertEquals(13, result.size)
        assertTrue(result.keys.containsAll(
            listOf(
                "name",
                "roaster",
                "origin",
                "region",
                "farm",
                "variety",
                "processType",
                "altitude",
                "tastingNotes",
                "roastLevel",
                "roastDate",
                "expiryDate",
                "weight",
            ),
        ))
    }

    @Test
    fun `integrate frame skips blank and whitespace only values`() {
        val frame = FrameResult(
            ocrResult = OcrExtractionResult(
                origin = "   ",
                roaster = "\t",
            ),
            quality = makeQuality(),
            frameIndex = 0,
            timestampMs = System.currentTimeMillis(),
        )

        val result = engine.integrateFrame(emptyMap(), frame)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `integrate frame preserves very long field values`() {
        val longName = "Ethiopia " + "washed ".repeat(20).trim()
        val frame = FrameResult(
            ocrResult = OcrExtractionResult(name = longName),
            quality = makeQuality(),
            frameIndex = 0,
            timestampMs = System.currentTimeMillis(),
        )

        val result = engine.integrateFrame(emptyMap(), frame)

        assertTrue(result["name"]!!.candidates.containsKey(longName.trim().lowercase()))
    }

    @Test
    fun `integrate frame accumulates repeated identical frames even with same frame index`() {
        val frame = makeFrame(origin = "Ethiopia", frameIndex = 7)

        var fields = engine.integrateFrame(emptyMap(), frame)
        fields = engine.integrateFrame(fields, frame)

        val candidate = fields["origin"]!!.candidates.values.first()
        assertEquals(2, candidate.observationCount)
        assertEquals(7, candidate.lastSeenFrameIndex)
    }

    @Test
    fun `integrate frame preserves provisional status and resolved value`() {
        var fields = buildDominantField("origin", "ethiopia", 10, 1)
        fields = engine.runStateMachine(fields, KnownFieldValues.EMPTY)
        assertEquals(FieldStatus.PROVISIONAL, fields["origin"]!!.status)

        fields = engine.integrateFrame(fields, makeFrame(origin = "Ethiopia", frameIndex = 20))

        assertEquals(FieldStatus.PROVISIONAL, fields["origin"]!!.status)
        assertEquals("ethiopia", fields["origin"]!!.resolvedValue)
    }

    @Test
    fun `integrate frame preserves locked status and lock score`() {
        var fields = buildDominantField("origin", "ethiopia", 10, 1)
        repeat(3) {
            fields = engine.runStateMachine(fields, KnownFieldValues.EMPTY)
        }
        val before = fields["origin"]!!

        fields = engine.integrateFrame(fields, makeFrame(origin = "Ethiopia", frameIndex = 30))
        val after = fields["origin"]!!

        assertEquals(FieldStatus.LOCKED, after.status)
        assertEquals(before.lockScore, after.lockScore, 0.01f)
        assertEquals(before.resolvedValue, after.resolvedValue)
    }

    @Test
    fun `integrate frame records side information on candidates`() {
        val frame = makeFrame(origin = "Ethiopia", frameIndex = 0).copy(side = 1)
        val fields = engine.integrateFrame(emptyMap(), frame)

        assertTrue(fields["origin"]!!.candidates.values.first().sides.contains(1))
    }

    @Test
    fun `integrate frame accumulates multiple fields from same frame`() {
        val frame = FrameResult(
            ocrResult = OcrExtractionResult(
                origin = "Ethiopia",
                roaster = "Onyx",
                variety = "Heirloom",
                processType = "Washed",
                fieldConfidence = mapOf(
                    "origin" to BagFieldConfidence.MEDIUM,
                    "roaster" to BagFieldConfidence.HIGH,
                    "variety" to BagFieldConfidence.LOW,
                    "processType" to BagFieldConfidence.MEDIUM,
                ),
            ),
            quality = makeQuality(),
            frameIndex = 0,
            timestampMs = System.currentTimeMillis(),
        )

        val result = engine.integrateFrame(emptyMap(), frame)

        assertEquals(4, result.size)
        assertEquals(1, result["origin"]!!.candidates.values.first().observationCount)
        assertEquals(1, result["roaster"]!!.candidates.values.first().observationCount)
        assertEquals(1, result["variety"]!!.candidates.values.first().observationCount)
        assertEquals(1, result["processType"]!!.candidates.values.first().observationCount)
    }

    // --- Enrichment Integration ---

    @Test
    fun `enrichment evidence gets high weight`() {
        val fields = engine.integrateEnrichment(
            currentFields = emptyMap(),
            fieldValues = mapOf("roaster" to "Onyx Coffee Lab"),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            frameIndex = 0,
        )

        val candidate = fields["roaster"]!!.candidates.values.first()
        assertTrue(candidate.sourceTypes.contains(BagFieldSourceType.BARCODE_LOOKUP))
        assertTrue(candidate.qualityWeightedVotes > 10f)
    }

    @Test
    fun `multiple enrichment source types accumulate on same field`() {
        var fields = engine.integrateFrame(emptyMap(), makeFrame(roaster = "Onyx", frameIndex = 0))
        fields = engine.integrateEnrichment(
            currentFields = fields,
            fieldValues = mapOf("roaster" to "Onyx"),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            frameIndex = 1,
        )
        fields = engine.integrateEnrichment(
            currentFields = fields,
            fieldValues = mapOf("roaster" to "Onyx"),
            sourceType = BagFieldSourceType.QR_LINK_LOOKUP,
            frameIndex = 2,
        )

        val candidate = fields["roaster"]!!.candidates.values.first()
        assertEquals(
            setOf(
                BagFieldSourceType.OCR,
                BagFieldSourceType.BARCODE_LOOKUP,
                BagFieldSourceType.QR_LINK_LOOKUP,
            ),
            candidate.sourceTypes,
        )
        assertEquals(3, candidate.observationCount)
    }

    @Test
    fun `enrichment merges into existing OCR candidate rather than creating duplicate`() {
        var fields = engine.integrateFrame(emptyMap(), makeFrame(origin = "Ethiopia", frameIndex = 0))
        fields = engine.integrateEnrichment(
            currentFields = fields,
            fieldValues = mapOf("origin" to "ETHIOPIA"),
            sourceType = BagFieldSourceType.LOCAL_BARCODE_MATCH,
            frameIndex = 1,
        )

        assertEquals(1, fields["origin"]!!.candidates.size)
        assertEquals(2, fields["origin"]!!.candidates.values.first().observationCount)
    }

    @Test
    fun `enrichment skips empty field values`() {
        val existing = engine.integrateFrame(emptyMap(), makeFrame(origin = "Ethiopia", frameIndex = 0))

        val result = engine.integrateEnrichment(
            currentFields = existing,
            fieldValues = mapOf("origin" to "   "),
            sourceType = BagFieldSourceType.QR_LINK_LOOKUP,
            frameIndex = 1,
        )

        assertEquals(existing, result)
    }

    @Test
    fun `multiple enrichment fields are integrated simultaneously`() {
        val result = engine.integrateEnrichment(
            currentFields = emptyMap(),
            fieldValues = mapOf(
                "roaster" to "Onyx",
                "origin" to "Ethiopia",
                "variety" to "Heirloom",
            ),
            sourceType = BagFieldSourceType.QR_LINK_LOOKUP,
            frameIndex = 0,
        )

        assertEquals(3, result.size)
        assertEquals(BagFieldSourceType.QR_LINK_LOOKUP, result["roaster"]!!.candidates.values.first().sourceTypes.first())
        assertEquals(BagFieldSourceType.QR_LINK_LOOKUP, result["origin"]!!.candidates.values.first().sourceTypes.first())
    }

    // --- Bayesian Posteriors ---

    @Test
    fun `posteriors sum to approximately 1`() {
        val frame1 = makeFrame(origin = "Ethiopia", frameIndex = 0)
        val frame2 = makeFrame(origin = "Colombia", frameIndex = 1)

        var fields = engine.integrateFrame(emptyMap(), frame1)
        fields = engine.integrateFrame(fields, frame2)

        val posteriors = engine.computePosteriors(
            fields["origin"]!!.candidates,
            emptyMap(),
        )
        val sum = posteriors.values.sum()
        assertEquals(1f, sum, 0.01f)
    }

    @Test
    fun `known values boost prior probability`() {
        val knownValues = KnownFieldValues(origins = listOf("Ethiopia", "Ethiopia", "Colombia"))

        val frame1 = makeFrame(origin = "Ethiopia", frameIndex = 0)
        val frame2 = makeFrame(origin = "Colombia", frameIndex = 1)

        var fields = engine.integrateFrame(emptyMap(), frame1)
        fields = engine.integrateFrame(fields, frame2)

        val priors = engine.buildPrior("origin", knownValues)
        val posteriors = engine.computePosteriors(fields["origin"]!!.candidates, priors)

        assertTrue(posteriors["ethiopia"]!! > posteriors["colombia"]!!)
    }

    @Test
    fun `single candidate always gets full posterior`() {
        val candidates = mapOf(
            "ethiopia" to candidateState("ethiopia", votes = 3f),
        )

        val posteriors = engine.computePosteriors(candidates, emptyMap())

        assertEquals(1f, posteriors["ethiopia"]!!, 0.01f)
    }

    @Test
    fun `three candidates with equal votes get equal posteriors`() {
        val candidates = mapOf(
            "ethiopia" to candidateState("ethiopia", votes = 1f),
            "colombia" to candidateState("colombia", votes = 1f),
            "kenya" to candidateState("kenya", votes = 1f),
        )

        val posteriors = engine.computePosteriors(candidates, emptyMap())

        assertEquals(1f / 3f, posteriors["ethiopia"]!!, 0.01f)
        assertEquals(1f / 3f, posteriors["colombia"]!!, 0.01f)
        assertEquals(1f / 3f, posteriors["kenya"]!!, 0.01f)
    }

    @Test
    fun `known values prior handles many repeated entries`() {
        val priors = engine.buildPrior(
            "origin",
            KnownFieldValues(
                origins = listOf(
                    "Ethiopia",
                    "Ethiopia",
                    "Ethiopia",
                    "Colombia",
                    "Kenya",
                    "Kenya",
                ),
            ),
        )

        assertEquals(0.5f, priors["ethiopia"]!!, 0.01f)
        assertEquals(1f / 6f, priors["colombia"]!!, 0.01f)
        assertEquals(2f / 6f, priors["kenya"]!!, 0.01f)
    }

    @Test
    fun `compute posteriors returns empty map for empty candidates`() {
        assertTrue(engine.computePosteriors(emptyMap(), emptyMap()).isEmpty())
    }

    @Test
    fun `build prior returns empty map for unknown field name`() {
        val priors = engine.buildPrior("unknownField", KnownFieldValues(origins = listOf("Ethiopia")))

        assertTrue(priors.isEmpty())
    }

    @Test
    fun `compute posteriors falls back to uniform distribution when likelihood total is zero`() {
        val candidates = mapOf(
            "ethiopia" to candidateState("ethiopia", votes = 0f),
            "colombia" to candidateState("colombia", votes = 0f),
        )

        val posteriors = engine.computePosteriors(candidates, emptyMap())

        assertEquals(0.5f, posteriors["ethiopia"]!!, 0.01f)
        assertEquals(0.5f, posteriors["colombia"]!!, 0.01f)
    }

    // --- Field State Machine ---

    @Test
    fun `field transitions from SCANNING to PROVISIONAL when threshold met`() {
        val fields = buildDominantField("origin", "ethiopia", 10, 1)
        val knownValues = KnownFieldValues.EMPTY

        val result = engine.runStateMachine(fields, knownValues)

        assertEquals(FieldStatus.PROVISIONAL, result["origin"]!!.status)
        assertEquals("ethiopia", result["origin"]!!.resolvedValue)
    }

    @Test
    fun `field transitions from PROVISIONAL to LOCKED after 3 cycles`() {
        val knownValues = KnownFieldValues.EMPTY
        var fields = buildDominantField("origin", "ethiopia", 10, 1)

        repeat(3) {
            fields = engine.runStateMachine(fields, knownValues)
        }

        assertEquals(FieldStatus.LOCKED, fields["origin"]!!.status)
        assertEquals(1f, fields["origin"]!!.lockScore, 0.01f)
    }

    @Test
    fun `locked field decays on counter-evidence`() {
        val knownValues = KnownFieldValues.EMPTY
        var fields = buildDominantField("origin", "ethiopia", 10, 1)

        repeat(3) { fields = engine.runStateMachine(fields, knownValues) }
        assertEquals(FieldStatus.LOCKED, fields["origin"]!!.status)

        fields = buildDominantField("origin", "colombia", 20, 1, existingFields = fields)
        fields = engine.runStateMachine(fields, knownValues)

        assertTrue(fields["origin"]!!.lockScore < 1f)
    }

    @Test
    fun `conflict is detected when two candidates are strong`() {
        val knownValues = KnownFieldValues.EMPTY
        var fields = buildEvenField("origin", "ethiopia", "colombia", 5)

        repeat(5) {
            fields = engine.runStateMachine(fields, knownValues)
        }

        assertEquals(FieldStatus.CONFLICT, fields["origin"]!!.status)
    }

    @Test
    fun `USER_LOCKED field never changes`() {
        val knownValues = KnownFieldValues.EMPTY
        var fields = buildDominantField("origin", "ethiopia", 10, 1)
        fields = fields.mapValues { (_, field) ->
            field.copy(
                status = FieldStatus.USER_LOCKED,
                resolvedValue = "ethiopia",
            )
        }

        repeat(10) { fields = engine.runStateMachine(fields, knownValues) }

        assertEquals(FieldStatus.USER_LOCKED, fields["origin"]!!.status)
    }

    @Test
    fun `scanning field stays scanning when posterior is below threshold`() {
        val field = fieldWithPosteriors(
            status = FieldStatus.SCANNING,
            candidates = linkedMapOf(
                "ethiopia" to 0.84f,
                "colombia" to 0.16f,
            ),
        )

        val result = engine.transitionField(field)

        assertEquals(FieldStatus.SCANNING, result.status)
        assertEquals(0, result.consecutiveLockCycles)
    }

    @Test
    fun `provisional field falls back to scanning when posterior drops below threshold`() {
        val field = fieldWithPosteriors(
            status = FieldStatus.PROVISIONAL,
            consecutiveLockCycles = 1,
            resolvedValue = "ethiopia",
            candidates = linkedMapOf(
                "ethiopia" to 0.70f,
                "colombia" to 0.30f,
            ),
        )

        val result = engine.transitionField(field)

        assertEquals(FieldStatus.SCANNING, result.status)
        assertNull(result.resolvedValue)
        assertNull(result.resolvedEvidence)
    }

    @Test
    fun `locked field stays locked when same value remains dominant`() {
        val field = fieldWithPosteriors(
            status = FieldStatus.LOCKED,
            resolvedValue = "ethiopia",
            lockScore = 1f,
            candidates = linkedMapOf(
                "ethiopia" to 0.92f,
                "colombia" to 0.08f,
            ),
        )

        val result = engine.transitionField(field)

        assertEquals(FieldStatus.LOCKED, result.status)
        assertEquals(1f, result.lockScore, 0.01f)
        assertEquals("ethiopia", result.resolvedValue)
    }

    @Test
    fun `locked field returns to scanning when lock score reaches zero`() {
        val field = fieldWithPosteriors(
            status = FieldStatus.LOCKED,
            resolvedValue = "ethiopia",
            lockScore = config.lockDecayRate,
            candidates = linkedMapOf(
                "colombia" to 0.90f,
                "ethiopia" to 0.10f,
            ),
        )

        val result = engine.transitionField(field)

        assertEquals(FieldStatus.SCANNING, result.status)
        assertEquals(0f, result.lockScore, 0.01f)
        assertNull(result.resolvedValue)
    }

    @Test
    fun `conflict field transitions to provisional when one candidate pulls ahead`() {
        val field = fieldWithPosteriors(
            status = FieldStatus.CONFLICT,
            consecutiveConflictCycles = 4,
            candidates = linkedMapOf(
                "ethiopia" to 0.90f,
                "colombia" to 0.10f,
            ),
        )

        val result = engine.transitionField(field)

        assertEquals(FieldStatus.PROVISIONAL, result.status)
        assertEquals(1, result.consecutiveLockCycles)
        assertEquals(0, result.consecutiveConflictCycles)
        assertEquals("ethiopia", result.resolvedValue)
    }

    @Test
    fun `conflict field stays conflict while top two candidates remain close`() {
        val field = fieldWithPosteriors(
            status = FieldStatus.CONFLICT,
            consecutiveConflictCycles = 4,
            candidates = linkedMapOf(
                "ethiopia" to 0.60f,
                "colombia" to 0.40f,
            ),
        )

        val result = engine.transitionField(field)

        assertEquals(FieldStatus.CONFLICT, result.status)
        assertEquals(4, result.consecutiveConflictCycles)
    }

    @Test
    fun `run state machine handles multiple fields with different statuses simultaneously`() {
        val fields = linkedMapOf(
            "origin" to fieldWithVotes(
                fieldName = "origin",
                status = FieldStatus.SCANNING,
                votes = linkedMapOf("ethiopia" to 9f, "colombia" to 1f),
            ),
            "roaster" to fieldWithVotes(
                fieldName = "roaster",
                status = FieldStatus.SCANNING,
                consecutiveConflictCycles = 3,
                votes = linkedMapOf("onyx" to 5f, "sey" to 5f),
            ),
            "variety" to fieldWithVotes(
                fieldName = "variety",
                status = FieldStatus.LOCKED,
                resolvedValue = "heirloom",
                lockScore = 1f,
                votes = linkedMapOf("heirloom" to 10f, "gesha" to 1f),
            ),
        )

        val result = engine.runStateMachine(fields, KnownFieldValues.EMPTY)

        assertEquals(FieldStatus.PROVISIONAL, result["origin"]!!.status)
        assertEquals(FieldStatus.CONFLICT, result["roaster"]!!.status)
        assertEquals(FieldStatus.LOCKED, result["variety"]!!.status)
    }

    @Test
    fun `lock score decays by exactly configured rate`() {
        val field = fieldWithPosteriors(
            status = FieldStatus.LOCKED,
            resolvedValue = "ethiopia",
            lockScore = 1f,
            candidates = linkedMapOf(
                "colombia" to 0.91f,
                "ethiopia" to 0.09f,
            ),
        )

        val result = engine.transitionField(field)

        assertEquals(1f - config.lockDecayRate, result.lockScore, 0.01f)
    }

    @Test
    fun `consecutive lock cycles increment while provisional field remains above threshold`() {
        val field = fieldWithPosteriors(
            status = FieldStatus.PROVISIONAL,
            consecutiveLockCycles = 1,
            resolvedValue = "ethiopia",
            candidates = linkedMapOf(
                "ethiopia" to 0.91f,
                "colombia" to 0.09f,
            ),
        )

        val result = engine.transitionField(field)

        assertEquals(2, result.consecutiveLockCycles)
        assertEquals(FieldStatus.PROVISIONAL, result.status)
    }

    @Test
    fun `consecutive conflict cycles reset when conflict resolves`() {
        val field = fieldWithPosteriors(
            status = FieldStatus.CONFLICT,
            consecutiveConflictCycles = 4,
            candidates = linkedMapOf(
                "ethiopia" to 0.90f,
                "colombia" to 0.10f,
            ),
        )

        val result = engine.transitionField(field)

        assertEquals(0, result.consecutiveConflictCycles)
    }

    @Test
    fun `transition field with no candidates resets to scanning`() {
        val field = FieldAccumulation(
            fieldName = "origin",
            candidates = emptyMap(),
            status = FieldStatus.LOCKED,
            consecutiveLockCycles = 2,
            consecutiveConflictCycles = 3,
        )

        val result = engine.transitionField(field)

        assertEquals(FieldStatus.SCANNING, result.status)
        assertEquals(0, result.consecutiveLockCycles)
        assertEquals(0, result.consecutiveConflictCycles)
    }

    @Test
    fun `resolved evidence uses consensus source type when multiple source types contribute`() {
        var fields = engine.integrateFrame(emptyMap(), makeFrame(origin = "Ethiopia", frameIndex = 0).copy(side = 0))
        fields = engine.integrateEnrichment(
            currentFields = fields,
            fieldValues = mapOf("origin" to "Ethiopia"),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            frameIndex = 1,
            side = 1,
        )

        repeat(3) {
            fields = engine.runStateMachine(fields, KnownFieldValues.EMPTY)
        }

        val evidence = fields["origin"]!!.resolvedEvidence
        assertNotNull(evidence)
        assertEquals(BagFieldSourceType.CONSENSUS, evidence!!.sourceType)
        assertNull(evidence.side)
    }

    @Test
    fun `resolved evidence uses back side when candidate only appears on back`() {
        var fields = engine.integrateFrame(emptyMap(), makeFrame(origin = "Ethiopia", frameIndex = 0).copy(side = 1))

        repeat(3) {
            fields = engine.runStateMachine(fields, KnownFieldValues.EMPTY)
        }

        assertEquals(BagCaptureSide.BACK, fields["origin"]!!.resolvedEvidence!!.side)
    }

    // --- Quality Gating ---

    @Test
    fun `sharp frame passes quality gate`() {
        val frame = makeFrame(origin = "test", blurScore = 20f, glarePercent = 0.1f)
        assertTrue(engine.framePassesQualityGate(frame))
    }

    @Test
    fun `blurry frame fails quality gate`() {
        val frame = makeFrame(origin = "test", blurScore = 5f, glarePercent = 0.1f)
        assertFalse(engine.framePassesQualityGate(frame))
    }

    @Test
    fun `blurry frame passes relaxed quality gate`() {
        val frame = makeFrame(origin = "test", blurScore = 9f, glarePercent = 0.1f)
        assertFalse(engine.framePassesQualityGate(frame, isRelaxed = false))
        assertTrue(engine.framePassesQualityGate(frame, isRelaxed = true))
    }

    @Test
    fun `frame at exact blur threshold passes quality gate`() {
        val frame = makeFrame(origin = "test", blurScore = config.minBlurScore, glarePercent = 0.1f)

        assertTrue(engine.framePassesQualityGate(frame))
    }

    @Test
    fun `frame at exact glare threshold passes quality gate`() {
        val frame = makeFrame(origin = "test", blurScore = 20f, glarePercent = config.maxGlarePercent)

        assertTrue(engine.framePassesQualityGate(frame))
    }

    @Test
    fun `relaxed mode uses blur relaxation factor correctly`() {
        val relaxedBlur = config.minBlurScore * config.qualityRelaxationFactor
        val passingFrame = makeFrame(origin = "test", blurScore = relaxedBlur, glarePercent = 0.1f)
        val failingFrame = makeFrame(origin = "test", blurScore = relaxedBlur - 0.01f, glarePercent = 0.1f)

        assertTrue(engine.framePassesQualityGate(passingFrame, isRelaxed = true))
        assertFalse(engine.framePassesQualityGate(failingFrame, isRelaxed = true))
    }

    @Test
    fun `relaxed mode divides glare threshold by relaxation factor`() {
        val relaxedGlare = config.maxGlarePercent / config.qualityRelaxationFactor
        val passingFrame = makeFrame(origin = "test", blurScore = 20f, glarePercent = relaxedGlare)
        val failingFrame = makeFrame(origin = "test", blurScore = 20f, glarePercent = relaxedGlare + 0.01f)

        assertTrue(engine.framePassesQualityGate(passingFrame, isRelaxed = true))
        assertFalse(engine.framePassesQualityGate(failingFrame, isRelaxed = true))
    }

    // --- Golden Frame Detection ---

    @Test
    fun `golden frame detection requires high quality`() {
        val goldenFrame = makeFrame(
            origin = "test",
            blurScore = 30f,
            glarePercent = 0.05f,
            textBlockCount = 5,
        )
        assertTrue(engine.isGoldenFrame(goldenFrame))

        val normalFrame = makeFrame(origin = "test", blurScore = 15f, textBlockCount = 1)
        assertFalse(engine.isGoldenFrame(normalFrame))
    }

    @Test
    fun `golden frame passes at exact two times blur threshold`() {
        val frame = FrameResult(
            ocrResult = OcrExtractionResult(origin = "Ethiopia"),
            quality = makeQuality(blurScore = config.minBlurScore * 2f),
            frameIndex = 0,
            timestampMs = System.currentTimeMillis(),
        )

        assertTrue(engine.isGoldenFrame(frame))
    }

    @Test
    fun `golden frame fails when blur criterion is missing`() {
        val frame = FrameResult(
            ocrResult = OcrExtractionResult(origin = "Ethiopia"),
            quality = makeQuality(blurScore = config.minBlurScore * 2f - 0.01f),
            frameIndex = 0,
            timestampMs = System.currentTimeMillis(),
        )

        assertFalse(engine.isGoldenFrame(frame))
    }

    @Test
    fun `golden frame fails when glare is too high`() {
        val frame = FrameResult(
            ocrResult = OcrExtractionResult(origin = "Ethiopia"),
            quality = makeQuality(
                blurScore = config.minBlurScore * 2f,
                glarePercent = config.maxGlarePercent + 0.01f,
            ),
            frameIndex = 0,
            timestampMs = System.currentTimeMillis(),
        )

        assertFalse(engine.isGoldenFrame(frame))
    }

    @Test
    fun `golden frame fails when exposure is poor`() {
        val frame = FrameResult(
            ocrResult = OcrExtractionResult(origin = "Ethiopia"),
            quality = BagCaptureQuality(
                blurScore = config.minBlurScore * 2f,
                glarePercent = 0.05f,
                overexposedPercent = 0.30f,
                underexposedPercent = 0.2f,
                textBlockCount = 3,
                textDetected = true,
            ),
            frameIndex = 0,
            timestampMs = System.currentTimeMillis(),
        )

        assertFalse(engine.isGoldenFrame(frame))
    }

    @Test
    fun `golden frame fails when too few text blocks are present`() {
        val frame = FrameResult(
            ocrResult = OcrExtractionResult(origin = "Ethiopia"),
            quality = makeQuality(
                blurScore = config.minBlurScore * 2f,
                textBlockCount = 2,
            ),
            frameIndex = 0,
            timestampMs = System.currentTimeMillis(),
        )

        assertFalse(engine.isGoldenFrame(frame))
    }

    // --- Extract Field Values ---

    @Test
    fun `extract field values includes all fourteen supported fields`() {
        val extracted = engine.extractFieldValues(
            OcrExtractionResult(
                name = "Geometry",
                roaster = "Onyx",
                origin = "Ethiopia",
                region = "Yirgacheffe",
                farm = "Chelbesa",
                variety = "Heirloom",
                processType = "Washed",
                altitude = "2100 masl",
                tastingNotes = "Tea, Peach",
                roastLevel = "Light",
                roastDate = "2025-01-01",
                expiryDate = "2025-03-01",
                weight = "250 g",
                isDecaf = true,
                fieldConfidence = mapOf(
                    "origin" to BagFieldConfidence.HIGH,
                    "weight" to BagFieldConfidence.MEDIUM,
                    "isDecaf" to BagFieldConfidence.HIGH,
                ),
            ),
        )

        assertEquals(14, extracted.size)
    }

    @Test
    fun `extract field values omits missing fields`() {
        val extracted = engine.extractFieldValues(
            OcrExtractionResult(
                roaster = "Onyx",
                origin = "Ethiopia",
            ),
        )

        assertEquals(setOf("roaster", "origin"), extracted.keys)
    }

    @Test
    fun `extract field values maps provided confidence hints`() {
        val extracted = engine.extractFieldValues(
            OcrExtractionResult(
                origin = "Ethiopia",
                roastDate = "2025-01-01",
                fieldConfidence = mapOf(
                    "origin" to BagFieldConfidence.HIGH,
                    "roastDate" to BagFieldConfidence.MEDIUM,
                ),
            ),
        )

        assertEquals(BagFieldConfidence.HIGH, extracted["origin"]!!.second)
        assertEquals(BagFieldConfidence.MEDIUM, extracted["roastDate"]!!.second)
    }

    @Test
    fun `extract field values defaults missing confidence hints to low`() {
        val extracted = engine.extractFieldValues(
            OcrExtractionResult(
                origin = "Ethiopia",
                variety = "Heirloom",
            ),
        )

        assertEquals(BagFieldConfidence.LOW, extracted["origin"]!!.second)
        assertEquals(BagFieldConfidence.LOW, extracted["variety"]!!.second)
    }

    @Test
    fun `extract field values includes decaf when present`() {
        val extracted = engine.extractFieldValues(
            OcrExtractionResult(
                isDecaf = true,
                fieldConfidence = mapOf("isDecaf" to BagFieldConfidence.HIGH),
            ),
        )

        assertEquals("Decaf", extracted["isDecaf"]!!.first)
        assertEquals(BagFieldConfidence.HIGH, extracted["isDecaf"]!!.second)
    }

    // --- Convergence Scenario ---

    @Test
    fun `full convergence scenario - 15 frames of same bag`() {
        val knownValues = KnownFieldValues(
            origins = listOf("Ethiopia"),
            roasters = listOf("Onyx Coffee Lab"),
        )
        val engineWithKnown = ConsensusEngine(config)

        var fields = emptyMap<String, FieldAccumulation>()
        val variants = listOf(
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab", frameIndex = 0),
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab", frameIndex = 1),
            makeFrame(origin = "Ethopia", roaster = "Onyx Coffee Lab", frameIndex = 2),
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab.", frameIndex = 3),
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab", frameIndex = 4),
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab", frameIndex = 5),
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab", frameIndex = 6),
            makeFrame(origin = "Ethiopia", roaster = "ONYX COFFEE LAB", frameIndex = 7),
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab", frameIndex = 8),
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab", frameIndex = 9),
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab", frameIndex = 10),
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab", frameIndex = 11),
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab", frameIndex = 12),
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab", frameIndex = 13),
            makeFrame(origin = "Ethiopia", roaster = "Onyx Coffee Lab", frameIndex = 14),
        )

        for (frame in variants) {
            fields = engineWithKnown.integrateFrame(fields, frame)
        }

        repeat(3) {
            fields = engineWithKnown.runStateMachine(fields, knownValues)
        }

        val originField = fields["origin"]!!
        val roasterField = fields["roaster"]!!

        assertTrue(
            "Origin should be PROVISIONAL or LOCKED but was ${originField.status}",
            originField.status == FieldStatus.LOCKED || originField.status == FieldStatus.PROVISIONAL,
        )
        assertNotNull("Origin should have resolved value", originField.resolvedValue)

        assertTrue(
            "Roaster should be PROVISIONAL or LOCKED but was ${roasterField.status}",
            roasterField.status == FieldStatus.LOCKED || roasterField.status == FieldStatus.PROVISIONAL,
        )
    }

    // --- Deep Temporal Scenarios ---

    @Test
    fun `wavering bag eventually locks to Ethiopia instead of conflict when typo variants cluster together`() {
        val knownValues = KnownFieldValues(origins = List(6) { "Ethiopia" })
        var fields = emptyMap<String, FieldAccumulation>()

        repeat(20) { i ->
            val waveringValue = if (i % 2 == 0) "Ethiopia" else "Ethopia"
            fields = engine.integrateFrame(fields, makeFrame(origin = waveringValue, frameIndex = i))
            fields = engine.runStateMachine(fields, knownValues)
        }

        repeat(5) { i ->
            fields = engine.integrateFrame(
                fields,
                makeFrame(origin = "Ethiopia", frameIndex = 20 + i, isGolden = true),
            )
            fields = engine.runStateMachine(fields, knownValues)
        }

        val originField = fields["origin"]!!
        assertEquals(1, originField.candidates.size)
        assertEquals(FieldStatus.LOCKED, originField.status)
        assertEquals("ethiopia", originField.resolvedValue)
        assertEquals("Ethiopia", originField.resolvedEvidence!!.value)
        assertTrue(originField.candidates.values.first().rawVariants.contains("Ethopia"))
    }

    @Test
    fun `interleaved front and back side frames accumulate independently across fields`() {
        var fields = emptyMap<String, FieldAccumulation>()

        repeat(6) { i ->
            fields = engine.integrateFrame(
                fields,
                makeRichFrame(
                    origin = "Ethiopia",
                    roaster = "Onyx",
                    frameIndex = i * 2,
                    side = 0,
                ),
            )
            fields = engine.runStateMachine(fields, KnownFieldValues.EMPTY)

            fields = engine.integrateFrame(
                fields,
                makeRichFrame(
                    processType = "Washed",
                    altitude = "1800-2000 masl",
                    frameIndex = i * 2 + 1,
                    side = 1,
                ),
            )
            fields = engine.runStateMachine(fields, KnownFieldValues.EMPTY)
        }

        repeat(3) {
            fields = engine.runStateMachine(fields, KnownFieldValues.EMPTY)
        }

        val originField = fields["origin"]!!
        val roasterField = fields["roaster"]!!
        val processField = fields["processType"]!!
        val altitudeField = fields["altitude"]!!

        assertEquals(FieldStatus.LOCKED, originField.status)
        assertEquals(FieldStatus.LOCKED, roasterField.status)
        assertEquals(FieldStatus.LOCKED, processField.status)
        assertEquals(FieldStatus.LOCKED, altitudeField.status)

        assertEquals(setOf(0), originField.topCandidate!!.sides)
        assertEquals(setOf(0), roasterField.topCandidate!!.sides)
        assertEquals(setOf(1), processField.topCandidate!!.sides)
        assertEquals(setOf(1), altitudeField.topCandidate!!.sides)

        assertEquals(BagCaptureSide.FRONT, originField.resolvedEvidence!!.side)
        assertEquals(BagCaptureSide.FRONT, roasterField.resolvedEvidence!!.side)
        assertEquals(BagCaptureSide.BACK, processField.resolvedEvidence!!.side)
        assertEquals(BagCaptureSide.BACK, altitudeField.resolvedEvidence!!.side)
    }

    @Test
    fun `barcode rescue immediately dominates noisy OCR posterior`() {
        var fields = emptyMap<String, FieldAccumulation>()
        val noisyVariants = listOf(
            "Onix Coffee Lab",
            "Onyx Coffee",
            "Onlyx Coffee Lab",
            "Onyx Coffe Lab",
        )

        repeat(10) { i ->
            fields = engine.integrateFrame(
                fields,
                makeFrame(
                    roaster = noisyVariants[i % noisyVariants.size],
                    frameIndex = i,
                    blurScore = 12f,
                ),
            )
        }

        fields = engine.integrateEnrichment(
            currentFields = fields,
            fieldValues = mapOf("roaster" to "Onyx Coffee Lab"),
            sourceType = BagFieldSourceType.BARCODE_LOOKUP,
            frameIndex = 100,
        )

        repeat(3) {
            fields = engine.runStateMachine(fields, KnownFieldValues.EMPTY)
        }

        val roasterField = fields["roaster"]!!
        val topCandidate = roasterField.topCandidate!!

        assertEquals(FieldStatus.LOCKED, roasterField.status)
        // The enrichment source should be present in the winning candidate's sources
        assertTrue(
            "Top candidate should include BARCODE_LOOKUP source",
            topCandidate.sourceTypes.contains(BagFieldSourceType.BARCODE_LOOKUP),
        )
        // The enrichment's high weight should make this candidate dominate
        assertTrue(topCandidate.qualityWeightedVotes > 20f)
    }

    @Test
    fun `late correction eventually reopens a confirmed lock after sustained counter evidence`() {
        var fields = emptyMap<String, FieldAccumulation>()

        repeat(10) { i ->
            fields = engine.integrateFrame(fields, makeFrame(origin = "Colombia", frameIndex = i))
        }
        repeat(3) {
            fields = engine.runStateMachine(fields, KnownFieldValues.EMPTY)
        }

        repeat(5) { i ->
            fields = engine.integrateFrame(
                fields,
                makeFrame(origin = "Colombia", frameIndex = 100 + i, isGolden = true),
            )
            fields = engine.runStateMachine(fields, KnownFieldValues.EMPTY)
        }

        val afterConfirmation = fields["origin"]!!
        assertEquals(FieldStatus.LOCKED, afterConfirmation.status)
        assertEquals(1f, afterConfirmation.lockScore, 0.01f)

        // Sustained counter-evidence with heavy golden frames for Guatemala
        // Lock decays at 0.15 per cycle — need ceil(1.0/0.15) = 7 cycles to break lock
        // But the top candidate only changes once Guatemala votes exceed Colombia votes,
        // so we need enough frames to shift the posterior too
        repeat(20) { i ->
            fields = engine.integrateFrame(
                fields,
                makeFrame(origin = "Guatemala", frameIndex = 200 + i, isGolden = true),
            )
            fields = engine.runStateMachine(fields, KnownFieldValues.EMPTY)
        }

        val correctedField = fields["origin"]!!
        // Lock should have decayed and field reopened — could be SCANNING, PROVISIONAL,
        // CONFLICT (both candidates strong), or re-LOCKED to Guatemala
        assertTrue(
            "Field should have reopened from original lock (was ${correctedField.status}, lockScore=${correctedField.lockScore})",
            correctedField.lockScore < 1f || correctedField.resolvedValue != "colombia",
        )
        // Guatemala should now be the top candidate
        assertEquals("guatemala", correctedField.topCandidate!!.normalizedValue)
    }

    // --- Deep Invariants ---

    @Test
    fun `random posterior distributions always normalize to one`() {
        val random = Random(1234)
        val variants = listOf("Ethiopia", "Colombia", "Guatemala", "Kenya", "Rwanda", "Panama")

        repeat(50) { cycle ->
            var fields = emptyMap<String, FieldAccumulation>()
            val frameCount = random.nextInt(5, 25)

            repeat(frameCount) { index ->
                fields = engine.integrateFrame(
                    fields,
                    makeFrame(
                        origin = variants.random(random),
                        frameIndex = cycle * 100 + index,
                        blurScore = random.nextDouble(12.0, 100.0).toFloat(),
                    ),
                )
            }

            val posteriors = engine.computePosteriors(fields["origin"]!!.candidates, emptyMap())
            assertEquals(1f, posteriors.values.sum(), 0.01f)
        }
    }

    @Test
    fun `lock score only decreases when counter evidence dethrones the resolved value`() {
        val stableLockedField = fieldWithPosteriors(
            status = FieldStatus.LOCKED,
            resolvedValue = "ethiopia",
            lockScore = 0.8f,
            candidates = linkedMapOf(
                "ethiopia" to 0.92f,
                "colombia" to 0.08f,
            ),
        )

        val stableResult = engine.transitionField(stableLockedField)
        assertEquals(0.8f, stableResult.lockScore, 0.01f)

        val counterEvidence = stableLockedField.copy(
            candidates = linkedMapOf(
                "colombia" to candidateState("colombia", votes = 0.92f, posterior = 0.92f),
                "ethiopia" to candidateState("ethiopia", votes = 0.08f, posterior = 0.08f),
            ),
        )

        val firstDecay = engine.transitionField(counterEvidence)
        val secondDecay = engine.transitionField(firstDecay.copy(candidates = counterEvidence.candidates))

        assertTrue(firstDecay.lockScore < stableLockedField.lockScore)
        assertTrue(secondDecay.lockScore <= firstDecay.lockScore)
    }

    @Test
    fun `candidate eviction keeps highest vote survivors during giant candidate explosion`() {
        val customConfig = config.copy(maxCandidatesPerField = 10)
        val customEngine = ConsensusEngine(customConfig)
        val rankedCandidates = (0 until 50).map { index ->
            farApartOriginValue(index) to (50 - index)
        }

        var fields = emptyMap<String, FieldAccumulation>()
        rankedCandidates.forEachIndexed { valueIndex, (origin, votes) ->
            repeat(votes) { observation ->
                fields = customEngine.integrateFrame(
                    fields,
                    makeFrame(
                        origin = origin,
                        frameIndex = valueIndex * 100 + observation,
                    ),
                )
            }
        }

        val survivors = fields["origin"]!!.candidates.keys
        val expectedTopTen = rankedCandidates.take(10).map { it.first.lowercase() }.toSet()

        assertEquals(customConfig.maxCandidatesPerField, survivors.size)
        assertEquals(expectedTopTen, survivors)
    }

    @Test
    fun `selected medoid is always one of the cluster members`() {
        val random = Random(7)

        repeat(40) {
            val cluster = List(random.nextInt(2, 8)) { farApartOriginValue(random.nextInt(0, 50)) }
            val medoid = engine.selectMedoid(cluster)
            assertTrue(cluster.contains(medoid))
        }
    }

    @Test
    fun `levenshtein distance obeys triangle inequality for random triples`() {
        val random = Random(99)

        repeat(100) {
            val a = randomWord(random)
            val b = randomWord(random)
            val c = randomWord(random)

            val ab = engine.levenshteinDistance(a, b)
            val bc = engine.levenshteinDistance(b, c)
            val ac = engine.levenshteinDistance(a, c)

            assertTrue(ac <= ab + bc)
        }
    }

    @Test
    fun `quality gate admission is deterministic for repeated evaluation of the same frame`() {
        val frame = makeFrame(origin = "Ethiopia", blurScore = 14f, glarePercent = 0.11f)
        val strictDecision = engine.framePassesQualityGate(frame)
        val relaxedDecision = engine.framePassesQualityGate(frame, isRelaxed = true)

        repeat(50) {
            assertEquals(strictDecision, engine.framePassesQualityGate(frame))
            assertEquals(relaxedDecision, engine.framePassesQualityGate(frame, isRelaxed = true))
        }
    }

    @Test
    fun `state machine outputs never contain impossible lock invariants`() {
        val random = Random(55)
        val fieldNames = listOf("origin", "roaster", "variety", "processType")

        repeat(60) {
            val fields = fieldNames.associateWith { fieldName ->
                randomField(fieldName, random)
            }

            val result = engine.runStateMachine(fields, KnownFieldValues.EMPTY)

            result.values.forEach { field ->
                when (field.status) {
                    FieldStatus.PROVISIONAL -> {
                        assertTrue(field.consecutiveLockCycles > 0)
                        assertNotNull(field.resolvedValue)
                    }

                    FieldStatus.LOCKED -> {
                        assertTrue(field.lockScore > 0f)
                        assertNotNull(field.resolvedValue)
                    }

                    FieldStatus.CONFLICT -> assertNotNull(field.runnerUp)
                    else -> Unit
                }
            }
        }
    }

    // --- Adversarial Edge Cases ---

    @Test
    fun `zero quality frame never passes even with relaxed thresholds`() {
        val frame = makeFrame(origin = "Ethiopia", blurScore = 0f, glarePercent = 1f)

        assertFalse(engine.framePassesQualityGate(frame))
        assertFalse(engine.framePassesQualityGate(frame, isRelaxed = true))
    }

    @Test
    fun `extreme threshold config can still lock when posterior is effectively certain`() {
        val strictEngine = ConsensusEngine(config.copy(resolveThreshold = 0.99f, lockCycles = 1))
        var fields = strictEngine.integrateFrame(emptyMap(), makeFrame(origin = "Ethiopia", frameIndex = 0))

        repeat(2) {
            fields = strictEngine.runStateMachine(fields, KnownFieldValues.EMPTY)
        }

        val originField = fields["origin"]!!
        assertEquals(FieldStatus.LOCKED, originField.status)
        assertTrue(originField.topCandidate!!.posteriorProbability >= 0.99f)
    }

    @Test
    fun `trivial threshold still requires a follow up consensus cycle before lock`() {
        val easyEngine = ConsensusEngine(config.copy(resolveThreshold = 0.01f, lockCycles = 1))
        var fields = easyEngine.integrateFrame(emptyMap(), makeFrame(origin = "Ethiopia", frameIndex = 0))

        fields = easyEngine.runStateMachine(fields, KnownFieldValues.EMPTY)
        assertEquals(FieldStatus.PROVISIONAL, fields["origin"]!!.status)

        fields = easyEngine.runStateMachine(fields, KnownFieldValues.EMPTY)
        assertEquals(FieldStatus.LOCKED, fields["origin"]!!.status)
    }

    // --- Helpers ---

    private fun makeFrame(
        origin: String? = null,
        roaster: String? = null,
        variety: String? = null,
        processType: String? = null,
        frameIndex: Int = 0,
        blurScore: Float = 20f,
        glarePercent: Float = 0.05f,
        textBlockCount: Int = 3,
        isGolden: Boolean = false,
    ): FrameResult {
        val confidence = mutableMapOf<String, BagFieldConfidence>()
        origin?.let { confidence["origin"] = BagFieldConfidence.MEDIUM }
        roaster?.let { confidence["roaster"] = BagFieldConfidence.MEDIUM }
        variety?.let { confidence["variety"] = BagFieldConfidence.MEDIUM }
        processType?.let { confidence["processType"] = BagFieldConfidence.MEDIUM }

        return FrameResult(
            ocrResult = OcrExtractionResult(
                origin = origin,
                roaster = roaster,
                variety = variety,
                processType = processType,
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

    private fun makeRichFrame(
        origin: String? = null,
        roaster: String? = null,
        processType: String? = null,
        altitude: String? = null,
        frameIndex: Int = 0,
        side: Int = 0,
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
            quality = makeQuality(),
            frameIndex = frameIndex,
            timestampMs = System.currentTimeMillis(),
            side = side,
        )
    }

    private fun buildDominantField(
        fieldName: String,
        dominantValue: String,
        dominantObs: Int,
        minorityObs: Int,
        existingFields: Map<String, FieldAccumulation> = emptyMap(),
    ): Map<String, FieldAccumulation> {
        var fields = existingFields
        repeat(dominantObs) { i ->
            val frame = when (fieldName) {
                "origin" -> makeFrame(origin = dominantValue, frameIndex = i)
                "roaster" -> makeFrame(roaster = dominantValue, frameIndex = i)
                else -> makeFrame(origin = dominantValue, frameIndex = i)
            }
            fields = engine.integrateFrame(fields, frame)
        }
        repeat(minorityObs) { i ->
            val frame = when (fieldName) {
                "origin" -> makeFrame(origin = "minority_value", frameIndex = dominantObs + i)
                "roaster" -> makeFrame(roaster = "minority_value", frameIndex = dominantObs + i)
                else -> makeFrame(origin = "minority_value", frameIndex = dominantObs + i)
            }
            fields = engine.integrateFrame(fields, frame)
        }
        return fields
    }

    private fun buildEvenField(
        fieldName: String,
        value1: String,
        value2: String,
        obsEach: Int,
    ): Map<String, FieldAccumulation> {
        var fields = emptyMap<String, FieldAccumulation>()
        repeat(obsEach) { i ->
            val f1 = when (fieldName) {
                "origin" -> makeFrame(origin = value1, frameIndex = i * 2)
                else -> makeFrame(origin = value1, frameIndex = i * 2)
            }
            val f2 = when (fieldName) {
                "origin" -> makeFrame(origin = value2, frameIndex = i * 2 + 1)
                else -> makeFrame(origin = value2, frameIndex = i * 2 + 1)
            }
            fields = engine.integrateFrame(fields, f1)
            fields = engine.integrateFrame(fields, f2)
        }
        return fields
    }

    private fun makeQuality(
        blurScore: Float = 20f,
        glarePercent: Float = 0.05f,
        textBlockCount: Int = 3,
    ): BagCaptureQuality {
        return BagCaptureQuality(
            blurScore = blurScore,
            glarePercent = glarePercent,
            overexposedPercent = 0.1f,
            underexposedPercent = 0.2f,
            textBlockCount = textBlockCount,
            textDetected = true,
        )
    }

    private fun candidateState(
        normalizedValue: String,
        votes: Float,
        posterior: Float = 0f,
        rawVariants: List<String> = listOf(normalizedValue.replaceFirstChar { it.uppercase() }),
        sourceTypes: Set<BagFieldSourceType> = setOf(BagFieldSourceType.OCR),
        sides: Set<Int> = setOf(0),
        confidence: BagFieldConfidence = BagFieldConfidence.MEDIUM,
    ): CandidateState {
        return CandidateState(
            normalizedValue = normalizedValue,
            rawVariants = rawVariants,
            posteriorProbability = posterior,
            qualityWeightedVotes = votes,
            observationCount = rawVariants.size,
            lastSeenFrameIndex = 0,
            sourceTypes = sourceTypes,
            bestConfidenceHint = confidence,
            sides = sides,
        )
    }

    private fun fieldWithPosteriors(
        fieldName: String = "origin",
        status: FieldStatus,
        candidates: LinkedHashMap<String, Float>,
        resolvedValue: String? = null,
        lockScore: Float = 0f,
        consecutiveLockCycles: Int = 0,
        consecutiveConflictCycles: Int = 0,
    ): FieldAccumulation {
        return FieldAccumulation(
            fieldName = fieldName,
            candidates = candidates.mapValues { (key, posterior) ->
                candidateState(
                    normalizedValue = key,
                    votes = posterior,
                    posterior = posterior,
                )
            },
            status = status,
            lockScore = lockScore,
            consecutiveLockCycles = consecutiveLockCycles,
            consecutiveConflictCycles = consecutiveConflictCycles,
            resolvedValue = resolvedValue,
        )
    }

    private fun fieldWithVotes(
        fieldName: String,
        status: FieldStatus,
        votes: LinkedHashMap<String, Float>,
        resolvedValue: String? = null,
        lockScore: Float = 0f,
        consecutiveLockCycles: Int = 0,
        consecutiveConflictCycles: Int = 0,
    ): FieldAccumulation {
        return FieldAccumulation(
            fieldName = fieldName,
            candidates = votes.mapValues { (key, value) -> candidateState(key, votes = value) },
            status = status,
            lockScore = lockScore,
            consecutiveLockCycles = consecutiveLockCycles,
            consecutiveConflictCycles = consecutiveConflictCycles,
            resolvedValue = resolvedValue,
        )
    }

    private fun randomField(fieldName: String, random: Random): FieldAccumulation {
        val candidateCount = random.nextInt(1, 4)
        val weightedCandidates = LinkedHashMap<String, Float>()
        repeat(candidateCount) { index ->
            val value = "${fieldName}_${index}_${random.nextInt(100)}"
            weightedCandidates[value] = random.nextDouble(0.2, 5.0).toFloat()
        }

        val orderedCandidates = weightedCandidates.entries.sortedByDescending { it.value }
        val topKey = orderedCandidates.first().key
        val runnerUpKey = orderedCandidates.getOrNull(1)?.key

        return when (random.nextInt(4)) {
            0 -> fieldWithVotes(
                fieldName = fieldName,
                status = FieldStatus.SCANNING,
                votes = LinkedHashMap(weightedCandidates),
            )

            1 -> {
                val candidates = linkedMapOf(topKey to 0.9f)
                runnerUpKey?.let { candidates[it] = 0.1f }
                fieldWithPosteriors(
                    fieldName = fieldName,
                    status = FieldStatus.PROVISIONAL,
                    candidates = candidates,
                    resolvedValue = topKey,
                    consecutiveLockCycles = 1,
                )
            }

            2 -> {
                val candidates = linkedMapOf(topKey to 0.92f)
                runnerUpKey?.let { candidates[it] = 0.08f }
                fieldWithPosteriors(
                    fieldName = fieldName,
                    status = FieldStatus.LOCKED,
                    candidates = candidates,
                    resolvedValue = topKey,
                    lockScore = 0.7f,
                )
            }

            else -> {
                val candidates = linkedMapOf(topKey to 0.55f)
                candidates[runnerUpKey ?: "${fieldName}_runner"] = 0.45f
                fieldWithPosteriors(
                    fieldName = fieldName,
                    status = FieldStatus.CONFLICT,
                    candidates = candidates,
                    consecutiveConflictCycles = 2,
                )
            }
        }
    }

    private fun farApartOriginValue(index: Int): String {
        val symbol = ('A'.code + (index % 26)).toChar()
        return "${symbol.toString().repeat(8)}-${index.toString(36).uppercase()}"
    }

    private fun randomWord(random: Random): String {
        val length = random.nextInt(3, 10)
        val alphabet = ('a'..'z').toList()
        return buildString(length) {
            repeat(length) {
                append(alphabet[random.nextInt(alphabet.size)])
            }
        }
    }
}
