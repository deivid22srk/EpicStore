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
            
            if (authManager.isLoggedIn()) {
                showLibrary()
                viewModel.loadGames(this)
            } else {
                showLoginScreen()
            }
            
            handleAuthCallback(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            finish()
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthCallback(intent)
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
                launchBrowserForAuth()
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
        } catch (e: Exception) {
            Log.e(TAG, "Error showing login screen", e)
        }
    }
    
    private fun showLibrary() {
        try {
            binding.loginLayout.visibility = View.GONE
            binding.contentLayout.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "Error showing library", e)
        }
    }
    
    private fun launchBrowserForAuth() {
        try {
            val authUrl = authManager.getAuthorizationUrl()
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            
            try {
                customTabsIntent.launchUrl(this, Uri.parse(authUrl))
            } catch (e: Exception) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                startActivity(browserIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching browser", e)
            Snackbar.make(binding.root, "Error opening browser: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }
    
    private fun handleAuthCallback(intent: Intent?) {
        try {
            val uri = intent?.data
            if (uri != null && uri.scheme == "epicstore" && uri.host == "callback") {
                val code = uri.getQueryParameter("code")
                if (code != null) {
                    lifecycleScope.launch {
                        try {
                            binding.progressBar.visibility = View.VISIBLE
                            val result = authManager.exchangeCodeForToken(code)
                            binding.progressBar.visibility = View.GONE
                            
                            if (result.isSuccess) {
                                showLibrary()
                                viewModel.loadGames(this@MainActivity)
                                Snackbar.make(binding.root, R.string.login_success, Snackbar.LENGTH_SHORT).show()
                            } else {
                                Snackbar.make(
                                    binding.root,
                                    getString(R.string.login_failed, result.exceptionOrNull()?.message),
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling auth callback", e)
                            binding.progressBar.visibility = View.GONE
                            Snackbar.make(
                                binding.root,
                                "Authentication error: ${e.message}",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    val error = uri.getQueryParameter("error")
                    Snackbar.make(
                        binding.root,
                        getString(R.string.auth_error, error ?: "Unknown"),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleAuthCallback", e)
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        try {
            if (authManager.isLoggedIn()) {
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
                    invalidateOptionsMenu()
                    Snackbar.make(binding.root, R.string.logged_out, Snackbar.LENGTH_SHORT).show()
                    true
                }
                R.id.action_refresh -> {
                    viewModel.loadGames(this)
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
