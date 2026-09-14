package com.gigscope.auditor.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gigscope.auditor.domain.model.DiscrepancyType

@Entity(tableName = "spark_completed_trips")
data class SparkTripEntity(
    @PrimaryKey val tripId: String,
    val tripDateEpochDay: Long,
    val initialOfferedTip: Double,
    val confirmedTip: Double?,
    val basePay: Double?,
    val customerDropDetails: String?,
    val isCompleted: Boolean = true
)

@Entity(tableName = "onepay_deposits")
data class OnePayDepositEntity(
    @PrimaryKey val referenceId: String,
    val depositDateEpochDay: Long,
    val amount: Double,
    val sender: String,
    val matchedTripId: String? = null
)

@Entity(tableName = "photo_offers")
data class PhotoOfferEntity(
    @PrimaryKey val tripId: String,
    val offeredTip: Double,
    val basePay: Double,
    val captureDateEpochDay: Long,
    val imageUri: String?,
    val isAccepted: Boolean = false
)

@Entity(tableName = "tip_audit_results")
data class TipAuditResultEntity(
    @PrimaryKey val tripId: String,
    val dateEpochDay: Long,
    val customerIdentifier: String,
    val offeredTip: Double,
    val finalReceivedTip: Double,
    val lossAmount: Double,
    val discrepancyType: DiscrepancyType,
    val screenshotProofUri: String?
)
