package com.epicstore.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.epicstore.app.MainActivity
import com.epicstore.app.R
import com.epicstore.app.auth.EpicAuthManager
import com.epicstore.app.network.EpicGamesApi
import com.epicstore.app.model.ManifestResponse
import com.epicstore.app.download.ManifestParser
import com.epicstore.app.download.ChunkDownloader
import com.epicstore.app.download.FileAssembler
import com.epicstore.app.download.ChunkInfo
import com.epicstore.app.download.DecodedChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {
    
    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_CHANNEL_ID = "epic_download_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val EXTRA_GAME_NAME = "game_name"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_NAMESPACE = "namespace"
        const val EXTRA_CATALOG_ITEM_ID = "catalog_item_id"
        
        const val DOWNLOAD_DIR = "/storage/emulated/0/EpicStoreHG"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var authManager: EpicAuthManager
    private lateinit var notificationManager: NotificationManager
    
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "EpicGamesLauncher/11.0.1-14907503+++Portal+Release-Live Windows/10.0.19041.1.256.64bit")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    
    override fun onCreate() {
        super.onCreate()
        authManager = EpicAuthManager(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val gameName = intent?.getStringExtra(EXTRA_GAME_NAME) ?: "Unknown"
        val appName = intent?.getStringExtra(EXTRA_APP_NAME) ?: return START_NOT_STICKY
        val namespace = intent?.getStringExtra(EXTRA_NAMESPACE) ?: return START_NOT_STICKY
        val catalogItemId = intent?.getStringExtra(EXTRA_CATALOG_ITEM_ID) ?: return START_NOT_STICKY
        
        startForeground(NOTIFICATION_ID, createNotification(gameName, 0))
        
        serviceScope.launch {
            downloadGame(gameName, appName, namespace, catalogItemId)
        }
        
        return START_NOT_STICKY
    }
    
    private suspend fun downloadGame(gameName: String, appName: String, namespace: String, catalogItemId: String) {
        try {
            Log.d(TAG, "Starting download for $gameName")
            updateNotification(gameName, 0, "Obtendo informações do jogo...")
            
            val tokenResult = authManager.getLauncherToken()
            if (tokenResult.isFailure) {
                Log.e(TAG, "Failed to get launcher token")
                updateNotification(gameName, 0, "Erro: Falha na autenticação")
                stopSelf()
                return
            }
            
            val token = tokenResult.getOrNull()!!
            
            val launcherRetrofit = Retrofit.Builder()
                .baseUrl("https://launcher-public-service-prod06.ol.epicgames.com/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            val manifestApi = launcherRetrofit.create(EpicGamesApi::class.java)
            
            Log.d(TAG, "Fetching manifest info...")
            updateNotification(gameName, 5, "Obtendo informações do manifest...")
            
            val manifestResponse = manifestApi.getGameManifest(
                "bearer $token",
                "Windows",
                namespace,
                catalogItemId,
                appName,
                "Live"
            )
            
            if (!manifestResponse.isSuccessful || manifestResponse.body() == null) {
                Log.e(TAG, "Failed to get manifest: ${manifestResponse.code()}")
                updateNotification(gameName, 0, "Erro: Falha ao obter manifest")
                stopSelf()
                return
            }
            
            val elements = manifestResponse.body()!!.elements
            if (elements.isNullOrEmpty()) {
                Log.e(TAG, "No manifest elements found")
                updateNotification(gameName, 0, "Erro: Manifest vazio")
                stopSelf()
                return
            }
            
            val manifestUri = elements[0].manifests?.firstOrNull()?.uri
            if (manifestUri == null) {
                Log.e(TAG, "No manifest URI found")
                updateNotification(gameName, 0, "Erro: URI do manifest não encontrada")
                stopSelf()
                return
            }
            
            Log.d(TAG, "Manifest URI: $manifestUri")
            val baseUrl = manifestUri.substringBeforeLast('/')
            
            updateNotification(gameName, 10, "Baixando arquivo manifest...")
            
            val manifestBytes = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(manifestUri)
                    .header("User-Agent", "EpicGamesLauncher/11.0.1-14907503+++Portal+Release-Live Windows/10.0.19041.1.256.64bit")
                    .build()
                    
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("Failed to download manifest file: ${response.code}")
                }
                response.body?.bytes() ?: throw Exception("Empty manifest response")
            }
            
            Log.d(TAG, "Manifest downloaded: ${manifestBytes.size} bytes")
            updateNotification(gameName, 15, "Processando manifest...")
            
            val manifest = ManifestParser.parse(manifestBytes)
            val totalSize = manifest.getTotalSize()
            val totalChunks = manifest.getTotalChunks()
            val totalFiles = manifest.files.size
            
            Log.d(TAG, "Manifest parsed successfully")
            Log.d(TAG, "  App: ${manifest.meta.appName}, Version: ${manifest.meta.buildVersion}")
            Log.d(TAG, "  Total files: $totalFiles")
            Log.d(TAG, "  Total chunks: $totalChunks")
            Log.d(TAG, "  Total size: ${totalSize / 1024 / 1024} MB")
            
            val downloadDir = File(DOWNLOAD_DIR, appName)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val chunkDownloader = ChunkDownloader(okHttpClient)
            val fileAssembler = FileAssembler(downloadDir)
            
            val chunkCache = ConcurrentHashMap<String, DecodedChunk>()
            
            updateNotification(gameName, 20, "Iniciando download de $totalChunks chunks...")
            
            var downloadedChunks = 0
            var downloadedBytes = 0L
            val startTime = System.currentTimeMillis()
            
            val uniqueChunks = mutableMapOf<String, ChunkInfo>()
            for (chunk in manifest.chunks) {
                val key = chunk.getGuidStr()
                if (!uniqueChunks.containsKey(key)) {
                    uniqueChunks[key] = chunk
                }
            }
            
            Log.d(TAG, "Downloading ${uniqueChunks.size} unique chunks...")
            
            var processedFiles = 0
            for (file in manifest.files) {
                try {
                    Log.d(TAG, "Processing file: ${file.filename} (${file.fileSize} bytes)")
                    
                    val neededChunks = mutableSetOf<String>()
                    for (part in file.chunkParts) {
                        neededChunks.add(part.getGuidStr())
                    }
                    
                    for (chunkGuid in neededChunks) {
                        if (!chunkCache.containsKey(chunkGuid)) {
                            val chunkInfo = uniqueChunks[chunkGuid]
                            if (chunkInfo != null) {
                                val chunkUrl = "$baseUrl/${chunkInfo.getPath()}"
                                
                                try {
                                    val chunk = chunkDownloader.downloadAndDecodeChunk(chunkUrl)
                                    chunkCache[chunkGuid] = chunk
                                    
                                    downloadedChunks++
                                    downloadedBytes += chunk.originalSize
                                    
                                    val progress = 20 + ((downloadedChunks.toFloat() / totalChunks) * 60).toInt()
                                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000
                                    val speed = if (elapsedSec > 0) downloadedBytes / elapsedSec else 0
                                    val speedMB = speed / 1024 / 1024
                                    
                                    updateNotification(
                                        gameName,
                                        progress,
                                        "Baixando chunks: $downloadedChunks/$totalChunks (${speedMB}MB/s)"
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to download chunk $chunkGuid: ${e.message}", e)
                                }
                            }
                        }
                    }
                    
                    fileAssembler.assembleFile(file, manifest.chunks) { chunkInfo ->
                        chunkCache[chunkInfo.getGuidStr()] ?: throw Exception("Chunk not in cache: ${chunkInfo.getGuidStr()}")
                    }
                    
                    processedFiles++
                    val fileProgress = 80 + ((processedFiles.toFloat() / totalFiles) * 15).toInt()
                    updateNotification(
                        gameName,
                        fileProgress,
                        "Montando arquivos: $processedFiles/$totalFiles"
                    )
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing file ${file.filename}: ${e.message}", e)
                }
            }
            
            fileAssembler.cleanup()
            
            updateNotification(gameName, 95, "Finalizando download...")
            Log.d(TAG, "Download completed: $downloadedChunks chunks, $processedFiles files")
            
            updateNotification(gameName, 100, "Download concluído!")
            Thread.sleep(3000)
            stopSelf()
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            updateNotification(gameName, 0, "Erro: ${e.message}")
            Thread.sleep(5000)
            stopSelf()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Downloads de Jogos",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificações de progresso de download"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(gameName: String, progress: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Baixando $gameName")
            .setContentText("Preparando download...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
    }
    
    private fun updateNotification(gameName: String, progress: Int, message: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Baixando $gameName")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(progress < 100)
            .setProgress(100, progress, false)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
