package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.testutil.FakeBrewLogDao
import com.adsamcik.starlitcoffee.testutil.FakeFlavorTagDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewLogRepositoryMeasurementTest {
    @Test
    fun `measurement updates preserve complete physically possible pairs`() = runTest {
        val logDao = FakeBrewLogDao()
        val repository = BrewLogRepository(
            brewLogDao = logDao,
            flavorTagDao = FakeFlavorTagDao(),
        )
        val logId = repository.insertLog(
            BrewLogEntity(
                method = "V60",
                doseG = 20f,
                waterG = 340f,
                ratio = 17f,
            ),
        )

        assertFalse(repository.updateMeasurements(logId, 340f, null))
        assertFalse(repository.updateMeasurements(logId, Float.NaN, 295f))
        assertFalse(repository.updateMeasurements(logId, 295f, 296f))
        assertNull(repository.getLogById(logId)?.measuredWaterInputG)
        assertNull(repository.getLogById(logId)?.measuredBeverageOutputG)

        assertTrue(repository.updateMeasurements(logId, 339.6f, 296.4f))
        assertEquals(339.6f, repository.getLogById(logId)?.measuredWaterInputG ?: 0f, 0.001f)
        assertEquals(296.4f, repository.getLogById(logId)?.measuredBeverageOutputG ?: 0f, 0.001f)

        assertTrue(repository.updateMeasurements(logId, null, null))
        assertNull(repository.getLogById(logId)?.measuredWaterInputG)
        assertNull(repository.getLogById(logId)?.measuredBeverageOutputG)
    }
}
