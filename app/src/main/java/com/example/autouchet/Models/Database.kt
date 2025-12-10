package com.example.autouchet.Models

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

@Database(
    entities = [Car::class, Expense::class, TireReplacement::class, Reminder::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)  // Добавь эту строку
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun tireReplacementDao(): TireReplacementDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autouchet_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}