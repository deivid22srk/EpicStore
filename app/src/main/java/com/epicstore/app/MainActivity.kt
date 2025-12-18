package com.epicstore.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.epicstore.app.adapter.GamesAdapter
import com.epicstore.app.auth.EpicAuthManager
import com.epicstore.app.databinding.ActivityMainBinding
import com.epicstore.app.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var authManager: EpicAuthManager
    private lateinit var gamesAdapter: GamesAdapter
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setSupportActionBar(binding.toolbar)
            
            authManager = EpicAuthManager(this)
            setupRecyclerView()
            setupObservers()
            setupListeners()
            
            if (authManager.hasDeviceAuth()) {
                lifecycleScope.launch {
                    val result = authManager.deviceAuthLogin()
                    if (result.isSuccess) {
                        showLibrary()
                        viewModel.loadGames(this@MainActivity)
                    } else {
                        showLoginScreen()
                    }
                }
            } else {
                showLoginScreen()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        try {
            gamesAdapter = GamesAdapter()
            binding.gamesRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = gamesAdapter
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up RecyclerView", e)
        }
    }
    
    private fun setupObservers() {
        try {
            lifecycleScope.launch {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.swipeRefresh.isRefreshing = state.isLoading
                    
                    state.games?.let { games ->
                        if (games.isEmpty()) {
                            binding.emptyView.visibility = View.VISIBLE
                            binding.gamesRecyclerView.visibility = View.GONE
                            binding.emptyMessage.text = getString(R.string.no_games_found)
                        } else {
                            binding.emptyView.visibility = View.GONE
                            binding.gamesRecyclerView.visibility = View.VISIBLE
                            gamesAdapter.submitList(games)
                        }
                    }
                    
                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up observers", e)
        }
    }
    
    private fun setupListeners() {
        try {
            binding.loginButton.setOnClickListener {
                startDeviceAuthFlow()
            }
            
            binding.swipeRefresh.setOnRefreshListener {
                viewModel.loadGames(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up listeners", e)
        }
    }
    
    private fun showLoginScreen() {
        try {
            binding.loginLayout.visibility = View.VISIBLE
            binding.contentLayout.visibility = View.GONE
            invalidateOptionsMenu()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing login screen", e)
        }
    }
    
    private fun showLibrary() {
        try {
            binding.loginLayout.visibility = View.GONE
            binding.contentLayout.visibility = View.VISIBLE
            invalidateOptionsMenu()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing library", e)
        }
    }
    
    private fun startDeviceAuthFlow() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.loginButton.isEnabled = false
                
                val deviceCodeResult = authManager.startDeviceAuthFlow()
                
                if (deviceCodeResult.isFailure) {
                    binding.progressBar.visibility = View.GONE
                    binding.loginButton.isEnabled = true
                    Snackbar.make(
                        binding.root,
                        "Erro ao iniciar login: ${deviceCodeResult.exceptionOrNull()?.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@launch
                }
                
                val deviceCode = deviceCodeResult.getOrNull()!!
                
                openBrowserForAuth(deviceCode.verificationUriComplete)
                
                Snackbar.make(
                    binding.root,
                    "Aguardando login no navegador...",
                    Snackbar.LENGTH_LONG
                ).show()
                
                val authResult = authManager.pollForDeviceAuthorization(deviceCode.deviceCode)
                
                if (authResult.isFailure) {
                    binding.progressBar.visibility = View.GONE
                    binding.loginButton.isEnabled = true
                    Snackbar.make(
                        binding.root,
                        "Login cancelado ou expirou",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@launch
                }
                
                val switchAuth = authResult.getOrNull()!!
                
                val androidTokenResult = authManager.exchangeToAndroidToken(switchAuth.accessToken)
                
                if (androidTokenResult.isFailure) {
                    binding.progressBar.visibility = View.GONE
                    binding.loginButton.isEnabled = true
                    Snackbar.make(
                        binding.root,
                        "Erro ao trocar token",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@launch
                }
                
                val androidAuth = androidTokenResult.getOrNull()!!
                
                val deviceAuthResult = authManager.createDeviceAuth(
                    androidAuth.accessToken,
                    androidAuth.accountId ?: ""
                )
                
                if (deviceAuthResult.isFailure) {
                    binding.progressBar.visibility = View.GONE
                    binding.loginButton.isEnabled = true
                    Snackbar.make(
                        binding.root,
                        "Erro ao criar device auth",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@launch
                }
                
                binding.progressBar.visibility = View.GONE
                binding.loginButton.isEnabled = true
                
                showLibrary()
                viewModel.loadGames(this@MainActivity)
                
                Snackbar.make(
                    binding.root,
                    "Login realizado com sucesso!",
                    Snackbar.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in device auth flow", e)
                binding.progressBar.visibility = View.GONE
                binding.loginButton.isEnabled = true
                Snackbar.make(
                    binding.root,
                    "Erro no login: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).show()
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
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        try {
            if (authManager.hasDeviceAuth()) {
                menuInflater.inflate(R.menu.menu_main, menu)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating options menu", e)
        }
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return try {
            when (item.itemId) {
                R.id.action_logout -> {
                    authManager.clearAuthData()
                    gamesAdapter.submitList(emptyList())
                    showLoginScreen()
                    Snackbar.make(binding.root, R.string.logged_out, Snackbar.LENGTH_SHORT).show()
                    true
                }
                R.id.action_refresh -> {
                    lifecycleScope.launch {
                        val result = authManager.deviceAuthLogin()
                        if (result.isSuccess) {
                            viewModel.loadGames(this@MainActivity)
                        } else {
                            Snackbar.make(
                                binding.root,
                                "Erro ao atualizar. Faça login novamente.",
                                Snackbar.LENGTH_LONG
                            ).show()
                            authManager.clearAuthData()
                            showLoginScreen()
                        }
                    }
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling menu item", e)
            false
        }
    }
}
