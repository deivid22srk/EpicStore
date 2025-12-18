package com.epicstore.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.epicstore.app.DownloadsActivity
import com.epicstore.app.R
import com.epicstore.app.auth.EpicAuthManager
import com.epicstore.app.database.AppDatabase
import com.epicstore.app.download.ChunkDownloader
import com.epicstore.app.download.DecodedChunk
import com.epicstore.app.download.FileAssembler
import com.epicstore.app.download.ManifestParser
import com.epicstore.app.model.DownloadState
import com.epicstore.app.model.DownloadStatus
import com.epicstore.app.network.EpicGamesApi
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class DownloadService : Service() {
    
    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_CHANNEL_ID = "epic_download_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val EXTRA_GAME_NAME = "game_name"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_NAMESPACE = "namespace"
        const val EXTRA_CATALOG_ITEM_ID = "catalog_item_id"
        const val EXTRA_RESUME = "resume"
        
        const val DOWNLOAD_DIR = "/storage/emulated/0/EpicStoreHG"
        private const val UPDATE_INTERVAL_MS = 1000L
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var authManager: EpicAuthManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var database: AppDatabase
    
    private var isRunning = true
    private var currentDownloadJob: Job? = null
    
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "UELauncher/14.0.8-22004686+++Portal+Release-Live Windows/10.0.19041.1.256.64bit")
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
        database = AppDatabase.getDatabase(applicationContext)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val gameName = intent?.getStringExtra(EXTRA_GAME_NAME) ?: "Unknown"
        val appName = intent?.getStringExtra(EXTRA_APP_NAME) ?: return START_NOT_STICKY
        val namespace = intent?.getStringExtra(EXTRA_NAMESPACE) ?: return START_NOT_STICKY
        val catalogItemId = intent?.getStringExtra(EXTRA_CATALOG_ITEM_ID) ?: return START_NOT_STICKY
        val isResume = intent?.getBooleanExtra(EXTRA_RESUME, false) ?: false
        
        startForeground(NOTIFICATION_ID, createNotification(gameName, 0, "Preparando..."))
        
        currentDownloadJob = serviceScope.launch {
            downloadGame(gameName, appName, namespace, catalogItemId, isResume)
        }
        
        return START_STICKY
    }
    
    private suspend fun downloadGame(
        gameName: String, 
        appName: String, 
        namespace: String, 
        catalogItemId: String,
        isResume: Boolean
    ) {
        try {
            Log.d(TAG, "Starting download for $gameName (Resume: $isResume)")
            
            val existingDownload = database.downloadDao().getDownload(appName)
            
            if (existingDownload?.status == DownloadStatus.PAUSED) {
                isRunning = false
                return
            }
            
            updateNotification(gameName, 0, "Obtendo informações do jogo...")
            
            val tokenResult = authManager.getLauncherToken()
            if (tokenResult.isFailure) {
                Log.e(TAG, "Failed to get launcher token")
                saveDownloadError(appName, gameName, namespace, catalogItemId, "Falha na autenticação")
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
            updateNotification(gameName, 5, "Obtendo manifest...")
            
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
                saveDownloadError(appName, gameName, namespace, catalogItemId, "Falha ao obter manifest")
                stopSelf()
                return
            }
            
            val elements = manifestResponse.body()!!.elements
            if (elements.isNullOrEmpty()) {
                Log.e(TAG, "No manifest elements found")
                saveDownloadError(appName, gameName, namespace, catalogItemId, "Manifest vazio")
                stopSelf()
                return
            }
            
            val manifestInfo = elements[0].manifests?.firstOrNull()
            if (manifestInfo == null) {
                Log.e(TAG, "No manifest info found")
                saveDownloadError(appName, gameName, namespace, catalogItemId, "Manifest não encontrado")
                stopSelf()
                return
            }
            
            var manifestUri = manifestInfo.uri
            val queryParams = manifestInfo.queryParams
            
            if (!queryParams.isNullOrEmpty()) {
                val params = queryParams.joinToString("&") { "${it.name}=${it.value}" }
                manifestUri = "$manifestUri?$params"
            }
            
            val baseUrl = manifestInfo.uri.substringBeforeLast('/')
            
            updateNotification(gameName, 10, "Baixando manifest...")
            
            val manifestBytes = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(manifestUri)
                    .build()
                    
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("Failed to download manifest: ${response.code}")
                }
                response.body?.bytes() ?: throw Exception("Empty manifest")
            }
            
            val manifest = ManifestParser.parse(manifestBytes)
            val totalSize = manifest.getTotalSize()
            val totalChunks = manifest.getTotalChunks()
            val totalFiles = manifest.files.size
            
            Log.d(TAG, "Manifest: ${manifest.meta.appName} v${manifest.meta.buildVersion}")
            Log.d(TAG, "Files: $totalFiles, Chunks: $totalChunks, Size: ${totalSize / 1024 / 1024} MB")
            
            val downloadDir = File(DOWNLOAD_DIR, appName)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val resumeFile = File(downloadDir, ".resume")
            val completedFiles = mutableSetOf<String>()
            
            if (isResume && resumeFile.exists()) {
                try {
                    resumeFile.readLines().forEach { line ->
                        val (hash, filename) = line.split(":", limit = 2)
                        completedFiles.add(filename)
                    }
                    Log.d(TAG, "Resuming download, ${completedFiles.size} files already completed")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading resume file", e)
                }
            }
            
            val downloadState = DownloadState(
                appName = appName,
                gameName = gameName,
                namespace = namespace,
                catalogItemId = catalogItemId,
                totalSize = totalSize,
                downloadedSize = 0L,
                status = DownloadStatus.DOWNLOADING
            )
            database.downloadDao().insertDownload(downloadState)
            
            val chunkDownloader = ChunkDownloader(okHttpClient)
            val fileAssembler = FileAssembler(downloadDir)
            val chunkCache = ConcurrentHashMap<String, DecodedChunk>()
            
            val uniqueChunks = mutableMapOf<String, com.epicstore.app.download.ChunkInfo>()
            manifest.chunks.forEach { chunk ->
                val key = chunk.getGuidStr()
                if (!uniqueChunks.containsKey(key)) {
                    uniqueChunks[key] = chunk
                }
            }
            
            var downloadedChunks = 0
            var downloadedBytes = 0L
            var processedFiles = 0
            
            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L
            val startTime = System.currentTimeMillis()
            
            for (file in manifest.files) {
                if (!isRunning) {
                    Log.d(TAG, "Download paused by user")
                    break
                }
                
                if (completedFiles.contains(file.filename)) {
                    processedFiles++
                    continue
                }
                
                try {
                    val neededChunks = mutableSetOf<String>()
                    file.chunkParts.forEach { part ->
                        neededChunks.add(part.getGuidStr())
                    }
                    
                    for (chunkGuid in neededChunks) {
                        if (!isRunning) break
                        
                        if (!chunkCache.containsKey(chunkGuid)) {
                            val chunkInfo = uniqueChunks[chunkGuid]
                            if (chunkInfo != null) {
                                val chunkUrl = "$baseUrl/${chunkInfo.getPath()}"
                                
                                val chunk = chunkDownloader.downloadAndDecodeChunk(chunkUrl)
                                chunkCache[chunkGuid] = chunk
                                
                                downloadedChunks++
                                downloadedBytes += chunk.originalSize
                                bytesSinceLastUpdate += chunk.originalSize
                                
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
                                    val delta = (currentTime - lastUpdateTime) / 1000.0
                                    val speed = (bytesSinceLastUpdate / delta).toLong()
                                    
                                    val progress = ((downloadedBytes.toDouble() / totalSize) * 100).roundToInt()
                                    val speedMB = speed / (1024.0 * 1024.0)
                                    
                                    updateNotification(
                                        gameName,
                                        progress,
                                        String.format("%.2f MB/s - %d/%d chunks", speedMB, downloadedChunks, totalChunks)
                                    )
                                    
                                    database.downloadDao().updateDownload(
                                        downloadState.copy(
                                            downloadedSize = downloadedBytes,
                                            downloadSpeed = speed,
                                            lastUpdateTime = currentTime
                                        )
                                    )
                                    
                                    lastUpdateTime = currentTime
                                    bytesSinceLastUpdate = 0L
                                }
                            }
                        }
                    }
                    
                    if (!isRunning) break
                    
                    fileAssembler.assembleFile(file, manifest.chunks) { chunkInfo ->
                        chunkCache[chunkInfo.getGuidStr()] ?: throw Exception("Chunk not in cache")
                    }
                    
                    processedFiles++
                    val fileHash = file.hash.joinToString("") { "%02x".format(it) }
                    resumeFile.appendText("$fileHash:${file.filename}\n")
                    
                    chunkCache.entries.removeIf { entry ->
                        !manifest.files.subList(processedFiles, manifest.files.size).any { f ->
                            f.chunkParts.any { cp -> cp.getGuidStr() == entry.key }
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing file ${file.filename}", e)
                }
            }
            
            fileAssembler.cleanup()
            
            if (isRunning && processedFiles == totalFiles) {
                Log.d(TAG, "Download completed!")
                resumeFile.delete()
                
                database.downloadDao().updateDownload(
                    downloadState.copy(
                        downloadedSize = totalSize,
                        status = DownloadStatus.COMPLETED,
                        downloadSpeed = 0L,
                        lastUpdateTime = System.currentTimeMillis()
                    )
                )
                
                updateNotification(gameName, 100, "Download concluído!")
                Thread.sleep(3000)
            } else {
                Log.d(TAG, "Download paused or interrupted")
                database.downloadDao().updateDownload(
                    downloadState.copy(
                        downloadedSize = downloadedBytes,
                        status = DownloadStatus.PAUSED,
                        downloadSpeed = 0L,
                        lastUpdateTime = System.currentTimeMillis()
                    )
                )
            }
            
            stopSelf()
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            saveDownloadError(appName, gameName, namespace, catalogItemId, e.message ?: "Erro desconhecido")
            stopSelf()
        }
    }
    
    private suspend fun saveDownloadError(
        appName: String,
        gameName: String,
        namespace: String,
        catalogItemId: String,
        errorMessage: String
    ) {
        val existingDownload = database.downloadDao().getDownload(appName)
        val downloadState = existingDownload?.copy(
            status = DownloadStatus.ERROR,
            errorMessage = errorMessage,
            downloadSpeed = 0L,
            lastUpdateTime = System.currentTimeMillis()
        ) ?: DownloadState(
            appName = appName,
            gameName = gameName,
            namespace = namespace,
            catalogItemId = catalogItemId,
            status = DownloadStatus.ERROR,
            errorMessage = errorMessage
        )
        
        database.downloadDao().insertDownload(downloadState)
        updateNotification(gameName, 0, "Erro: $errorMessage")
        Thread.sleep(5000)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Downloads de Jogos",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progresso de download dos jogos"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(gameName: String, progress: Int, message: String): Notification {
        val intent = Intent(this, DownloadsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Baixando $gameName")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setOnlyAlertOnce(true)
            .build()
    }
    
    private fun updateNotification(gameName: String, progress: Int, message: String) {
        val notification = createNotification(gameName, progress, message)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        currentDownloadJob?.cancel()
        serviceScope.cancel()
    }
}
