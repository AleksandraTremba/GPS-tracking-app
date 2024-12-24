package com.taltech.ee.finalproject

data class Checkpoint(
    val id: Long,
    val sessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
