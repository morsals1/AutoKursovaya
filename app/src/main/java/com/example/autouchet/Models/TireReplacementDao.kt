package com.example.autouchet.Models

import androidx.room.*

@Dao
interface TireReplacementDao {
    @Insert
    suspend fun insert(tire: TireReplacement): Long

    @Update
    suspend fun update(tire: TireReplacement)

    @Query("SELECT * FROM tire_replacements WHERE carId = :carId ORDER BY installationDate DESC")
    suspend fun getByCar(carId: Int): List<TireReplacement>
}