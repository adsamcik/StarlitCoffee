package com.adsamcik.starlitcoffee.scan.model

/**
 * Visible status of the LLM subsystem during a live scan session.
 *
 * Lives in `scan.model` (rather than the `viewmodel` package) so the scan
 * pipeline — e.g. [com.adsamcik.starlitcoffee.scan.LlmEscalationCoordinator] —
 * can reference it without depending on the `viewmodel` layer, breaking the
 * scan <-> viewmodel package cycle.
 */
enum class LlmUiStatus {
    IDLE,          // Not yet triggered
    CONNECTING,    // Pre-warming / connecting to service
    WAITING,       // Connected, trigger conditions not yet met
    PROCESSING,    // LLM inference in progress
    COMPLETED,     // LLM returned results
    FAILED,        // LLM failed
    UNAVAILABLE,   // Mindlayer not installed or not connected
}
