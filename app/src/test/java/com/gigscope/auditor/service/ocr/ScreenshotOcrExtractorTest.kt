package com.gigscope.auditor.service.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ScreenshotOcrExtractorTest {

    @Test
    fun testExtractDateFromFile() {
        val file1 = File("/sdcard/Pictures/Screenshots/Screenshot_20260915-071514_Spark Driver.png")
        val date1 = ScreenshotOcrExtractor.extractDateFromFile(file1)
        assertNotNull(date1)
        assertEquals(LocalDate.of(2026, 9, 15), date1)

        val file2 = File("/sdcard/Pictures/Screenshots/Screenshot_20260914-181505_Spark Driver.png")
        val date2 = ScreenshotOcrExtractor.extractDateFromFile(file2)
        assertNotNull(date2)
        assertEquals(LocalDate.of(2026, 9, 14), date2)
    }

    @Test
    fun testParseSparkOfferTamara() {
        val ocrText = """
            07:15:13
            2 stops • 3.8 miles • 19 mins
            Estimated total $11.50
            Base $10.50
            Boost $1.00
            Customers may add tips later. After offer acceptance, customers may add items including heavy/bulky items. You'll be notified and can cancel.
            2 stops 2 items (2 qty)
            Shopping
            ASAP (arrive within 25 mins)
            Walmart SAN RAMON #5610
            9100 ALCOSTA BLVD
            SAN RAMON, CA 94583-3857
            Express Shopping
            7 mins estimated shopping time
            3.8 miles
            Drop-off
            7:31 AM
            Tamara B.
            721 Amberstone Ln
            San Ramon, CA 94582
            Apartment
            Requires customer verification
            REJECT ACCEPT
        """.trimIndent()

        val record = ScreenshotOcrExtractor.parseOfferText(
            text = ocrText,
            fileDate = LocalDate.of(2026, 9, 15),
            imageUri = "file:///sdcard/Pictures/Screenshots/Screenshot_20260915-071514_Spark Driver.png",
            fileName = "Screenshot_20260915-071514_Spark Driver.png"
        )

        assertEquals(11.50, record.estimatedTotal ?: 0.0, 0.001)
        assertEquals(10.50, record.basePay, 0.001)
        assertEquals(1.00, record.offeredTip, 0.001)
        assertEquals("Tamara B.", record.customerName)
        assertTrue(record.dropoffAddress?.contains("721 Amberstone Ln") == true)
        assertTrue(record.dropoffAddress?.contains("San Ramon, CA 94582") == true)
        assertTrue(record.storeName?.contains("Walmart SAN RAMON #5610") == true)
        assertEquals(2, record.stopCount)
        assertEquals(3.8, record.distanceMiles ?: 0.0, 0.001)
        assertEquals("7:31 AM", record.timestamp)
    }

    @Test
    fun testParseSparkOfferDeepika() {
        val ocrText = """
            18:15:05
            2 stops • 23.7 miles • 57 mins
            Estimated total $29.11
            Base $29.11
            Customers may add tips later.
            2 stops 21 items (58 qty)
            Shopping
            Walmart SAN RAMON #5610
            9100 ALCOSTA BLVD
            23.7 miles
            Drop-off
            6:46 PM
            Deepika B.
            5000 woodminster lane
            Oakland, CA 94602
            REJECT ACCEPT
        """.trimIndent()

        val record = ScreenshotOcrExtractor.parseOfferText(
            text = ocrText,
            fileDate = LocalDate.of(2026, 9, 14),
            imageUri = "file:///sdcard/Pictures/Screenshots/Screenshot_20260914-181505_Spark Driver.png",
            fileName = "Screenshot_20260914-181505_Spark Driver.png"
        )

        assertEquals(29.11, record.estimatedTotal ?: 0.0, 0.001)
        assertEquals(29.11, record.basePay, 0.001)
        assertEquals(0.00, record.offeredTip, 0.001)
        assertEquals("Deepika B.", record.customerName)
        assertTrue(record.dropoffAddress?.contains("5000 woodminster lane") == true)
        assertTrue(record.dropoffAddress?.contains("Oakland, CA 94602") == true)
        assertEquals("6:46 PM", record.timestamp)
        assertEquals(2, record.stopCount)
        assertEquals(23.7, record.distanceMiles ?: 0.0, 0.001)
    }
}
