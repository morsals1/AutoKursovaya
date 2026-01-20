package com.example.autouchet.Models

import androidx.room.*

@Dao
interface TireReplacementDao {
    @Insert
    suspend fun insert(tire: TireReplacement): Long

    @Update
    suspend fun update(tire: TireReplacement)

    @Delete
    suspend fun delete(tire: TireReplacement)

    @Query("SELECT * FROM tire_replacements WHERE carId = :carId ORDER BY installationDate DESC")
    suspend fun getByCar(carId: Int): List<TireReplacement>

    @Query("SELECT * FROM tire_replacements WHERE carId = :carId AND isActive = 1")
    suspend fun getActiveTires(carId: Int): List<TireReplacement>

    @Query("SELECT * FROM tire_replacements WHERE id = :id")
    suspend fun getById(id: Int): TireReplacement?

    @Query("UPDATE tire_replacements SET isActive = 0 WHERE carId = :carId AND isActive = 1")
    suspend fun deactivateAllTires(carId: Int)

    @Query("SELECT * FROM tire_replacements WHERE carId = :carId AND tireType = :tireType AND isActive = 1 LIMIT 1")
    suspend fun getActiveTireByType(carId: Int, tireType: String): TireReplacement?

    @Query("SELECT * FROM tire_replacements WHERE carId = :carId AND installationMileage = :mileage ORDER BY installationDate DESC LIMIT 1")
    suspend fun getByMileage(carId: Int, mileage: Int): TireReplacement?
}