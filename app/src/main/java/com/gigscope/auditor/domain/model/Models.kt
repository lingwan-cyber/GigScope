package com.gigscope.auditor.domain.model

import java.time.LocalDate

data class SparkCompletedTrip(
    val tripId: String,
    val tripDate: LocalDate,
    val initialOfferedTip: Double,
    val customerDropDetails: String?,
    val isCompleted: Boolean = true
)

data class SparkEarningsBreakdown(
    val tripId: String,
    val date: LocalDate,
    val basePay: Double,
    val confirmedTip: Double,
    val totalEarnings: Double
)

data class OnePayDeposit(
    val referenceId: String,
    val date: LocalDate,
    val amount: Double,
    val sender: String,
    val matchedTripId: String? = null
)

data class PhotoOfferRecord(
    val tripId: String,
    val offeredTip: Double,
    val basePay: Double,
    val captureDate: LocalDate,
    val imageUri: String? = null,
    val isAccepted: Boolean = false
)

enum class DiscrepancyType {
    CONFIRMED_MATCH,
    TIP_INCREASE,
    PARTIAL_REDUCTION,
    FULL_REMOVAL
}

data class CustomerTipDiscrepancy(
    val tripId: String,
    val date: LocalDate,
    val customerIdentifier: String,
    val offeredTip: Double,
    val finalReceivedTip: Double,
    val lossAmount: Double,
    val discrepancyType: DiscrepancyType,
    val screenshotProofUri: String?
)
