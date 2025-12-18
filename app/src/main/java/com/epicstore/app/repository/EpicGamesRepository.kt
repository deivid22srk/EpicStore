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
        private const val LAUNCHER_BASE_URL = "https://launcher-public-service-prod06.ol.epicgames.com/"
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
    
    private val launcherRetrofit = Retrofit.Builder()
        .baseUrl(LAUNCHER_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val gamesApi = libraryRetrofit.create(EpicGamesApi::class.java)
    private val catalogApi = catalogRetrofit.create(EpicGamesApi::class.java)
    private val manifestApi = launcherRetrofit.create(EpicGamesApi::class.java)
    
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
                val allGames = response.body()!!.records ?: emptyList()
                Log.d(TAG, "Retrieved ${allGames.size} total items")
                
                // Filtrar apenas jogos de PC válidos
                val filteredGames = allGames
                    .filter { game ->
                        // Apenas Windows
                        game.platform?.contains("Windows") == true &&
                        // Apenas APPLICATION (não DLC)
                        game.recordType == "APPLICATION" &&
                        // Tem nome válido
                        !game.sandboxName.isNullOrBlank() &&
                        // Não é "Live" genérico
                        game.sandboxName != "Live"
                    }
                    .distinctBy { it.sandboxName } // Remove duplicatas pelo nome
                    .sortedBy { it.sandboxName }
                
                Log.d(TAG, "Filtered to ${filteredGames.size} unique PC games")
                
                // Buscar imagens em paralelo - método melhorado baseado no Legendary
                filteredGames.forEach { game ->
                    if (game.namespace != null && game.catalogItemId != null) {
                        try {
                            Log.d(TAG, "Fetching image for ${game.sandboxName} (namespace: ${game.namespace}, itemId: ${game.catalogItemId})")
                            val catalogResponse = catalogApi.getCatalogInfo(
                                "bearer $launcherToken",
                                game.namespace,
                                game.catalogItemId
                            )
                            
                            if (catalogResponse.isSuccessful && catalogResponse.body() != null) {
                                val responseBody = catalogResponse.body()!!
                                Log.d(TAG, "Catalog response keys: ${responseBody.keys}")
                                
                                val catalogData = responseBody[game.catalogItemId]
                                
                                if (catalogData != null) {
                                    val keyImages = catalogData.keyImages
                                    Log.d(TAG, "Available image types for ${game.sandboxName}: ${keyImages?.map { it.type }}")
                                    
                                    // Tentar múltiplos tipos de imagem em ordem de preferência
                                    val imageUrl = keyImages?.firstOrNull { 
                                        it.type == "DieselStoreFrontWide" 
                                    }?.url ?: keyImages?.firstOrNull { 
                                        it.type == "OfferImageWide" 
                                    }?.url ?: keyImages?.firstOrNull { 
                                        it.type == "DieselGameBoxTall" 
                                    }?.url ?: keyImages?.firstOrNull { 
                                        it.type == "DieselGameBox" 
                                    }?.url ?: keyImages?.firstOrNull { 
                                        it.type == "Thumbnail" 
                                    }?.url ?: keyImages?.firstOrNull()?.url
                                    
                                    game.imageUrl = imageUrl
                                    if (imageUrl != null) {
                                        Log.d(TAG, "✓ Image loaded for ${game.sandboxName}: $imageUrl")
                                    } else {
                                        Log.w(TAG, "✗ No image found for ${game.sandboxName}")
                                    }
                                } else {
                                    Log.w(TAG, "No catalog data found for ${game.sandboxName}")
                                }
                            } else {
                                val errorBody = catalogResponse.errorBody()?.string()
                                Log.e(TAG, "Catalog API failed for ${game.sandboxName}: ${catalogResponse.code()} - $errorBody")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading image for ${game.sandboxName}", e)
                        }
                    } else {
                        Log.w(TAG, "Missing namespace or catalogItemId for ${game.sandboxName}")
                    }
                }
                
                Result.success(filteredGames)
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
