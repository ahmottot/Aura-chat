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
) {
    fun buildUrl(path: String): String {
        val cleanIp = ipAddress.trim()
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        
        // If it already looks like a URI scheme, use it as is
        if (cleanIp.startsWith("http://") || cleanIp.startsWith("https://")) {
            val base = if (cleanIp.endsWith("/")) cleanIp.dropLast(1) else cleanIp
            return "$base$cleanPath"
        }
        
        // If it looks like a public network tunnel address
        if (cleanIp.contains(".ngrok") || cleanIp.contains(".localtunnel") || cleanIp.contains(".trycloudflare") || cleanIp.contains(".telebit") || cleanIp.contains("localhost")) {
            // Public tunnel services use standard SSL/HTTPS
            val prefix = if (cleanIp.contains("localhost")) "http://" else "https://"
            val base = if (cleanIp.endsWith("/")) cleanIp.dropLast(1) else cleanIp
            return "$prefix$base$cleanPath"
        }
        
        // Regular IP:Port pairing
        return "http://$cleanIp:$port$cleanPath"
    }
}

