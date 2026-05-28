package com.example.autouchet.Models

import androidx.room.*

@Dao
interface PendingSyncDao {
    @Insert
    suspend fun insert(entity: PendingSyncEntity): Long

    @Delete
    suspend fun delete(entity: PendingSyncEntity)

    @Query("SELECT * FROM pending_sync ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingSyncEntity>

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM pending_sync")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM pending_sync")
    suspend fun getCount(): Int
}