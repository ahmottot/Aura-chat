package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paired_devices")
data class PairedDevice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val pinCode: String,
    val isCurrent: Boolean = false,
    val pairedAt: Long = System.currentTimeMillis()
)
