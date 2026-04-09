package com.example.autouchet.Models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val carId: Int,
    val amount: Double,
    val category: String,
    val categoryId: Int? = null,
    val date: Date,
    val mileage: Int,
    val comment: String = "",
    val shopName: String = "",
    val receiptScanned: Boolean = false,
    val createdByReceipt: Boolean = false
)