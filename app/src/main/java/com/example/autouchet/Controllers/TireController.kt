package com.example.autouchet.Controllers

import android.content.Context
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.TireReplacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class TireController(private val context: Context) {

    suspend fun addTireReplacement(
        carId: Int,
        tireType: String,
        brand: String,
        model: String,
        size: String,
        installationDate: Date,
        installationMileage: Int,
        price: Double,
        expectedLifetimeYears: Int = 4,
        expectedLifetimeKm: Int = 60000,
        notes: String = ""
    ): Long {
        return CoroutineScope(Dispatchers.IO).run {
            val database = AppDatabase.getDatabase(context)

            // Деактивируем старые шины такого же типа
            database.tireReplacementDao().deactivateAllTires(carId)

            // Создаем новую запись
            val tire = TireReplacement(
                carId = carId,
                tireType = tireType,
                brand = brand,
                model = model,
                size = size,
                installationDate = installationDate,
                installationMileage = installationMileage,
                price = price,
                expectedLifetimeYears = expectedLifetimeYears,
                expectedLifetimeKm = expectedLifetimeKm,
                notes = notes,
                isActive = true
            )

            database.tireReplacementDao().insert(tire)
        }
    }

    suspend fun getActiveTires(carId: Int): List<TireReplacement> {
        return CoroutineScope(Dispatchers.IO).run {
            val database = AppDatabase.getDatabase(context)
            database.tireReplacementDao().getActiveTires(carId)
        }
    }

    suspend fun getTireHistory(carId: Int): List<TireReplacement> {
        return CoroutineScope(Dispatchers.IO).run {
            val database = AppDatabase.getDatabase(context)
            database.tireReplacementDao().getByCar(carId)
        }
    }

    suspend fun checkTireCondition(carId: Int, currentDate: Date, currentMileage: Int): List<Pair<TireReplacement, String>> {
        val activeTires = getActiveTires(carId)
        val results = mutableListOf<Pair<TireReplacement, String>>()

        for (tire in activeTires) {
            val (needsReplacement, message) = tire.needsReplacement(currentDate, currentMileage)
            results.add(Pair(tire, message))
        }

        return results
    }

    suspend fun updateTire(tire: TireReplacement) {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(context)
            database.tireReplacementDao().update(tire)
        }
    }

    suspend fun deleteTire(tire: TireReplacement) {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(context)
            database.tireReplacementDao().delete(tire)
        }
    }
}