package com.example.autouchet.Models

import androidx.room.*

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Query("SELECT * FROM reminders WHERE carId = :carId AND isCompleted = 0 ORDER BY targetDate ASC")
    suspend fun getActiveByCar(carId: Int): List<Reminder>

    @Query("SELECT * FROM reminders WHERE carId = :carId ORDER BY createdDate DESC")
    suspend fun getAllByCar(carId: Int): List<Reminder>
}