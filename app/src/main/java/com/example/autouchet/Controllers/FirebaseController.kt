package com.example.autouchet.Controllers

import com.example.autouchet.Models.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

class FirebaseController {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = Firebase.firestore

    suspend fun registerUser(email: String, password: String): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("User creation failed")
            val user = User(uid = uid, email = email, displayName = email.substringBefore("@"))
            firestore.collection("users").document(uid).set(user).await()
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
            firestore.collection("users").document(uid).update("lastLoginAt", System.currentTimeMillis()).await()
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(Exception("Неверный email или пароль"))
        }
    }

    fun logout() {
        auth.signOut()
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка отправки письма для восстановления пароля"))
        }
    }

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun observeAuthState(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser != null)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun createCarGroup(carId: Int): Result<Pair<String, String>> {
        return try {
            val uid = getCurrentUserId() ?: throw Exception("Not authenticated")
            val inviteCode = generateInviteCode()
            val groupData = hashMapOf(
                "groupId" to "",
                "inviteCode" to inviteCode,
                "carId" to carId,
                "ownerUid" to uid,
                "members" to mapOf(uid to "owner"),
                "createdAt" to System.currentTimeMillis()
            )
            val docRef = firestore.collection("carGroups").add(groupData).await()
            val groupId = docRef.id
            firestore.collection("carGroups").document(groupId).update("groupId", groupId).await()
            Result.success(Pair(groupId, inviteCode))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinCarGroup(inviteCode: String): Result<String> {
        return try {
            val uid = getCurrentUserId() ?: throw Exception("Not authenticated")
            val snapshot = firestore.collection("carGroups").whereEqualTo("inviteCode", inviteCode).get().await()
            if (snapshot.isEmpty) {
                return Result.failure(Exception("Неверный код приглашения"))
            }
            val document = snapshot.documents.first()
            val groupId = document.id
            val members = document.get("members") as? Map<String, String> ?: emptyMap()
            if (members.containsKey(uid)) {
                return Result.success(groupId)
            }
            val updatedMembers = members.toMutableMap()
            updatedMembers[uid] = "member"
            firestore.collection("carGroups").document(groupId).update("members", updatedMembers).await()
            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncExpenseToCloud(expense: com.example.autouchet.Models.Expense, groupId: String): Result<Unit> {
        return try {
            val uid = getCurrentUserId() ?: throw Exception("Not authenticated")
            val syncData = hashMapOf(
                "localId" to expense.id,
                "carId" to expense.carId,
                "groupId" to groupId,
                "amount" to expense.amount,
                "category" to expense.category,
                "date" to expense.date,
                "mileage" to expense.mileage,
                "comment" to expense.comment,
                "shopName" to expense.shopName,
                "createdBy" to uid,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis(),
                "isDeleted" to false
            )
            firestore.collection("expenses").add(syncData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncExpensesFromCloud(groupId: String): Result<List<SyncExpense>> {
        return try {
            val snapshot = firestore.collection("expenses").whereEqualTo("groupId", groupId).whereEqualTo("isDeleted", false).get().await()
            val expenses = snapshot.documents.mapNotNull { doc ->
                try {
                    SyncExpense(
                        id = doc.id,
                        localId = doc.getLong("localId")?.toInt() ?: 0,
                        carId = doc.getLong("carId")?.toInt() ?: 0,
                        groupId = doc.getString("groupId") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        category = doc.getString("category") ?: "",
                        date = doc.getDate("date") ?: Date(),
                        mileage = doc.getLong("mileage")?.toInt() ?: 0,
                        comment = doc.getString("comment") ?: "",
                        shopName = doc.getString("shopName") ?: "",
                        createdBy = doc.getString("createdBy") ?: ""
                    )
                } catch (e: Exception) { null }
            }
            Result.success(expenses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8).map { chars[Random().nextInt(chars.length)] }.joinToString("")
    }
}