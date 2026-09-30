package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import kotlinx.coroutines.flow.Flow

/** Writes one method at a time so concurrent preference changes cannot erase other setups. */
interface CalculatorSetupStore {
    val userPreferences: Flow<UserPreferences>

    suspend fun updateCalculatorSetup(method: BrewMethod, setup: CalculatorSetup)
}
