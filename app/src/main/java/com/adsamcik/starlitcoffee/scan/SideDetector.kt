package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.scan.model.AccumulatorConfig

/**
 * Detects side flips of the coffee bag by computing a lightweight perceptual hash
 * of each camera frame and monitoring for large Hamming distance spikes.
 *
 * Flow:
 * 1. Camera analyzer calls [onFrame] with per-frame luma averages
 * 2. If Hamming distance exceeds threshold → [onPotentialFlip] callback fires
 * 3. The accumulator waits for text confirmation within 2s before committing the flip
 */
class SideDetector(
    private val config: AccumulatorConfig = AccumulatorConfig.DEFAULT,
    private val onPotentialFlip: () -> Unit,
) {
    private var previousHash: Long = 0L
    private var hasBaseline: Boolean = false

    /**
     * Compute a simple 64-bit perceptual hash from an 8×8 luma grid.
     * Each bit represents whether that cell is above the mean luma.
     *
     * @param lumaGrid 64 average luma values from an 8×8 grid of the frame
     */
    fun computeHash(lumaGrid: ByteArray): Long {
        require(lumaGrid.size == 64) { "Expected 64-element 8×8 luma grid, got ${lumaGrid.size}" }

        val mean = lumaGrid.map { it.toInt() and 0xFF }.average()
        var hash = 0L
        for (i in lumaGrid.indices) {
            if ((lumaGrid[i].toInt() and 0xFF) >= mean) {
                hash = hash or (1L shl i)
            }
        }
        return hash
    }

    /**
     * Compute Hamming distance between two 64-bit hashes.
     */
    fun hammingDistance(a: Long, b: Long): Int {
        return java.lang.Long.bitCount(a xor b)
    }

    /**
     * Process a new frame's luma grid. Compares with previous hash and fires
     * [onPotentialFlip] if the Hamming distance exceeds threshold.
     *
     * @param lumaGrid 64 average luma values from 8×8 grid
     * @return the Hamming distance from previous frame (0 on first frame)
     */
    fun onFrame(lumaGrid: ByteArray): Int {
        val currentHash = computeHash(lumaGrid)

        if (!hasBaseline) {
            previousHash = currentHash
            hasBaseline = true
            return 0
        }

        val distance = hammingDistance(previousHash, currentHash)
        previousHash = currentHash

        if (distance >= config.sideFlipHammingThreshold) {
            onPotentialFlip()
        }

        return distance
    }

    /**
     * Reset state. Call when starting a new scan session.
     */
    fun reset() {
        previousHash = 0L
        hasBaseline = false
    }
}
