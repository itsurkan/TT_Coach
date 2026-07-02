package com.ttcoachai.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ttcoachai.models.CustomDrillEntity
import com.ttcoachai.models.DrillConfigEntity
import com.ttcoachai.models.PersonalBaselineEntity
import com.ttcoachai.models.TrainingSession
import com.ttcoachai.models.UserProgress

@Database(
    entities = [
        TrainingSession::class,
        UserProgress::class,
        PersonalBaselineEntity::class,
        DrillConfigEntity::class,
        CustomDrillEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(BaselineConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trainingDao(): TrainingDao
    abstract fun progressDao(): ProgressDao
    abstract fun personalBaselineDao(): PersonalBaselineDao
    abstract fun drillConfigDao(): DrillConfigDao
    abstract fun customDrillDao(): CustomDrillDao

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
