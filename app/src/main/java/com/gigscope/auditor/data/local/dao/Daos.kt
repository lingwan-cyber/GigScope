package com.gigscope.auditor.data.local.dao

import androidx.room.*
import com.gigscope.auditor.data.local.entity.*

@Dao
interface SparkTripDao {
    @Query("SELECT * FROM spark_completed_trips WHERE tripDateEpochDay BETWEEN :startEpochDay AND :endEpochDay")
    suspend fun getTripsInRange(startEpochDay: Long, endEpochDay: Long): List<SparkTripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<SparkTripEntity>)

    @Query("SELECT * FROM spark_completed_trips WHERE tripId = :tripId")
    suspend fun getTripById(tripId: String): SparkTripEntity?
}

@Dao
interface OnePayDao {
    @Query("SELECT * FROM onepay_deposits WHERE depositDateEpochDay BETWEEN :startEpochDay AND :endEpochDay")
    suspend fun getDepositsInRange(startEpochDay: Long, endEpochDay: Long): List<OnePayDepositEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeposits(deposits: List<OnePayDepositEntity>)
}

@Dao
interface PhotoOfferDao {
    @Query("SELECT * FROM photo_offers WHERE tripId = :tripId")
    suspend fun getOfferByTripId(tripId: String): PhotoOfferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffers(offers: List<PhotoOfferEntity>)
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM tip_audit_results ORDER BY lossAmount DESC")
    suspend fun getAllDiscrepancies(): List<TipAuditResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscrepancies(results: List<TipAuditResultEntity>)
}
