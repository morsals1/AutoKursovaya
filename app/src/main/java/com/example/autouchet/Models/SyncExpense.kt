package com.example.autouchet.Models

import java.util.Date

data class SyncExpense(
    val id: String = "",
    val localId: Int = 0,
    val carId: Int = 0,
    val groupId: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val date: Date = Date(),
    val mileage: Int = 0,
    val comment: String = "",
    val shopName: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)