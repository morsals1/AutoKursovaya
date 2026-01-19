package com.example.autouchet.Models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "tire_replacements",
    foreignKeys = [ForeignKey(
        entity = Car::class,
        parentColumns = ["id"],
        childColumns = ["carId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TireReplacement(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val carId: Int,
    val tireType: String, // "Зимняя", "Летняя", "Всесезонная"
    val brand: String = "", // Марка шин
    val model: String = "", // Модель шин
    val size: String = "", // Размер, например: 195/65 R15
    val installationDate: Date,
    val installationMileage: Int,
    val price: Double = 0.0,
    val reminderSet: Boolean = false,
    val expectedLifetimeYears: Int = 4,
    val expectedLifetimeKm: Int = 60000,
    val notes: String = "",
    val isActive: Boolean = true // Активны ли сейчас эти шины на автомобиле
) {
    fun needsReplacement(currentDate: Date, currentMileage: Int): Pair<Boolean, String> {
        val daysPassed = (currentDate.time - installationDate.time) / (1000L * 60 * 60 * 24)
        val yearsPassed = daysPassed / 365.0
        val kmPassed = currentMileage - installationMileage

        return when {
            yearsPassed >= expectedLifetimeYears -> {
                Pair(true, "Срок службы шин истёк ($yearsPassed лет)")
            }
            kmPassed >= expectedLifetimeKm -> {
                Pair(true, "Пробег шин превышен ($kmPassed км)")
            }
            else -> {
                val kmLeft = expectedLifetimeKm - kmPassed
                val daysLeft = (expectedLifetimeYears * 365 - daysPassed).toInt()
                Pair(false, "Осталось: $kmLeft км или $daysLeft дней")
            }
        }
    }

    fun getTireInfo(): String {
        return "$brand $model $size ($tireType)"
    }
}