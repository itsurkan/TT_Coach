package com.ttcoachai.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ttcoachai.models.DrillConfigEntity
import com.ttcoachai.models.PersonalBaselineEntity
import com.ttcoachai.models.TrainingSession
import com.ttcoachai.models.UserProgress

@Database(
    entities = [
        TrainingSession::class,
        UserProgress::class,
        PersonalBaselineEntity::class,
        DrillConfigEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(BaselineConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trainingDao(): TrainingDao
    abstract fun progressDao(): ProgressDao
    abstract fun personalBaselineDao(): PersonalBaselineDao
    abstract fun drillConfigDao(): DrillConfigDao

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
