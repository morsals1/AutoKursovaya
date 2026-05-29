package com.example.autouchet.Controllers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.autouchet.Models.*
import kotlinx.coroutines.*

class SyncController(private val context: Context) {

    private val firebaseController = FirebaseController()
    private val database = AppDatabase.getDatabase(context)

    private val syncScope =
        CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var groupId: String? = null

    fun isNetworkAvailable(): Boolean {

        val connectivityManager =
            context.getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

        val network =
            connectivityManager.activeNetwork
                ?: return false

        val capabilities =
            connectivityManager.getNetworkCapabilities(network)
                ?: return false

        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
    }

    fun setGroupId(gId: String) {
        groupId = gId
    }

    fun startRealtimeSync(onUpdate: () -> Unit) {

        val gId = groupId ?: return

        firebaseController.startRealtimeSync(
            gId,
            database,
            onUpdate
        )
    }

    fun stopRealtimeSync() {
        firebaseController.stopRealtimeSync()
    }

    fun syncExpense(expense: Expense) {
        val gId = groupId ?: return

        syncScope.launch {
            try {
                if (!isNetworkAvailable()) {
                    if (expense.cloudId.isEmpty()) {
                        database.pendingSyncDao().insert(
                            PendingSyncEntity(
                                entityType = "expense",
                                entityId = expense.id,
                                operation = "save"
                            )
                        )
                    }
                    return@launch
                }

                val result = firebaseController.syncExpenseToCloud(expense, gId)

                result.onSuccess { cloudId ->
                    if (expense.cloudId.isEmpty()) {
                        database.expenseDao().update(
                            expense.copy(
                                cloudId = cloudId,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                result.onFailure { error ->
                    Log.e("SyncController", "Failed to sync expense: ${error.message}")
                    if (expense.cloudId.isEmpty()) {
                        database.pendingSyncDao().insert(
                            PendingSyncEntity(
                                entityType = "expense",
                                entityId = expense.id,
                                operation = "save"
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e("SyncController", "Error syncing expense: ${e.message}")
                if (expense.cloudId.isEmpty()) {
                    database.pendingSyncDao().insert(
                        PendingSyncEntity(
                            entityType = "expense",
                            entityId = expense.id,
                            operation = "save"
                        )
                    )
                }
            }
        }
    }

    fun syncCar(car: Car) {

        val gId = groupId ?: return

        syncScope.launch {

            try {

                if (!isNetworkAvailable()) {

                    database.pendingSyncDao().insert(
                        PendingSyncEntity(
                            entityType = "car",
                            entityId = car.id,
                            operation = "save"
                        )
                    )

                    return@launch
                }

                val result =
                    firebaseController.syncCarToCloud(
                        car,
                        gId
                    )

                result.onSuccess { cloudId ->

                    if (car.cloudId.isEmpty()) {

                        database.carDao().update(
                            car.copy(
                                cloudId = cloudId,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

            } catch (e: Exception) {

                database.pendingSyncDao().insert(
                    PendingSyncEntity(
                        entityType = "car",
                        entityId = car.id,
                        operation = "save"
                    )
                )
            }
        }
    }

    fun syncCategory(category: ExpenseCategory) {

        val gId = groupId ?: return

        syncScope.launch {

            try {

                if (!isNetworkAvailable()) {

                    database.pendingSyncDao().insert(
                        PendingSyncEntity(
                            entityType = "category",
                            entityId = category.id,
                            operation = "save"
                        )
                    )

                    return@launch
                }

                val result =
                    firebaseController.syncCategoryToCloud(
                        category,
                        gId
                    )

                result.onSuccess { cloudId ->

                    if (category.cloudId.isEmpty()) {

                        database.categoryDao().update(
                            category.copy(
                                cloudId = cloudId,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

            } catch (e: Exception) {

                database.pendingSyncDao().insert(
                    PendingSyncEntity(
                        entityType = "category",
                        entityId = category.id,
                        operation = "save"
                    )
                )
            }
        }
    }

    fun syncReminder(reminder: Reminder) {

        val gId = groupId ?: return

        syncScope.launch {

            try {

                if (!isNetworkAvailable()) {

                    database.pendingSyncDao().insert(
                        PendingSyncEntity(
                            entityType = "reminder",
                            entityId = reminder.id,
                            operation = "save"
                        )
                    )

                    return@launch
                }

                val result =
                    firebaseController.syncReminderToCloud(
                        reminder,
                        gId
                    )

                result.onSuccess { cloudId ->

                    if (reminder.cloudId.isEmpty()) {

                        database.reminderDao().update(
                            reminder.copy(
                                cloudId = cloudId,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

            } catch (e: Exception) {

                database.pendingSyncDao().insert(
                    PendingSyncEntity(
                        entityType = "reminder",
                        entityId = reminder.id,
                        operation = "save"
                    )
                )
            }
        }
    }

    fun syncTire(tire: TireReplacement) {

        val gId = groupId ?: return

        syncScope.launch {

            try {

                if (!isNetworkAvailable()) {

                    database.pendingSyncDao().insert(
                        PendingSyncEntity(
                            entityType = "tire",
                            entityId = tire.id,
                            operation = "save"
                        )
                    )

                    return@launch
                }

                val result =
                    firebaseController.syncTireToCloud(
                        tire,
                        gId
                    )

                result.onSuccess { cloudId ->

                    if (tire.cloudId.isEmpty()) {

                        database.tireReplacementDao().update(
                            tire.copy(
                                cloudId = cloudId,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

            } catch (e: Exception) {

                database.pendingSyncDao().insert(
                    PendingSyncEntity(
                        entityType = "tire",
                        entityId = tire.id,
                        operation = "save"
                    )
                )
            }
        }
    }

    fun deleteReminder(reminder: Reminder) {

        val gId = groupId ?: return

        syncScope.launch {

            try {

                if (!isNetworkAvailable()) {

                    database.pendingSyncDao().insert(
                        PendingSyncEntity(
                            entityType = "reminder",
                            entityId = reminder.id,
                            operation = "delete",
                            cloudId = reminder.cloudId
                        )
                    )

                    return@launch
                }

                firebaseController.deleteReminderByCloudId(
                    reminder.cloudId,
                    gId
                )

            } catch (e: Exception) {

                database.pendingSyncDao().insert(
                    PendingSyncEntity(
                        entityType = "reminder",
                        entityId = reminder.id,
                        operation = "delete",
                        cloudId = reminder.cloudId
                    )
                )
            }
        }
    }

    fun deleteExpense(expense: Expense) {

        val gId = groupId ?: return

        syncScope.launch {

            try {

                if (!isNetworkAvailable()) {

                    database.pendingSyncDao().insert(
                        PendingSyncEntity(
                            entityType = "expense",
                            entityId = expense.id,
                            operation = "delete",
                            cloudId = expense.cloudId
                        )
                    )

                    return@launch
                }

                firebaseController.deleteExpenseByCloudId(
                    expense.cloudId,
                    gId
                )

            } catch (e: Exception) {

                database.pendingSyncDao().insert(
                    PendingSyncEntity(
                        entityType = "expense",
                        entityId = expense.id,
                        operation = "delete",
                        cloudId = expense.cloudId
                    )
                )
            }
        }
    }

    fun deleteCategory(category: ExpenseCategory) {

        val gId = groupId ?: return

        syncScope.launch {

            try {

                if (!isNetworkAvailable()) {

                    database.pendingSyncDao().insert(
                        PendingSyncEntity(
                            entityType = "category",
                            entityId = category.id,
                            operation = "delete",
                            cloudId = category.cloudId
                        )
                    )

                    return@launch
                }

                firebaseController.deleteCategoryByCloudId(
                    category.cloudId,
                    gId
                )

            } catch (e: Exception) {

                database.pendingSyncDao().insert(
                    PendingSyncEntity(
                        entityType = "category",
                        entityId = category.id,
                        operation = "delete",
                        cloudId = category.cloudId
                    )
                )
            }
        }
    }

    suspend fun syncAllLocalToCloud() {
        val gId = groupId ?: return
        if (!isNetworkAvailable()) return

        try {
            // Синхронизируем только записи без cloudId
            val expenses = database.expenseDao().getAllActive()
            for (expense in expenses) {
                if (expense.cloudId.isEmpty()) {
                    val result = firebaseController.syncExpenseToCloud(expense, gId)
                    result.onSuccess { cloudId ->
                        database.expenseDao().update(
                            expense.copy(
                                cloudId = cloudId,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    delay(100)
                }
            }

            val cars = database.carDao().getAllActive()
            for (car in cars) {
                if (car.cloudId.isEmpty()) {
                    firebaseController.syncCarToCloud(car, gId).onSuccess { cloudId ->
                        database.carDao().update(
                            car.copy(cloudId = cloudId, updatedAt = System.currentTimeMillis())
                        )
                    }
                    delay(100)
                }
            }

            val categories = database.categoryDao().getAll()

            for (category in categories) {

                if (category.cloudId.isEmpty()) {

                    firebaseController
                        .syncCategoryToCloud(category, gId)
                        .onSuccess { cloudId ->

                            database.categoryDao().update(
                                category.copy(
                                    cloudId = cloudId,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }

                    delay(100)
                }
            }

            val reminders = database.reminderDao().getAllActive()
            for (reminder in reminders) {
                if (reminder.cloudId.isEmpty()) {
                    firebaseController.syncReminderToCloud(reminder, gId).onSuccess { cloudId ->
                        database.reminderDao().update(
                            reminder.copy(cloudId = cloudId, updatedAt = System.currentTimeMillis())
                        )
                    }
                    delay(100)
                }
            }

            val tires = database.tireReplacementDao().getAllActive()
            for (tire in tires) {
                if (tire.cloudId.isEmpty()) {
                    firebaseController.syncTireToCloud(tire, gId).onSuccess { cloudId ->
                        database.tireReplacementDao().update(
                            tire.copy(cloudId = cloudId, updatedAt = System.currentTimeMillis())
                        )
                    }
                    delay(100)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncController", "Error in syncAllLocalToCloud: ${e.message}")
        }
    }

    fun processPendingSync() {

        syncScope.launch {

            if (!isNetworkAvailable()) {
                return@launch
            }

            val pendingItems =
                database.pendingSyncDao().getAll()

            for (item in pendingItems) {

                try {

                    when (item.entityType) {

                        "expense" -> {

                            if (item.operation == "delete") {

                                if (item.cloudId.isNotEmpty()) {

                                    firebaseController.deleteExpenseByCloudId(
                                        item.cloudId,
                                        groupId!!
                                    )
                                }

                            } else {

                                val expense =
                                    database.expenseDao()
                                        .getById(item.entityId)

                                if (expense != null) {

                                    val result =
                                        firebaseController
                                            .syncExpenseToCloud(
                                                expense,
                                                groupId!!
                                            )

                                    result.onSuccess { cloudId ->

                                        if (expense.cloudId.isEmpty()) {

                                            database.expenseDao().update(
                                                expense.copy(
                                                    cloudId = cloudId,
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "car" -> {

                            val car =
                                database.carDao()
                                    .getById(item.entityId)

                            if (car != null) {

                                val result =
                                    firebaseController
                                        .syncCarToCloud(
                                            car,
                                            groupId!!
                                        )

                                result.onSuccess { cloudId ->

                                    if (car.cloudId.isEmpty()) {

                                        database.carDao().update(
                                            car.copy(
                                                cloudId = cloudId,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        "category" -> {

                            if (item.operation == "delete") {

                                if (item.cloudId.isNotEmpty()) {

                                    firebaseController.deleteCategoryByCloudId(
                                        item.cloudId,
                                        groupId!!
                                    )
                                }

                            } else {

                                val category =
                                    database.categoryDao()
                                        .getById(item.entityId)

                                if (category != null) {

                                    val result =
                                        firebaseController
                                            .syncCategoryToCloud(
                                                category,
                                                groupId!!
                                            )

                                    result.onSuccess { cloudId ->

                                        if (category.cloudId.isEmpty()) {

                                            database.categoryDao().update(
                                                category.copy(
                                                    cloudId = cloudId,
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "reminder" -> {

                            if (item.operation == "delete") {

                                if (item.cloudId.isNotEmpty()) {

                                    firebaseController.deleteReminderByCloudId(
                                        item.cloudId,
                                        groupId!!
                                    )
                                }

                            } else {

                                val reminder =
                                    database.reminderDao()
                                        .getById(item.entityId)

                                if (reminder != null) {

                                    val result =
                                        firebaseController
                                            .syncReminderToCloud(
                                                reminder,
                                                groupId!!
                                            )

                                    result.onSuccess { cloudId ->

                                        if (reminder.cloudId.isEmpty()) {

                                            database.reminderDao().update(
                                                reminder.copy(
                                                    cloudId = cloudId,
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "tire" -> {

                            val tire =
                                database.tireReplacementDao()
                                    .getById(item.entityId)

                            if (tire != null) {

                                val result =
                                    firebaseController
                                        .syncTireToCloud(
                                            tire,
                                            groupId!!
                                        )

                                result.onSuccess { cloudId ->

                                    if (tire.cloudId.isEmpty()) {

                                        database.tireReplacementDao().update(
                                            tire.copy(
                                                cloudId = cloudId,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    database.pendingSyncDao()
                        .deleteById(item.id)

                } catch (e: Exception) {
                }
            }
        }
    }

    fun startAutoSync() {

        syncScope.launch {

            while (isActive) {

                if (isNetworkAvailable()) {
                    processPendingSync()
                }

                delay(15000)
            }
        }
    }
}