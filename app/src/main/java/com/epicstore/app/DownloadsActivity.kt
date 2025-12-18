package com.epicstore.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.epicstore.app.adapter.DownloadsAdapter
import com.epicstore.app.databinding.ActivityDownloadsBinding
import com.epicstore.app.viewmodel.DownloadsViewModel
import kotlinx.coroutines.launch

class DownloadsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDownloadsBinding
    private val viewModel: DownloadsViewModel by viewModels()
    private lateinit var adapter: DownloadsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Downloads"
        
        setupRecyclerView()
        observeDownloads()
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = DownloadsAdapter(
            onPause = { download ->
                lifecycleScope.launch {
                    viewModel.pauseDownload(download.appName)
                }
            },
            onResume = { download ->
                val intent = Intent(this, com.epicstore.app.service.DownloadService::class.java).apply {
                    putExtra(com.epicstore.app.service.DownloadService.EXTRA_GAME_NAME, download.gameName)
                    putExtra(com.epicstore.app.service.DownloadService.EXTRA_APP_NAME, download.appName)
                    putExtra(com.epicstore.app.service.DownloadService.EXTRA_NAMESPACE, download.namespace)
                    putExtra(com.epicstore.app.service.DownloadService.EXTRA_CATALOG_ITEM_ID, download.catalogItemId)
                    putExtra(com.epicstore.app.service.DownloadService.EXTRA_RESUME, true)
                }
                startService(intent)
            },
            onCancel = { download ->
                lifecycleScope.launch {
                    viewModel.cancelDownload(download.appName)
                }
            }
        )
        
        binding.downloadsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DownloadsActivity)
            adapter = this@DownloadsActivity.adapter
        }
    }
    
    private fun observeDownloads() {
        lifecycleScope.launch {
            viewModel.downloads.collect { downloads ->
                if (downloads.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.downloadsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyView.visibility = View.GONE
                    binding.downloadsRecyclerView.visibility = View.VISIBLE
                    adapter.submitList(downloads)
                }
            }
        }
    }
}
