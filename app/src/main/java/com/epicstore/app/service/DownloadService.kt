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
import kotlinx.coroutines.delay
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
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
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
            Log.d(TAG, "🚀 Starting download for $gameName (Resume: $isResume)")
            
            val existingDownload = database.downloadDao().getDownload(appName)
            
            if (existingDownload?.status == DownloadStatus.PAUSED) {
                isRunning = false
                return
            }
            
            updateNotification(gameName, 0, "Obtendo informações do jogo...")
            
            val token = retryWithExponentialBackoff(
                maxAttempts = 3,
                initialDelayMs = 1000,
                maxDelayMs = 10000,
                operation = "obter token de autenticação"
            ) {
                val tokenResult = authManager.getLauncherToken()
                if (tokenResult.isFailure) {
                    throw Exception("Failed to get launcher token: ${tokenResult.exceptionOrNull()?.message}")
                }
                tokenResult.getOrNull()!!
            }
            
            val launcherRetrofit = Retrofit.Builder()
                .baseUrl("https://launcher-public-service-prod06.ol.epicgames.com/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            val manifestApi = launcherRetrofit.create(EpicGamesApi::class.java)
            
            Log.d(TAG, "📥 Fetching manifest info...")
            updateNotification(gameName, 5, "Obtendo manifest...")
            
            val manifestResponse = retryWithExponentialBackoff(
                maxAttempts = 5,
                initialDelayMs = 2000,
                maxDelayMs = 30000,
                operation = "buscar informações do manifest"
            ) {
                withContext(Dispatchers.IO) {
                    val response = manifestApi.getGameManifest(
                        "bearer $token",
                        "Windows",
                        namespace,
                        catalogItemId,
                        appName,
                        "Live"
                    )
                    
                    if (!response.isSuccessful || response.body() == null) {
                        throw Exception("Failed to get manifest: ${response.code()} - ${response.message()}")
                    }
                    
                    response
                }
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
            
            Log.d(TAG, "📥 Downloading manifest from: $manifestUri")
            updateNotification(gameName, 10, "Baixando manifest...")
            
            val manifestBytes = retryWithExponentialBackoff(
                maxAttempts = 5,
                initialDelayMs = 2000,
                maxDelayMs = 30000,
                operation = "baixar arquivo manifest"
            ) {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(manifestUri)
                        .build()
                        
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        throw Exception("Failed to download manifest: ${response.code} - ${response.message}")
                    }
                    response.body?.bytes() ?: throw Exception("Empty manifest")
                }
            }
            
            Log.d(TAG, "✓ Manifest downloaded (${manifestBytes.size} bytes)")
            updateNotification(gameName, 15, "Analisando manifest...")
            
            Log.d(TAG, "→ Parsing manifest...")
            val manifest = ManifestParser.parse(manifestBytes)
            val totalSize = manifest.getTotalSize()
            val totalChunks = manifest.getTotalChunks()
            val totalFiles = manifest.files.size
            
            Log.d(TAG, "✓ Manifest: ${manifest.meta.appName} v${manifest.meta.buildVersion}")
            Log.d(TAG, "  Files: $totalFiles, Chunks: $totalChunks, Size: ${totalSize / 1024 / 1024} MB")
            
            val downloadDir = File(DOWNLOAD_DIR, appName)
            if (!downloadDir.exists()) {
                Log.d(TAG, "→ Creating download directory: ${downloadDir.absolutePath}")
                downloadDir.mkdirs()
            }
            
            val chunkCacheDir = File(downloadDir, ".chunks")
            if (!chunkCacheDir.exists()) {
                Log.d(TAG, "→ Creating chunk cache directory")
                chunkCacheDir.mkdirs()
            }
            
            updateNotification(gameName, 20, "Preparando download...")
            
            Log.d(TAG, "→ Identifying unique chunks...")
            val uniqueChunks = mutableMapOf<String, com.epicstore.app.download.ChunkInfo>()
            manifest.chunks.forEach { chunk ->
                val key = chunk.getGuidStr()
                if (!uniqueChunks.containsKey(key)) {
                    uniqueChunks[key] = chunk
                }
            }
            
            Log.d(TAG, "✓ Total unique chunks: ${uniqueChunks.size}")
            
            val downloadedChunksFile = File(downloadDir, ".downloaded_chunks")
            val downloadedChunks = mutableSetOf<String>()
            
            if (isResume && downloadedChunksFile.exists()) {
                try {
                    downloadedChunksFile.readLines().forEach { line ->
                        downloadedChunks.add(line.trim())
                    }
                    Log.d(TAG, "→ Resuming download, ${downloadedChunks.size} chunks already downloaded")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading downloaded chunks file", e)
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
            
            updateNotification(gameName, 25, "Iniciando download de chunks...")
            
            Log.d(TAG, "")
            Log.d(TAG, "========== PHASE 1: DOWNLOADING CHUNKS ==========")
            Log.d(TAG, "")
            
            val chunkDownloader = ChunkDownloader(okHttpClient)
            var downloadedChunkCount = downloadedChunks.size
            var downloadedBytes = 0L
            
            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L
            
            for ((chunkGuid, chunkInfo) in uniqueChunks) {
                if (!isRunning) {
                    Log.d(TAG, "⏸ Download paused by user")
                    break
                }
                
                val chunkFile = File(chunkCacheDir, chunkGuid)
                
                if (chunkFile.exists() && downloadedChunks.contains(chunkGuid)) {
                    downloadedChunkCount++
                    continue
                }
                
                try {
                    val chunkUrl = "$baseUrl/${chunkInfo.getPath()}"
                    
                    Log.d(TAG, "⬇ Downloading chunk $downloadedChunkCount/${uniqueChunks.size}: $chunkGuid")
                    
                    val chunk = retryWithExponentialBackoff(
                        maxAttempts = 3,
                        initialDelayMs = 500,
                        maxDelayMs = 5000,
                        operation = "baixar chunk $chunkGuid"
                    ) {
                        withContext(Dispatchers.IO) {
                            chunkDownloader.downloadAndDecodeChunk(chunkUrl)
                        }
                    }
                    
                    withContext(Dispatchers.IO) {
                        chunkFile.outputStream().use { output ->
                            output.write(chunk.data)
                        }
                    }
                    
                    downloadedChunksFile.appendText("$chunkGuid\n")
                    downloadedChunks.add(chunkGuid)
                    
                    downloadedChunkCount++
                    downloadedBytes += chunk.originalSize
                    bytesSinceLastUpdate += chunk.originalSize
                    
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
                        val delta = (currentTime - lastUpdateTime) / 1000.0
                        val speed = (bytesSinceLastUpdate / delta).toLong()
                        
                        val progress = 25 + ((downloadedChunkCount.toDouble() / uniqueChunks.size) * 50).roundToInt()
                        val speedMB = speed / (1024.0 * 1024.0)
                        
                        updateNotification(
                            gameName,
                            progress,
                            String.format("%.2f MB/s - Baixando chunks %d/%d", speedMB, downloadedChunkCount, uniqueChunks.size)
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
                    
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error downloading chunk $chunkGuid", e)
                    throw e
                }
            }
            
            if (!isRunning) {
                Log.d(TAG, "Download paused during chunk download phase")
                database.downloadDao().updateDownload(
                    downloadState.copy(
                        downloadedSize = downloadedBytes,
                        status = DownloadStatus.PAUSED,
                        downloadSpeed = 0L,
                        lastUpdateTime = System.currentTimeMillis()
                    )
                )
                stopSelf()
                return
            }
            
            Log.d(TAG, "")
            Log.d(TAG, "✓ All chunks downloaded successfully!")
            Log.d(TAG, "")
            Log.d(TAG, "========== PHASE 2: ASSEMBLING FILES ==========")
            Log.d(TAG, "")
            
            updateNotification(gameName, 75, "Montando arquivos...")
            
            val assembledFilesFile = File(downloadDir, ".assembled_files")
            val assembledFiles = mutableSetOf<String>()
            
            if (isResume && assembledFilesFile.exists()) {
                try {
                    assembledFilesFile.readLines().forEach { line ->
                        assembledFiles.add(line.trim())
                    }
                    Log.d(TAG, "→ Resuming assembly, ${assembledFiles.size} files already assembled")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading assembled files file", e)
                }
            }
            
            var assembledFileCount = assembledFiles.size
            
            for (file in manifest.files) {
                if (!isRunning) {
                    Log.d(TAG, "⏸ Download paused by user")
                    break
                }
                
                if (assembledFiles.contains(file.filename)) {
                    assembledFileCount++
                    continue
                }
                
                try {
                    Log.d(TAG, "🔧 Assembling file $assembledFileCount/${totalFiles}: ${file.filename}")
                    
                    val outputFile = File(downloadDir, file.filename)
                    outputFile.parentFile?.mkdirs()
                    
                    withContext(Dispatchers.IO) {
                        outputFile.outputStream().buffered().use { output ->
                            for (part in file.chunkParts) {
                                val chunkGuid = part.getGuidStr()
                                val chunkFile = File(chunkCacheDir, chunkGuid)
                                
                                if (!chunkFile.exists()) {
                                    throw Exception("Chunk file not found: $chunkGuid")
                                }
                                
                                val chunkData = chunkFile.readBytes()
                                
                                if (part.offset + part.size > chunkData.size) {
                                    throw Exception("Invalid chunk part: offset=${part.offset}, size=${part.size}, chunkSize=${chunkData.size}")
                                }
                                
                                output.write(chunkData, part.offset, part.size)
                            }
                        }
                    }
                    
                    if (file.isExecutable) {
                        outputFile.setExecutable(true)
                    }
                    
                    assembledFilesFile.appendText("${file.filename}\n")
                    assembledFiles.add(file.filename)
                    
                    assembledFileCount++
                    
                    val progress = 75 + ((assembledFileCount.toDouble() / totalFiles) * 25).roundToInt()
                    updateNotification(
                        gameName,
                        progress,
                        String.format("Montando arquivos %d/%d", assembledFileCount, totalFiles)
                    )
                    
                    Log.d(TAG, "✓ File assembled: ${file.filename}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error assembling file ${file.filename}", e)
                    throw e
                }
            }
            
            if (!isRunning) {
                Log.d(TAG, "Download paused during assembly phase")
                database.downloadDao().updateDownload(
                    downloadState.copy(
                        downloadedSize = downloadedBytes,
                        status = DownloadStatus.PAUSED,
                        downloadSpeed = 0L,
                        lastUpdateTime = System.currentTimeMillis()
                    )
                )
                stopSelf()
                return
            }
            
            Log.d(TAG, "")
            Log.d(TAG, "✓ All files assembled successfully!")
            Log.d(TAG, "→ Cleaning up temporary files...")
            
            chunkCacheDir.deleteRecursively()
            downloadedChunksFile.delete()
            assembledFilesFile.delete()
            
            Log.d(TAG, "")
            Log.d(TAG, "🎉 DOWNLOAD COMPLETED SUCCESSFULLY!")
            Log.d(TAG, "")
            
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
            
            stopSelf()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Download failed", e)
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
    
    private suspend fun <T> retryWithExponentialBackoff(
        maxAttempts: Int,
        initialDelayMs: Long,
        maxDelayMs: Long,
        factor: Double = 2.0,
        operation: String,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null
        
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts - 1) {
                    Log.w(TAG, "Tentativa ${attempt + 1}/$maxAttempts falhou ao $operation: ${e.message}. Tentando novamente em ${currentDelay}ms...")
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
                } else {
                    Log.e(TAG, "Todas as $maxAttempts tentativas falharam ao $operation", e)
                }
            }
        }
        
        throw lastException ?: Exception("Operação falhou: $operation")
    }
}
