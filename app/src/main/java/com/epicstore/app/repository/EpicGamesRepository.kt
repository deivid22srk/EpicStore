package com.epicstore.app.repository

import android.util.Log
import com.epicstore.app.auth.EpicAuthManager
import com.epicstore.app.model.Game
import com.epicstore.app.network.EpicGamesApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class EpicGamesRepository(private val authManager: EpicAuthManager) {
    
    companion object {
        private const val TAG = "EpicGamesRepository"
        private const val LIBRARY_BASE_URL = "https://library-service.live.use1a.on.epicgames.com/"
        private const val CATALOG_BASE_URL = "https://catalog-public-service-prod06.ol.epicgames.com/"
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "UELauncher/14.0.8-22004686+++Portal+Release-Live Windows/10.0.19041.1.256.64bit")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val libraryRetrofit = Retrofit.Builder()
        .baseUrl(LIBRARY_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val catalogRetrofit = Retrofit.Builder()
        .baseUrl(CATALOG_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val gamesApi = libraryRetrofit.create(EpicGamesApi::class.java)
    private val catalogApi = catalogRetrofit.create(EpicGamesApi::class.java)
    
    suspend fun getLibraryGames(): Result<List<Game>> {
        return try {
            Log.d(TAG, "Getting Launcher token for library access...")
            val tokenResult = authManager.getLauncherToken()
            
            if (tokenResult.isFailure) {
                Log.e(TAG, "Failed to get Launcher token")
                return Result.failure(Exception("Failed to get Launcher token"))
            }
            
            val launcherToken = tokenResult.getOrNull() ?: return Result.failure(Exception("No launcher token"))
            
            Log.d(TAG, "Fetching library games...")
            val response = gamesApi.getLibraryGames("bearer $launcherToken")
            
            if (response.isSuccessful && response.body() != null) {
                val games = response.body()!!.records ?: emptyList()
                Log.d(TAG, "Retrieved ${games.size} games")
                
                // Buscar imagens para os primeiros 10 jogos (evitar muitas requisições)
                games.take(10).forEach { game ->
                    if (game.namespace != null && game.catalogItemId != null) {
                        try {
                            val catalogResponse = catalogApi.getCatalogInfo(
                                "bearer $launcherToken",
                                game.namespace,
                                game.catalogItemId
                            )
                            
                            if (catalogResponse.isSuccessful && catalogResponse.body() != null) {
                                val catalogData = catalogResponse.body()!![game.catalogItemId]
                                val imageUrl = catalogData?.keyImages?.firstOrNull { 
                                    it.type == "DieselStoreFrontWide" || it.type == "OfferImageWide"
                                }?.url
                                game.imageUrl = imageUrl
                                Log.d(TAG, "Loaded image for ${game.sandboxName}: $imageUrl")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading image for ${game.appName}", e)
                        }
                    }
                }
                
                Result.success(games)
            } else {
                Log.e(TAG, "Failed to get library: ${response.code()} - ${response.message()}")
                Log.e(TAG, "Error body: ${response.errorBody()?.string()}")
                Result.failure(Exception("Failed to get library: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting library", e)
            Result.failure(e)
        }
    }
}
