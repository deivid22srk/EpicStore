package com.epicstore.app.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.epicstore.app.DownloadsActivityCompose
import com.epicstore.app.model.DownloadState
import com.epicstore.app.model.DownloadStatus
import com.epicstore.app.service.DownloadService
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailsScreen(
    gameName: String,
    appName: String,
    namespace: String,
    catalogItemId: String,
    imageUrl: String?,
    gameSize: String?,
    download: DownloadState?,
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(gameName) },
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
        },
        floatingActionButton = {
            if (download == null) {
                ExtendedFloatingActionButton(
                    onClick = onDownloadClick,
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    text = { Text("Baixar") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = gameName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentScale = ContentScale.Crop
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = gameName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                GameInfoRow("App Name", appName)
                Spacer(modifier = Modifier.height(12.dp))
                
                GameInfoRow("Namespace", namespace)
                Spacer(modifier = Modifier.height(12.dp))
                
                GameInfoRow("Tamanho", gameSize ?: "Carregando...")
                
                if (download != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    DownloadProgressCard(
                        download = download,
                        onResumeClick = {
                            val intent = Intent(context, DownloadService::class.java).apply {
                                putExtra(DownloadService.EXTRA_GAME_NAME, gameName)
                                putExtra(DownloadService.EXTRA_APP_NAME, appName)
                                putExtra(DownloadService.EXTRA_NAMESPACE, namespace)
                                putExtra(DownloadService.EXTRA_CATALOG_ITEM_ID, catalogItemId)
                                putExtra(DownloadService.EXTRA_RESUME, true)
                            }
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        },
                        onViewDownloadsClick = {
                            context.startActivity(Intent(context, DownloadsActivityCompose::class.java))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GameInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun DownloadProgressCard(
    download: DownloadState,
    onResumeClick: () -> Unit,
    onViewDownloadsClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Download em andamento",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val progress = if (download.totalSize > 0) {
                (download.downloadedSize.toFloat() / download.totalSize.toFloat())
            } else {
                0f
            }
            
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val downloadedMB = download.downloadedSize / (1024.0 * 1024.0)
                val totalMB = download.totalSize / (1024.0 * 1024.0)
                
                Text(
                    text = String.format("%.1f MB / %.1f MB", downloadedMB, totalMB),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                val progressPercent = (progress * 100).roundToInt()
                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            when (download.status) {
                DownloadStatus.DOWNLOADING -> {
                    if (download.downloadSpeed > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val speedMB = download.downloadSpeed / (1024.0 * 1024.0)
                        Text(
                            text = String.format("%.2f MB/s", speedMB),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                DownloadStatus.PAUSED -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onResumeClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retomar Download")
                    }
                }
                DownloadStatus.ERROR -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Erro - Toque para tentar novamente",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onResumeClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tentar Novamente")
                    }
                }
                else -> {}
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(
                onClick = onViewDownloadsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ver Downloads")
            }
        }
    }
}
