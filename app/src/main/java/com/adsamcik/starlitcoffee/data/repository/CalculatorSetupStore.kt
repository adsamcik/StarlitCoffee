package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import kotlinx.coroutines.flow.Flow

/** Calculator preferences, with set writes and a legacy per-method migration boundary. */
interface CalculatorSetupStore : BrewingSetWriter {
    val userPreferences: Flow<UserPreferences>

    suspend fun updateCalculatorSetup(method: BrewMethod, setup: CalculatorSetup)
}
