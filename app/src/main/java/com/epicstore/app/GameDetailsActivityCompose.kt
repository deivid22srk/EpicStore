package com.epicstore.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.epicstore.app.auth.EpicAuthManager
import com.epicstore.app.network.EpicGamesApi
import com.epicstore.app.service.DownloadService
import com.epicstore.app.ui.screens.GameDetailsScreen
import com.epicstore.app.ui.theme.EpicStoreTheme
import com.epicstore.app.viewmodel.DownloadsViewModel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class GameDetailsActivityCompose : ComponentActivity() {
    
    companion object {
        private const val TAG = "GameDetailsActivity"
    }
    
    private lateinit var authManager: EpicAuthManager
    private val downloadsViewModel: DownloadsViewModel by viewModels()
    
    private var gameName: String = ""
    private var appName: String = ""
    private var namespace: String = ""
    private var catalogItemId: String = ""
    private var imageUrl: String? = null
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                startDownload()
            } else {
                Toast.makeText(this, "Permissão negada. Não é possível baixar.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startDownload()
        } else {
            Toast.makeText(this, "Permissão negada. Não é possível baixar.", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        authManager = EpicAuthManager(this)
        
        gameName = intent.getStringExtra("GAME_NAME") ?: ""
        appName = intent.getStringExtra("APP_NAME") ?: ""
        namespace = intent.getStringExtra("NAMESPACE") ?: ""
        catalogItemId = intent.getStringExtra("CATALOG_ITEM_ID") ?: ""
        imageUrl = intent.getStringExtra("IMAGE_URL")
        
        setContent {
            EpicStoreTheme {
                val download by downloadsViewModel.getDownload(appName).collectAsState(initial = null)
                var gameSize by remember { mutableStateOf<String?>(null) }
                
                GameDetailsScreen(
                    gameName = gameName,
                    appName = appName,
                    namespace = namespace,
                    catalogItemId = catalogItemId,
                    imageUrl = imageUrl,
                    gameSize = gameSize,
                    download = download,
                    onBackClick = { finish() },
                    onDownloadClick = { checkPermissionsAndDownload() }
                )
            }
        }
        
        fetchGameManifest()
    }
    
    private fun fetchGameManifest() {
        lifecycleScope.launch {
            try {
                val tokenResult = authManager.getLauncherToken()
                if (tokenResult.isFailure) {
                    return@launch
                }
                
                val token = tokenResult.getOrNull() ?: return@launch
                
                val client = OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", "UELauncher/14.0.8-22004686+++Portal+Release-Live Windows/10.0.19041.1.256.64bit")
                            .build()
                        chain.proceed(request)
                    }
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build()
                
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://launcher-public-service-prod06.ol.epicgames.com/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                
                val api = retrofit.create(EpicGamesApi::class.java)
                
                val response = api.getGameManifest(
                    "bearer $token",
                    "Windows",
                    namespace,
                    catalogItemId,
                    appName,
                    "Live"
                )
                
                if (response.isSuccessful && response.body() != null) {
                    val manifestData = response.body()!!
                    val manifestUrl = manifestData.elements?.firstOrNull()?.manifests?.firstOrNull()?.uri
                    
                    if (manifestUrl != null) {
                        val manifestResponse = client.newCall(
                            okhttp3.Request.Builder()
                                .url(manifestUrl)
                                .build()
                        ).execute()
                        
                        if (manifestResponse.isSuccessful) {
                            val manifestBytes = manifestResponse.body?.bytes()
                            if (manifestBytes != null) {
                                val gameSize = manifestBytes.size.toLong()
                                val sizeMB = gameSize / (1024.0 * 1024.0)
                                val sizeGB = sizeMB / 1024.0
                                
                                val gameSizeText = if (sizeGB >= 1.0) {
                                    String.format("%.2f GB", sizeGB)
                                } else {
                                    String.format("%.1f MB", sizeMB)
                                }
                                
                                
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching manifest", e)
            }
        }
    }
    
    private fun checkPermissionsAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                startDownload()
            } else {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    storagePermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    storagePermissionLauncher.launch(intent)
                }
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            
            if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
                startDownload()
            } else {
                legacyPermissionLauncher.launch(permissions)
            }
        }
    }
    
    private fun startDownload() {
        if (namespace.isNullOrBlank() || catalogItemId.isNullOrBlank()) {
            Toast.makeText(this, "Informações do jogo incompletas", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, "Iniciando download de $gameName...", Toast.LENGTH_SHORT).show()
        
        val intent = Intent(this, DownloadService::class.java).apply {
            putExtra(DownloadService.EXTRA_GAME_NAME, gameName)
            putExtra(DownloadService.EXTRA_APP_NAME, appName)
            putExtra(DownloadService.EXTRA_NAMESPACE, namespace)
            putExtra(DownloadService.EXTRA_CATALOG_ITEM_ID, catalogItemId)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        startActivity(Intent(this, DownloadsActivityCompose::class.java))
        finish()
    }
}
