package com.gigscope.auditor.domain.reconciliation

import com.gigscope.auditor.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TipReconciliationEngineTest {

    private val engine = TipReconciliationEngine()

    private val today = LocalDate.of(2026, 9, 12)
    private val startDate = LocalDate.of(2026, 9, 1)
    private val endDate = LocalDate.of(2026, 9, 12)

    @Test
    fun `identifies full tip removal when customer reduces tip to zero`() {
        val completedTrips = listOf(
            SparkCompletedTrip(
                tripId = "TRIP-101",
                tripDate = today.minusDays(2),
                initialOfferedTip = 10.00,
                customerDropDetails = "Stop 1: 123 Main St (Alice)"
            )
        )

        val earnings = listOf(
            SparkEarningsBreakdown(
                tripId = "TRIP-101",
                date = today.minusDays(1),
                basePay = 8.50,
                confirmedTip = 0.00,
                totalEarnings = 8.50
            )
        )

        val discrepancies = engine.reconcile(
            startDate = startDate,
            endDate = endDate,
            completedTrips = completedTrips,
            earningsBreakdowns = earnings,
            onePayDeposits = emptyList(),
            screenshotOffers = emptyList()
        )

        assertEquals(1, discrepancies.size)
        val item = discrepancies.first()
        assertEquals("TRIP-101", item.tripId)
        assertEquals(10.00, item.offeredTip, 0.001)
        assertEquals(0.00, item.finalReceivedTip, 0.001)
        assertEquals(10.00, item.lossAmount, 0.001)
        assertEquals(DiscrepancyType.FULL_REMOVAL, item.discrepancyType)
        assertTrue(item.customerIdentifier.contains("Alice"))
    }

    @Test
    fun `identifies partial tip reduction when customer lowers tip`() {
        val completedTrips = listOf(
            SparkCompletedTrip(
                tripId = "TRIP-102",
                tripDate = today.minusDays(3),
                initialOfferedTip = 15.00,
                customerDropDetails = "Stop 2: 456 Elm St (Bob)"
            )
        )

        val earnings = listOf(
            SparkEarningsBreakdown(
                tripId = "TRIP-102",
                date = today.minusDays(2),
                basePay = 9.00,
                confirmedTip = 5.00,
                totalEarnings = 14.00
            )
        )

        val discrepancies = engine.reconcile(
            startDate = startDate,
            endDate = endDate,
            completedTrips = completedTrips,
            earningsBreakdowns = earnings,
            onePayDeposits = emptyList(),
            screenshotOffers = emptyList()
        )

        assertEquals(1, discrepancies.size)
        val item = discrepancies.first()
        assertEquals(15.00, item.offeredTip, 0.001)
        assertEquals(5.00, item.finalReceivedTip, 0.001)
        assertEquals(10.00, item.lossAmount, 0.001)
        assertEquals(DiscrepancyType.PARTIAL_REDUCTION, item.discrepancyType)
    }

    @Test
    fun `discards screenshots for offers that were never accepted`() {
        // Screenshot exists in Google Photos, but driver didn't accept/take it
        val screenshotOffers = listOf(
            PhotoOfferRecord(
                tripId = "TRIP-UNACCEPTED-999",
                offeredTip = 20.00,
                basePay = 10.00,
                captureDate = today.minusDays(2),
                isAccepted = false
            )
        )

        // Spark completed trips does NOT contain TRIP-UNACCEPTED-999
        val completedTrips = listOf(
            SparkCompletedTrip(
                tripId = "TRIP-200",
                tripDate = today.minusDays(2),
                initialOfferedTip = 7.00,
                customerDropDetails = "Stop 1: 789 Oak St (Charlie)"
            )
        )

        val earnings = listOf(
            SparkEarningsBreakdown(
                tripId = "TRIP-200",
                date = today.minusDays(1),
                basePay = 8.00,
                confirmedTip = 7.00, // Exact match
                totalEarnings = 15.00
            )
        )

        val discrepancies = engine.reconcile(
            startDate = startDate,
            endDate = endDate,
            completedTrips = completedTrips,
            earningsBreakdowns = earnings,
            onePayDeposits = emptyList(),
            screenshotOffers = screenshotOffers
        )

        // The unaccepted offer should be completely discarded and no discrepancy reported
        assertTrue(discrepancies.isEmpty())
    }

    @Test
    fun `ignores tip increase or exact match without reporting loss`() {
        val completedTrips = listOf(
            SparkCompletedTrip(
                tripId = "TRIP-301",
                tripDate = today.minusDays(1),
                initialOfferedTip = 5.00,
                customerDropDetails = "Stop 1: Generous Customer"
            )
        )

        val earnings = listOf(
            SparkEarningsBreakdown(
                tripId = "TRIP-301",
                date = today,
                basePay = 7.00,
                confirmedTip = 10.00, // Customer increased tip!
                totalEarnings = 17.00
            )
        )

        val discrepancies = engine.reconcile(
            startDate = startDate,
            endDate = endDate,
            completedTrips = completedTrips,
            earningsBreakdowns = earnings,
            onePayDeposits = emptyList(),
            screenshotOffers = emptyList()
        )

        assertTrue(discrepancies.isEmpty())
    }

    @Test
    fun `excludes trips outside of configured date range`() {
        val completedTrips = listOf(
            SparkCompletedTrip(
                tripId = "TRIP-OLD-001",
                tripDate = LocalDate.of(2026, 8, 15), // Outside range
                initialOfferedTip = 10.00,
                customerDropDetails = "Old Trip"
            )
        )

        val earnings = listOf(
            SparkEarningsBreakdown(
                tripId = "TRIP-OLD-001",
                date = LocalDate.of(2026, 8, 16),
                basePay = 8.00,
                confirmedTip = 0.00,
                totalEarnings = 8.00
            )
        )

        val discrepancies = engine.reconcile(
            startDate = startDate,
            endDate = endDate,
            completedTrips = completedTrips,
            earningsBreakdowns = earnings,
            onePayDeposits = emptyList(),
            screenshotOffers = emptyList()
        )

        assertTrue(discrepancies.isEmpty())
    }
}
