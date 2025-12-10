package com.example.autouchet.Models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "cars")
data class Car(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val brand: String,
    val model: String,
    val year: Int,
    val horsepower: Int,
    val region: String,
    var currentMileage: Int,
    val averageConsumption: Double = 8.5,
    val createdAt: Date = Date()
) {
    fun getFullName(): String = "$brand $model ($year)"
}