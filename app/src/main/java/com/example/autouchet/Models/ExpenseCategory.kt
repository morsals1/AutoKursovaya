package com.example.autouchet.Models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class ExpenseCategory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val icon: String,
    val color: Int,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)