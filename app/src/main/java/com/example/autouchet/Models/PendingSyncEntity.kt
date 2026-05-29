package com.example.autouchet.Models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sync")
data class PendingSyncEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val entityType: String,
    val entityId: Int,
    val operation: String,

    val cloudId: String = "",

    val createdAt: Long = System.currentTimeMillis()
)