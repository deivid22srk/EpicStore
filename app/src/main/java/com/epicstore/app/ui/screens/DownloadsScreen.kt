package com.epicstore.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.epicstore.app.model.DownloadState
import com.epicstore.app.service.DownloadService
import com.epicstore.app.ui.components.DownloadCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloads: List<DownloadState>,
    onBackClick: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit
) {
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        if (downloads.isEmpty()) {
            EmptyDownloadsContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads) { download ->
                    DownloadCard(
                        download = download,
                        onPause = { onPauseDownload(download.appName) },
                        onResume = {
                            val intent = Intent(context, DownloadService::class.java).apply {
                                putExtra(DownloadService.EXTRA_GAME_NAME, download.gameName)
                                putExtra(DownloadService.EXTRA_APP_NAME, download.appName)
                                putExtra(DownloadService.EXTRA_NAMESPACE, download.namespace)
                                putExtra(DownloadService.EXTRA_CATALOG_ITEM_ID, download.catalogItemId)
                                putExtra(DownloadService.EXTRA_RESUME, true)
                            }
                            context.startService(intent)
                        },
                        onCancel = { onCancelDownload(download.appName) }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDownloadsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Nenhum download ativo",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Os downloads que você iniciar aparecerão aqui",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}
