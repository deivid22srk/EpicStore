package com.epicstore.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.epicstore.app.databinding.ActivityGameDetailsBinding
import com.epicstore.app.service.DownloadService

class GameDetailsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityGameDetailsBinding
    private var gameName: String = ""
    private var appName: String = ""
    private var namespace: String = ""
    private var catalogItemId: String = ""
    
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
        
        finish()
    }
}
