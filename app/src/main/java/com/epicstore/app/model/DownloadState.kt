package com.epicstore.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadState(
    @PrimaryKey
    val appName: String,
    val gameName: String,
    val namespace: String,
    val catalogItemId: String,
    val totalSize: Long = 0L,
    val downloadedSize: Long = 0L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val downloadSpeed: Long = 0L,
    val resumeData: String? = null,
    val errorMessage: String? = null,
    val startTime: Long = System.currentTimeMillis(),
    val lastUpdateTime: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR,
    CANCELLED
}
