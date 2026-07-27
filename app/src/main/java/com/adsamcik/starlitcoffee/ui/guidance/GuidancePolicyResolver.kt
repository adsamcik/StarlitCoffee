package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.MethodFamilyId

/**
 * The origin of a resolved guidance choice. Keeping this visible to callers
 * lets a future UI distinguish a temporary choice from a remembered one
 * without changing the stage plan or recipe.
 */
enum class GuidancePolicySource {
    SESSION_OVERRIDE,
    PROFILE_OVERRIDE,
    FAMILY_PREFERENCE,
    SAFE_DEFAULT,
}

/**
 * Pure input to the centralized preference-precedence rule.
 *
 * The values deliberately use stable catalogue IDs. A persistence adapter can
 * retain unknown raw IDs independently; this resolver only acts on the
 * selected, known family/profile supplied by its caller.
 */
data class GuidancePolicyContext(
    val methodFamilyId: MethodFamilyId,
    val brewerProfileId: BrewerProfileId? = null,
    val sessionOverride: GuidancePresentationLevel? = null,
    val profileOverrides: Map<BrewerProfileId, GuidancePresentationLevel> = emptyMap(),
    val familyPreferences: Map<MethodFamilyId, GuidancePresentationLevel> = emptyMap(),
)

/**
 * The level and provenance that a renderer should use for one Learn or live
 * Brew surface. This is presentation state only: it never changes a recipe,
 * stage order, timer, or completion rule.
 */
data class ResolvedGuidancePolicy(
    val methodFamilyId: MethodFamilyId,
    val brewerProfileId: BrewerProfileId?,
    val level: GuidancePresentationLevel,
    val source: GuidancePolicySource,
) {
    /**
     * Safety content bypasses the routine visibility policy as a second,
     * defensive guard. Catalogues also validate this invariant at creation
     * time, but a renderer must never rely on that validation alone.
     */
    fun isVisible(
        visibility: GuidanceVisibilityPolicy,
        safetyCritical: Boolean,
    ): Boolean = safetyCritical || visibility.isVisibleAt(level)

    fun isVisible(content: GuidanceContentRecord): Boolean =
        isVisible(content.visibility, content.safetyCritical)

    fun <T : GuidanceVisibilityItem> visibleItems(items: Iterable<T>): List<T> =
        items.filter { item -> isVisible(item.visibility, item.safetyCritical) }
}

/**
 * A small contract shared by pure, built-in content catalogues. It makes the
 * safety override testable without forcing every future content source to use
 * Android resource-backed [GuidanceContentRecord] instances.
 */
interface GuidanceVisibilityItem {
    val visibility: GuidanceVisibilityPolicy
    val safetyCritical: Boolean
}

/**
 * Resolves the guidance level in the one supported order:
 * session override, profile override, family preference, then Full.
 *
 * Full is intentionally the safe default for a family/profile that has not
 * been seen before. [GuidancePresentationLevel.CUSTOM] remains a first-class
 * result; its individual layout choices stay with the rendering layer while
 * records opt into CUSTOM through [GuidanceVisibilityPolicy].
 */
object GuidancePolicyResolver {
    val safeDefaultLevel: GuidancePresentationLevel = GuidancePresentationLevel.FULL

    fun resolve(context: GuidancePolicyContext): ResolvedGuidancePolicy {
        context.sessionOverride?.let { level ->
            return resolved(context, level, GuidancePolicySource.SESSION_OVERRIDE)
        }

        context.brewerProfileId?.let { profileId ->
            context.profileOverrides[profileId]?.let { level ->
                return resolved(context, level, GuidancePolicySource.PROFILE_OVERRIDE)
            }
        }

        context.familyPreferences[context.methodFamilyId]?.let { level ->
            return resolved(context, level, GuidancePolicySource.FAMILY_PREFERENCE)
        }

        return resolved(context, safeDefaultLevel, GuidancePolicySource.SAFE_DEFAULT)
    }

    private fun resolved(
        context: GuidancePolicyContext,
        level: GuidancePresentationLevel,
        source: GuidancePolicySource,
    ): ResolvedGuidancePolicy = ResolvedGuidancePolicy(
        methodFamilyId = context.methodFamilyId,
        brewerProfileId = context.brewerProfileId,
        level = level,
        source = source,
    )
}
