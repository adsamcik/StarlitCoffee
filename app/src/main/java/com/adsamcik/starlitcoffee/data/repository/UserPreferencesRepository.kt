package com.adsamcik.starlitcoffee.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val enabledMethods: Set<BrewMethod> = BrewMethod.entries.toSet(),
    val defaultMethod: BrewMethod = BrewMethod.PULSAR,
    val defaultFilterType: FilterType? = null,
    val selectedGrinderId: String? = null,
    val qrLinkExplorerEnabled: Boolean = false,
    val lastUsedRatio: Float = 17f,
    val defaultInputDirection: String = "WATER",
    val skipMethodSelection: Boolean = false,
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
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
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
            defaultInputDirection = prefs[Keys.DEFAULT_INPUT_DIRECTION] ?: "WATER",
            skipMethodSelection = prefs[Keys.SKIP_METHOD_SELECTION] ?: false,
        )
    }

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

    suspend fun resetOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = false
        }
    }
}
