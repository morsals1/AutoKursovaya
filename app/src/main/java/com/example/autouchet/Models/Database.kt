package com.example.autouchet.Models

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Car::class, Expense::class, TireReplacement::class, Reminder::class, ExpenseCategory::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun tireReplacementDao(): TireReplacementDao
    abstract fun reminderDao(): ReminderDao
    abstract fun categoryDao(): CategoryDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM categories")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autouchet_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getDatabase(context)
                                val categoryDao = database.categoryDao()

                                val defaultCategories = listOf(
                                    ExpenseCategory(name = "Топливо", icon = "⛽", color = 0xFF4CAF50.toInt(), isDefault = true, sortOrder = 0),
                                    ExpenseCategory(name = "Обслуживание", icon = "🔧", color = 0xFF2196F3.toInt(), isDefault = true, sortOrder = 1),
                                    ExpenseCategory(name = "Шины", icon = "🚗", color = 0xFFFF9800.toInt(), isDefault = true, sortOrder = 2),
                                    ExpenseCategory(name = "Налоги", icon = "💼", color = 0xFFF44336.toInt(), isDefault = true, sortOrder = 3),
                                    ExpenseCategory(name = "Страховка", icon = "🛡️", color = 0xFF9C27B0.toInt(), isDefault = true, sortOrder = 4),
                                    ExpenseCategory(name = "Ремонт", icon = "⚙️", color = 0xFF795548.toInt(), isDefault = true, sortOrder = 5),
                                    ExpenseCategory(name = "Мойка", icon = "🚿", color = 0xFF00BCD4.toInt(), isDefault = true, sortOrder = 6),
                                    ExpenseCategory(name = "Парковка", icon = "🅿️", color = 0xFF607D8B.toInt(), isDefault = true, sortOrder = 7),
                                    ExpenseCategory(name = "Штрафы", icon = "📋", color = 0xFFFF5722.toInt(), isDefault = true, sortOrder = 8),
                                    ExpenseCategory(name = "Прочее", icon = "💰", color = 0xFF9E9E9E.toInt(), isDefault = true, sortOrder = 9)
                                )

                                defaultCategories.forEach { category ->
                                    categoryDao.insert(category)
                                }
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}