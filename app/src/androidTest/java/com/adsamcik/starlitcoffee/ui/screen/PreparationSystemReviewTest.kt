package com.adsamcik.starlitcoffee.ui.screen

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.GrindSaveScope
import com.adsamcik.starlitcoffee.data.model.GrindSettingSource
import com.adsamcik.starlitcoffee.data.repository.*
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreparationSystemReviewTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var brew: BrewViewModel
    private val store = ViewModelStore()
    private var firstId = 0L
    private var secondId = 0L
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = CoffeeBagRepository(db.coffeeBagDao(), db.coffeeIdentityDao(), TransactionRunner.room(db))
        firstId = repository.insertBag(CoffeeBagEntity(name = "Gedeb Chelbesa", roaster = "Beansmith's", weightG = 180f,
            initialWeightG = 250f, status = "OPEN", barcode = "123456789012"))
        val first = requireNotNull(repository.getBagByIdOnce(firstId))
        secondId = repository.insertBag(first.copy(id = 0L, status = "SEALED", openedDate = null, weightG = 250f))
        brew = BrewViewModel(coffeeBagRepository = repository, grindMemoryRepository = GrindMemoryRepository(db.grindMemoryDao()),
            grinderData = com.adsamcik.starlitcoffee.data.model.GrinderDataSource.getInstance(context))
        store.put("brew", brew)
        brew.setMethod(BrewMethod.PULSAR)
        brew.setFilterType(com.adsamcik.starlitcoffee.data.model.FilterType.PAPER)
        brew.setAmount("16.5"); brew.setGrinder("1zpresso-zp6-special")
    }
    @After fun close() { rule.runOnIdle { store.clear() }; db.close() }
    private fun render(narrow: Boolean = false, scanned: String? = null) {
        rule.waitUntil(10_000) { brew.coffeeBags.value.size == 2 }
        rule.runOnIdle { brew.selectBag(firstId) }
        rule.setContent {
            val density = LocalDensity.current
            val width = LocalWindowInfo.current.containerSize.width / density.density
            CompositionLocalProvider(LocalDensity provides Density(
                if (narrow) density.density * width / 320f else density.density, if (narrow) 1.6f else 1f,
            )) {
                StarlitCoffeeTheme(darkTheme = true, dynamicColor = false) {
                    GrindPrepScreen(brew, dimModeEnabled = false, onBack = {}, onNavigateToBrew = {},
                        scannedChooseBarcode = scanned, onScanToChoose = {})
                }
            }
        }
    }
    @Test fun packPickerAndGrindEditorPersistExactScopeWithoutOpening() {
        render()
        save("prepare-native-coffee")
        rule.onNodeWithTag("prep_coffee_selector").performClick()
        rule.onNodeWithTag("prep_pack_$secondId").assertIsDisplayed()
        save("prepare-native-pack-picker", "prep_pack_picker")
        rule.onNodeWithTag("prep_pack_$secondId").performClick()
        rule.waitUntil { brew.selectedBagId.value == secondId }
        assertEquals(BrewMethod.PULSAR, brew.uiState.value.method)
        assertEquals("SEALED", runBlocking { db.coffeeBagDao().getByIdOnce(secondId) }?.status)
        rule.onNodeWithTag("prep_edit_grind").performClick()
        rule.onNodeWithTag("prep_grind_input").performTextReplacement("1.14")
        rule.onNode(hasText(context.getString(R.string.prep_grind_source_recommendation)) and
            hasAnyAncestor(hasTestTag("prep_grind_editor"))).assertDoesNotExist()
        rule.onNodeWithTag("prep_grind_scope_disclosure").performClick()
        rule.onNodeWithTag("prep_grind_scope_pack").performScrollTo().performClick()
        save("prepare-native-grind-scope", "prep_grind_editor")
        rule.onNodeWithTag("prep_grind_save").performScrollTo().performClick()
        rule.waitUntil(10_000) { brew.uiState.value.preparedGrind.source == GrindSettingSource.PACK }
        assertEquals("1.14", brew.uiState.value.preparedGrind.displayValue)
        rule.onNodeWithTag("prep_coffee_selector").performClick()
        rule.onNodeWithTag("prep_pack_$firstId").performClick()
        rule.waitUntil { brew.selectedBagId.value == firstId }
        assertEquals(GrindSettingSource.RECOMMENDATION, brew.uiState.value.preparedGrind.source)
    }
    @Test fun newPackReviewStartsBlankAndAddsAnIndependentPack() {
        render()
        rule.onNodeWithTag("prep_coffee_selector").performClick()
        rule.onNodeWithTag("prep_add_pack").performScrollTo().performClick()
        val coffeeId = runBlocking { requireNotNull(db.coffeeBagDao().getByIdOnce(firstId)?.coffeeId) }
        rule.onNodeWithTag("prep_rebuy_coffee_$coffeeId").performScrollTo().performClick()
        rule.onNodeWithTag("prep_new_pack_weight").assertTextContains("")
        save("prepare-native-new-pack", "prep_new_pack_sheet")
        rule.onNodeWithTag("prep_new_pack_weight").performTextReplacement("500")
        rule.onNodeWithTag("prep_new_pack_commit").performScrollTo().performClick()
        rule.waitUntil(10_000) { brew.coffeeBags.value.size == 3 }
        val fresh = brew.coffeeBags.value.single { it.id != firstId && it.id != secondId }
        assertEquals(coffeeId, fresh.coffeeId); assertEquals(500f, fresh.weightG)
        assertEquals("SEALED", fresh.status); assertEquals(3, fresh.packNumber)
        assertEquals(180f, brew.coffeeBags.value.single { it.id == firstId }.weightG)
    }
    @Test fun scanToChooseRequiresExplicitPhysicalPackAndDoesNotOpenIt() {
        render(scanned = "123456789012")
        rule.onNodeWithTag("barcode_brew_pack_$secondId").assertIsDisplayed().performClick()
        rule.waitUntil { brew.selectedBagId.value == secondId }
        assertEquals("SEALED", runBlocking { db.coffeeBagDao().getByIdOnce(secondId) }?.status)
    }
    @Test fun narrowLargeTextGrindScopeAndOpenedNewPackRemainReachable() {
        render(narrow = true)
        save("prepare-native-narrow")
        rule.onNodeWithTag("prep_edit_grind").performScrollTo().performClick()
        rule.onNodeWithTag("prep_grind_scope_disclosure").performScrollTo().performClick()
        rule.onNodeWithTag("prep_grind_scope_coffee").performScrollTo().assertIsDisplayed()
        save("prepare-native-grind-narrow", "prep_grind_editor")
        rule.onNodeWithTag("prep_grind_save").performScrollTo().assertIsDisplayed().performClick()
        rule.waitUntil(10_000) { brew.uiState.value.preparedGrind.saveRevision > 0 }
        rule.onNodeWithTag("prep_coffee_selector").performClick()
        rule.onNodeWithTag("prep_add_pack").performScrollTo().performClick()
        val coffeeId = runBlocking { requireNotNull(db.coffeeBagDao().getByIdOnce(firstId)?.coffeeId) }
        rule.onNodeWithTag("prep_rebuy_coffee_$coffeeId").performScrollTo().performClick()
        rule.onNodeWithTag("prep_new_pack_weight").performTextReplacement("250")
        rule.onNodeWithText(context.getString(R.string.prep_new_pack_opened)).performScrollTo().performClick()
        rule.onNodeWithTag("prep_new_pack_remaining").performScrollTo().performTextReplacement("150")
        save("prepare-native-open-pack-narrow", "prep_new_pack_sheet")
        rule.onNodeWithTag("prep_new_pack_commit").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }
    private fun save(name: String, tag: String? = null) {
        val dir = File(context.getExternalFilesDir(null), "brewing-review").apply { mkdirs() }
        val node = if (tag == null) rule.onRoot() else rule.onNodeWithTag(tag)
        File(dir, "$name.png").outputStream().use {
            node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
