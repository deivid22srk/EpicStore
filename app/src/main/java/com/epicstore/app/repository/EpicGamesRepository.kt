package com.epicstore.app.repository

import com.epicstore.app.auth.EpicAuthManager
import com.epicstore.app.model.AssetInfo
import com.epicstore.app.model.Game
import com.epicstore.app.network.EpicGamesApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class EpicGamesRepository(private val authManager: EpicAuthManager) {
    
    companion object {
        private const val LIBRARY_BASE_URL = "https://library-service.live.use1a.on.epicgames.com/"
        private const val CATALOG_BASE_URL = "https://catalog-public-service-prod06.ol.epicgames.com/"
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
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
    
    private val libraryApi = libraryRetrofit.create(EpicGamesApi::class.java)
    private val catalogApi = catalogRetrofit.create(EpicGamesApi::class.java)
    
    suspend fun getLibraryGames(): Result<List<Game>> {
        val token = authManager.getAccessToken() ?: return Result.failure(Exception("Not logged in"))
        
        return try {
            val response = libraryApi.getLibraryGames("Bearer $token")
            
            if (response.isSuccessful && response.body() != null) {
                val games = response.body()!!.records ?: emptyList()
                Result.success(games)
            } else {
                Result.failure(Exception("Failed to get library: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getCatalogInfo(namespace: String, itemId: String): Result<AssetInfo?> {
        val token = authManager.getAccessToken() ?: return Result.failure(Exception("Not logged in"))
        
        return try {
            val response = catalogApi.getCatalogInfo(
                authorization = "Bearer $token",
                namespace = namespace,
                itemIds = itemId
            )
            
            if (response.isSuccessful && response.body() != null) {
                val info = response.body()!![itemId]
                Result.success(info)
            } else {
                Result.failure(Exception("Failed to get catalog info: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
