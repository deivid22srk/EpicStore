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
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.epicstore.app.auth.EpicAuthManager
import com.epicstore.app.databinding.ActivityGameDetailsBinding
import com.epicstore.app.model.DownloadStatus
import com.epicstore.app.network.EpicGamesApi
import com.epicstore.app.service.DownloadService
import com.epicstore.app.viewmodel.DownloadsViewModel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class GameDetailsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "GameDetailsActivity"
    }
    
    private lateinit var binding: ActivityGameDetailsBinding
    private lateinit var authManager: EpicAuthManager
    private val downloadsViewModel: DownloadsViewModel by viewModels()
    
    private var gameName: String = ""
    private var appName: String = ""
    private var namespace: String = ""
    private var catalogItemId: String = ""
    private var gameSize: Long = 0L
    
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
        binding = ActivityGameDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        authManager = EpicAuthManager(this)
        
        gameName = intent.getStringExtra("GAME_NAME") ?: ""
        appName = intent.getStringExtra("APP_NAME") ?: ""
        namespace = intent.getStringExtra("NAMESPACE") ?: ""
        catalogItemId = intent.getStringExtra("CATALOG_ITEM_ID") ?: ""
        val imageUrl = intent.getStringExtra("IMAGE_URL")
        
        binding.collapsingToolbar.title = gameName
        binding.gameTitle.text = gameName
        binding.gameAppName.text = "App: $appName"
        binding.gameNamespace.text = "Namespace: $namespace"
        
        if (imageUrl != null) {
            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.ic_game_placeholder)
                .error(R.drawable.ic_game_placeholder)
                .into(binding.gameImage)
        } else {
            binding.gameImage.setImageResource(R.drawable.ic_game_placeholder)
        }
        
        binding.downloadButton.setOnClickListener {
            checkPermissionsAndDownload()
        }
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        fetchGameManifest()
        observeDownloadProgress()
    }
    
    private fun fetchGameManifest() {
        lifecycleScope.launch {
            try {
                binding.loadingIndicator.visibility = View.VISIBLE
                binding.sizeValue.text = "Carregando..."
                
                val tokenResult = authManager.getLauncherToken()
                if (tokenResult.isFailure) {
                    binding.sizeValue.text = "Não disponível"
                    binding.loadingIndicator.visibility = View.GONE
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
                                gameSize = manifestBytes.size.toLong()
                                val sizeMB = gameSize / (1024.0 * 1024.0)
                                val sizeGB = sizeMB / 1024.0
                                
                                binding.sizeValue.text = if (sizeGB >= 1.0) {
                                    String.format("%.2f GB", sizeGB)
                                } else {
                                    String.format("%.1f MB", sizeMB)
                                }
                                
                                Log.d(TAG, "Game size: ${binding.sizeValue.text}")
                            }
                        }
                    } else {
                        binding.sizeValue.text = "Estimado: ~50 GB"
                    }
                } else {
                    binding.sizeValue.text = "Não disponível"
                    Log.e(TAG, "Failed to get manifest: ${response.code()}")
                }
                
                binding.loadingIndicator.visibility = View.GONE
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching manifest", e)
                binding.sizeValue.text = "Não disponível"
                binding.loadingIndicator.visibility = View.GONE
            }
        }
    }
    
    private fun observeDownloadProgress() {
        lifecycleScope.launch {
            downloadsViewModel.getDownload(appName).collect { download ->
                if (download != null) {
                    updateDownloadUI(download)
                } else {
                    binding.downloadProgressCard.visibility = View.GONE
                    binding.downloadButton.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun updateDownloadUI(download: com.epicstore.app.model.DownloadState) {
        binding.downloadProgressCard.visibility = View.VISIBLE
        binding.downloadButton.visibility = View.GONE
        
        val progress = if (download.totalSize > 0) {
            ((download.downloadedSize.toDouble() / download.totalSize.toDouble()) * 100).roundToInt()
        } else {
            0
        }
        
        binding.downloadProgressBar.progress = progress
        binding.downloadProgressText.text = "$progress%"
        
        val downloadedMB = download.downloadedSize / (1024.0 * 1024.0)
        val totalMB = download.totalSize / (1024.0 * 1024.0)
        binding.downloadSizeText.text = String.format("%.1f MB / %.1f MB", downloadedMB, totalMB)
        
        val speedMB = download.downloadSpeed / (1024.0 * 1024.0)
        binding.downloadSpeedText.text = when (download.status) {
            DownloadStatus.DOWNLOADING -> String.format("%.2f MB/s", speedMB)
            DownloadStatus.PAUSED -> "Pausado - Toque para continuar"
            DownloadStatus.ERROR -> "Erro - Toque para tentar novamente"
            else -> ""
        }
        
        binding.downloadProgressCard.setOnClickListener {
            if (download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.ERROR) {
                val intent = Intent(this, DownloadService::class.java).apply {
                    putExtra(DownloadService.EXTRA_GAME_NAME, gameName)
                    putExtra(DownloadService.EXTRA_APP_NAME, appName)
                    putExtra(DownloadService.EXTRA_NAMESPACE, namespace)
                    putExtra(DownloadService.EXTRA_CATALOG_ITEM_ID, catalogItemId)
                    putExtra(DownloadService.EXTRA_RESUME, true)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }
    }
    
    private fun checkPermissionsAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                startDownload()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Permissão Necessária")
                    .setMessage("O app precisa de permissão para gerenciar todos os arquivos para salvar os jogos em /storage/emulated/0/EpicStoreHG/")
                    .setPositiveButton("Conceder") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            storagePermissionLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            storagePermissionLauncher.launch(intent)
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            
            if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
                startDownload()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Permissão Necessária")
                    .setMessage("O app precisa de permissão para acessar o armazenamento.")
                    .setPositiveButton("Conceder") { _, _ ->
                        legacyPermissionLauncher.launch(permissions)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
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
        
        startActivity(Intent(this, DownloadsActivity::class.java))
        finish()
    }
}
