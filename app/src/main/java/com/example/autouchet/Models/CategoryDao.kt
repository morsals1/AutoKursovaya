package com.example.autouchet.Models

import androidx.room.*

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: ExpenseCategory): Long

    @Update
    suspend fun update(category: ExpenseCategory)

    @Delete
    suspend fun delete(category: ExpenseCategory)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<ExpenseCategory>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ExpenseCategory?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Int): ExpenseCategory?

    @Query("DELETE FROM categories WHERE isDefault = 0")
    suspend fun deleteAllCustom()

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Int, sortOrder: Int)

    @Query("SELECT * FROM categories WHERE name LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<ExpenseCategory>
}