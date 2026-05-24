package com.example.autouchet.Controllers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.Expense
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class SyncController(private val context: Context) {
    private val firebaseController = FirebaseController()
    private val database = AppDatabase.getDatabase(context)
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingSync = ConcurrentHashMap<Int, Boolean>()

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun syncExpense(expense: Expense, groupId: String) {
        if (!isNetworkAvailable()) {
            pendingSync[expense.id] = true
            return
        }
        syncScope.launch {
            try {
                firebaseController.syncExpenseToCloud(expense, groupId)
                pendingSync.remove(expense.id)
            } catch (e: Exception) {
                pendingSync[expense.id] = true
            }
        }
    }

    suspend fun fullSync(groupId: String): Result<Unit> {
        return try {
            if (!isNetworkAvailable()) {
                return Result.failure(Exception("Нет подключения к интернету"))
            }
            val cloudExpenses = firebaseController.syncExpensesFromCloud(groupId).getOrThrow()
            val localExpenses = database.expenseDao().getAll()
            for (cloudExpense in cloudExpenses) {
                val localExpense = localExpenses.find { it.id == cloudExpense.localId }
                if (localExpense == null) {
                    val newExpense = Expense(
                        carId = cloudExpense.carId,
                        amount = cloudExpense.amount,
                        category = cloudExpense.category,
                        date = cloudExpense.date,
                        mileage = cloudExpense.mileage,
                        comment = cloudExpense.comment,
                        shopName = cloudExpense.shopName
                    )
                    database.expenseDao().insert(newExpense)
                }
            }
            for (localExpense in localExpenses) {
                val exists = cloudExpenses.any { it.localId == localExpense.id }
                if (!exists) {
                    firebaseController.syncExpenseToCloud(localExpense, groupId)
                }
            }
            pendingSync.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startAutoSync(groupId: String) {
        syncScope.launch {
            while (isActive) {
                if (isNetworkAvailable() && pendingSync.isNotEmpty()) {
                    fullSync(groupId)
                }
                delay(30000)
            }
        }
    }
}