package com.example.autouchet.Controllers

import android.content.Context
import android.util.Log
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.ExpenseCategory
import com.example.autouchet.Utils.SharedPrefsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategoryController(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)

    suspend fun getAllCategories(): List<ExpenseCategory> {
        return withContext(Dispatchers.IO) {
            database.categoryDao().getAll()
        }
    }

    suspend fun addCategory(name: String, icon: String, color: Int): Long {
        return withContext(Dispatchers.IO) {
            val existing = database.categoryDao().getByName(name)
            if (existing != null) {
                return@withContext -1L
            }
            val category = ExpenseCategory(
                name = name,
                icon = icon,
                color = color,
                isDefault = false,
                sortOrder = Int.MAX_VALUE
            )
            database.categoryDao().insert(category)
        }
    }

    suspend fun updateCategory(category: ExpenseCategory) {
        withContext(Dispatchers.IO) {
            database.categoryDao().update(category)
        }
    }

    suspend fun deleteCategory(categoryId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            val category = database.categoryDao().getById(categoryId)
            if (category?.isDefault == true) {
                return@withContext false
            }

            if (category != null) {
                // Удаляем локально
                database.categoryDao().delete(category)

                // Синхронизируем удаление с Firebase
                if (category.cloudId.isNotEmpty()) {
                    try {
                        val syncController = SyncController(context)
                        val groupId = SharedPrefsHelper.getGroupId(context)
                        if (groupId != null) {
                            syncController.setGroupId(groupId)
                            syncController.deleteCategory(category)
                            Log.d("CategoryController", "Delete synced: ${category.cloudId}")
                        }
                    } catch (e: Exception) {
                        Log.e("CategoryController", "Sync error: ${e.message}")
                    }
                }
                true
            } else {
                false
            }
        }
    }

    fun deleteCategoryWithSync(categoryId: Int, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val category = database.categoryDao().getById(categoryId)

                if (category == null || category.isDefault) {
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }

                // Удаляем локально
                database.categoryDao().delete(category)

                // Синхронизируем удаление
                if (category.cloudId.isNotEmpty()) {
                    val syncController = SyncController(context)
                    val groupId = SharedPrefsHelper.getGroupId(context)
                    if (groupId != null) {
                        syncController.setGroupId(groupId)
                        syncController.deleteCategory(category)
                    }
                }

                withContext(Dispatchers.Main) { onComplete(true) }

            } catch (e: Exception) {
                Log.e("CategoryController", "Error: ${e.message}")
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    suspend fun getCategoryByName(name: String): ExpenseCategory? {
        return withContext(Dispatchers.IO) {
            database.categoryDao().getByName(name)
        }
    }

    suspend fun updateCategoryOrder(categories: List<ExpenseCategory>) {
        withContext(Dispatchers.IO) {
            categories.forEachIndexed { index, category ->
                database.categoryDao().updateSortOrder(category.id, index)
            }
        }
    }

    fun getDefaultIcons(): List<String> {
        return listOf(
            "💰", "⛽", "🔧", "🚗", "💼", "🛡️", "⚙️", "🚿", "🅿️", "📋",
            "🔋", "🛞", "🧰", "🧽", "🚘", "🔌", "🛢️", "🧪", "🔩", "⚡",
            "🎫", "📄", "🏪", "🏦", "💳", "📊", "📈", "🏁", "🚦", "🛣️"
        )
    }

    fun getDefaultColors(): List<Pair<String, Int>> {
        return listOf(
            "Зеленый" to 0xFF4CAF50.toInt(),
            "Синий" to 0xFF2196F3.toInt(),
            "Оранжевый" to 0xFFFF9800.toInt(),
            "Красный" to 0xFFF44336.toInt(),
            "Фиолетовый" to 0xFF9C27B0.toInt(),
            "Коричневый" to 0xFF795548.toInt(),
            "Голубой" to 0xFF00BCD4.toInt(),
            "Серый" to 0xFF9E9E9E.toInt(),
            "Темно-синий" to 0xFF3F51B5.toInt(),
            "Розовый" to 0xFFE91E63.toInt(),
            "Желтый" to 0xFFFFEB3B.toInt(),
            "Бирюзовый" to 0xFF009688.toInt(),
            "Лайм" to 0xFFCDDC39.toInt(),
            "Янтарный" to 0xFFFFC107.toInt(),
            "Индиго" to 0xFF536DFE.toInt()
        )
    }
}