package com.ttcoachai.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ttcoachai.models.TrainingSession
import com.ttcoachai.models.UserProgress

@Database(entities = [TrainingSession::class, UserProgress::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trainingDao(): TrainingDao
    abstract fun progressDao(): ProgressDao

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
