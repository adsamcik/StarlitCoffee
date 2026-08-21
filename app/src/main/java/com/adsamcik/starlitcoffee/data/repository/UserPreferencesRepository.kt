package com.adsamcik.starlitcoffee.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.CatalogResolution
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewVibrationTheme
import com.adsamcik.starlitcoffee.data.model.FilterType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val enabledMethods: Set<BrewMethod> = BrewMethod.entries.toSet(),
    val defaultMethod: BrewMethod = BrewMethod.PULSAR,
    val defaultFilterType: FilterType? = null,
    val selectedGrinderId: String? = null,
    val qrLinkExplorerEnabled: Boolean = false,
    val lastUsedRatio: Float = 17f,
    val defaultInputDirection: String = "DOSE",
    val skipMethodSelection: Boolean = false,
    val dimModeEnabled: Boolean = true,
    val dimModeTrueBlack: Boolean = true,
    val dimModeReduceBrightness: Boolean = true,
    val dimModeFullscreen: Boolean = true,
    val dimModeForceDarkInLight: Boolean = true,
    val showBrewingInstructions: Boolean = true,
    val showEnglishBrewingTerms: Boolean = false,
    val bloomSpritesheetWeights: Map<String, Int> = emptyMap(),
    // How many times each spritesheet has been picked for a brew. Used by the
    // domain selector to bias future picks toward under-shown flowers, so
    // every flower in the user's allow-list gets fair rotation over many brews.
    val bloomSpritesheetDisplayCounts: Map<String, Int> = emptyMap(),
    // When true, schedule a notification ~30 minutes after a brew is logged
    // asking the user to rate it with 5 emojis. Opt-in because it needs
    // POST_NOTIFICATIONS permission on Android 13+ and not every user wants it.
    val ratingReminderEnabled: Boolean = false,
    val brewVibrationTheme: BrewVibrationTheme = BrewVibrationTheme.CLASSIC,
    // When true, the bag-scan review screen records, on-device, a diff between
    // what the model extracted and what the user finally saved. This is a
    // privacy-sensitive quality signal (it captures edited label metadata), so
    // it is strictly opt-in and stays on the device — nothing is uploaded.
    val scanCorrectionLoggingEnabled: Boolean = false,
)

data class MethodSelection(
    val enabledMethods: Set<BrewMethod>,
    val defaultMethod: BrewMethod,
)

internal fun normalizeMethodSelection(
    enabledMethods: Set<BrewMethod>,
    requestedDefault: BrewMethod,
): MethodSelection {
    val normalizedMethods = enabledMethods.ifEmpty { setOf(BrewMethod.PULSAR) }
    val normalizedDefault = requestedDefault.takeIf(normalizedMethods::contains)
        ?: BrewMethod.entries.first { normalizedMethods.contains(it) }
    return MethodSelection(normalizedMethods, normalizedDefault)
}

// This is the single DataStore boundary for all settings surfaces; keeping the
// atomic write API together prevents screens from rebuilding partial updates.
@Suppress("TooManyFunctions")
interface UserPreferencesStore {
    val userPreferences: Flow<UserPreferences>

    suspend fun completeOnboarding(
        enabledMethods: Set<BrewMethod>,
        defaultMethod: BrewMethod,
        defaultFilterType: FilterType?,
        selectedGrinderId: String?,
    )

    suspend fun updateMethodSelection(enabledMethods: Set<BrewMethod>, defaultMethod: BrewMethod)
    suspend fun updateDefaultFilterType(filterType: FilterType?)
    suspend fun updateSelectedGrinder(grinderId: String?)
    suspend fun updateSkipMethodSelection(enabled: Boolean)
    suspend fun updateShowBrewingInstructions(enabled: Boolean)
    suspend fun updateShowEnglishBrewingTerms(enabled: Boolean)
    suspend fun updateBloomSpritesheetWeights(weights: Map<String, Int>)
    suspend fun updateRatingReminderEnabled(enabled: Boolean)
    suspend fun updateBrewVibrationTheme(theme: BrewVibrationTheme)
    suspend fun updateScanCorrectionLoggingEnabled(enabled: Boolean)
    suspend fun updateDimModeEnabled(enabled: Boolean)
    suspend fun updateDimModeTrueBlack(enabled: Boolean)
    suspend fun updateDimModeReduceBrightness(enabled: Boolean)
    suspend fun updateDimModeFullscreen(enabled: Boolean)
    suspend fun updateDimModeForceDarkInLight(enabled: Boolean)
}

