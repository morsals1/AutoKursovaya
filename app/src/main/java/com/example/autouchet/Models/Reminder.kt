package com.example.autouchet.Models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val carId: Int,
    val title: String,
    val type: String, // "date", "mileage", "periodic"
    val targetDate: Date? = null,
    val targetMileage: Int? = null,
    val periodMonths: Int? = null,
    val isCompleted: Boolean = false,
    val completedDate: Date? = null,
    val completedMileage: Int? = null,
    val createdDate: Date = Date(),
    val notifyDaysBefore: Int = 7,
    val notifyKmBefore: Int = 500,
    val note: String = "" // УБЕРИТЕ amount, оно не нужно для напоминаний
) {
    fun getStatus(currentMileage: Int, currentDate: Date): String {
        if (isCompleted) return "Выполнено"

        return when(type) {
            "date" -> {
                targetDate?.let {
                    val daysLeft = (it.time - currentDate.time) / (1000 * 60 * 60 * 24)
                    if (daysLeft > 0) "Осталось $daysLeft дней"
                    else "Просрочено ${-daysLeft} дней"
                } ?: "Нет даты"
            }
            "mileage" -> {
                targetMileage?.let {
                    val kmLeft = it - currentMileage
                    if (kmLeft > 0) "Осталось $kmLeft км"
                    else "Просрочено ${-kmLeft} км"
                } ?: "Нет пробега"
            }
            "periodic" -> {
                if (periodMonths != null) "Повтор каждые $periodMonths мес."
                else "Периодическое"
            }
            else -> "Неизвестный тип"
        }
    }
}