package com.example.autouchet.Models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY date DESC LIMIT :limit")
    fun getRecentByCarFlow(carId: Int, limit: Int = 10): Flow<List<Expense>>

    // Синхронная версия для прямого доступа
    @Query("SELECT * FROM expenses WHERE carId = :carId ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentByCar(carId: Int, limit: Int = 10): List<Expense>

    @Query("SELECT SUM(amount) FROM expenses WHERE carId = :carId AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalByDateRange(carId: Int, startDate: Long, endDate: Long): Double?

    @Query("SELECT * FROM expenses WHERE carId = :carId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getByDateRange(carId: Int, startDate: Long, endDate: Long): List<Expense>

    @Query("SELECT category, SUM(amount) as total FROM expenses WHERE carId = :carId AND date BETWEEN :startDate AND :endDate GROUP BY category")
    suspend fun getCategoryTotals(carId: Int, startDate: Long, endDate: Long): List<CategoryTotal>
}

data class CategoryTotal(
    val category: String,
    val total: Double
)