private object UserPreferenceKeys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val ENABLED_METHODS = stringSetPreferencesKey("enabled_methods")
    val DEFAULT_METHOD = stringPreferencesKey("default_method")
    val DEFAULT_FILTER_TYPE = stringPreferencesKey("default_filter_type")
    val SELECTED_GRINDER_ID = stringPreferencesKey("selected_grinder_id")
    val QR_LINK_EXPLORER_ENABLED = booleanPreferencesKey("qr_link_explorer_enabled")
    val LAST_USED_RATIO = floatPreferencesKey("last_used_ratio")
    val DEFAULT_INPUT_DIRECTION = stringPreferencesKey("default_input_direction")
    val SKIP_METHOD_SELECTION = booleanPreferencesKey("skip_method_selection")
    val DIM_MODE_ENABLED = booleanPreferencesKey("dim_mode_enabled")
    val DIM_MODE_TRUE_BLACK = booleanPreferencesKey("dim_mode_true_black")
    val DIM_MODE_REDUCE_BRIGHTNESS = booleanPreferencesKey("dim_mode_reduce_brightness")
    val DIM_MODE_FULLSCREEN = booleanPreferencesKey("dim_mode_fullscreen")
    val DIM_MODE_FORCE_DARK_IN_LIGHT = booleanPreferencesKey("dim_mode_force_dark_in_light")
    val SHOW_BREWING_INSTRUCTIONS = booleanPreferencesKey("show_brewing_instructions")
    val SHOW_ENGLISH_BREWING_TERMS = booleanPreferencesKey("show_english_brewing_terms")
    val BLOOM_SPRITESHEET_WEIGHTS = stringSetPreferencesKey("bloom_spritesheet_weights")
    val BLOOM_SPRITESHEET_DISPLAY_COUNTS = stringSetPreferencesKey("bloom_spritesheet_display_counts")
    val RATING_REMINDER_ENABLED = booleanPreferencesKey("rating_reminder_enabled")
    val BREW_VIBRATION_THEME = stringPreferencesKey("brew_vibration_theme")
    val SCAN_CORRECTION_LOGGING_ENABLED = booleanPreferencesKey("scan_correction_logging_enabled")
    val ENABLED_BREWER_PROFILE_IDS = stringSetPreferencesKey("enabled_brewer_profile_ids")
    val DEFAULT_BREWER_PROFILE_ID = stringPreferencesKey("default_brewer_profile_id")
    val GUIDANCE_BY_METHOD_FAMILY = stringSetPreferencesKey("guidance_by_method_family")
    val GUIDANCE_BY_BREWER_PROFILE = stringSetPreferencesKey("guidance_by_brewer_profile")
    val UTILITY_MODULES_BY_METHOD_FAMILY = stringSetPreferencesKey("utility_modules_by_method_family")
}
abstract class UserPreferencesWriter protected constructor(
    protected val context: Context,
) : UserPreferencesStore {
    override suspend fun updateDefaultFilterType(filterType: FilterType?) {
        context.dataStore.edit { prefs ->
            if (filterType != null) {
                prefs[UserPreferenceKeys.DEFAULT_FILTER_TYPE] = filterType.name
            } else {
                prefs.remove(UserPreferenceKeys.DEFAULT_FILTER_TYPE)
            }
        }
    }

    override suspend fun updateSelectedGrinder(grinderId: String?) {
        context.dataStore.edit { prefs ->
            if (grinderId != null) {
                prefs[UserPreferenceKeys.SELECTED_GRINDER_ID] = grinderId
            } else {
                prefs.remove(UserPreferenceKeys.SELECTED_GRINDER_ID)
            }
        }
    }

    suspend fun updateQrLinkExplorerEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.QR_LINK_EXPLORER_ENABLED] = enabled
        }
    }

    suspend fun updateLastUsedRatio(ratio: Float) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.LAST_USED_RATIO] = ratio
        }
    }

    suspend fun updateDefaultInputDirection(direction: String) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.DEFAULT_INPUT_DIRECTION] = direction
        }
    }

    override suspend fun updateSkipMethodSelection(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.SKIP_METHOD_SELECTION] = enabled
        }
    }

    override suspend fun updateDimModeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.DIM_MODE_ENABLED] = enabled
        }
    }

    override suspend fun updateDimModeTrueBlack(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.DIM_MODE_TRUE_BLACK] = enabled
        }
    }

    override suspend fun updateDimModeReduceBrightness(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.DIM_MODE_REDUCE_BRIGHTNESS] = enabled
        }
    }

    override suspend fun updateDimModeFullscreen(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.DIM_MODE_FULLSCREEN] = enabled
        }
    }

    override suspend fun updateDimModeForceDarkInLight(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.DIM_MODE_FORCE_DARK_IN_LIGHT] = enabled
        }
    }

    override suspend fun updateShowBrewingInstructions(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.SHOW_BREWING_INSTRUCTIONS] = enabled
        }
    }

    override suspend fun updateShowEnglishBrewingTerms(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.SHOW_ENGLISH_BREWING_TERMS] = enabled
        }
    }

    override suspend fun updateBloomSpritesheetWeights(weights: Map<String, Int>) {
        context.dataStore.edit { prefs ->
            val persistedWeights = weights
                .mapValues { (_, weight) -> weight.coerceIn(0, 2) }
                .filterValues { weight -> weight != 1 }
                .map { (id, weight) -> "$id=$weight" }
                .toSet()

            if (persistedWeights.isEmpty()) {
                prefs.remove(UserPreferenceKeys.BLOOM_SPRITESHEET_WEIGHTS)
            } else {
                prefs[UserPreferenceKeys.BLOOM_SPRITESHEET_WEIGHTS] = persistedWeights
            }
        }
    }

    override suspend fun updateRatingReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.RATING_REMINDER_ENABLED] = enabled
        }
    }
    override suspend fun updateBrewVibrationTheme(theme: BrewVibrationTheme) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.BREW_VIBRATION_THEME] = theme.name
        }
    }


    override suspend fun updateScanCorrectionLoggingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.SCAN_CORRECTION_LOGGING_ENABLED] = enabled
        }
    }
}
class UserPreferencesRepository(context: Context) :
    UserPreferencesWriter(context),
    BrewingPreferenceStore {

    override val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            // DataStore surfaces read failures (e.g. corrupt prefs file) as
            // IOException. Recover by emitting defaults instead of terminating
            // the flow, which would otherwise leave the whole app without
            // preferences. Any other exception is a real bug and is rethrown.
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val enabledMethods = prefs[UserPreferenceKeys.ENABLED_METHODS]
                ?.mapNotNull { name -> BrewMethod.entries.find { it.name == name } }
                ?.toSet()
                ?: BrewMethod.entries.toSet()
            val requestedDefault = prefs[UserPreferenceKeys.DEFAULT_METHOD]
                ?.let { name -> BrewMethod.entries.find { it.name == name } }
                ?: BrewMethod.PULSAR
            val methodSelection = normalizeMethodSelection(enabledMethods, requestedDefault)
            UserPreferences(
                onboardingCompleted = prefs[UserPreferenceKeys.ONBOARDING_COMPLETED] ?: false,
                enabledMethods = methodSelection.enabledMethods,
                defaultMethod = methodSelection.defaultMethod,
                defaultFilterType = prefs[UserPreferenceKeys.DEFAULT_FILTER_TYPE]
                    ?.let { name -> FilterType.entries.find { it.name == name } }
                    ?.takeIf { methodSelection.enabledMethods.contains(BrewMethod.PULSAR) },
                selectedGrinderId = prefs[UserPreferenceKeys.SELECTED_GRINDER_ID],
                qrLinkExplorerEnabled = prefs[UserPreferenceKeys.QR_LINK_EXPLORER_ENABLED] ?: false,
                lastUsedRatio = prefs[UserPreferenceKeys.LAST_USED_RATIO] ?: 17f,
                defaultInputDirection = prefs[UserPreferenceKeys.DEFAULT_INPUT_DIRECTION] ?: "DOSE",
                skipMethodSelection = prefs[UserPreferenceKeys.SKIP_METHOD_SELECTION] ?: false,
                dimModeEnabled = prefs[UserPreferenceKeys.DIM_MODE_ENABLED] ?: true,
                dimModeTrueBlack = prefs[UserPreferenceKeys.DIM_MODE_TRUE_BLACK] ?: true,
                dimModeReduceBrightness = prefs[UserPreferenceKeys.DIM_MODE_REDUCE_BRIGHTNESS] ?: true,
                dimModeFullscreen = prefs[UserPreferenceKeys.DIM_MODE_FULLSCREEN] ?: true,
                dimModeForceDarkInLight = prefs[UserPreferenceKeys.DIM_MODE_FORCE_DARK_IN_LIGHT] ?: true,
                showBrewingInstructions = prefs[UserPreferenceKeys.SHOW_BREWING_INSTRUCTIONS] ?: true,
                showEnglishBrewingTerms = prefs[UserPreferenceKeys.SHOW_ENGLISH_BREWING_TERMS] ?: false,
                bloomSpritesheetWeights = parseBloomSpritesheetWeights(
                    prefs[UserPreferenceKeys.BLOOM_SPRITESHEET_WEIGHTS].orEmpty(),
                ),
                bloomSpritesheetDisplayCounts = parseBloomSpritesheetDisplayCounts(
                    prefs[UserPreferenceKeys.BLOOM_SPRITESHEET_DISPLAY_COUNTS].orEmpty(),
                ),
                ratingReminderEnabled = prefs[UserPreferenceKeys.RATING_REMINDER_ENABLED] ?: false,
                brewVibrationTheme = prefs[UserPreferenceKeys.BREW_VIBRATION_THEME]
                    ?.let { name -> BrewVibrationTheme.entries.find { it.name == name } }
                    ?: BrewVibrationTheme.CLASSIC,
                scanCorrectionLoggingEnabled = prefs[UserPreferenceKeys.SCAN_CORRECTION_LOGGING_ENABLED] ?: false,
            )
        }
        .distinctUntilChanged()

    override val brewingPreferences: Flow<StableBrewingPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map(::readStableBrewingPreferences)
        .distinctUntilChanged()

    override suspend fun updateBrewingPreferences(preferences: StableBrewingPreferences) {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.ENABLED_BREWER_PROFILE_IDS] = preferences.enabledBrewerProfileIds
            if (preferences.defaultBrewerProfileId == null) {
                prefs.remove(UserPreferenceKeys.DEFAULT_BREWER_PROFILE_ID)
            } else {
                prefs[UserPreferenceKeys.DEFAULT_BREWER_PROFILE_ID] = preferences.defaultBrewerProfileId
            }
            prefs[UserPreferenceKeys.GUIDANCE_BY_METHOD_FAMILY] = encodeKeyValueMap(
                preferences.guidanceByMethodFamilyId,
            )
            prefs[UserPreferenceKeys.GUIDANCE_BY_BREWER_PROFILE] = encodeKeyValueMap(
                preferences.guidanceByBrewerProfileId,
            )
            prefs[UserPreferenceKeys.UTILITY_MODULES_BY_METHOD_FAMILY] = encodeUtilityModules(
                preferences.utilityModulesByMethodFamilyId,
            )
        }
    }

    /**
     * Persists one explicit live-brew guidance choice without rewriting the
     * rest of the stable profile/family maps. Unknown values already stored
     * by a newer app remain untouched.
     */
    suspend fun updateGuidanceForBrewerProfile(
        profileId: String,
        guidanceLevel: String,
    ) {
        context.dataStore.edit { prefs ->
            val existing = parseKeyValueMap(prefs[UserPreferenceKeys.GUIDANCE_BY_BREWER_PROFILE].orEmpty())
            prefs[UserPreferenceKeys.GUIDANCE_BY_BREWER_PROFILE] = encodeKeyValueMap(
                existing + (profileId to guidanceLevel),
            )
        }
    }

    override suspend fun completeOnboarding(
        enabledMethods: Set<BrewMethod>,
        defaultMethod: BrewMethod,
        defaultFilterType: FilterType?,
        selectedGrinderId: String?,
    ) {
        val methodSelection = normalizeMethodSelection(enabledMethods, defaultMethod)
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.ONBOARDING_COMPLETED] = true
            prefs[UserPreferenceKeys.ENABLED_METHODS] = methodSelection.enabledMethods.map { it.name }.toSet()
            prefs[UserPreferenceKeys.DEFAULT_METHOD] = methodSelection.defaultMethod.name
            writeStableSelection(prefs, methodSelection)
            if (defaultFilterType != null &&
                methodSelection.enabledMethods.contains(BrewMethod.PULSAR)
            ) {
                prefs[UserPreferenceKeys.DEFAULT_FILTER_TYPE] = defaultFilterType.name
            } else {
                prefs.remove(UserPreferenceKeys.DEFAULT_FILTER_TYPE)
            }
            if (selectedGrinderId != null) {
                prefs[UserPreferenceKeys.SELECTED_GRINDER_ID] = selectedGrinderId
            } else {
                prefs.remove(UserPreferenceKeys.SELECTED_GRINDER_ID)
            }
        }
    }

    override suspend fun updateMethodSelection(
        enabledMethods: Set<BrewMethod>,
        defaultMethod: BrewMethod,
    ) {
        val methodSelection = normalizeMethodSelection(enabledMethods, defaultMethod)
        context.dataStore.edit { prefs ->
            writeMethodSelection(prefs, methodSelection)
            if (!methodSelection.enabledMethods.contains(BrewMethod.PULSAR)) {
                prefs.remove(UserPreferenceKeys.DEFAULT_FILTER_TYPE)
            }
        }
    }

    suspend fun resetOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[UserPreferenceKeys.ONBOARDING_COMPLETED] = false
        }
    }

    /**
     * Atomically increments the display count for [id] by 1. Used by the
     * brew flow each time a bloom spritesheet is picked, so the domain
     * selector can rotate flowers fairly over time.
     */
    suspend fun incrementBloomSpritesheetDisplayCount(id: String) {
        if (id.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = parseBloomSpritesheetDisplayCounts(
                prefs[UserPreferenceKeys.BLOOM_SPRITESHEET_DISPLAY_COUNTS].orEmpty(),
            ).toMutableMap()
            val nextCount = (existing[id] ?: 0) + 1
            existing[id] = nextCount
            prefs[UserPreferenceKeys.BLOOM_SPRITESHEET_DISPLAY_COUNTS] = existing
                .filterValues { it > 0 }
                .map { (k, v) -> "$k=$v" }
                .toSet()
        }
    }

    /**
     * Clears all spritesheet display counts. Useful from a "reset rotation"
     * settings affordance, or for tests.
     */
    suspend fun resetBloomSpritesheetDisplayCounts() {
        context.dataStore.edit { prefs ->
            prefs.remove(UserPreferenceKeys.BLOOM_SPRITESHEET_DISPLAY_COUNTS)
        }
    }

    private fun parseBloomSpritesheetWeights(entries: Set<String>): Map<String, Int> {
        return entries.mapNotNull { entry ->
            val separatorIndex = entry.indexOf('=')
            if (separatorIndex <= 0 || separatorIndex == entry.lastIndex) return@mapNotNull null

            val id = entry.substring(0, separatorIndex)
            val weight = entry.substring(separatorIndex + 1).toIntOrNull() ?: return@mapNotNull null
            id to weight.coerceIn(0, 2)
        }.toMap()
    }

    private fun parseBloomSpritesheetDisplayCounts(entries: Set<String>): Map<String, Int> {
        return entries.mapNotNull { entry ->
            val separatorIndex = entry.indexOf('=')
            if (separatorIndex <= 0 || separatorIndex == entry.lastIndex) return@mapNotNull null

            val id = entry.substring(0, separatorIndex)
            val count = entry.substring(separatorIndex + 1).toIntOrNull() ?: return@mapNotNull null
            if (count <= 0) return@mapNotNull null
            id to count
        }.toMap()
    }

    private fun writeMethodSelection(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        selection: MethodSelection,
    ) {
        prefs[UserPreferenceKeys.ENABLED_METHODS] = selection.enabledMethods.map { it.name }.toSet()
        prefs[UserPreferenceKeys.DEFAULT_METHOD] = selection.defaultMethod.name
        writeStableSelection(prefs, selection)
    }
    private fun readStableBrewingPreferences(prefs: Preferences): StableBrewingPreferences {
        val enabledProfileIds = prefs[UserPreferenceKeys.ENABLED_BREWER_PROFILE_IDS]
            ?: legacyEnabledBrewerProfileIds(prefs)
        val defaultProfileId = prefs[UserPreferenceKeys.DEFAULT_BREWER_PROFILE_ID]
            ?: legacyDefaultBrewerProfileId(prefs)
        val guidanceByFamily = prefs[UserPreferenceKeys.GUIDANCE_BY_METHOD_FAMILY]
            ?.let(::parseKeyValueMap)
            ?: legacyGuidanceByFamily(enabledProfileIds, prefs[UserPreferenceKeys.SHOW_BREWING_INSTRUCTIONS] ?: true)
        return StableBrewingPreferences(
            enabledBrewerProfileIds = enabledProfileIds,
            defaultBrewerProfileId = defaultProfileId,
            guidanceByMethodFamilyId = guidanceByFamily,
            guidanceByBrewerProfileId = parseKeyValueMap(
                prefs[UserPreferenceKeys.GUIDANCE_BY_BREWER_PROFILE].orEmpty(),
            ),
            utilityModulesByMethodFamilyId = parseUtilityModules(
                prefs[UserPreferenceKeys.UTILITY_MODULES_BY_METHOD_FAMILY].orEmpty(),
            ),
        )
    }

    private fun legacyEnabledBrewerProfileIds(prefs: Preferences): Set<String> {
        val rawMethods = prefs[UserPreferenceKeys.ENABLED_METHODS] ?: BrewMethod.entries.map(BrewMethod::name).toSet()
        return rawMethods.map(::legacyBrewerProfileId).toSet()
    }

    private fun legacyDefaultBrewerProfileId(prefs: Preferences): String =
        legacyBrewerProfileId(prefs[UserPreferenceKeys.DEFAULT_METHOD] ?: BrewMethod.PULSAR.name)

    private fun legacyBrewerProfileId(rawMethodId: String): String = when (
        val resolution = BuiltinBrewingCatalog.instance.resolveLegacyMethod(rawMethodId)
    ) {
        is CatalogResolution.Known -> resolution.value.value
        is CatalogResolution.Unknown -> resolution.rawId
    }

    private fun legacyGuidanceByFamily(
        profileIds: Set<String>,
        showInstructions: Boolean,
    ): Map<String, String> {
        val level = if (showInstructions) "CONCISE" else "FOCUSED"
        return profileIds.mapNotNull { profileId ->
            BuiltinBrewingCatalog.instance.brewerProfiles
                .find { it.id.value == profileId }
                ?.familyId
                ?.value
        }.associateWith { level }
    }

    private fun writeStableSelection(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        selection: MethodSelection,
    ) {
        val profileIds = selection.enabledMethods.map { method ->
            legacyBrewerProfileId(method.name)
        }.toSet()
        prefs[UserPreferenceKeys.ENABLED_BREWER_PROFILE_IDS] = profileIds
        prefs[UserPreferenceKeys.DEFAULT_BREWER_PROFILE_ID] = legacyBrewerProfileId(selection.defaultMethod.name)
        if (prefs[UserPreferenceKeys.GUIDANCE_BY_METHOD_FAMILY] == null) {
            val guidance = legacyGuidanceByFamily(
                profileIds = profileIds,
                showInstructions = prefs[UserPreferenceKeys.SHOW_BREWING_INSTRUCTIONS] ?: true,
            )
            prefs[UserPreferenceKeys.GUIDANCE_BY_METHOD_FAMILY] = encodeKeyValueMap(guidance)
        }
    }

    private fun parseKeyValueMap(entries: Set<String>): Map<String, String> = entries.mapNotNull { entry ->
        val separator = entry.indexOf('=')
        if (separator <= 0 || separator == entry.lastIndex) return@mapNotNull null
        val key = entry.substring(0, separator)
        val value = entry.substring(separator + 1)
        if (key.isBlank() || value.isBlank()) null else key to value
    }.toMap()

    private fun encodeKeyValueMap(values: Map<String, String>): Set<String> = values
        .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
        .map { (key, value) -> "$key=$value" }
        .toSet()

    private fun parseUtilityModules(entries: Set<String>): Map<String, Set<String>> = entries
        .mapNotNull { entry ->
            val separator = entry.indexOf('=')
            if (separator <= 0 || separator == entry.lastIndex) return@mapNotNull null
            entry.substring(0, separator) to entry.substring(separator + 1)
        }
        .filter { (familyId, moduleId) -> familyId.isNotBlank() && moduleId.isNotBlank() }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, modules) -> modules.toSet() }

    private fun encodeUtilityModules(values: Map<String, Set<String>>): Set<String> = values
        .flatMap { (familyId, moduleIds) ->
            moduleIds.filter(String::isNotBlank).map { moduleId -> "$familyId=$moduleId" }
        }
        .filter { entry -> entry.indexOf('=') > 0 }
        .toSet()

}
