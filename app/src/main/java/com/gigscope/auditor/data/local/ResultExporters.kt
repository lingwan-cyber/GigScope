package com.gigscope.auditor.data.local

import com.gigscope.auditor.domain.model.OnePayDeposit
import com.gigscope.auditor.domain.model.OnePayTransactionType
import com.gigscope.auditor.domain.model.PhotoOfferRecord
import com.gigscope.auditor.domain.model.SparkCompletedTrip
import com.gigscope.auditor.domain.model.SparkEarningsBreakdown
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ResultExporters {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Serializes collected Spark Trips and Spark Earnings into a comprehensive JSON document.
     */
    fun exportSparkResultsJson(
        startDate: LocalDate,
        endDate: LocalDate,
        trips: List<SparkCompletedTrip>,
        earnings: List<SparkEarningsBreakdown>
    ): String {
        val root = JSONObject()
        root.put("application", "Spark Driver")
        root.put("exportDate", LocalDate.now().format(dateFormatter))
        
        val dateRangeObj = JSONObject()
        dateRangeObj.put("startDate", startDate.format(dateFormatter))
        dateRangeObj.put("endDate", endDate.format(dateFormatter))
        root.put("dateRange", dateRangeObj)

        val summaryObj = JSONObject()
        summaryObj.put("totalTripsCount", trips.size)
        summaryObj.put("totalEarningsRecordsCount", earnings.size)
        val totalEarningsSum = earnings.sumOf { it.totalEarnings }
        val totalBasePaySum = earnings.sumOf { it.basePay }
        val totalConfirmedTipsSum = earnings.sumOf { it.confirmedTip }
        val totalExtraEarningsSum = earnings.sumOf { it.extraEarnings }
        summaryObj.put("totalEarnings", totalEarningsSum)
        summaryObj.put("totalBasePay", totalBasePaySum)
        summaryObj.put("totalConfirmedTips", totalConfirmedTipsSum)
        summaryObj.put("totalExtraEarnings", totalExtraEarningsSum)
        root.put("summary", summaryObj)

        val tripsArray = JSONArray()
        for (trip in trips) {
            val tripObj = JSONObject()
            tripObj.put("tripId", trip.tripId)
            tripObj.put("tripDate", trip.tripDate.format(dateFormatter))
            tripObj.put("completedTime", trip.completedTime ?: "")
            tripObj.put("tripType", trip.tripType ?: "Delivery")
            tripObj.put("stopCount", trip.stopCount)
            tripObj.put("initialOfferedTip", trip.initialOfferedTip)
            tripObj.put("customerDropDetails", trip.customerDropDetails ?: "")
            tripObj.put("isCompleted", trip.isCompleted)
            if (trip.rawTotalEstimate != null) {
                tripObj.put("rawTotalEstimate", trip.rawTotalEstimate)
            }
            if (!trip.rawText.isNullOrBlank()) {
                tripObj.put("rawText", trip.rawText)
            }
            tripsArray.put(tripObj)
        }
        root.put("trips", tripsArray)

        val earningsArray = JSONArray()
        for (earning in earnings) {
            val earnObj = JSONObject()
            earnObj.put("tripId", earning.tripId)
            earnObj.put("date", earning.date.format(dateFormatter))
            earnObj.put("timestamp", earning.timestamp ?: "")
            earnObj.put("basePay", earning.basePay)
            earnObj.put("confirmedTip", earning.confirmedTip)
            earnObj.put("extraEarnings", earning.extraEarnings)
            earnObj.put("totalEarnings", earning.totalEarnings)
            if (!earning.rawEarningDetails.isNullOrBlank()) {
                earnObj.put("rawEarningDetails", earning.rawEarningDetails)
            }
            earningsArray.put(earnObj)
        }
        root.put("earnings", earningsArray)

        return root.toString(2)
    }

    /**
     * Serializes collected OnePay Activity (Trip Earnings vs. Tip Deposits) with exact timestamps into JSON.
     */
    fun exportOnePayResultsJson(
        startDate: LocalDate,
        endDate: LocalDate,
        deposits: List<OnePayDeposit>
    ): String {
        val root = JSONObject()
        root.put("application", "OnePay (One Finance)")
        root.put("exportDate", LocalDate.now().format(dateFormatter))

        val dateRangeObj = JSONObject()
        dateRangeObj.put("startDate", startDate.format(dateFormatter))
        dateRangeObj.put("endDate", endDate.format(dateFormatter))
        root.put("dateRange", dateRangeObj)

        val tripEarnings = deposits.filter { it.transactionType == OnePayTransactionType.TRIP_EARNING }
        val tipDeposits = deposits.filter { it.transactionType == OnePayTransactionType.TIP_DEPOSIT }
        val otherDeposits = deposits.filter { it.transactionType == OnePayTransactionType.OTHER }

        val summaryObj = JSONObject()
        summaryObj.put("totalTransactionsCount", deposits.size)
        summaryObj.put("tripEarningsCount", tripEarnings.size)
        summaryObj.put("tripEarningsTotal", tripEarnings.sumOf { it.amount })
        summaryObj.put("tipDepositsCount", tipDeposits.size)
        summaryObj.put("tipDepositsTotal", tipDeposits.sumOf { it.amount })
        summaryObj.put("grandTotalDeposits", deposits.sumOf { it.amount })
        root.put("summary", summaryObj)

        fun serializeDepositList(list: List<OnePayDeposit>): JSONArray {
            val arr = JSONArray()
            for (item in list) {
                val itemObj = JSONObject()
                itemObj.put("referenceId", item.referenceId)
                itemObj.put("date", item.date.format(dateFormatter))
                itemObj.put("timestamp", item.timestamp ?: "")
                itemObj.put("amount", item.amount)
                itemObj.put("sender", item.sender)
                itemObj.put("transactionType", item.transactionType.name)
                itemObj.put("matchedTripId", item.matchedTripId ?: "")
                if (!item.rawDescription.isNullOrBlank()) {
                    itemObj.put("rawDescription", item.rawDescription)
                }
                arr.put(itemObj)
            }
            return arr
        }

        root.put("tripEarnings", serializeDepositList(tripEarnings))
        root.put("tipDeposits", serializeDepositList(tipDeposits))
        if (otherDeposits.isNotEmpty()) {
            root.put("otherTransactions", serializeDepositList(otherDeposits))
        }

        return root.toString(2)
    }

    /**
     * Serializes Google Photos Screenshot Offer extractions and text OCR into JSON.
     */
    fun exportPhotosResultsJson(
        startDate: LocalDate,
        endDate: LocalDate,
        offers: List<PhotoOfferRecord>
    ): String {
        val root = JSONObject()
        root.put("application", "Google Photos")
        root.put("exportDate", LocalDate.now().format(dateFormatter))

        val dateRangeObj = JSONObject()
        dateRangeObj.put("startDate", startDate.format(dateFormatter))
        dateRangeObj.put("endDate", endDate.format(dateFormatter))
        root.put("dateRange", dateRangeObj)

        val summaryObj = JSONObject()
        summaryObj.put("totalScreenshotsExtracted", offers.size)
        summaryObj.put("totalEstimatedPay", offers.sumOf { it.basePay })
        summaryObj.put("totalOfferedTips", offers.sumOf { it.offeredTip })
        root.put("summary", summaryObj)

        val offersArray = JSONArray()
        for (offer in offers) {
            val offerObj = JSONObject()
            offerObj.put("tripId", offer.tripId)
            offerObj.put("captureDate", offer.captureDate.format(dateFormatter))
            offerObj.put("timestamp", offer.timestamp ?: "")
            offerObj.put("basePay", offer.basePay)
            offerObj.put("offeredTip", offer.offeredTip)
            if (offer.estimatedTotal != null) {
                offerObj.put("estimatedTotal", offer.estimatedTotal)
            }
            offerObj.put("imageUri", offer.imageUri ?: "")
            offerObj.put("isAccepted", offer.isAccepted)
            offerObj.put("extractedText", offer.extractedText ?: "")
            offersArray.put(offerObj)
        }
        root.put("screenshotExtractions", offersArray)

        return root.toString(2)
    }
}
