package com.example.autouchet.Models

import androidx.room.*

@Dao
interface CarDao {
    @Insert
    suspend fun insert(car: Car): Long

    @Update
    suspend fun update(car: Car)

    @Query("SELECT * FROM cars")
    suspend fun getAll(): List<Car>

    @Query("SELECT * FROM cars WHERE id = :id")
    suspend fun getById(id: Int): Car?

    @Query("DELETE FROM cars WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM cars WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getByCloudId(cloudId: String): Car?

    @Query("SELECT * FROM cars WHERE isDeleted = 0")
    suspend fun getAllActive(): List<Car>
}