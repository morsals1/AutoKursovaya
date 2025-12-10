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
    val date: Date,
    val mileage: Int,
    val comment: String = "",
    val shopName: String = "",
    val receiptScanned: Boolean = false,
    val createdByReceipt: Boolean = false
) {
    fun getCategoryColor(): Int {
        return when(category) {
            "Топливо" -> 0xFF4CAF50.toInt()
            "Обслуживание" -> 0xFF2196F3.toInt()
            "Шины" -> 0xFFFF9800.toInt()
            "Налоги" -> 0xFFF44336.toInt()
            "Страховка" -> 0xFF9C27B0.toInt()
            "Ремонт" -> 0xFF795548.toInt()
            "Мойка" -> 0xFF00BCD4.toInt()
            else -> 0xFF9E9E9E.toInt()
        }
    }

    fun getCategoryIcon(): String {
        return when(category) {
            "Топливо" -> "⛽"
            "Обслуживание" -> "🔧"
            "Шины" -> "🚗"
            "Налоги" -> "💼"
            "Страховка" -> "🛡️"
            "Ремонт" -> "⚙️"
            "Мойка" -> "🚿"
            else -> "💰"
        }
    }
}