package com.adsamcik.starlitcoffee.ui.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoffeeCodeDecoderInstrumentedTest {
    @Test
    fun importedProductPhotoUsesBundledDecoderAndRetainsCompleteIdentifier() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = File.createTempFile("coffee-code-", ".png", context.cacheDir)
        val bitmap = ean13Fixture()
        try {
            photo.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
            val codes = readCoffeeCodes(context, Uri.fromFile(photo).toString())
            assertEquals(listOf(ScannedCoffeeCode("5901234123457", isWebLink = false)), codes)
            val artifacts = File(requireNotNull(context.getExternalFilesDir(null)), "unified-coffee-scan")
            check(artifacts.isDirectory || artifacts.mkdirs())
            photo.copyTo(File(artifacts, "coffee-code-fixture.png"), overwrite = true)
        } finally {
            bitmap.recycle()
            photo.delete()
        }
    }

    private fun ean13Fixture(): Bitmap {
        // EAN-13 5901234123457: start + LGGLLG left digits + middle + right digits + end.
        val modules = "101" +
            "0001011" + "0100111" + "0110011" + "0010011" + "0111101" + "0011101" +
            "01010" +
            "1100110" + "1101100" + "1000010" + "1011100" + "1001110" + "1000100" +
            "101"
        val scale = 6
        val quietZone = 12
        val bitmap = Bitmap.createBitmap((modules.length + quietZone * 2) * scale, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK }
        modules.forEachIndexed { index, bit ->
            if (bit == '1') {
                val left = ((index + quietZone) * scale).toFloat()
                canvas.drawRect(left, 30f, left + scale, 330f, paint)
            }
        }
        return bitmap
    }
}
