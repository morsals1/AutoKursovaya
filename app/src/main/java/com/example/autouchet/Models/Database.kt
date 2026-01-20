package com.example.autouchet.Models

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [Car::class, Expense::class, TireReplacement::class, Reminder::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun tireReplacementDao(): TireReplacementDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE reminders ADD COLUMN periodMonths INTEGER DEFAULT NULL")
                    database.execSQL("ALTER TABLE reminders ADD COLUMN notifyDaysBefore INTEGER DEFAULT 7")
                    database.execSQL("ALTER TABLE reminders ADD COLUMN notifyKmBefore INTEGER DEFAULT 500")
                    database.execSQL("ALTER TABLE reminders ADD COLUMN note TEXT DEFAULT ''")
                } catch (e: Exception) {
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autouchet_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}