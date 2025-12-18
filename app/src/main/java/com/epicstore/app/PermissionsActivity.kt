package com.epicstore.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.epicstore.app.databinding.ActivityPermissionsBinding

class PermissionsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPermissionsBinding
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissions()
    }
    
    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        checkPermissions()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val permissionsGranted = prefs.getBoolean("permissions_granted", false)
        
        if (permissionsGranted && hasRequiredPermissions()) {
            startMainActivity()
            return
        }
        
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
        checkPermissions()
    }
    
    private fun setupViews() {
        binding.continueButton.setOnClickListener {
            requestPermissions()
        }
        
        binding.skipButton.setOnClickListener {
            startMainActivity()
        }
    }
    
    private fun checkPermissions() {
        val hasStorage = hasRequiredPermissions()
        
        binding.storageIcon.setImageResource(
            if (hasStorage) R.drawable.ic_check_circle else R.drawable.ic_error
        )
        
        binding.storageStatus.text = if (hasStorage) {
            "Concedida"
        } else {
            "Necessária"
        }
        
        if (hasStorage) {
            binding.continueButton.text = "Continuar"
            binding.continueButton.setOnClickListener {
                savePermissionsGranted()
                startMainActivity()
            }
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val permissions = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            permissions.all { 
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED 
            }
        }
    }
    
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                storagePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storagePermissionLauncher.launch(intent)
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            legacyPermissionLauncher.launch(permissions)
        }
    }
    
    private fun savePermissionsGranted() {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("permissions_granted", true)
            .apply()
    }
    
    private fun startMainActivity() {
        startActivity(Intent(this, MainActivityCompose::class.java))
        finish()
    }
}
