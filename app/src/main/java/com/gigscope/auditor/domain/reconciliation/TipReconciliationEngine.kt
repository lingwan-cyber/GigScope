package com.gigscope.auditor.domain.reconciliation

import com.gigscope.auditor.domain.model.CustomerTipDiscrepancy
import com.gigscope.auditor.domain.model.DiscrepancyType
import com.gigscope.auditor.domain.model.OnePayDeposit
import com.gigscope.auditor.domain.model.PhotoOfferRecord
import com.gigscope.auditor.domain.model.SparkCompletedTrip
import com.gigscope.auditor.domain.model.SparkEarningsBreakdown
import java.time.LocalDate
import kotlin.math.abs

class TipReconciliationEngine {

    companion object {
        private const val EPSILON = 0.01
    }

    /**
     * Correlates data from Spark Driver ("Trips" and "Earnings"), OnePay ("Checking" Activity),
     * and Google Photos ("Screenshots" album) within a given date range.
     *
     * Crucially:
     * - Filters Google Photos screenshots so that unaccepted offers (not in Spark Trips) are ignored.
     * - Compares the baseline offered tip against finalized earnings / deposits.
     * - Identifies the specific customer / drop who reduced the tip.
     */
    fun reconcile(
        startDate: LocalDate,
        endDate: LocalDate,
        completedTrips: List<SparkCompletedTrip>,
        earningsBreakdowns: List<SparkEarningsBreakdown>,
        onePayDeposits: List<OnePayDeposit>,
        screenshotOffers: List<PhotoOfferRecord>
    ): List<CustomerTipDiscrepancy> {
        val discrepancies = mutableListOf<CustomerTipDiscrepancy>()

        // 1. Filter completed trips within user date boundaries
        val inScopeTrips = completedTrips.filter { trip ->
            !trip.tripDate.isBefore(startDate) && !trip.tripDate.isAfter(endDate)
        }

        val acceptedTripIds = inScopeTrips.map { it.tripId }.toSet()
        val earningsMap = earningsBreakdowns.associateBy { it.tripId }

        // 2. Filter screenshots - ONLY keep offers for trips the driver actually completed
        val validScreenshotOffers = screenshotOffers
            .filter { acceptedTripIds.contains(it.tripId) }
            .associateBy { it.tripId }

        for (trip in inScopeTrips) {
            // 3. Determine baseline initial offered tip
            val photoProof = validScreenshotOffers[trip.tripId]
            val offeredTip = when {
                photoProof != null && photoProof.offeredTip > 0.0 -> photoProof.offeredTip
                else -> trip.initialOfferedTip
            }

            if (offeredTip <= 0.0) {
                // Trip had zero tip originally, cannot be tip-baited
                continue
            }

            // 4. Resolve finalized tip
            val sparkEarnings = earningsMap[trip.tripId]
            val finalTip = when {
                sparkEarnings != null -> sparkEarnings.confirmedTip
                else -> {
                    // Fallback to secondary OnePay deposit matching the trip
                    val expectedDeposit = onePayDeposits.firstOrNull { deposit ->
                        deposit.matchedTripId == trip.tripId ||
                                (deposit.date == trip.tripDate.plusDays(1) && deposit.amount <= offeredTip)
                    }
                    expectedDeposit?.amount ?: 0.0
                }
            }

            // 5. Calculate tip variance
            val delta = finalTip - offeredTip

            if (delta < -EPSILON) {
                val isFullRemoval = finalTip <= EPSILON
                discrepancies.add(
                    CustomerTipDiscrepancy(
                        tripId = trip.tripId,
                        date = trip.tripDate,
                        customerIdentifier = trip.customerDropDetails
                            ?: "Customer on Trip #${trip.tripId}",
                        offeredTip = offeredTip,
                        finalReceivedTip = finalTip,
                        lossAmount = abs(delta),
                        discrepancyType = if (isFullRemoval) DiscrepancyType.FULL_REMOVAL else DiscrepancyType.PARTIAL_REDUCTION,
                        screenshotProofUri = photoProof?.imageUri
                    )
                )
            }
        }

        return discrepancies.sortedByDescending { it.lossAmount }
    }
}
