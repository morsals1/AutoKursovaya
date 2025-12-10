package com.example.autouchet.Models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "tire_replacements")
data class TireReplacement(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val carId: Int,
    val tireType: String, // "Зимняя", "Летняя", "Всесезонная"
    val installationDate: Date,
    val installationMileage: Int,
    val price: Double,
    val reminderSet: Boolean = false,
    val expectedLifetimeYears: Int = 2,
    val expectedLifetimeKm: Int = 40000
) {
    fun needsReplacement(currentDate: Date, currentMileage: Int): Boolean {
        val yearsPassed = (currentDate.time - installationDate.time) / (1000L * 60 * 60 * 24 * 365)
        val kmPassed = currentMileage - installationMileage

        return yearsPassed >= expectedLifetimeYears || kmPassed >= expectedLifetimeKm
    }
}