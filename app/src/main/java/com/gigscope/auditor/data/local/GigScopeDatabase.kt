package com.gigscope.auditor.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gigscope.auditor.data.local.dao.*
import com.gigscope.auditor.data.local.entity.*
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        SparkTripEntity::class,
        OnePayDepositEntity::class,
        PhotoOfferEntity::class,
        TipAuditResultEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GigScopeDatabase : RoomDatabase() {

    abstract fun sparkTripDao(): SparkTripDao
    abstract fun onePayDao(): OnePayDao
    abstract fun photoOfferDao(): PhotoOfferDao
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var INSTANCE: GigScopeDatabase? = null

        fun getDatabase(context: Context, passphrase: ByteArray): GigScopeDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = SupportFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GigScopeDatabase::class.java,
                    "gigscope_encrypted.db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
