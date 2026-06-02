package com.adsamcik.starlitcoffee.data.network.llm

import android.content.Context
import com.adsamcik.starlitcoffee.scan.model.FieldSource
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.mindlayer.sdk.ConnectionState
import com.adsamcik.mindlayer.sdk.InferenceBackend
import com.adsamcik.mindlayer.sdk.InferenceHandle
import com.adsamcik.mindlayer.sdk.Mindlayer
import com.adsamcik.mindlayer.sdk.MindlayerException
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val MINDLAYER_LLM_TAG = "MindlayerLlm"

/**
 * LLM inference provider that uses the Mindlayer on-device LLM service.
 *
 * # Architecture: OCR-first, text-only LLM
 *
 * Coffee bag photos are recognised by Mindlayer's PaddleOCR pipeline
 * (`MindlayerOcrService`) into raw text BEFORE this provider is invoked. This
 * class then runs Gemma 4 as a **text-only** structured-extraction step over
 * the merged OCR text — no image is sent to the LLM. The flow is:
 *
 *   camera/gallery → crop/deskew/denoise (ImagePreprocessor)
 *                 → PaddleOCR / PP-OCRv5 (Mindlayer.ocr)
 *                 → Gemma 4 E2B text-only (this provider, here)
 *                       └── interpret, correct, classify, structure
 *
 * Why text-only:
 *  - Sidesteps LiteRT-LM #2028 (Gemma 4 + CPU + isolated process →
 *    SIGSEGV in `liblitertlm_jni.so` on the second multimodal inference).
 *  - 5–10× faster: Gemma 4 vision tokenises each image to ~256 tokens and
 *    runs a vision encoder pass that dominates latency on CPU/emulator;
 *    text-only inference is single-digit seconds in the warm case.
 *  - Smaller context budget: no vision tokens compete with prompt + schema.
 *  - Better accuracy on small/known fields PaddleOCR has already nailed
 *    (origin, roaster, weight) — the LLM gets to verify and structure
 *    rather than re-recognise pixels.
 *
 * Each extraction runs as a stateless one-shot via the v1 canonical
 * builder `mindlayer.infer { ephemeralSession; text; outputText() }`: no
 * conversation history is carried over between bags, so the model never sees
 * a previous scan's text or response. This is essential for field-extraction
 * correctness — the SDK would otherwise accumulate turns in its HistoryStore
 * and replay them as context.
 *
 * Sampling is tuned for deterministic JSON (low temperature, narrow topK/topP)
 * rather than chat defaults (0.7 / 40 / 0.95).
 *
 * No cloud, no API keys, fully private, works offline.
 */
