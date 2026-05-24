package com.example.autouchet.Models

data class CarGroup(
    val groupId: String = "",
    val inviteCode: String = "",
    val carId: Int = 0,
    val ownerUid: String = "",
    val members: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)