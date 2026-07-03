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
import com.adsamcik.starlitcoffee.data.model.BrewMethod
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
    val bloomSpritesheetWeights: Map<String, Int> = emptyMap(),
    // How many times each spritesheet has been picked for a brew. Used by the
    // domain selector to bias future picks toward under-shown flowers, so
    // every flower in the user's allow-list gets fair rotation over many brews.
    val bloomSpritesheetDisplayCounts: Map<String, Int> = emptyMap(),
    // When true, schedule a notification ~30 minutes after a brew is logged
    // asking the user to rate it with 5 emojis. Opt-in because it needs
    // POST_NOTIFICATIONS permission on Android 13+ and not every user wants it.
    val ratingReminderEnabled: Boolean = false,
    // When true, the bag-scan review screen records, on-device, a diff between
    // what the model extracted and what the user finally saved. This is a
    // privacy-sensitive quality signal (it captures edited label metadata), so
    // it is strictly opt-in and stays on the device — nothing is uploaded.
    val scanCorrectionLoggingEnabled: Boolean = false,
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
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
        val BLOOM_SPRITESHEET_WEIGHTS = stringSetPreferencesKey("bloom_spritesheet_weights")
        val BLOOM_SPRITESHEET_DISPLAY_COUNTS = stringSetPreferencesKey("bloom_spritesheet_display_counts")
        val RATING_REMINDER_ENABLED = booleanPreferencesKey("rating_reminder_enabled")
        val SCAN_CORRECTION_LOGGING_ENABLED = booleanPreferencesKey("scan_correction_logging_enabled")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data
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
            UserPreferences(
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
                enabledMethods = prefs[Keys.ENABLED_METHODS]
                    ?.mapNotNull { name -> BrewMethod.entries.find { it.name == name } }
                    ?.toSet()
                    ?: BrewMethod.entries.toSet(),
                defaultMethod = prefs[Keys.DEFAULT_METHOD]
                    ?.let { name -> BrewMethod.entries.find { it.name == name } }
                    ?: BrewMethod.PULSAR,
                defaultFilterType = prefs[Keys.DEFAULT_FILTER_TYPE]
                    ?.let { name -> FilterType.entries.find { it.name == name } },
                selectedGrinderId = prefs[Keys.SELECTED_GRINDER_ID],
                qrLinkExplorerEnabled = prefs[Keys.QR_LINK_EXPLORER_ENABLED] ?: false,
                lastUsedRatio = prefs[Keys.LAST_USED_RATIO] ?: 17f,
                defaultInputDirection = prefs[Keys.DEFAULT_INPUT_DIRECTION] ?: "DOSE",
                skipMethodSelection = prefs[Keys.SKIP_METHOD_SELECTION] ?: false,
                dimModeEnabled = prefs[Keys.DIM_MODE_ENABLED] ?: true,
                dimModeTrueBlack = prefs[Keys.DIM_MODE_TRUE_BLACK] ?: true,
                dimModeReduceBrightness = prefs[Keys.DIM_MODE_REDUCE_BRIGHTNESS] ?: true,
                dimModeFullscreen = prefs[Keys.DIM_MODE_FULLSCREEN] ?: true,
                dimModeForceDarkInLight = prefs[Keys.DIM_MODE_FORCE_DARK_IN_LIGHT] ?: true,
                showBrewingInstructions = prefs[Keys.SHOW_BREWING_INSTRUCTIONS] ?: true,
                bloomSpritesheetWeights = parseBloomSpritesheetWeights(
                    prefs[Keys.BLOOM_SPRITESHEET_WEIGHTS].orEmpty(),
                ),
                bloomSpritesheetDisplayCounts = parseBloomSpritesheetDisplayCounts(
                    prefs[Keys.BLOOM_SPRITESHEET_DISPLAY_COUNTS].orEmpty(),
                ),
                ratingReminderEnabled = prefs[Keys.RATING_REMINDER_ENABLED] ?: false,
                scanCorrectionLoggingEnabled = prefs[Keys.SCAN_CORRECTION_LOGGING_ENABLED] ?: false,
            )
        }
        .distinctUntilChanged()

    suspend fun completeOnboarding(
        enabledMethods: Set<BrewMethod>,
        defaultMethod: BrewMethod,
        defaultFilterType: FilterType?,
        selectedGrinderId: String?,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = true
            prefs[Keys.ENABLED_METHODS] = enabledMethods.map { it.name }.toSet()
            prefs[Keys.DEFAULT_METHOD] = defaultMethod.name
            if (defaultFilterType != null) {
                prefs[Keys.DEFAULT_FILTER_TYPE] = defaultFilterType.name
            } else {
                prefs.remove(Keys.DEFAULT_FILTER_TYPE)
            }
            if (selectedGrinderId != null) {
                prefs[Keys.SELECTED_GRINDER_ID] = selectedGrinderId
            } else {
                prefs.remove(Keys.SELECTED_GRINDER_ID)
            }
        }
    }

    suspend fun updateEnabledMethods(methods: Set<BrewMethod>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ENABLED_METHODS] = methods.map { it.name }.toSet()
        }
    }

    suspend fun updateDefaultMethod(method: BrewMethod) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_METHOD] = method.name
        }
    }

    suspend fun updateDefaultFilterType(filterType: FilterType?) {
        context.dataStore.edit { prefs ->
            if (filterType != null) {
                prefs[Keys.DEFAULT_FILTER_TYPE] = filterType.name
            } else {
                prefs.remove(Keys.DEFAULT_FILTER_TYPE)
            }
        }
    }

    suspend fun updateSelectedGrinder(grinderId: String?) {
        context.dataStore.edit { prefs ->
            if (grinderId != null) {
                prefs[Keys.SELECTED_GRINDER_ID] = grinderId
            } else {
                prefs.remove(Keys.SELECTED_GRINDER_ID)
            }
        }
    }

    suspend fun updateQrLinkExplorerEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.QR_LINK_EXPLORER_ENABLED] = enabled
        }
    }

    suspend fun updateLastUsedRatio(ratio: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_USED_RATIO] = ratio
        }
    }

    suspend fun updateDefaultInputDirection(direction: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_INPUT_DIRECTION] = direction
        }
    }

    suspend fun updateSkipMethodSelection(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SKIP_METHOD_SELECTION] = enabled
        }
    }

    suspend fun updateDimModeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DIM_MODE_ENABLED] = enabled
        }
    }

    suspend fun updateDimModeTrueBlack(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DIM_MODE_TRUE_BLACK] = enabled
        }
    }

    suspend fun updateDimModeReduceBrightness(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DIM_MODE_REDUCE_BRIGHTNESS] = enabled
        }
    }

    suspend fun updateDimModeFullscreen(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DIM_MODE_FULLSCREEN] = enabled
        }
    }

    suspend fun updateDimModeForceDarkInLight(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DIM_MODE_FORCE_DARK_IN_LIGHT] = enabled
        }
    }

    suspend fun updateShowBrewingInstructions(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_BREWING_INSTRUCTIONS] = enabled
        }
    }

    suspend fun updateBloomSpritesheetWeights(weights: Map<String, Int>) {
        context.dataStore.edit { prefs ->
            val persistedWeights = weights
                .mapValues { (_, weight) -> weight.coerceIn(0, 2) }
                .filterValues { weight -> weight != 1 }
                .map { (id, weight) -> "$id=$weight" }
                .toSet()

            if (persistedWeights.isEmpty()) {
                prefs.remove(Keys.BLOOM_SPRITESHEET_WEIGHTS)
            } else {
                prefs[Keys.BLOOM_SPRITESHEET_WEIGHTS] = persistedWeights
            }
        }
    }

    suspend fun updateRatingReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RATING_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun updateScanCorrectionLoggingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCAN_CORRECTION_LOGGING_ENABLED] = enabled
        }
    }

    suspend fun resetOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = false
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
                prefs[Keys.BLOOM_SPRITESHEET_DISPLAY_COUNTS].orEmpty(),
            ).toMutableMap()
            val nextCount = (existing[id] ?: 0) + 1
            existing[id] = nextCount
            prefs[Keys.BLOOM_SPRITESHEET_DISPLAY_COUNTS] = existing
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
            prefs.remove(Keys.BLOOM_SPRITESHEET_DISPLAY_COUNTS)
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
}
