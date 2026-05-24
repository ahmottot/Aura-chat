package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String, // "USER", "AI", "SYSTEM"
    val content: String,
    val imageUrl: String? = null, // Store local path or URL of desktop screenshots
    val filePath: String? = null, // Storage file path if downloaded
    val fileName: String? = null, // Original name of the PC file
    val timestamp: Long = System.currentTimeMillis()
)
