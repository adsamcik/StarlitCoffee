package com.adsamcik.starlitcoffee.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrewTrajectoryMatcherTest {

    private lateinit var matcher: BrewTrajectoryMatcher

    @Before
    fun setup() {
        matcher = BrewTrajectoryMatcher()
    }

    // --- Helpers ---

    /** Feed N seconds of frames with given feature values */
    private fun feedSeconds(
        seconds: Int,
        pourEnergyDb: Float,
        flatness: Float,
        coincidence: Int,
    ) {
        val framesPerSecond = 86
        repeat(seconds * framesPerSecond) {
            matcher.feedFrame(pourEnergyDb, flatness, coincidence)
        }
    }

    // --- Tests ---

    @Test
    fun `initial state is UNKNOWN with zero confidence`() {
        assertEquals(BrewTrajectoryMatcher.TrajectoryPhase.UNKNOWN, matcher.trajectoryPhase)
        assertEquals(0f, matcher.brewConfidence, 0.01f)
    }

    @Test
    fun `sustained high energy with flatness detects POURING`() {
        // Feed pour-like features for 5 seconds
        feedSeconds(5, pourEnergyDb = -10f, flatness = 0.15f, coincidence = 5)

        assertEquals(
            "Should detect POURING phase",
            BrewTrajectoryMatcher.TrajectoryPhase.POURING,
            matcher.trajectoryPhase,
        )
        assertTrue(
            "Confidence should be > 0.3, got ${matcher.brewConfidence}",
            matcher.brewConfidence > 0.3f,
        )
    }

    @Test
    fun `low energy detects SILENCE`() {
        feedSeconds(5, pourEnergyDb = -45f, flatness = 0.02f, coincidence = 1)

        assertEquals(
            BrewTrajectoryMatcher.TrajectoryPhase.SILENCE,
            matcher.trajectoryPhase,
        )
    }

    @Test
    fun `rising energy detects POUR_ONSET`() {
        // Start quiet
        feedSeconds(3, pourEnergyDb = -35f, flatness = 0.05f, coincidence = 1)
        // Ramp up
        feedSeconds(1, pourEnergyDb = -25f, flatness = 0.10f, coincidence = 3)
        feedSeconds(1, pourEnergyDb = -15f, flatness = 0.12f, coincidence = 4)

        val phase = matcher.trajectoryPhase
        assertTrue(
            "Should detect POUR_ONSET or POURING, got $phase",
            phase == BrewTrajectoryMatcher.TrajectoryPhase.POUR_ONSET ||
                phase == BrewTrajectoryMatcher.TrajectoryPhase.POURING,
        )
    }

    @Test
    fun `reset clears state`() {
        feedSeconds(5, pourEnergyDb = -10f, flatness = 0.15f, coincidence = 5)
        assertTrue(matcher.brewConfidence > 0)

        matcher.reset()
        assertEquals(BrewTrajectoryMatcher.TrajectoryPhase.UNKNOWN, matcher.trajectoryPhase)
        assertEquals(0f, matcher.brewConfidence, 0.01f)
    }

    @Test
    fun `unknown phase for ambiguous features`() {
        // Mid-range everything — not clearly any phase
        feedSeconds(5, pourEnergyDb = -25f, flatness = 0.05f, coincidence = 2)

        // Should not falsely trigger POURING
        assertTrue(
            "Ambiguous features should NOT be POURING, got ${matcher.trajectoryPhase}",
            matcher.trajectoryPhase != BrewTrajectoryMatcher.TrajectoryPhase.POURING,
        )
    }

    @Test
    fun `drip decay detected despite small rebound`() {
        // Pour phase first
        feedSeconds(5, pourEnergyDb = -10f, flatness = 0.15f, coincidence = 5)
        // Decay with a local rebound at second 3
        feedSeconds(1, pourEnergyDb = -12f, flatness = 0.10f, coincidence = 4)
        feedSeconds(1, pourEnergyDb = -15f, flatness = 0.08f, coincidence = 3)
        feedSeconds(1, pourEnergyDb = -13f, flatness = 0.09f, coincidence = 3) // small rebound
        feedSeconds(1, pourEnergyDb = -18f, flatness = 0.06f, coincidence = 2)
        feedSeconds(1, pourEnergyDb = -22f, flatness = 0.05f, coincidence = 2)

        val phase = matcher.trajectoryPhase
        assertTrue(
            "Should detect DRIP_DECAY despite local rebound, got $phase",
            phase == BrewTrajectoryMatcher.TrajectoryPhase.DRIP_DECAY ||
                phase == BrewTrajectoryMatcher.TrajectoryPhase.DRIPPING ||
                phase == BrewTrajectoryMatcher.TrajectoryPhase.SILENCE,
        )
    }

    @Test
    fun `drip decay rejected for sustained increase`() {
        // Pour, then energy keeps rising — not a decay
        feedSeconds(3, pourEnergyDb = -10f, flatness = 0.15f, coincidence = 5)
        feedSeconds(1, pourEnergyDb = -12f, flatness = 0.10f, coincidence = 4)
        feedSeconds(1, pourEnergyDb = -8f, flatness = 0.12f, coincidence = 4) // going back up
        feedSeconds(1, pourEnergyDb = -6f, flatness = 0.14f, coincidence = 5) // still rising

        assertTrue(
            "Sustained increase should NOT be DRIP_DECAY, got ${matcher.trajectoryPhase}",
            matcher.trajectoryPhase != BrewTrajectoryMatcher.TrajectoryPhase.DRIP_DECAY,
        )
    }
}
