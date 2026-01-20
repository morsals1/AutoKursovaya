package com.example.autouchet.Controllers

import android.content.Context
import com.example.autouchet.Models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ExpenseController(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    fun addExpense(
        carId: Int,
        amount: Double,
        category: String,
        date: Date = Date(),
        mileage: Int,
        comment: String = "",
        shopName: String = "",
        isFromReceipt: Boolean = false,
        onComplete: (Int) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val expense = Expense(
                carId = carId,
                amount = amount,
                category = category,
                date = date,
                mileage = mileage,
                comment = comment,
                shopName = shopName,
                createdByReceipt = isFromReceipt
            )

            val expenseId = database.expenseDao().insert(expense).toInt()
            val car = database.carDao().getById(carId)
            car?.let {
                if (mileage > it.currentMileage) {
                    it.currentMileage = mileage
                    database.carDao().update(it)
                }
            }

            withContext(Dispatchers.Main) {
                onComplete(expenseId)
            }
        }
    }

    fun getRecentExpenses(
        carId: Int,
        limit: Int = 10,
        onResult: (List<Expense>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val expenses = database.expenseDao().getRecentByCar(carId, limit)
            withContext(Dispatchers.Main) {
                onResult(expenses)
            }
        }
    }

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

    fun getMonthlyExpenses(
        carId: Int,
        onResult: (Double?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val (startDate, endDate) = getMonthRange()
            val total = database.expenseDao().getTotalByDateRange(carId, startDate, endDate)
            withContext(Dispatchers.Main) {
                onResult(total)
            }
        }
    }

    fun getPreviousMonthExpenses(
        carId: Int,
        onResult: (Double?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val (startDate, endDate) = getMonthRange(-1)
            val total = database.expenseDao().getTotalByDateRange(carId, startDate, endDate)
            withContext(Dispatchers.Main) {
                onResult(total)
            }
        }
    }

    fun getCategoryTotals(
        carId: Int,
        onResult: (List<CategoryTotal>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val (startDate, endDate) = getMonthRange()
            val totals = database.expenseDao().getCategoryTotals(carId, startDate, endDate)
            withContext(Dispatchers.Main) {
                onResult(totals)
            }
        }
    }

    fun getExpensesByMonth(
        carId: Int,
        monthOffset: Int = 0,
        onResult: (List<Expense>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val (startDate, endDate) = getMonthRange(monthOffset)
            val expenses = database.expenseDao().getByDateRange(carId, startDate, endDate)
            withContext(Dispatchers.Main) {
                onResult(expenses)
            }
        }
    }

    suspend fun getMonthlyExpensesSync(carId: Int): Double? {
        return withContext(Dispatchers.IO) {
            val (startDate, endDate) = getMonthRange()
            database.expenseDao().getTotalByDateRange(carId, startDate, endDate)
        }
    }

    suspend fun getPreviousMonthExpensesSync(carId: Int): Double? {
        return withContext(Dispatchers.IO) {
            val (startDate, endDate) = getMonthRange(-1)
            database.expenseDao().getTotalByDateRange(carId, startDate, endDate)
        }
    }

    suspend fun getCategoryTotalsSync(carId: Int): List<CategoryTotal> {
        return withContext(Dispatchers.IO) {
            val (startDate, endDate) = getMonthRange()
            database.expenseDao().getCategoryTotals(carId, startDate, endDate)
        }
    }

    suspend fun getExpensesForMonthSync(carId: Int, year: Int, month: Int): List<Expense> {
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
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

            database.expenseDao().getByDateRange(carId, startDate, endDate)
        }
    }

    suspend fun deleteExpense(expenseId: Int) {
        withContext(Dispatchers.IO) {
            val expense = database.expenseDao().getById(expenseId)
            expense?.let {
                database.expenseDao().delete(it)
            }
        }
    }

    suspend fun getExpenseById(expenseId: Int): Expense? {
        return withContext(Dispatchers.IO) {
            database.expenseDao().getById(expenseId)
        }
    }

    suspend fun getAllExpensesByCarSync(carId: Int): List<Expense> {
        return withContext(Dispatchers.IO) {
            database.expenseDao().getAllByCar(carId)
        }
    }
}