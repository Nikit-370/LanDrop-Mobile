package com.landrop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val fileSize: Long,
    val isUpload: Boolean, // true if uploaded to phone, false if downloaded from phone
    val remoteDevice: String, // Client IP or custom device name
    val progress: Float = 0f, // 0f to 1f
    val status: String, // "PENDING", "TRANSFERRING", "SUCCESS", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)
