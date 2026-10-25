package com.adsamcik.starlitcoffee.ui.guidance

/** Canonical Pulsar pixels exist at runtime only after complete hash-bound scene admission. */
object PulsarLearnInstructionAssetCatalog {
    const val TRACKER_PATH = "docs/brewing/pulsar-learning-guide-production-2026-08-19.json"

    fun runtimeAssets(): List<InstructionAssetRecord> = GeneratedGuidanceCatalog.pulsarAssets
}
