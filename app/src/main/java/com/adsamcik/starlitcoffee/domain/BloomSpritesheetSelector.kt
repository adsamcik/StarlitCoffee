package com.adsamcik.starlitcoffee.domain

import kotlin.random.Random

/**
 * Source-of-truth list of available bloom spritesheet IDs.
 *
 * UI metadata (drawable + label + description) is mapped to these IDs in
 * `BloomSpritesheetAnimation.kt`; this list keeps the picker free of any
 * Android resource dependency so it can be called from `BrewViewModel`
 * without crossing the UI layer boundary.
 */
val BloomSpritesheetIds: List<String> = listOf(
    "coffee_flower",
    "coffee_starlit",
    "coffee_latte",
    "coffee_plant",
    "coffee_brew",
    "rose",
    "lotus",
    "sunflower",
    "orchid",
    "jasmine",
)

/**
 * Default boost applied per "display behind" the leader. With the default
 * user weight of 1, a flower one display behind gets weight `1 + 1*2 = 3`
 * vs the leader's `1` — a 3× preference. Higher values rotate harder
 * (toward strict round-robin); lower values approach pure random.
 */
const val DefaultBloomRotationMultiplier: Int = 2

/**
 * Picks a single bloom spritesheet ID with weighted random selection.
 *
 * Each candidate's effective weight is:
 *
 *     userWeight + (maxDisplay - ownDisplay) * rotationMultiplier
 *
 * where `userWeight` is the user's preference (0..2, default 1; 0 disables
 * the flower entirely) and `maxDisplay` is the highest display count *among
 * eligible (user-enabled) flowers* — disabled flowers don't skew rotation
 * for the rest. Flowers shown less than the leader receive proportional
 * extra chances, so over many brews the distribution evens out while still
 * honoring the user's per-flower preference. Returns null when every
 * available ID has been weighted out.
 */
fun pickWeightedBloomSpritesheetId(
    weights: Map<String, Int>,
    displayCounts: Map<String, Int> = emptyMap(),
    rotationMultiplier: Int = DefaultBloomRotationMultiplier,
    random: Random = Random,
): String? {
    val eligible = BloomSpritesheetIds.mapNotNull { id ->
        val baseWeight = weights[id]?.coerceIn(0, 2) ?: 1
        if (baseWeight <= 0) null else id to baseWeight
    }
    if (eligible.isEmpty()) return null

    // Compute "max" only over eligible flowers — a disabled flower with a
    // huge legacy count must not over-boost the rest of the field.
    val maxDisplay = eligible.maxOf { (id, _) -> displayCounts[id] ?: 0 }
    val safeMultiplier = rotationMultiplier.coerceAtLeast(0)

    val weighted = eligible.map { (id, baseWeight) ->
        val displayed = (displayCounts[id] ?: 0).coerceAtLeast(0)
        val extra = (maxDisplay - displayed) * safeMultiplier
        id to (baseWeight + extra)
    }
    val totalWeight = weighted.sumOf { (_, weight) -> weight }
    if (totalWeight <= 0) return null

    var remaining = random.nextInt(totalWeight)
    weighted.forEach { (id, weight) ->
        if (remaining < weight) return id
        remaining -= weight
    }
    return weighted.last().first
}
