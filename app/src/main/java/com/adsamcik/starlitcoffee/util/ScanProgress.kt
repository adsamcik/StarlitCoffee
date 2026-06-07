package com.adsamcik.starlitcoffee.util

/**
 * Ordered stages of the bag-photo extraction pipeline, surfaced to the user as
 * live progress while extraction runs (both in the in-app analyzing screen and
 * the foreground-service notification).
 *
 * Declaration order is execution order. Not every stage runs on every pass —
 * the LLM stages are skipped when AI is unavailable or the user opted out — so
 * the extractor plans the concrete subset up front (see [ScanProgress.stepCount])
 * rather than assuming the full list.
 */
enum class ScanStage {
    /** Decoding captured photos and running OCR passes. */
    OCR,

    /** Resolving barcode / QR and any product-database lookups. */
    BARCODE_LOOKUP,

    /** First (text-only) LLM extraction pass over the merged OCR text. */
    LLM_EXTRACT,

    /** Cropping the detected label region for the vision pass. */
    LABEL_CROP,

    /** Multimodal vision pass over the (cropped) label image. */
    VISION,

    /** Final text-only LLM pass reconciling prior stage outputs. */
    COMBINING,

    /** Resolving consensus, building review hints and the prefill. */
    FINALIZING,
}

/**
 * A point-in-time progress snapshot emitted by `BagPhotoExtractor`.
 *
 * [stepIndex] is 1-based within [stepCount], the number of stages PLANNED for
 * the current run, so the UI can render a determinate bar without knowing the
 * pipeline internals. A stage that ends up skipped (e.g. vision had nothing to
 * do) simply isn't reported; the next reported stage keeps the bar monotonic.
 */
data class ScanProgress(
    val stage: ScanStage,
    val stepIndex: Int,
    val stepCount: Int,
) {
    /** Completion fraction in `0f..1f`, safe against a zero/empty plan. */
    val fraction: Float
        get() = if (stepCount <= 0) 0f else (stepIndex.toFloat() / stepCount).coerceIn(0f, 1f)
}
