package com.ttcoachai.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ttcoachai.models.CustomDrillEntity
import com.ttcoachai.models.PersonalBaselineEntity
import com.ttcoachai.models.SessionAnalyticsEntity
import com.ttcoachai.models.TrainingSession
import com.ttcoachai.models.UserProgress

@Database(
    entities = [
        TrainingSession::class,
        UserProgress::class,
        PersonalBaselineEntity::class,
        CustomDrillEntity::class,
        SessionAnalyticsEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(BaselineConverters::class, SessionAnalyticsConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trainingDao(): TrainingDao
    abstract fun progressDao(): ProgressDao
    abstract fun personalBaselineDao(): PersonalBaselineDao
    abstract fun customDrillDao(): CustomDrillDao
    abstract fun sessionAnalyticsDao(): SessionAnalyticsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ttcoach_database"
                )
                .fallbackToDestructiveMigration() // For dev phase, we can use this
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
