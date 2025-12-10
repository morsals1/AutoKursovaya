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
}