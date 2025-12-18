package com.epicstore.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.epicstore.app.auth.EpicAuthManager
import com.epicstore.app.ui.screens.MainScreen
import com.epicstore.app.ui.theme.EpicStoreTheme
import com.epicstore.app.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivityCompose : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivityCompose"
    }
    
    private lateinit var authManager: EpicAuthManager
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        authManager = EpicAuthManager(this)
        
        setContent {
            EpicStoreTheme {
                val uiState by viewModel.uiState.collectAsState()
                var isLoggedIn by remember { mutableStateOf(authManager.hasDeviceAuth()) }
                val coroutineScope = rememberCoroutineScope()
                
                MainScreen(
                    uiState = uiState,
                    isLoggedIn = isLoggedIn,
                    onLoginClick = {
                        startDeviceAuthFlow { success ->
                            if (success) {
                                isLoggedIn = true
                                viewModel.loadGames(this@MainActivityCompose)
                            }
                        }
                    },
                    onLogoutClick = {
                        authManager.clearAuthData()
                        isLoggedIn = false
                    },
                    onRefreshClick = {
                        coroutineScope.launch {
                            val result = authManager.deviceAuthLogin()
                            if (result.isSuccess) {
                                viewModel.loadGames(this@MainActivityCompose)
                            } else {
                                authManager.clearAuthData()
                                isLoggedIn = false
                            }
                        }
                    },
                    onDownloadsClick = {
                        startActivity(Intent(this, DownloadsActivityCompose::class.java))
                    }
                )
            }
        }
        
        if (authManager.hasDeviceAuth()) {
            lifecycleScope.launch {
                val result = authManager.deviceAuthLogin()
                if (result.isSuccess) {
                    viewModel.loadGames(this@MainActivityCompose)
                }
            }
        }
    }
    
    private fun startDeviceAuthFlow(onComplete: (Boolean) -> Unit) {
        lifecycleScope.launch {
            try {
                val deviceCodeResult = authManager.startDeviceAuthFlow()
                
                if (deviceCodeResult.isFailure) {
                    onComplete(false)
                    return@launch
                }
                
                val deviceCode = deviceCodeResult.getOrNull()!!
                openBrowserForAuth(deviceCode.verificationUriComplete)
                
                val authResult = authManager.pollForDeviceAuthorization(deviceCode.deviceCode)
                
                if (authResult.isFailure) {
                    onComplete(false)
                    return@launch
                }
                
                val switchAuth = authResult.getOrNull()!!
                val androidTokenResult = authManager.exchangeToAndroidToken(switchAuth.accessToken)
                
                if (androidTokenResult.isFailure) {
                    onComplete(false)
                    return@launch
                }
                
                val androidAuth = androidTokenResult.getOrNull()!!
                val deviceAuthResult = authManager.createDeviceAuth(
                    androidAuth.accessToken,
                    androidAuth.accountId ?: ""
                )
                
                if (deviceAuthResult.isFailure) {
                    onComplete(false)
                    return@launch
                }
                
                onComplete(true)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in device auth flow", e)
                onComplete(false)
            }
        }
    }
    
    private fun openBrowserForAuth(url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            
            customTabsIntent.launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        }
    }
}
