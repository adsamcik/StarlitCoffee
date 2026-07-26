package com.adsamcik.starlitcoffee.data.model

/**
 * A small set of recognisably different haptic personalities for brew cues.
 * Each theme preserves event meaning: preparation cues are brief, bloom
 * completion is more distinct, and the target-time cue is the clearest.
 */
enum class BrewVibrationTheme {
    SOFT,
    CLASSIC,
    BOLD;

    fun patternFor(event: BrewVibrationEvent): LongArray = when (this) {
        SOFT -> when (event) {
            BrewVibrationEvent.MINUTE -> longArrayOf(0, 45)
            BrewVibrationEvent.BLOOM_WARNING -> longArrayOf(0, 40, 90, 40)
            BrewVibrationEvent.BLOOM_COMPLETE -> longArrayOf(0, 90, 100, 90)
            BrewVibrationEvent.TARGET_REACHED -> longArrayOf(0, 120, 100, 120)
        }
        CLASSIC -> when (event) {
            BrewVibrationEvent.MINUTE -> longArrayOf(0, 100, 80, 100)
            BrewVibrationEvent.BLOOM_WARNING -> longArrayOf(0, 60)
            BrewVibrationEvent.BLOOM_COMPLETE -> longArrayOf(0, 300, 120, 300, 120, 500)
            BrewVibrationEvent.TARGET_REACHED -> longArrayOf(0, 220, 100, 220, 100, 320)
        }
        BOLD -> when (event) {
            BrewVibrationEvent.MINUTE -> longArrayOf(0, 140, 80, 140)
            BrewVibrationEvent.BLOOM_WARNING -> longArrayOf(0, 90, 80, 90)
            BrewVibrationEvent.BLOOM_COMPLETE -> longArrayOf(0, 400, 100, 400, 100, 600)
            BrewVibrationEvent.TARGET_REACHED -> longArrayOf(0, 320, 90, 320, 90, 500)
        }
    }

    fun alertChannelPattern(): LongArray = patternFor(BrewVibrationEvent.TARGET_REACHED)
}

enum class BrewVibrationEvent {
    MINUTE,
    BLOOM_WARNING,
    BLOOM_COMPLETE,
    TARGET_REACHED,
}
