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
        notes: String = "",
        replaceAllTires: Boolean = false
    ): Long {
        return CoroutineScope(Dispatchers.IO).run {
            val database = AppDatabase.getDatabase(context)

            if (replaceAllTires) {
                val activeTires = database.tireReplacementDao().getByCar(carId)
                    .filter { it.isActive }
                activeTires.forEach { tire ->
                    database.tireReplacementDao().update(tire.copy(isActive = false))
                }
            } else {
                val oldTires = database.tireReplacementDao().getByCar(carId)
                    .filter { it.isActive && it.tireType == tireType }
                oldTires.forEach { oldTire ->
                    database.tireReplacementDao().update(oldTire.copy(isActive = false))
                }
            }
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
            database.tireReplacementDao().getByCar(carId).filter { it.isActive }
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