class MindlayerLlmInferenceProvider(
    context: Context,
) : LlmInferenceProvider {

    private val mindlayer: Mindlayer = Mindlayer.connect(context.applicationContext)
    @Volatile
    private var connectionFailed: Boolean = false

    override fun isAvailable(): Boolean {
        val state = mindlayer.connectionState.value
        val isConnectingOrConnected = state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
        if (isConnectingOrConnected) {
            connectionFailed = false
        }
        return isConnectingOrConnected || connectionFailed
    }

    override suspend fun extractBagFields(
        request: LlmExtractionRequest,
    ): LlmExtractionResult = withContext(Dispatchers.IO) {
        // Run the entire extraction off the main thread:
        //  * The Mindlayer SDK does not switch dispatchers internally — every
        //    binder call and per-token flow resumption runs on the caller's
        //    dispatcher. When called from viewModelScope.launch (Main), this
        //    would otherwise back up ML Kit completion callbacks and delay
        //    ImageProxy.close() on the analyzer thread, visibly freezing the
        //    camera preview while the model is generating tokens.
        // Boundary catch: Mindlayer's binder service can fail with various
        // service-disconnection or remote exceptions; the SDK surfaces them
        // via `awaitConnected`. Treat any non-cancellation throwable as
        // service-unavailable so the consensus engine falls back gracefully.
        @Suppress("TooGenericExceptionCaught")
        try {
            awaitMindlayerConnected()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext LlmExtractionResult.Unavailable(
                "Mindlayer service not available: ${e.message}",
            )
        }

        // Text-only architecture: the LLM never sees the image. If the OCR
        // pipeline produced no text (blank label, dark photo, OCR failure),
        // there is nothing for the LLM to interpret — degrade to Unavailable
        // so the consensus engine surfaces a useful retry path rather than
        // asking the model to hallucinate.
        if (request.rawOcrText.isNullOrBlank()) {
            return@withContext LlmExtractionResult.Unavailable(
                "No OCR text available for LLM extraction (text-only mode)",
            )
        }

        // Pre-warm the engine with the safe (CPU) backend BEFORE
        // `mindlayer.infer { ephemeralSession { ... } }` triggers `createSession`.
        // `createSession` doesn't take a backend hint, so on a cold service
        // process the engine init falls back to the service-side default —
        // historically GPU, which the emulator's software GPU SIGSEGVs on
        // during LiteRT-LM's `nativeCreateEngine` log-formatting. Calling
        // `prewarm(CPU)` first locks in the safe backend; if the engine was
        // already loaded with a different backend, `prewarm` is a no-op.
        // Errors here are non-fatal — they may mean the service is briefly
        // unavailable; the actual `infer` call below will surface a clean
        // `Failed` if connection is genuinely broken.
        @Suppress("TooGenericExceptionCaught")
        try {
            mindlayer.prewarm(PREWARM_BACKEND)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort — fall through to inference and let the
            // streaming infer call surface a meaningful failure if the
            // service is truly down.
        }

        val prompt = buildExtractionPrompt(request)
        val extended = useExtendedSchema(request)

        // Boundary catch on the generic `Exception` branch: model inference
        // can throw anything from JSON parsing failures to native crashes;
        // mapping all of them to a retryable Failed lets the consensus
        // engine try again on the next golden frame.
        @Suppress("TooGenericExceptionCaught")
        try {
            // Stateless one-shot via the v1 canonical builder — a fresh
            // ephemeral session runs the inference and is torn down after.
            // No history carries over to the next extraction. Structured
            // output is requested through the prompt-embedded schema
            // (PromptAndValidate); the v1 infer{} builder has no extraContext
            // envelope, and parseResponse lenient-parses the JSON it returns.
            //
            // Text-only: no `image(...)` input. The OCR text travels in the
            // prompt itself, escaped via the triple-quote block in
            // `buildExtractionPrompt`.
            val responseText = withTimeout(EXTRACTION_TIMEOUT_MS) {
                val handle = mindlayer.infer {
                    ephemeralSession {
                        systemPrompt = buildSystemPrompt(extended)
                        maxTokens = MAX_TOKENS
                    }
                    text(prompt)
                    sampling {
                        temperature = EXTRACTION_TEMPERATURE
                        topK = EXTRACTION_TOP_K
                        topP = EXTRACTION_TOP_P
                    }
                    outputText()
                }
                (handle as InferenceHandle.Text).awaitText()
            }
            android.util.Log.d(
                MINDLAYER_LLM_TAG,
                "LLM inference complete: ${responseText.length} chars",
            )
            if (com.adsamcik.starlitcoffee.BuildConfig.DEBUG) {
                logLongDebug("LLM prompt (debug)", prompt)
                logLongDebug("LLM response (debug)", responseText)
            }
            parseResponse(responseText, request.fieldsNeeded)
        } catch (_: TimeoutCancellationException) {
            LlmExtractionResult.Failed(
                "Inference timed out after ${EXTRACTION_TIMEOUT_MS / 1000}s",
                retryable = true,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancellation must propagate so callers (e.g. LlmEscalationCoordinator)
            // know the work was abandoned and don't apply stale results to a
            // session that has already been torn down.
            throw e
        } catch (e: MindlayerException) {
            LlmExtractionResult.Failed(
                "Inference failed: ${e.message}",
                retryable = e.codeName != "UNSUPPORTED_TOOL_CALL",
            )
        } catch (e: Exception) {
            LlmExtractionResult.Failed("Inference failed: ${e.message}", retryable = true)
        }
    }

    /** Release resources. Call when the provider is no longer needed. */
    fun close() {
        mindlayer.disconnect()
    }

    /**
     * Pre-warm: connect to the Mindlayer service AND load the inference engine
     * so the first extraction doesn't pay the 5–10 s engine init cost.
     *
     * Runs on [Dispatchers.IO] — `awaitConnected` performs a binder transaction
     * and `prewarm` blocks until the engine is loaded; both must stay off the
     * main thread to avoid freezing the camera preview during the first scan.
     */
    override suspend fun prewarm() {
        withContext(Dispatchers.IO) {
            awaitMindlayerConnected()
            mindlayer.prewarm(PREWARM_BACKEND)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun awaitMindlayerConnected() {
        try {
            mindlayer.awaitConnected(kotlin.time.Duration.INFINITE)
            connectionFailed = false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            connectionFailed = true
            throw e
        }
    }

    /**
     * Log a multi-line / potentially large string in 3-4 KB chunks so logcat's
     * ~4 KB per-message ceiling doesn't truncate the diagnostic dump. Only
     * called from the debug-build branch above.
     */
    private fun logLongDebug(label: String, payload: String) {
        val maxChunk = 3500
        if (payload.length <= maxChunk) {
            android.util.Log.d(MINDLAYER_LLM_TAG, "$label: $payload")
            return
        }
        val chunks = (payload.length + maxChunk - 1) / maxChunk
        payload.chunked(maxChunk).forEachIndexed { index, chunk ->
            android.util.Log.d(MINDLAYER_LLM_TAG, "$label (${index + 1}/$chunks): $chunk")
        }
    }

    /**
     * Backwards-compatible alias for [prewarm]. Older call sites used this
     * name before the interface gained a [prewarm] hook; kept for any
     * external test that still references it directly.
     */
    @Deprecated("Use prewarm() instead", ReplaceWith("prewarm()"))
    suspend fun ensureConnected() = prewarm()

    companion object {
        /**
         * Max tokens per extraction.
         *
         * Sized for the 14-field extended schema with verbose tasting notes
         * and roastery text in any language. The earlier 2048 budget hit
         * "Failed to parse LLM response as JSON: ... 'EOF' instead at path"
         * mid-field on real Czech-language Nordbeans bags where the model
         * generated multi-sentence "uncertain" explanations for
         * `processType` / `tastingNotes` before circling back to the rest
         * of the schema. 4096 matches the engine's `maxMaxTokens`
         * (`MemoryBudget.deviceTier.maxMaxTokens`) so we always have the
         * full headroom the engine can give us.
         */
        private const val MAX_TOKENS = 4096

        /** Low temperature for deterministic structured JSON output. */
        private const val EXTRACTION_TEMPERATURE = 0.1f

        /** Narrow topK — JSON schema is rigid; we don't want creative tokens. */
        private const val EXTRACTION_TOP_K = 20

        /** Tight topP for structured output. */
        private const val EXTRACTION_TOP_P = 0.9f

        /**
         * Preferred backend for prewarm.
         *
         * **CPU was chosen over GPU intentionally for the bag-scan path.**
         * The emulator's software GPU is unreliable for Gemma 4 E2B init —
         * LiteRT-LM's GPU initialiser SIGSEGVs roughly half the time on the
         * Android x86_64 SDK emulator during `nativeCreateEngine`, taking the
         * `:ml` service process down with it. That's a fresh-process crash
         * that the [EngineRestartStore] / process-restart workaround cannot
         * recover from in the same scan session, because:
         *   1. The first scan's prewarm hits the SIGSEGV → process dies.
         *   2. The reconnect lands in the rate-limit window and gets
         *      `MLERR:5002` for the next ~60 s.
         *   3. By the time rate-limit clears, the scan has already failed
         *      and surfaced "Inference failed: null" to the user.
         *
         * CPU init takes ~5–10 s longer on the first cold load (~45 s vs
         * ~35 s on this emulator) but never crashes. Subsequent extractions
         * stay warm. On real devices the GPU path is healthy; if a future
         * SDK adds an `AUTO` backend or per-feature health caching, switch
         * back. Tracked alongside LiteRT-LM #1686 / #2028.
         */
        private val PREWARM_BACKEND = InferenceBackend.CPU

        /**
         * Bounded generation time so a wedged model cannot hang a scan forever.
         *
         * Sized for text-only Gemma 4 E2B inference (no vision encoder pass).
         * Typical warm-pass latency on CPU emulator: 10–30 s for the schema +
         * extraction. First-call cold init adds the engine warmup cost
         * separately (handled by Mindlayer's [Mindlayer.prewarm]), so this
         * budget covers the inference itself plus a small grace margin.
         */
        internal const val EXTRACTION_TIMEOUT_MS = 90_000L

        internal fun buildSystemPrompt(extended: Boolean = false): String =
            if (extended) SYSTEM_PROMPT_14 else SYSTEM_PROMPT_10

        /**
         * Decide whether to request the extended 14-field schema.
         *
         * Eval shows that without OCR or existing-field grounding, the extended
         * schema **regresses** (extra fields → more confabulation on sparse
         * labels). We only opt into it when there is grounding context that
         * lets the model anchor the extra fields (region/farm/expiryDate/isDecaf).
         */
        internal fun useExtendedSchema(request: LlmExtractionRequest): Boolean =
            !request.rawOcrText.isNullOrBlank() || request.existingFields.isNotEmpty()

        private val SYSTEM_PROMPT_10 = """
You are a coffee bag label analyzer. The user has run OCR on a coffee bag
label photo. You receive the raw OCR text (which may contain recognition
errors, line breaks, and noise) and you must extract structured fields.

For each field, report your confidence:
- "found": The OCR text clearly contains this value
- "uncertain": The OCR text hints at this but is garbled, partial, or ambiguous
- "not_visible": The OCR text does not contain enough information for this field

Response format (JSON only, no markdown):
{
  "fields": {
    "name": {"value": "Yirgacheffe", "status": "found"},
    "roaster": {"value": "Counter Culture", "status": "found"},
    "origin": {"value": "Ethiopia", "status": "found"},
    "variety": {"value": null, "status": "not_visible"},
    "process": {"value": "Washed", "status": "uncertain"},
    "roastLevel": {"value": null, "status": "not_visible"},
    "tastingNotes": {"value": "blueberry, jasmine, citrus", "status": "found"},
    "altitude": {"value": "1900-2100 masl", "status": "found"},
    "weight": {"value": "340g", "status": "found"},
    "roastDate": {"value": null, "status": "not_visible"}
  }
}

Multilingual rules (apply BEFORE everything else):
- The OCR text may be in ANY language. Read it in its source language and use your knowledge of coffee terminology in that language to understand what each token means. Do not enumerate languages — handle whatever you receive.
- Output CONCEPT fields in canonical ENGLISH regardless of the source language:
  * `origin`: the English country name (e.g. "Colombia", "Ethiopia", "Kenya"). Translate from any source language.
  * `process`: the English coffee-industry term (e.g. "Washed", "Natural", "Honey", "Anaerobic", "Wet-hulled", "Carbonic Maceration", "Sugarcane EA Decaf", "Swiss Water Decaf", "CO2 Decaf"). Translate from any source language.
  * `roastLevel`: the English roast term (e.g. "Light", "Medium", "Dark", "Filter", "Espresso", "Omni"). Translate from any source language.
  * `tastingNotes`: translate flavour descriptors to English, lowercase, comma-separated.
- Keep PROPER-NOUN fields VERBATIM in their original spelling — brand and identity strings must not be translated:
  * `name`: blend / product name.
  * `roaster`: company / brand name.
- Structural fields use universal formats: `roastDate` as YYYY-MM-DD when possible; `weight` in its native unit (metric preferred); `altitude` as the number range plus unit.

Rules:
- Use "not_visible" when the OCR text does not contain the information. Never guess.
- Use "uncertain" when the OCR characters are garbled, partial, or ambiguous, OR when you translated a non-English source.
- Use "found" only when you can clearly read or determine the value from the OCR text.
- name vs roaster: `name` is the product / blend; `roaster` is the company brand. Do not confuse the two.
- Universal field-type guards (apply regardless of language):
  * Words that describe a coffee PROCESS (the local-language word for "washed", "natural", "honey", "anaerobic", "wet-hulled", etc.) or a ROAST ACTION (the local-language word for "roasted", "roast", "roasting", etc.) are NEVER `origin`, `name`, or `roaster`. They describe how the coffee was made or roasted, never where it came from or what it's called.
  * Words that describe BEAN FORM (the local-language word for "beans", "whole bean", "ground", etc.) are NEVER `process`. Emit `not_visible` for `process` if only bean-form words are present.
  * Measurement and date strings (numbers + a unit like "m", "g", "kg", "%", or a date in any format) are NEVER `name`, `roaster`, or `origin`. Emit `not_visible` if no real proper-noun value is present.
- Correct obvious OCR errors only when the intended word is unambiguous from context.
- Respond with ONLY a JSON object. No markdown fences or explanation.
""".trimIndent()

        private val SYSTEM_PROMPT_14 = """
You are a coffee bag label analyzer. The user has run OCR on a coffee bag
label photo. You receive the raw OCR text (which may contain recognition
errors, line breaks, and noise) and you must extract structured fields.

The OCR text may be split into labeled sections — `--- FRONT ---` for
the bag's front face, `--- BACK ---` for the back face. When both
sections are present:
- the FRONT typically carries the brand / name / origin / variety /
  tasting-note text (marketing face);
- the BACK typically carries the structured metadata strip — roast date,
  expiry date, weight, batch number / EAN, process method, altitude.
- **Negative constraint for proper-noun fields**: field LABELS that
  precede metadata values on the BACK ("Datum pražení", "Hmotnost",
  "Métode", "Mindestens haltbar bis", "Nadmořská výška", "Odrůda
  kávovníku", "Chuťový profil", "Zubereitung", "Roast date", "Weight",
  "Altitude", "Variety", "Process", "Best before") are NOT proper
  nouns — they MUST NEVER be extracted as `name`, `roaster`, or `farm`,
  even when the FRONT's proper-noun text is OCR-garbled and the BACK's
  label text looks like a clean string.
- **Multi-page cross-reference for proper nouns**: FRONT and BACK
  are two photos of the same physical bag. When the same brand or
  product name appears in MULTIPLE OCR variants across the two faces
  (cursive logo on the front OCR'd differently than the printed
  sticker on the back, or printed-and-mashed-together with adjacent
  labels), treat them all as evidence of one intended word. Pick
  the canonical spelling most likely to be a real brand or product,
  not the most-frequent OCR variant. This applies to `name` /
  `roaster` / `farm` only — the structured fields (weight, dates,
  process, roastLevel, altitude, isDecaf) follow the type-specific
  rules below.
- **Decaf is NOT a process value**: `processType` lists what was done
  to the green coffee (Washed / Natural / Honey / Anaerobic /
  Sugarcane EA Decaf / Swiss Water Decaf / CO2 Decaf / etc.). The
  bare word "Decaf" / "Decaffeinated" (in any language) sets
  `isDecaf: true` but never `processType: "Decaf"`. If the OCR has
  both a primary process word AND a decaf marker, use the primary
  process for `processType` and set `isDecaf: true` separately. Only
  emit a decaf-process string (e.g. "Sugarcane EA Decaf") when the
  OCR explicitly names the decaffeination method AND no primary
  process is present.
- **Roast and expiry dates are different**: `roastDate` is when the
  beans were roasted; `expiryDate` is best-before / minimum
  durability. When both are labeled on the BACK (a roast-date label
  vs a best-before / minimum-durability label, in whatever language
  the bag uses), read each label's value. Never copy `roastDate`
  into `expiryDate` — if only the roast date is visible, emit
  `expiryDate: null, status: "not_visible"`.
- **CONCEPT fields are still translated regardless of source side**:
  `origin`, `region`, `process`, `roastLevel`, `variety`, `tastingNotes`
  follow the multilingual rules below even when the only candidate
  appears on the BACK — Czech "KOLUMBIE" on the back is still
  `origin: "Colombia"`, not `origin: "KOLUMBIE"`. The front-vs-back
  routing applies to WHICH token is the candidate, not to whether the
  translation rule fires.
Use this routing when a token appears in only one section. When sections
are absent, treat the entire input as one face and apply the rules below.

For each field, report your confidence:
- "found": The OCR text clearly contains this value
- "uncertain": The OCR text hints at this but is garbled, partial, or ambiguous
- "not_visible": The OCR text does not contain enough information for this field

Response format (JSON only, no markdown):
{
  "fields": {
    "name":         {"value": "Yirgacheffe",           "status": "found"},
    "roaster":      {"value": "Counter Culture",       "status": "found"},
    "origin":       {"value": "Ethiopia",              "status": "found"},
    "region":       {"value": "Yirgacheffe",           "status": "found"},
    "farm":         {"value": null,                    "status": "not_visible"},
    "variety":      {"value": null,                    "status": "not_visible"},
    "process":      {"value": "Washed",                "status": "uncertain"},
    "roastLevel":   {"value": null,                    "status": "not_visible"},
    "tastingNotes": {"value": "blueberry, jasmine",    "status": "found"},
    "altitude":     {"value": "1900-2100 masl",        "status": "found"},
    "weight":       {"value": "340g",                  "status": "found"},
    "roastDate":    {"value": "2026-03-01",            "status": "found"},
    "expiryDate":   {"value": "2026-09-01",            "status": "found"},
    "isDecaf":      {"value": false,                   "status": "found"}
  }
}

Multilingual rules (apply BEFORE field notes):
- The OCR text may be in ANY language. Read it in its source language and use your knowledge of coffee terminology in that language to understand what each token means. Do not enumerate languages — handle whatever you receive.
- Output CONCEPT fields in canonical ENGLISH regardless of the source language:
  * `origin`: the English country name (e.g. "Colombia", "Ethiopia", "Kenya"). Translate the country from any source language to its standard English form.
  * `region`: the standard English transliteration when one exists (e.g. "Yirgacheffe", "Huila", "Nariño"); otherwise the local spelling.
  * `process`: the English coffee-industry term — "Washed", "Natural" (or "Dry"), "Honey" (with colour qualifier when present: "Red Honey", "Yellow Honey", "Black Honey", "White Honey"), "Anaerobic", "Semi-washed", "Wet-hulled" (or "Giling Basah"), "Carbonic Maceration", "Sugarcane EA Decaf", "Swiss Water Decaf", "CO2 Decaf". Translate the source-language process word to its English equivalent.
  * `roastLevel`: the English roast term — "Light", "Medium", "Dark", "Filter", "Espresso", "Omni". Translate from any source language.
  * `variety`: the standard cultivar name (e.g. "Heirloom", "Bourbon", "Geisha", "Caturra", "Catuai", "SL28", "SL34", "Pacamara", "Maragogype", "Typica"). Cultivar names are essentially language-independent — use the standard form.
  * `tastingNotes`: translate flavour descriptors to English. Keep them lowercase, comma-separated.
- Keep PROPER-NOUN fields VERBATIM in their original spelling — these are brand and identity strings that must not be translated:
  * `name`: blend / product name. If OCR garbled it but the intended local-language spelling is recoverable, output the recovered local-language spelling. Never translate.
  * `roaster`: company / brand name. Never translate, never substitute the country or product name. Trademarked brand names stay verbatim.
  * `farm`: estate / cooperative name. Keep the local spelling.
- Structural fields use universal formats:
  * `roastDate` / `expiryDate`: emit as YYYY-MM-DD whenever the source format makes a full date recoverable. Common source formats include DD.MM.YYYY, MM/YYYY (no day → YYYY-MM), YYYY/MM/DD, and best-before/use-by phrases (strip the phrase, keep the date). When only a month + year are present, emit YYYY-MM.
  * `weight`: native unit as written (metric preferred: 250g, 1kg, 340g; ounces when the bag is imperial-only).
  * `altitude`: the number range plus unit, ASCII when possible (e.g. "1400-2100m", "1900 masl").
  * `isDecaf`: boolean. true when the OCR text explicitly contains a decaffeination marker in ANY language (the local-language word for "decaf" / "decaffeinated", or a named decaffeination process like "Sugarcane EA Decaf", "Swiss Water Decaf", "CO2 Decaf"). false when clearly regular / caffeinated. not_visible otherwise.

Field notes:
- name vs roaster — separating the bag's PRODUCT designation from the COMPANY:
  * **name** = the BAG-SPECIFIC product designation that distinguishes
    this bag from other coffees by the same roaster. Often a product
    descriptor combining origin / variety / process / decaf state (e.g.
    "Tumbaga Decaf", "Yirgacheffe Natural", "Sidamo G1"), or a named
    blend ("Wildkaffee", "Espresso Blend"), or just the famous origin
    region when the roaster sells it under that name ("Yirgacheffe").
    NOT the roaster's company name.
  * **roaster** = the COMPANY that roasted the coffee. Usually a brand
    logo on the front and the legal entity on the back's contact info.
    A single word or short brand phrase.
  * **When both a brand logo AND a product descriptor sticker are on
    the front** (very common pattern): the logo word goes in `roaster`,
    the descriptor sticker text goes in `name`. Do not put the logo
    word in `name` just because it's the most prominent visually — the
    LOGO is the brand. Example: a bag with a large cursive "Acme"
    logo + a small "Sidamo Natural" sticker on the front has
    `name = "Sidamo Natural"`, `roaster = "Acme"`. If OCR garbled
    the cursive logo (e.g. "Acne" instead of "Acme") and a cleaner
    derivative of the same word appears on the back's contact info,
    prefer the cleaner spelling for `roaster` per the multi-page
    cross-reference rule above.
- processType: ONLY actual processing methods. Do NOT use grind / form descriptors (the local-language word for "beans", "whole bean", "ground", etc.) — those are bean form, not process. When the OCR text only contains bean-form words, emit `not_visible` for processType.

Universal field-type guards (apply regardless of language — these prevent process / roast verbs and measurements from leaking into proper-noun fields when OCR is noisy):
- Words that describe a coffee PROCESS (the local-language word for "washed", "natural", "honey", "anaerobic", "wet-hulled", "carbonic maceration", etc.) or a ROAST ACTION (the local-language word for "roasted", "roast", "roasting", etc.) and their OCR-garbled variants are NEVER `origin`, `region`, `farm`, `name`, or `roaster`. If the only candidate for one of those fields is such a verb, emit `not_visible` instead.
- Measurement and date strings (numbers + a unit like "m", "g", "kg", "%", or a date in any format) are NEVER `name`, `roaster`, `origin`, `region`, or `farm`. Emit `not_visible` for those fields if no real proper-noun value is present.

Rules:
- Use "not_visible" when the OCR text does not contain the information. Never guess.
- Use "uncertain" when the OCR characters are garbled, partial, or ambiguous, OR when you translated a non-English source.
- Use "found" only when you can clearly read or determine the value from the OCR text.
- Correct obvious OCR errors only when the intended word is unambiguous from context (e.g. "FESAUVAGE" → "CAFÉ SAUVAGE", "TUNBAGA" → "Tumbaga").
- Respond with ONLY a JSON object. No markdown fences or explanation.
""".trimIndent()

        /**
         * Build the `extraContextJson` payload requesting structured output
         * from the Mindlayer service. Uses the PROMPT_AND_VALIDATE strategy —
         * safer than TOOL_ROUTING when multimodal (image) inputs are in play.
         * On services that predate the structured-output engine, this payload
         * is silently ignored.
         */
        internal fun buildStructuredOutputExtraContext(extended: Boolean): String {
            val schema = if (extended) SCHEMA_14 else SCHEMA_10
            val envelope = buildJsonObject {
                put(
                    "structured_output",
                    buildJsonObject {
                        put("schema", schema)
                        put("strategy", JsonPrimitive("prompt_and_validate"))
                        put("max_retries", JsonPrimitive(2))
                    },
                )
            }
            return Json.encodeToString(JsonObject.serializer(), envelope)
        }

        private val SCHEMA_10: JsonObject = buildFieldsSchema(
            listOf(
                "name", "roaster", "origin", "variety", "process", "roastLevel",
                "tastingNotes", "altitude", "weight", "roastDate",
            ),
            booleanField = null,
        )

        private val SCHEMA_14: JsonObject = buildFieldsSchema(
            listOf(
                "name", "roaster", "origin", "region", "farm", "variety",
                "process", "roastLevel", "tastingNotes", "altitude",
                "weight", "roastDate", "expiryDate",
            ),
            booleanField = "isDecaf",
        )

        private fun buildFieldsSchema(
            stringFields: List<String>,
            booleanField: String?,
        ): JsonObject = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "fields",
                        buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put(
                                "properties",
                                buildJsonObject {
                                    stringFields.forEach { name ->
                                        put(name, fieldEntrySchema(valueTypes = listOf("string", "null")))
                                    }
                                    if (booleanField != null) {
                                        put(
                                            booleanField,
                                            fieldEntrySchema(valueTypes = listOf("boolean", "null")),
                                        )
                                    }
                                },
                            )
                            put("required", buildJsonArray { add("fields") })
                        },
                    )
                },
            )
            put("required", buildJsonArray { add("fields") })
        }

        private fun fieldEntrySchema(valueTypes: List<String>): JsonObject = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "value",
                        buildJsonObject {
                            put(
                                "type",
                                buildJsonArray { valueTypes.forEach { add(JsonPrimitive(it)) } },
                            )
                        },
                    )
                    put(
                        "status",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "enum",
                                buildJsonArray {
                                    add(JsonPrimitive("found"))
                                    add(JsonPrimitive("uncertain"))
                                    add(JsonPrimitive("not_visible"))
                                },
                            )
                        },
                    )
                },
            )
            put(
                "required",
                buildJsonArray {
                    add(JsonPrimitive("value"))
                    add(JsonPrimitive("status"))
                },
            )
        }

        internal fun buildExtractionPrompt(request: LlmExtractionRequest): String = buildString {
            append("Extract coffee bag information from the OCR text below.")

            // OCR text is the primary input in the text-only architecture —
            // lead with it so the model sees the data before any context
            // qualifiers. Escape backslash + triple-quote so OCR content
            // cannot terminate the delimiter block and inject prompt
            // instructions.
            if (!request.rawOcrText.isNullOrBlank()) {
                val safe = request.rawOcrText
                    .replace("\\", "\\\\")
                    .replace("\"\"\"", "\\\"\\\"\\\"")
                append("\n\nRaw OCR text detected on the label:")
                append("\n\"\"\"")
                append("\n$safe")
                append("\n\"\"\"")
                append("\nThe OCR may have errors (mis-recognised glyphs, missing diacritics, run-together words) and may be in any language. Apply the system prompt's multilingual rules — translate concept fields (origin / region / process / roastLevel / variety / tastingNotes) to canonical English, keep proper-noun fields (name / roaster / farm) verbatim. Correct OCR glyph errors only when the intended word is unambiguous from context.")
            }

            if (request.existingFields.isNotEmpty()) {
                val grouped = request.existingFields.entries.groupBy { it.value.source }
                val userFields = grouped[FieldSource.USER].orEmpty()
                val llmFields = grouped[FieldSource.LLM].orEmpty()
                val ocrFields = grouped[FieldSource.OCR].orEmpty()
                val lookupFields = grouped[FieldSource.LOOKUP].orEmpty()

                // Serialise as real JSON rather than hand-concatenating — a
                // label value containing `"}` or `\n` would otherwise break
                // out of the context block and be read as prompt instructions.
                val contextObj = buildJsonObject {
                    if (userFields.isNotEmpty()) {
                        put("user_confirmed", entriesToJsonObject(userFields))
                    }
                    if (lookupFields.isNotEmpty()) {
                        put("barcode_lookup", entriesToJsonObject(lookupFields))
                    }
                    if (ocrFields.isNotEmpty()) {
                        put("ocr_detected", entriesToJsonObject(ocrFields))
                    }
                    if (llmFields.isNotEmpty()) {
                        put("previous_ai_run", entriesToJsonObject(llmFields))
                    }
                }
                append("\n\nContext from prior extraction (JSON):\n")
                append(Json.encodeToString(JsonObject.serializer(), contextObj))

                append("\n\nRules for existing values:")
                append("\n- user_confirmed: Treat as ground truth. Do not contradict.")
                append("\n- barcode_lookup: High confidence database match. Only correct if clearly wrong in the OCR text.")
                append("\n- ocr_detected: Algorithmic text detection (these fields were extracted from the OCR text above by a rule-based parser). Verify and correct where the LLM has better judgement.")
                append("\n- previous_ai_run: From a prior AI pass. Verify independently — do not blindly repeat.")
                append("\n\nFocus on fields not yet identified.")
            }

            if (request.fieldsNeeded.isNotEmpty()) {
                append("\n\nFields needed: ${request.fieldsNeeded.joinToString(", ")}")
            }

            request.knownFieldValues?.let { known ->
                val parts = mutableListOf<String>()

                known.origins.takeIf { it.isNotEmpty() }?.let { origins ->
                    parts.add("Known origins: ${origins.take(20).joinToString(", ")}")
                }
                known.varieties.takeIf { it.isNotEmpty() }?.let { varieties ->
                    parts.add("Known varieties: ${varieties.take(15).joinToString(", ")}")
                }
                known.processTypes.takeIf { it.isNotEmpty() }?.let { processes ->
                    parts.add("Known processes: ${processes.take(10).joinToString(", ")}")
                }
                known.roasters.takeIf { it.isNotEmpty() }?.let { roasters ->
                    parts.add("Known roasters: ${roasters.take(20).joinToString(", ")}")
                }

                if (parts.isNotEmpty()) {
                    append("\n\nReference vocabulary from user's coffee collection:")
                    parts.forEach { append("\n- $it") }
                    append(
                        "\nPrefer these values when a match is close. " +
                            "Do not force a match if the OCR text clearly says something different.",
                    )
                }
            }

            append("\n\nRespond with JSON only.")
        }

        private fun entriesToJsonObject(
            entries: List<Map.Entry<String, com.adsamcik.starlitcoffee.scan.model.FieldContext>>,
        ): JsonObject = buildJsonObject {
            entries.forEach { (k, v) -> put(k, JsonPrimitive(v.value)) }
        }

        internal val fieldMapping = mapOf(
            "name" to "name",
            "roaster" to "roaster",
            "origin" to "origin",
            "region" to "region",
            "farm" to "farm",
            "variety" to "variety",
            "process" to "processType",
            "roastLevel" to "roastLevel",
            "tastingNotes" to "tastingNotes",
            "altitude" to "altitude",
            "weight" to "weight",
            "roastDate" to "roastDate",
            "expiryDate" to "expiryDate",
            "isDecaf" to "isDecaf",
        )

        internal fun parseResponse(
            response: String,
            fieldsNeeded: Set<String>,
        ): LlmExtractionResult {
            val cleaned = response.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = Json { ignoreUnknownKeys = true }
            // Boundary catch: a malformed LLM response can throw
            // SerializationException, IllegalArgumentException, or anything
            // else from the parser; the only useful product behaviour is to
            // surface a single Failed result and let the consensus engine
            // try the next golden frame.
            @Suppress("TooGenericExceptionCaught")
            val jsonObj = try {
                json.parseToJsonElement(cleaned).jsonObject
            } catch (e: Exception) {
                return LlmExtractionResult.Failed(
                    "Failed to parse LLM response as JSON: ${e.message}",
                )
            }

            // Support both nested {"fields": {...}} and flat {"name": "value"} formats
            val fieldsObj = jsonObj["fields"]?.jsonObject ?: jsonObj

            val candidates = fieldMapping.mapNotNull { (jsonKey, fieldName) ->
                if (fieldsNeeded.isNotEmpty() && fieldName !in fieldsNeeded) {
                    null
                } else {
                    extractFieldCandidate(fieldName, fieldsObj[jsonKey])
                }
            }
            return LlmExtractionResult.Success(fieldCandidates = candidates)
        }

        /**
         * Map one entry from the parsed JSON to a [BagFieldCandidate].
         *
         * Supports two response shapes:
         *  * Nested `{"value": "...", "status": "found"}` (current schema).
         *  * Legacy flat `"name": "value"` (older deployments).
         *
         * Returns `null` when the field is missing, marked `not_visible`,
         * or has an empty value.
         */
        private fun extractFieldCandidate(
            fieldName: String,
            fieldEntry: kotlinx.serialization.json.JsonElement?,
        ): BagFieldCandidate? {
            val (value, status) = when {
                fieldEntry is JsonObject -> {
                    val v = fieldEntry["value"]?.let { e ->
                        if (e is JsonNull) null else e.jsonPrimitive.contentOrNull
                    }
                    val s = fieldEntry["status"]?.jsonPrimitive?.contentOrNull ?: "found"
                    v to s
                }
                fieldEntry != null && fieldEntry !is JsonNull -> {
                    fieldEntry.jsonPrimitive.contentOrNull to "found"
                }
                else -> null to "not_visible"
            }
            if (value.isNullOrBlank() || status == "not_visible") return null
            return BagFieldCandidate(
                fieldName = fieldName,
                value = value.trim(),
                sourceType = BagFieldSourceType.LLM,
                confidenceHint = when (status) {
                    "found" -> BagFieldConfidence.HIGH
                    "uncertain" -> BagFieldConfidence.LOW
                    else -> BagFieldConfidence.MEDIUM
                },
            )
        }
    }
}
