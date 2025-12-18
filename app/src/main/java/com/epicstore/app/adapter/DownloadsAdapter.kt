package com.epicstore.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.epicstore.app.R
import com.epicstore.app.databinding.ItemDownloadBinding
import com.epicstore.app.model.DownloadState
import com.epicstore.app.model.DownloadStatus
import kotlin.math.roundToInt

class DownloadsAdapter(
    private val onPause: (DownloadState) -> Unit,
    private val onResume: (DownloadState) -> Unit,
    private val onCancel: (DownloadState) -> Unit
) : ListAdapter<DownloadState, DownloadsAdapter.DownloadViewHolder>(DownloadDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = ItemDownloadBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return DownloadViewHolder(binding, onPause, onResume, onCancel)
    }
    
    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class DownloadViewHolder(
        private val binding: ItemDownloadBinding,
        private val onPause: (DownloadState) -> Unit,
        private val onResume: (DownloadState) -> Unit,
        private val onCancel: (DownloadState) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(download: DownloadState) {
            binding.gameName.text = download.gameName
            
            val progress = if (download.totalSize > 0) {
                ((download.downloadedSize.toDouble() / download.totalSize.toDouble()) * 100).roundToInt()
            } else {
                0
            }
            
            binding.progressBar.progress = progress
            binding.progressText.text = "$progress%"
            
            val downloadedMB = download.downloadedSize / (1024.0 * 1024.0)
            val totalMB = download.totalSize / (1024.0 * 1024.0)
            binding.sizeText.text = String.format("%.1f MB / %.1f MB", downloadedMB, totalMB)
            
            val speedMB = download.downloadSpeed / (1024.0 * 1024.0)
            binding.speedText.text = when (download.status) {
                DownloadStatus.DOWNLOADING -> String.format("%.2f MB/s", speedMB)
                DownloadStatus.PAUSED -> "Pausado"
                DownloadStatus.COMPLETED -> "Concluído"
                DownloadStatus.ERROR -> "Erro: ${download.errorMessage}"
                DownloadStatus.CANCELLED -> "Cancelado"
                else -> "Aguardando..."
            }
            
            when (download.status) {
                DownloadStatus.DOWNLOADING -> {
                    binding.actionButton.setIconResource(R.drawable.ic_pause)
                    binding.actionButton.contentDescription = "Pausar"
                    binding.actionButton.setOnClickListener { onPause(download) }
                    binding.cancelButton.visibility = View.VISIBLE
                }
                DownloadStatus.PAUSED, DownloadStatus.ERROR -> {
                    binding.actionButton.setIconResource(R.drawable.ic_play)
                    binding.actionButton.contentDescription = "Retomar"
                    binding.actionButton.setOnClickListener { onResume(download) }
                    binding.cancelButton.visibility = View.VISIBLE
                }
                DownloadStatus.COMPLETED -> {
                    binding.actionButton.visibility = View.GONE
                    binding.cancelButton.visibility = View.GONE
                }
                else -> {
                    binding.actionButton.visibility = View.GONE
                    binding.cancelButton.visibility = View.VISIBLE
                }
            }
            
            binding.cancelButton.setOnClickListener { onCancel(download) }
        }
    }
    
    private class DownloadDiffCallback : DiffUtil.ItemCallback<DownloadState>() {
        override fun areItemsTheSame(oldItem: DownloadState, newItem: DownloadState): Boolean {
            return oldItem.appName == newItem.appName
        }
        
        override fun areContentsTheSame(oldItem: DownloadState, newItem: DownloadState): Boolean {
            return oldItem == newItem
        }
    }
}
