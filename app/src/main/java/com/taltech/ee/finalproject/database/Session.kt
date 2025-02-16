package com.taltech.ee.finalproject.database

data class Session(
    val id: Long,
    val track: String,
    val name: String,
    val distance: Float,
    val time: Int,
    val pace: String,
    val backendId: String
)