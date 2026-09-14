package com.gigscope.auditor.domain.model

import java.time.LocalDate

data class SparkCompletedTrip(
    val tripId: String,
    val tripDate: LocalDate,
    val initialOfferedTip: Double,
    val customerDropDetails: String? = null,
    val isCompleted: Boolean = true,
    val completedTime: String? = null,
    val tripType: String? = null,
    val stopCount: Int = 1,
    val rawTotalEstimate: Double? = null,
    val rawText: String? = null
)

data class SparkEarningsBreakdown(
    val tripId: String,
    val date: LocalDate,
    val basePay: Double,
    val confirmedTip: Double,
    val totalEarnings: Double,
    val timestamp: String? = null,
    val extraEarnings: Double = 0.0,
    val rawEarningDetails: String? = null
)

enum class OnePayTransactionType {
    TRIP_EARNING,
    TIP_DEPOSIT,
    OTHER
}

data class OnePayDeposit(
    val referenceId: String,
    val date: LocalDate,
    val amount: Double,
    val sender: String = "Spark Driver / Walmart",
    val matchedTripId: String? = null,
    val transactionType: OnePayTransactionType = OnePayTransactionType.TIP_DEPOSIT,
    val timestamp: String? = null,
    val rawDescription: String? = null
)

data class PhotoOfferRecord(
    val tripId: String,
    val offeredTip: Double,
    val basePay: Double,
    val captureDate: LocalDate,
    val timestamp: String? = null,
    val imageUri: String? = null,
    val isAccepted: Boolean = false,
    val extractedText: String? = null,
    val estimatedTotal: Double? = null
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
