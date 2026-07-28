package com.adsamcik.starlitcoffee.ui.brewerprofile

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileRecipeDefaults
import com.adsamcik.starlitcoffee.domain.brewing.BrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewerProfileRecipeDefaults
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltinBrewerStagePlanFactory
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow

/**
 * A selectable P1 profile with only the data needed by the setup surface.
 *
 * The state factory deliberately exposes profiles only when their catalog
 * identity, conservative defaults, and durable stage plan all exist. This
 * keeps the setup UI from promising a brew that the session engine cannot
 * execute.
 */
data class P1BrewerProfileSetupOption(
    val profileId: BrewerProfileId,
    val displayName: String,
    val methodFamilyName: String,
    val defaults: BrewerProfileRecipeDefaults,
    val hasCompatibleFilters: Boolean,
    val allowsIntentionallyUnfiltered: Boolean,
) {
    val requiresHarioSwitchWorkflow: Boolean
        get() = profileId == HARIO_SWITCH_PROFILE_ID
}

/** The immutable choice passed to the durable P1-session starter. */
data class P1BrewerProfileStartSelection(
    val brewerProfileId: BrewerProfileId,
    val harioSwitchWorkflow: HarioSwitchWorkflow?,
)

/**
 * UI-owned selection state for the P1 brewer setup flow.
 *
 * Keep this state outside the session engine: it describes a prospective
 * brew, while a [P1BrewerProfileStartSelection] is handed off only after the
 * user presses start and the integration layer has made an immutable recipe
 * snapshot.
 */
data class P1BrewerProfileSetupUiState(
    val profiles: List<P1BrewerProfileSetupOption>,
    val selectedProfileId: BrewerProfileId? = null,
    val harioSwitchWorkflow: HarioSwitchWorkflow = HarioSwitchWorkflow.STEEP_AND_RELEASE,
    val isStarting: Boolean = false,
) {
    val selectedProfile: P1BrewerProfileSetupOption?
        get() = profiles.firstOrNull { it.profileId == selectedProfileId }

    val startSelection: P1BrewerProfileStartSelection?
        get() = selectedProfile?.let { option ->
            P1BrewerProfileStartSelection(
                brewerProfileId = option.profileId,
                harioSwitchWorkflow = harioSwitchWorkflow.takeIf {
                    option.requiresHarioSwitchWorkflow
                },
            )
        }

    val canStart: Boolean
        get() = startSelection != null && !isStarting

    /** Ignores an ID the P1 setup does not expose instead of guessing a substitute. */
    fun selectProfile(profileId: BrewerProfileId): P1BrewerProfileSetupUiState =
        if (profiles.any { it.profileId == profileId }) copy(selectedProfileId = profileId) else this

    /** The workflow has meaning only for the selected Hario Switch profile. */
    fun selectHarioSwitchWorkflow(
        workflow: HarioSwitchWorkflow,
    ): P1BrewerProfileSetupUiState = if (selectedProfile?.requiresHarioSwitchWorkflow == true) {
        copy(harioSwitchWorkflow = workflow)
    } else {
        this
    }

    fun withStarting(isStarting: Boolean): P1BrewerProfileSetupUiState = copy(isStarting = isStarting)
}

/** Builds P1 setup state from one catalog without assuming profile aliases or fallback plans. */
object P1BrewerProfileSetupStateFactory {

    fun create(
        catalog: BrewingCatalog = BuiltinBrewingCatalog.instance,
        selectedProfileId: BrewerProfileId? = null,
    ): P1BrewerProfileSetupUiState {
        val supportedIds = BuiltinBrewerStagePlanFactory.supportedBrewerProfileIds
            .intersect(BuiltinBrewerProfileRecipeDefaults.supportedProfileIds)
        val profilesById = catalog.brewerProfiles.associateBy { it.id }
        val orderedIds = DISPLAY_ORDER.filter { it in supportedIds } +
            (supportedIds - DISPLAY_ORDER.toSet()).sortedBy(BrewerProfileId::value)

        val options = orderedIds.mapNotNull { profileId ->
            val profile = profilesById[profileId] ?: return@mapNotNull null
            val defaults = BuiltinBrewerProfileRecipeDefaults.find(profileId) ?: return@mapNotNull null
            P1BrewerProfileSetupOption(
                profileId = profile.id,
                displayName = profile.displayName,
                methodFamilyName = catalog.findMethodFamily(profile.familyId)?.displayName
                    ?: profile.familyId.value,
                defaults = defaults,
                hasCompatibleFilters = profile.compatibleFilterIds.isNotEmpty(),
                allowsIntentionallyUnfiltered = profile.allowsIntentionallyUnfiltered,
            )
        }

        return P1BrewerProfileSetupUiState(
            profiles = options,
            selectedProfileId = selectedProfileId?.takeIf { id -> options.any { it.profileId == id } },
        )
    }

    private val DISPLAY_ORDER = listOf(
        BrewerProfileId("clever_style"),
        HARIO_SWITCH_PROFILE_ID,
        BrewerProfileId("valve_release_generic"),
        BrewerProfileId("cezve_generic"),
        BrewerProfileId("automatic_batch_generic"),
        BrewerProfileId("automatic_single_cup_generic"),
        BrewerProfileId("vietnamese_phin"),
    )
}

private val HARIO_SWITCH_PROFILE_ID = BrewerProfileId("hario_switch")
