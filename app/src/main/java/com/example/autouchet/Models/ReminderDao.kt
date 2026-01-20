package com.example.autouchet.Models

import androidx.room.*

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("SELECT * FROM reminders WHERE carId = :carId AND isCompleted = 0 ORDER BY targetDate ASC")
    suspend fun getActiveByCar(carId: Int): List<Reminder>

    @Query("SELECT * FROM reminders WHERE carId = :carId ORDER BY createdDate DESC")
    suspend fun getAllByCar(carId: Int): List<Reminder>


    @Query("SELECT * FROM reminders WHERE carId = :carId AND isCompleted = 1 ORDER BY completedDate DESC")
    suspend fun getCompletedByCar(carId: Int): List<Reminder>

    @Query("SELECT * FROM reminders WHERE carId = :carId AND type = :type AND isCompleted = 0 ORDER BY targetDate ASC")
    suspend fun getActiveByType(carId: Int, type: String): List<Reminder>

    @Query("SELECT * FROM reminders WHERE carId = :carId AND targetMileage IS NOT NULL AND targetMileage > :currentMileage AND targetMileage <= :maxMileage AND isCompleted = 0")
    suspend fun getUpcomingMileageReminders(carId: Int, currentMileage: Int, maxMileage: Int = 1000): List<Reminder>

    @Query("SELECT * FROM reminders WHERE carId = :carId AND targetDate IS NOT NULL AND targetDate BETWEEN :startDate AND :endDate AND isCompleted = 0")
    suspend fun getUpcomingDateReminders(carId: Int, startDate: Long, endDate: Long): List<Reminder>

    @Query("UPDATE reminders SET isCompleted = 1, completedDate = :date, completedMileage = :mileage WHERE id = :reminderId")
    suspend fun markAsCompleted(reminderId: Int, date: Long, mileage: Int)

    @Query("UPDATE reminders SET targetDate = :newDate WHERE id = :reminderId")
    suspend fun postponeReminder(reminderId: Int, newDate: Long)

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    suspend fun getById(reminderId: Int): Reminder?

    @Query("DELETE FROM reminders WHERE carId = :carId")
    suspend fun deleteByCarId(carId: Int)

    @Query("SELECT COUNT(*) FROM reminders WHERE carId = :carId AND isCompleted = 0")
    suspend fun getActiveCount(carId: Int): Int

    @Query("SELECT * FROM reminders WHERE carId = :carId AND isCompleted = 0 AND (targetDate <= :date OR targetMileage <= :mileage)")
    suspend fun getOverdueReminders(carId: Int, date: Long, mileage: Int): List<Reminder>
}