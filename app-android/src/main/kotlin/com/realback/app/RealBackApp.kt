package com.realback.app

import android.app.Application
import androidx.room.Room
import com.realback.app.data.db.RealBackDatabase
import com.realback.app.usage.UsageStatsCollector

/** Application-scoped singletons. Kept manual for M1; DI can wait until M3. */
class RealBackApp : Application() {

    val database: RealBackDatabase by lazy {
        Room.databaseBuilder(this, RealBackDatabase::class.java, DATABASE_NAME).build()
    }

    val usageCollector: UsageStatsCollector by lazy { UsageStatsCollector(this) }

    private companion object {
        const val DATABASE_NAME = "realback.db"
    }
}
