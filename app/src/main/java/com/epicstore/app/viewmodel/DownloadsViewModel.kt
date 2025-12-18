package com.epicstore.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.epicstore.app.database.AppDatabase
import com.epicstore.app.model.DownloadState
import com.epicstore.app.model.DownloadStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val downloadDao = database.downloadDao()
    
    val downloads = downloadDao.getAllDownloadsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    fun pauseDownload(appName: String) {
        viewModelScope.launch {
            val download = downloadDao.getDownload(appName)
            if (download != null) {
                downloadDao.updateDownload(
                    download.copy(
                        status = DownloadStatus.PAUSED,
                        lastUpdateTime = System.currentTimeMillis()
                    )
                )
            }
        }
    }
    
    fun cancelDownload(appName: String) {
        viewModelScope.launch {
            downloadDao.deleteDownload(appName)
        }
    }
    
    fun getDownload(appName: String) = downloadDao.getDownloadFlow(appName)
}
