package com.example.autouchet.Controllers

import android.util.Log
import com.example.autouchet.Models.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

class FirebaseController {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = Firebase.firestore
    private val listeners = mutableMapOf<String, ListenerRegistration>()

    suspend fun registerUser(email: String, password: String): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("User creation failed")

            val user = hashMapOf(
                "uid" to uid,
                "email" to email,
                "displayName" to email.substringBefore("@"),
                "createdAt" to System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(uid)
                .set(user)
                .await()

            Result.success(uid)
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(Exception("Пароль должен содержать минимум 6 символов"))
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(Exception("Пользователь с таким email уже существует"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<String> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("Login failed")

            firestore.collection("users")
                .document(uid)
                .update("lastLoginAt", System.currentTimeMillis())
                .await()

            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(Exception("Неверный email или пароль"))
        }
    }

    fun logout() {
        listeners.values.forEach { it.remove() }
        listeners.clear()
        auth.signOut()
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка отправки письма"))
        }
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun observeAuthState(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener {
            trySend(it.currentUser != null)
        }

        auth.addAuthStateListener(listener)

        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    suspend fun deleteReminderByCloudId(
        cloudId: String,
        groupId: String
    ) {
        firestore
            .collection("carGroups")
            .document(groupId)
            .collection("reminders")
            .document(cloudId)
            .delete()
            .await()
    }

    suspend fun deleteExpenseByCloudId(
        cloudId: String,
        groupId: String
    ) {

        firestore
            .collection("carGroups")
            .document(groupId)
            .collection("expenses")
            .document(cloudId)
            .delete()
            .await()
    }

    suspend fun deleteCategoryByCloudId(
        cloudId: String,
        groupId: String
    ) {
        try {
            firestore
                .collection("carGroups")
                .document(groupId)
                .collection("categories")
                .document(cloudId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("FirebaseController", "Error deleting category: ${e.message}")
        }
    }

    suspend fun createCarGroup(carId: Int): Result<Pair<String, String>> {
        return try {
            val uid = getCurrentUserId() ?: throw Exception("Not authenticated")

            val inviteCode = generateInviteCode()

            val groupData = hashMapOf(
                "inviteCode" to inviteCode,
                "carId" to carId,
                "ownerUid" to uid,
                "members" to listOf(uid),
                "createdAt" to System.currentTimeMillis()
            )

            val docRef = firestore.collection("carGroups")
                .add(groupData)
                .await()

            val groupId = docRef.id

            firestore.collection("carGroups")
                .document(groupId)
                .update(mapOf(
                    "groupId" to groupId,
                    "members" to listOf(uid)
                ))
                .await()

            Result.success(Pair(groupId, inviteCode))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinCarGroup(inviteCode: String): Result<String> {
        return try {
            val uid = getCurrentUserId()
                ?: throw Exception("Not authenticated")

            val snapshot = firestore.collection("carGroups")
                .whereEqualTo("inviteCode", inviteCode)
                .get()
                .await()

            if (snapshot.isEmpty) {
                return Result.failure(Exception("Неверный код приглашения"))
            }

            val document = snapshot.documents.first()
            val groupId = document.id

            val members = document.get("members") as? List<String> ?: emptyList()

            if (members.contains(uid)) {
                return Result.success(groupId)
            }

            val updatedMembers = members.toMutableList()
            updatedMembers.add(uid)

            firestore.collection("carGroups")
                .document(groupId)
                .update("members", updatedMembers)
                .await()

            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getGroupPath(groupId: String, collection: String): String {
        return "carGroups/$groupId/$collection"
    }

    suspend fun syncExpenseToCloud(
        expense: Expense,
        groupId: String
    ): Result<String> {
        return try {
            val uid = getCurrentUserId() ?: throw Exception("Not authenticated")
            val path = getGroupPath(groupId, "expenses")

            if (expense.cloudId.isNotEmpty()) {
                try {
                    val existingDoc = firestore.collection(path)
                        .document(expense.cloudId)
                        .get()
                        .await()

                    if (existingDoc.exists()) {
                        val updateData = mapOf<String, Any>(
                            "amount" to expense.amount,
                            "category" to expense.category,
                            "categoryId" to (expense.categoryId ?: 0),
                            "date" to expense.date.time,
                            "mileage" to expense.mileage,
                            "comment" to expense.comment,
                            "shopName" to expense.shopName,
                            "receiptScanned" to expense.receiptScanned,
                            "createdByReceipt" to expense.createdByReceipt,
                            "updatedAt" to System.currentTimeMillis(),
                            "updatedBy" to uid
                        )

                        firestore.collection(path)
                            .document(expense.cloudId)
                            .update(updateData)
                            .await()

                        return Result.success(expense.cloudId)
                    }
                } catch (e: Exception) {
                    Log.w("FirebaseController", "Document not found, creating new: ${e.message}")
                }
            }

            val duplicateCheck = firestore.collection(path)
                .whereEqualTo("carId", expense.carId)
                .whereEqualTo("amount", expense.amount)
                .whereEqualTo("category", expense.category)
                .whereEqualTo("date", expense.date.time)
                .whereEqualTo("mileage", expense.mileage)
                .whereEqualTo("createdBy", uid)
                .get()
                .await()

            if (!duplicateCheck.isEmpty) {
                val existingId = duplicateCheck.documents.first().id
                return Result.success(existingId)
            }

            val expenseData = mapOf<String, Any>(
                "carId" to expense.carId,
                "amount" to expense.amount,
                "category" to expense.category,
                "categoryId" to (expense.categoryId ?: 0),
                "date" to expense.date.time,
                "mileage" to expense.mileage,
                "comment" to expense.comment,
                "shopName" to expense.shopName,
                "receiptScanned" to expense.receiptScanned,
                "createdByReceipt" to expense.createdByReceipt,
                "createdBy" to uid,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis(),
                "isDeleted" to false
            )

            val docRef = firestore.collection(path)
                .add(expenseData)
                .await()

            Result.success(docRef.id)

        } catch (e: Exception) {
            Log.e("FirebaseController", "Error syncing expense: ${e.message}")
            Result.failure(e)
        }
    }
    suspend fun syncCarToCloud(
        car: Car,
        groupId: String
    ): Result<String> {
        return try {
            val uid = getCurrentUserId() ?: throw Exception("Not authenticated")
            val path = getGroupPath(groupId, "cars")

            if (car.cloudId.isNotEmpty()) {
                val carData = mapOf<String, Any>(
                    "brand" to car.brand,
                    "model" to car.model,
                    "year" to car.year,
                    "horsepower" to car.horsepower,
                    "region" to car.region,
                    "currentMileage" to car.currentMileage,
                    "averageConsumption" to car.averageConsumption,
                    "updatedAt" to System.currentTimeMillis(),
                    "updatedBy" to uid
                )

                firestore.collection(path)
                    .document(car.cloudId)
                    .update(carData)
                    .await()

                return Result.success(car.cloudId)
            }

            val duplicateCheck = firestore.collection(path)
                .whereEqualTo("brand", car.brand)
                .whereEqualTo("model", car.model)
                .whereEqualTo("year", car.year)
                .whereEqualTo("createdBy", uid)
                .get()
                .await()

            if (!duplicateCheck.isEmpty) {
                val existingId = duplicateCheck.documents.first().id
                return Result.success(existingId)
            }

            val carData = mapOf<String, Any>(
                "brand" to car.brand,
                "model" to car.model,
                "year" to car.year,
                "horsepower" to car.horsepower,
                "region" to car.region,
                "currentMileage" to car.currentMileage,
                "averageConsumption" to car.averageConsumption,
                "createdAt" to car.createdAt.time,
                "createdBy" to uid,
                "updatedAt" to System.currentTimeMillis(),
                "isDeleted" to false
            )

            val docRef = firestore.collection(path)
                .add(carData)
                .await()

            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCategoryToCloud(
        category: ExpenseCategory,
        groupId: String
    ): Result<String> {
        return try {
            val uid = getCurrentUserId() ?: throw Exception("Not authenticated")
            val path = getGroupPath(groupId, "categories")

            if (category.cloudId.isNotEmpty()) {
                firestore.collection(path)
                    .document(category.cloudId)
                    .set(mapOf<String, Any>(
                        "name" to category.name,
                        "icon" to category.icon,
                        "color" to category.color,
                        "isDefault" to category.isDefault,
                        "sortOrder" to category.sortOrder,
                        "updatedAt" to System.currentTimeMillis(),
                        "updatedBy" to uid
                    ))
                    .await()
                return Result.success(category.cloudId)
            }

            // Проверяем дубликаты в облаке по имени
            val duplicateCheck = firestore.collection(path)
                .whereEqualTo("name", category.name)
                .get()
                .await()

            if (!duplicateCheck.isEmpty) {
                val existingDoc = duplicateCheck.documents.first()
                // Обновляем существующий документ вместо создания нового
                firestore.collection(path)
                    .document(existingDoc.id)
                    .update(mapOf<String, Any>(
                        "icon" to category.icon,
                        "color" to category.color,
                        "sortOrder" to category.sortOrder,
                        "updatedAt" to System.currentTimeMillis(),
                        "updatedBy" to uid
                    ))
                    .await()
                return Result.success(existingDoc.id)
            }

            // Создаем новый
            val categoryData = mapOf<String, Any>(
                "name" to category.name,
                "icon" to category.icon,
                "color" to category.color,
                "isDefault" to category.isDefault,
                "sortOrder" to category.sortOrder,
                "createdAt" to System.currentTimeMillis(),
                "createdBy" to uid,
                "updatedAt" to System.currentTimeMillis(),
                "isDeleted" to false
            )

            val docRef = firestore.collection(path)
                .add(categoryData)
                .await()

            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncReminderToCloud(
        reminder: Reminder,
        groupId: String
    ): Result<String> {
        return try {
            val uid = getCurrentUserId() ?: throw Exception("Not authenticated")
            val path = getGroupPath(groupId, "reminders")

            val reminderData = hashMapOf(
                "carId" to reminder.carId,
                "title" to reminder.title,
                "type" to reminder.type,
                "targetDate" to reminder.targetDate?.time,
                "targetMileage" to reminder.targetMileage,
                "periodMonths" to reminder.periodMonths,
                "isCompleted" to reminder.isCompleted,
                "completedDate" to reminder.completedDate?.time,
                "completedMileage" to reminder.completedMileage,
                "notifyDaysBefore" to reminder.notifyDaysBefore,
                "notifyKmBefore" to reminder.notifyKmBefore,
                "note" to reminder.note,
                "createdDate" to reminder.createdDate.time,
                "createdBy" to uid,
                "updatedAt" to System.currentTimeMillis(),
                "isDeleted" to reminder.isDeleted
            )

            if (reminder.cloudId.isEmpty()) {
                val docRef = firestore.collection(path)
                    .add(reminderData)
                    .await()

                Result.success(docRef.id)
            } else {
                firestore.collection(path)
                    .document(reminder.cloudId)
                    .set(reminderData)
                    .await()

                Result.success(reminder.cloudId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncTireToCloud(
        tire: TireReplacement,
        groupId: String
    ): Result<String> {
        return try {
            val uid = getCurrentUserId() ?: throw Exception("Not authenticated")
            val path = getGroupPath(groupId, "tires")

            val tireData = hashMapOf(
                "carId" to tire.carId,
                "tireType" to tire.tireType,
                "brand" to tire.brand,
                "model" to tire.model,
                "size" to tire.size,
                "installationDate" to tire.installationDate.time,
                "installationMileage" to tire.installationMileage,
                "price" to tire.price,
                "reminderSet" to tire.reminderSet,
                "expectedLifetimeYears" to tire.expectedLifetimeYears,
                "expectedLifetimeKm" to tire.expectedLifetimeKm,
                "notes" to tire.notes,
                "isActive" to tire.isActive,
                "expenseId" to tire.expenseId,
                "createdBy" to uid,
                "updatedAt" to System.currentTimeMillis(),
                "isDeleted" to tire.isDeleted
            )

            if (tire.cloudId.isEmpty()) {
                val docRef = firestore.collection(path)
                    .add(tireData)
                    .await()

                Result.success(docRef.id)
            } else {
                firestore.collection(path)
                    .document(tire.cloudId)
                    .set(tireData)
                    .await()

                Result.success(tire.cloudId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkGroupAccess(groupId: String, userId: String): Boolean {
        return try {
            val doc = firestore.collection("carGroups")
                .document(groupId)
                .get()
                .await()

            if (!doc.exists()) return false

            val members = doc.get("members") as? List<String> ?: emptyList()
            val ownerUid = doc.getString("ownerUid")

            members.contains(userId) || ownerUid == userId
        } catch (e: Exception) {
            Log.e("FirebaseController", "Error checking group access: ${e.message}")
            false
        }
    }

    fun listenToExpenses(
        groupId: String,
        database: AppDatabase,
        onUpdate: () -> Unit
    ): ListenerRegistration {
        val path = getGroupPath(groupId, "expenses")
        val currentUserId = getCurrentUserId()

        return firestore.collection(path)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        for (doc in snapshot.documents) {
                            val cloudId = doc.id
                            val existing = database.expenseDao().getByCloudId(cloudId)
                            val updatedAt = doc.getLong("updatedAt") ?: 0L
                            val isDeleted = doc.getBoolean("isDeleted") ?: false

                            if (isDeleted) {
                                if (existing != null) {
                                    database.expenseDao().delete(existing)
                                }
                                continue
                            }

                            // Если уже есть с таким cloudId - обновляем, не вставляем новый
                            if (existing != null) {
                                if (existing.updatedAt >= updatedAt) continue

                                database.expenseDao().update(
                                    existing.copy(
                                        amount = doc.getDouble("amount") ?: existing.amount,
                                        category = doc.getString("category") ?: existing.category,
                                        date = doc.getLong("date")?.let { Date(it) } ?: existing.date,
                                        mileage = doc.getLong("mileage")?.toInt() ?: existing.mileage,
                                        comment = doc.getString("comment") ?: existing.comment,
                                        shopName = doc.getString("shopName") ?: existing.shopName,
                                        updatedAt = updatedAt
                                    )
                                )
                                continue
                            }

                            // Проверяем на дубликат по данным (не только по cloudId)
                            val carId = doc.getLong("carId")?.toInt() ?: 0
                            val amount = doc.getDouble("amount") ?: 0.0
                            val category = doc.getString("category") ?: ""
                            val date = doc.getLong("date")?.let { Date(it) } ?: Date()
                            val mileage = doc.getLong("mileage")?.toInt() ?: 0
                            val comment = doc.getString("comment") ?: ""

                            // Ищем похожий расход локально
                            val allExpenses = database.expenseDao().getAllByCar(carId)
                            val duplicate = allExpenses.find { exp ->
                                exp.amount == amount &&
                                        exp.category == category &&
                                        exp.date.time == date.time &&
                                        exp.mileage == mileage &&
                                        exp.comment == comment
                            }

                            if (duplicate != null) {
                                // Обновляем cloudId у существующего расхода
                                database.expenseDao().update(
                                    duplicate.copy(
                                        cloudId = cloudId,
                                        updatedAt = updatedAt
                                    )
                                )
                                continue
                            }

                            // Создаем новый только если нет дубликата
                            val expense = Expense(
                                carId = carId,
                                amount = amount,
                                category = category,
                                categoryId = doc.getLong("categoryId")?.toInt(),
                                date = date,
                                mileage = mileage,
                                comment = comment,
                                shopName = doc.getString("shopName") ?: "",
                                receiptScanned = doc.getBoolean("receiptScanned") ?: false,
                                createdByReceipt = doc.getBoolean("createdByReceipt") ?: false,
                                cloudId = cloudId,
                                updatedAt = updatedAt,
                                isDeleted = false
                            )

                            database.expenseDao().insert(expense)
                        }

                        withContext(Dispatchers.Main) {
                            onUpdate()
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseController", "Error in expense listener: ${e.message}")
                    }
                }
            }
    }

    fun listenToCars(
        groupId: String,
        database: AppDatabase,
        onUpdate: () -> Unit
    ): ListenerRegistration {
        val path = getGroupPath(groupId, "cars")
        val currentUserId = getCurrentUserId()

        return firestore.collection(path)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        for (doc in snapshot.documents) {
                            val cloudId = doc.id
                            val existing = database.carDao().getByCloudId(cloudId)
                            val updatedAt = doc.getLong("updatedAt") ?: 0L
                            val isDeleted = doc.getBoolean("isDeleted") ?: false

                            // ДОБАВЛЕНО: Пропускаем удаленные записи
                            if (isDeleted) {
                                if (existing != null) {
                                    database.carDao().delete(existing.id)
                                }
                                continue
                            }

                            // ДОБАВЛЕНО: Пропускаем, если локальная версия новее
                            if (existing != null && existing.updatedAt >= updatedAt) {
                                continue
                            }

                            // ДОБАВЛЕНО: Пропускаем свои недавние изменения
                            val createdBy = doc.getString("createdBy")
                            if (createdBy == currentUserId && existing != null &&
                                System.currentTimeMillis() - updatedAt < 5000) {
                                continue
                            }

                            val car = Car(
                                id = existing?.id ?: 0,
                                brand = doc.getString("brand") ?: "",
                                model = doc.getString("model") ?: "",
                                year = doc.getLong("year")?.toInt() ?: 0,
                                horsepower = doc.getLong("horsepower")?.toInt() ?: 0,
                                region = doc.getString("region") ?: "",
                                currentMileage = doc.getLong("currentMileage")?.toInt() ?: 0,
                                averageConsumption = doc.getDouble("averageConsumption") ?: 8.5,
                                createdAt = doc.getLong("createdAt")?.let { Date(it) } ?: Date(),
                                cloudId = cloudId,
                                updatedAt = updatedAt,
                                isDeleted = isDeleted
                            )

                            if (existing == null) {
                                // ДОБАВЛЕНО: Дополнительная проверка
                                val duplicateCheck = database.carDao().getByCloudId(cloudId)
                                if (duplicateCheck == null) {
                                    database.carDao().insert(car)
                                }
                            } else if (car.updatedAt > existing.updatedAt) {
                                database.carDao().update(car)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            onUpdate()
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseController", "Error in cars listener: ${e.message}")
                    }
                }
            }
    }

    fun listenToCategories(
        groupId: String,
        database: AppDatabase,
        onUpdate: () -> Unit
    ): ListenerRegistration {
        val path = getGroupPath(groupId, "categories")
        val currentUserId = getCurrentUserId()

        return firestore.collection(path)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        for (doc in snapshot.documents) {
                            val cloudId = doc.id
                            val existingByCloudId = database.categoryDao().getByCloudId(cloudId)
                            val updatedAt = doc.getLong("updatedAt") ?: 0L
                            val isDeleted = doc.getBoolean("isDeleted") ?: false

                            val name = doc.getString("name") ?: ""
                            val icon = doc.getString("icon") ?: "💰"
                            val color = doc.getLong("color")?.toInt() ?: 0xFF9E9E9E.toInt()
                            val isDefault = doc.getBoolean("isDefault") ?: false
                            val sortOrder = doc.getLong("sortOrder")?.toInt() ?: 0

                            // Если документ удален в Firebase
                            if (isDeleted) {
                                if (existingByCloudId != null) {
                                    database.categoryDao().delete(existingByCloudId)
                                }
                                continue
                            }

                            // Если уже есть с таким cloudId
                            if (existingByCloudId != null) {
                                if (existingByCloudId.updatedAt >= updatedAt) continue

                                database.categoryDao().update(
                                    existingByCloudId.copy(
                                        name = name,
                                        icon = icon,
                                        color = color,
                                        isDefault = isDefault,
                                        sortOrder = sortOrder,
                                        updatedAt = updatedAt
                                    )
                                )
                                continue
                            }

                            // Проверяем дубликат по имени
                            val existingByName = database.categoryDao().getByName(name)

                            if (existingByName != null) {
                                if (existingByName.cloudId.isEmpty()) {
                                    database.categoryDao().update(
                                        existingByName.copy(
                                            cloudId = cloudId,
                                            icon = icon,
                                            color = color,
                                            updatedAt = updatedAt
                                        )
                                    )
                                } else if (existingByName.cloudId == cloudId) {
                                    if (updatedAt > existingByName.updatedAt) {
                                        database.categoryDao().update(
                                            existingByName.copy(
                                                icon = icon,
                                                color = color,
                                                sortOrder = sortOrder,
                                                updatedAt = updatedAt
                                            )
                                        )
                                    }
                                }
                                continue
                            }

                            // Пропускаем свои недавние изменения
                            val createdBy = doc.getString("createdBy")
                            if (createdBy == currentUserId && System.currentTimeMillis() - updatedAt < 10000) {
                                continue
                            }

                            // Создаем новую категорию
                            val category = ExpenseCategory(
                                name = name,
                                icon = icon,
                                color = color,
                                isDefault = isDefault,
                                sortOrder = sortOrder,
                                cloudId = cloudId,
                                updatedAt = updatedAt
                            )
                            database.categoryDao().insert(category)
                        }

                        withContext(Dispatchers.Main) {
                            onUpdate()
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseController", "Error in categories listener: ${e.message}")
                    }
                }
            }
    }

    fun listenToReminders(
        groupId: String,
        database: AppDatabase,
        onUpdate: () -> Unit
    ): ListenerRegistration {
        val path = getGroupPath(groupId, "reminders")
        val currentUserId = getCurrentUserId()

        return firestore.collection(path)
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        for (doc in snapshot.documents) {
                            val cloudId = doc.id
                            val existing = database.reminderDao().getByCloudId(cloudId)
                            val updatedAt = doc.getLong("updatedAt") ?: 0L
                            val isDeleted = doc.getBoolean("isDeleted") ?: false

                            // ДОБАВЛЕНО: Пропускаем удаленные
                            if (isDeleted) {
                                if (existing != null) {
                                    database.reminderDao().delete(existing)
                                }
                                continue
                            }

                            // ДОБАВЛЕНО: Пропускаем, если локальная версия новее
                            if (existing != null && existing.updatedAt >= updatedAt) {
                                continue
                            }

                            // ДОБАВЛЕНО: Пропускаем свои недавние изменения
                            val createdBy = doc.getString("createdBy")
                            if (createdBy == currentUserId && existing != null &&
                                System.currentTimeMillis() - updatedAt < 5000) {
                                continue
                            }

                            // ОСТАЛЬНОЙ КОД БЕЗ ИЗМЕНЕНИЙ
                            val carId = doc.getLong("carId")?.toInt() ?: 0
                            val title = doc.getString("title") ?: ""
                            val type = doc.getString("type") ?: ""
                            val targetDate = doc.getLong("targetDate")?.let { Date(it) }
                            val targetMileage = doc.getLong("targetMileage")?.toInt()
                            val periodMonths = doc.getLong("periodMonths")?.toInt()
                            val isCompleted = doc.getBoolean("isCompleted") ?: false
                            val completedDate = doc.getLong("completedDate")?.let { Date(it) }
                            val completedMileage = doc.getLong("completedMileage")?.toInt()
                            val notifyDaysBefore = doc.getLong("notifyDaysBefore")?.toInt() ?: 7
                            val notifyKmBefore = doc.getLong("notifyKmBefore")?.toInt() ?: 500
                            val note = doc.getString("note") ?: ""
                            val createdDate = doc.getLong("createdDate")?.let { Date(it) } ?: Date()

                            if (existing == null) {
                                val reminder = Reminder(
                                    carId = carId,
                                    title = title,
                                    type = type,
                                    targetDate = targetDate,
                                    targetMileage = targetMileage,
                                    periodMonths = periodMonths,
                                    isCompleted = isCompleted,
                                    completedDate = completedDate,
                                    completedMileage = completedMileage,
                                    notifyDaysBefore = notifyDaysBefore,
                                    notifyKmBefore = notifyKmBefore,
                                    note = note,
                                    createdDate = createdDate,
                                    cloudId = cloudId,
                                    updatedAt = updatedAt
                                )
                                database.reminderDao().insert(reminder)
                            } else if (updatedAt > existing.updatedAt) {
                                database.reminderDao().update(
                                    existing.copy(
                                        title = title,
                                        type = type,
                                        targetDate = targetDate,
                                        targetMileage = targetMileage,
                                        periodMonths = periodMonths,
                                        isCompleted = isCompleted,
                                        completedDate = completedDate,
                                        completedMileage = completedMileage,
                                        notifyDaysBefore = notifyDaysBefore,
                                        notifyKmBefore = notifyKmBefore,
                                        note = note,
                                        updatedAt = updatedAt
                                    )
                                )
                            }
                        }

                        withContext(Dispatchers.Main) {
                            onUpdate()
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseController", "Error in reminders listener: ${e.message}")
                    }
                }
            }
    }

    fun listenToTires(
        groupId: String,
        database: AppDatabase,
        onUpdate: () -> Unit
    ): ListenerRegistration {
        val path = getGroupPath(groupId, "tires")
        val currentUserId = getCurrentUserId()

        return firestore.collection(path)
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        for (doc in snapshot.documents) {
                            val cloudId = doc.id
                            val existing = database.tireReplacementDao().getByCloudId(cloudId)
                            val updatedAt = doc.getLong("updatedAt") ?: 0L
                            val isDeleted = doc.getBoolean("isDeleted") ?: false

                            // ДОБАВЛЕНО: Пропускаем удаленные
                            if (isDeleted) {
                                if (existing != null) {
                                    database.tireReplacementDao().delete(existing)
                                }
                                continue
                            }

                            // ДОБАВЛЕНО: Пропускаем, если локальная версия новее
                            if (existing != null && existing.updatedAt >= updatedAt) {
                                continue
                            }

                            // ДОБАВЛЕНО: Пропускаем свои недавние изменения
                            val createdBy = doc.getString("createdBy")
                            if (createdBy == currentUserId && existing != null &&
                                System.currentTimeMillis() - updatedAt < 5000) {
                                continue
                            }

                            // ОСТАЛЬНОЙ КОД БЕЗ ИЗМЕНЕНИЙ
                            val carId = doc.getLong("carId")?.toInt() ?: 0
                            val tireType = doc.getString("tireType") ?: ""
                            val brand = doc.getString("brand") ?: ""
                            val model = doc.getString("model") ?: ""
                            val size = doc.getString("size") ?: ""
                            val installationDate = doc.getLong("installationDate")?.let { Date(it) } ?: Date()
                            val installationMileage = doc.getLong("installationMileage")?.toInt() ?: 0
                            val expectedLifetimeYears = doc.getLong("expectedLifetimeYears")?.toInt() ?: 4
                            val expectedLifetimeKm = doc.getLong("expectedLifetimeKm")?.toInt() ?: 60000
                            val isActive = doc.getBoolean("isActive") ?: true
                            val price = doc.getDouble("price") ?: 0.0
                            val reminderSet = doc.getBoolean("reminderSet") ?: false
                            val notes = doc.getString("notes") ?: ""
                            val expenseId = doc.getLong("expenseId")?.toInt()

                            if (existing == null) {
                                val tire = TireReplacement(
                                    carId = carId,
                                    tireType = tireType,
                                    brand = brand,
                                    model = model,
                                    size = size,
                                    installationDate = installationDate,
                                    installationMileage = installationMileage,
                                    expectedLifetimeYears = expectedLifetimeYears,
                                    expectedLifetimeKm = expectedLifetimeKm,
                                    isActive = isActive,
                                    price = price,
                                    reminderSet = reminderSet,
                                    notes = notes,
                                    expenseId = expenseId,
                                    cloudId = cloudId,
                                    updatedAt = updatedAt
                                )
                                database.tireReplacementDao().insert(tire)
                            } else if (updatedAt > existing.updatedAt) {
                                database.tireReplacementDao().update(
                                    existing.copy(
                                        tireType = tireType,
                                        brand = brand,
                                        model = model,
                                        size = size,
                                        installationDate = installationDate,
                                        installationMileage = installationMileage,
                                        expectedLifetimeYears = expectedLifetimeYears,
                                        expectedLifetimeKm = expectedLifetimeKm,
                                        isActive = isActive,
                                        price = price,
                                        reminderSet = reminderSet,
                                        notes = notes,
                                        expenseId = expenseId,
                                        updatedAt = updatedAt
                                    )
                                )
                            }
                        }

                        withContext(Dispatchers.Main) {
                            onUpdate()
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseController", "Error in tires listener: ${e.message}")
                    }
                }
            }
    }

    fun startRealtimeSync(
        groupId: String,
        database: AppDatabase,
        onUpdate: () -> Unit
    ) {
        listeners["expenses"] = listenToExpenses(groupId, database, onUpdate)
        listeners["cars"] = listenToCars(groupId, database, onUpdate)
        listeners["categories"] = listenToCategories(groupId, database, onUpdate)
        listeners["reminders"] = listenToReminders(groupId, database, onUpdate)
        listeners["tires"] = listenToTires(groupId, database, onUpdate)
    }

    fun stopRealtimeSync() {
        listeners.values.forEach { it.remove() }
        listeners.clear()
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8)
            .map { chars[Random().nextInt(chars.length)] }
            .joinToString("")
    }
}