package com.example.autouchet.Controllers

import android.content.Context
import com.example.autouchet.Models.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class AnalyticsController(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)

    private fun getMonthRange(monthOffset: Int = 0): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDate = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val endDate = calendar.timeInMillis

        return Pair(startDate, endDate)
    }

    suspend fun getExpensePerKm(carId: Int): Double {
        return withContext(Dispatchers.IO) {
            val (startDate, endDate) = getMonthRange()
            val expenses = database.expenseDao().getByDateRange(carId, startDate, endDate)

            if (expenses.isEmpty()) return@withContext 0.0

            val totalAmount = expenses.sumByDouble { it.amount }
            val startMileage = expenses.minByOrNull { it.mileage }?.mileage ?: 0
            val endMileage = expenses.maxByOrNull { it.mileage }?.mileage ?: 0
            val totalKm = endMileage - startMileage

            if (totalKm > 0) totalAmount / totalKm else 0.0
        }
    }

    suspend fun getAverageDailyExpense(carId: Int): Double {
        return withContext(Dispatchers.IO) {
            val (startDate, endDate) = getMonthRange()
            val expenses = database.expenseDao().getByDateRange(carId, startDate, endDate)

            if (expenses.isEmpty()) return@withContext 0.0

            val totalAmount = expenses.sumByDouble { it.amount }
            val calendar = Calendar.getInstance().apply { time = Date(startDate) }
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

            totalAmount / daysInMonth
        }
    }

    suspend fun getSeasonalComparison(carId: Int): SeasonalComparison {
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            var winterTotal = 0.0
            var winterCount = 0
            var summerTotal = 0.0
            var summerCount = 0

            for (monthOffset in 0..11) {
                calendar.time = Date()
                calendar.add(Calendar.MONTH, -monthOffset)
                val month = calendar.get(Calendar.MONTH) + 1

                val (startDate, endDate) = getMonthRange(-monthOffset)
                val total = database.expenseDao().getTotalByDateRange(carId, startDate, endDate) ?: 0.0

                if (month in 10..12 || month in 1..3) {
                    winterTotal += total
                    winterCount++
                } else if (month in 4..9) {
                    summerTotal += total
                    summerCount++
                }
            }

            val winterAvg = if (winterCount > 0) winterTotal / winterCount else 0.0
            val summerAvg = if (summerCount > 0) summerTotal / summerCount else 0.0

            SeasonalComparison(
                winterAverage = winterAvg,
                summerAverage = summerAvg,
                difference = winterAvg - summerAvg,
                percentDifference = if (summerAvg > 0) ((winterAvg / summerAvg) - 1) * 100 else 0.0
            )
        }
    }

    data class SeasonalComparison(
        val winterAverage: Double,
        val summerAverage: Double,
        val difference: Double,
        val percentDifference: Double
    )
}