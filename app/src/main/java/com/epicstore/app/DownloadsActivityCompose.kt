package com.epicstore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.epicstore.app.ui.screens.DownloadsScreen
import com.epicstore.app.ui.theme.EpicStoreTheme
import com.epicstore.app.viewmodel.DownloadsViewModel

class DownloadsActivityCompose : ComponentActivity() {
    
    private val viewModel: DownloadsViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            EpicStoreTheme {
                val downloads by viewModel.downloads.collectAsState()
                
                DownloadsScreen(
                    downloads = downloads,
                    onBackClick = { finish() },
                    onPauseDownload = { appName ->
                        viewModel.pauseDownload(appName)
                    },
                    onCancelDownload = { appName ->
                        viewModel.cancelDownload(appName)
                    }
                )
            }
        }
    }
}
