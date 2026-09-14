package com.gigscope.auditor.data.local

import com.gigscope.auditor.domain.model.OnePayDeposit
import com.gigscope.auditor.domain.model.OnePayTransactionType
import com.gigscope.auditor.domain.model.PhotoOfferRecord
import com.gigscope.auditor.domain.model.SparkCompletedTrip
import com.gigscope.auditor.domain.model.SparkEarningsBreakdown
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ResultExportersTest {

    private val startDate = LocalDate.of(2026, 9, 1)
    private val endDate = LocalDate.of(2026, 9, 13)

    @Test
    fun testExportSparkResultsJson_serializesTripsAndEarnings() {
        val trips = listOf(
            SparkCompletedTrip(
                tripId = "TRIP-101",
                tripDate = LocalDate.of(2026, 9, 10),
                initialOfferedTip = 7.50,
                customerDropDetails = "Stop 1: 123 Main St, Apt 4B • Stop 2: 456 Oak Rd",
                completedTime = "11:30 AM",
                tripType = "Curbside Pickup",
                stopCount = 2,
                rawTotalEstimate = 22.50,
                rawText = "Trip #TRIP-101 | 2 Stops | Curbside | $22.50 | Customer Tip $7.50"
            )
        )

        val earnings = listOf(
            SparkEarningsBreakdown(
                tripId = "TRIP-101",
                date = LocalDate.of(2026, 9, 11),
                basePay = 15.00,
                confirmedTip = 7.50,
                totalEarnings = 25.00,
                timestamp = "12:15 PM",
                extraEarnings = 2.50,
                rawEarningDetails = "Base Pay: $15.00 | Confirmed Tip: $7.50 | Extra Earnings: $2.50"
            )
        )

        val jsonStr = ResultExporters.exportSparkResultsJson(startDate, endDate, trips, earnings)
        val json = JSONObject(jsonStr)

        assertEquals("Spark Driver", json.getString("application"))
        assertEquals("2026-09-01", json.getJSONObject("dateRange").getString("startDate"))
        assertEquals("2026-09-13", json.getJSONObject("dateRange").getString("endDate"))

        val summary = json.getJSONObject("summary")
        assertEquals(1, summary.getInt("totalTripsCount"))
        assertEquals(1, summary.getInt("totalEarningsRecordsCount"))
        assertEquals(25.0, summary.getDouble("totalEarnings"), 0.001)
        assertEquals(15.0, summary.getDouble("totalBasePay"), 0.001)
        assertEquals(7.5, summary.getDouble("totalConfirmedTips"), 0.001)
        assertEquals(2.5, summary.getDouble("totalExtraEarnings"), 0.001)

        val tripsArr = json.getJSONArray("trips")
        assertEquals(1, tripsArr.length())
        val tripObj = tripsArr.getJSONObject(0)
        assertEquals("TRIP-101", tripObj.getString("tripId"))
        assertEquals("Curbside Pickup", tripObj.getString("tripType"))
        assertEquals(2, tripObj.getInt("stopCount"))
        assertTrue(tripObj.getString("customerDropDetails").contains("123 Main St"))
        assertEquals("11:30 AM", tripObj.getString("completedTime"))

        val earnArr = json.getJSONArray("earnings")
        assertEquals(1, earnArr.length())
        val earnObj = earnArr.getJSONObject(0)
        assertEquals("TRIP-101", earnObj.getString("tripId"))
        assertEquals(2.50, earnObj.getDouble("extraEarnings"), 0.001)
        assertEquals("12:15 PM", earnObj.getString("timestamp"))
    }

    @Test
    fun testExportOnePayResultsJson_separatesTripEarningsAndTipsWithTimestamps() {
        val deposits = listOf(
            OnePayDeposit(
                referenceId = "OP-TX-9901",
                date = LocalDate.of(2026, 9, 10),
                amount = 18.50,
                sender = "Spark Driver / Walmart",
                transactionType = OnePayTransactionType.TRIP_EARNING,
                timestamp = "2:15 PM",
                rawDescription = "Walmart Delivery Direct Deposit +$18.50 2:15 PM"
            ),
            OnePayDeposit(
                referenceId = "OP-TX-9902",
                date = LocalDate.of(2026, 9, 11),
                amount = 10.00,
                sender = "Spark Driver / Walmart",
                transactionType = OnePayTransactionType.TIP_DEPOSIT,
                timestamp = "3:30 PM",
                rawDescription = "Spark Customer Tip Deposit +$10.00 3:30 PM"
            )
        )

        val jsonStr = ResultExporters.exportOnePayResultsJson(startDate, endDate, deposits)
        val json = JSONObject(jsonStr)

        assertEquals("OnePay (One Finance)", json.getString("application"))
        val summary = json.getJSONObject("summary")
        assertEquals(2, summary.getInt("totalTransactionsCount"))
        assertEquals(1, summary.getInt("tripEarningsCount"))
        assertEquals(18.50, summary.getDouble("tripEarningsTotal"), 0.001)
        assertEquals(1, summary.getInt("tipDepositsCount"))
        assertEquals(10.00, summary.getDouble("tipDepositsTotal"), 0.001)

        val tripEarningsArr = json.getJSONArray("tripEarnings")
        assertEquals(1, tripEarningsArr.length())
        val tx1 = tripEarningsArr.getJSONObject(0)
        assertEquals("OP-TX-9901", tx1.getString("referenceId"))
        assertEquals("2:15 PM", tx1.getString("timestamp"))
        assertEquals("TRIP_EARNING", tx1.getString("transactionType"))

        val tipDepositsArr = json.getJSONArray("tipDeposits")
        assertEquals(1, tipDepositsArr.length())
        val tx2 = tipDepositsArr.getJSONObject(0)
        assertEquals("OP-TX-9902", tx2.getString("referenceId"))
        assertEquals("3:30 PM", tx2.getString("timestamp"))
        assertEquals("TIP_DEPOSIT", tx2.getString("transactionType"))
    }

    @Test
    fun testExportPhotosResultsJson_serializesExtractedTextAndTripOffers() {
        val offers = listOf(
            PhotoOfferRecord(
                tripId = "TRIP-555",
                offeredTip = 12.00,
                basePay = 14.50,
                captureDate = LocalDate.of(2026, 9, 12),
                timestamp = "10:14 AM",
                imageUri = "content://media/external/images/media/4501",
                isAccepted = true,
                extractedText = "Offer: Trip #TRIP-555 | Spark Driver | Delivery Pay $14.50 | Estimated Tip $12.00 | Total $26.50",
                estimatedTotal = 26.50
            )
        )

        val jsonStr = ResultExporters.exportPhotosResultsJson(startDate, endDate, offers)
        val json = JSONObject(jsonStr)

        assertEquals("Google Photos", json.getString("application"))
        val summary = json.getJSONObject("summary")
        assertEquals(1, summary.getInt("totalScreenshotsExtracted"))
        assertEquals(14.50, summary.getDouble("totalEstimatedPay"), 0.001)
        assertEquals(12.00, summary.getDouble("totalOfferedTips"), 0.001)

        val extractions = json.getJSONArray("screenshotExtractions")
        assertEquals(1, extractions.length())
        val extraction = extractions.getJSONObject(0)
        assertEquals("TRIP-555", extraction.getString("tripId"))
        assertEquals(26.50, extraction.getDouble("estimatedTotal"), 0.001)
        assertEquals("10:14 AM", extraction.getString("timestamp"))
        assertTrue(extraction.getString("extractedText").contains("Delivery Pay $14.50"))
    }
}
