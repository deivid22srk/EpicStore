package com.epicstore.app.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.epicstore.app.model.EpicAuthResponse
import com.epicstore.app.network.EpicAuthApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class EpicAuthManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EpicAuthManager"
        private const val CLIENT_ID = "34a02cf8f4414e29b15921876da36f9a"
        private const val CLIENT_SECRET = "daafbccc237768a039fe8bcc4c563da8"
        private const val AUTHORIZATION_BASE_URL = "https://www.epicgames.com/id/authorize"
        private const val OAUTH_BASE_URL = "https://account-public-service-prod03.ol.epicgames.com/"
        private const val REDIRECT_URI = "epicstore://callback"
        
        private const val PREFS_NAME = "epic_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
    
    private val sharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating EncryptedSharedPreferences, using regular SharedPreferences", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
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
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(OAUTH_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val authApi = retrofit.create(EpicAuthApi::class.java)
    
    fun getAuthorizationUrl(): String {
        return "$AUTHORIZATION_BASE_URL?client_id=$CLIENT_ID&response_type=code&redirect_uri=$REDIRECT_URI"
    }
    
    private fun getBasicAuthHeader(): String {
        val credentials = "$CLIENT_ID:$CLIENT_SECRET"
        val encodedCredentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        return "Basic $encodedCredentials"
    }
    
    suspend fun exchangeCodeForToken(code: String): Result<EpicAuthResponse> {
        return try {
            val response = authApi.getAccessToken(
                authorization = getBasicAuthHeader(),
                grantType = "authorization_code",
                code = code,
                redirectUri = REDIRECT_URI
            )
            
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                saveAuthData(authResponse)
                Result.success(authResponse)
            } else {
                Result.failure(Exception("Failed to get access token: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging code for token", e)
            Result.failure(e)
        }
    }
    
    suspend fun refreshToken(): Result<EpicAuthResponse> {
        val refreshToken = getRefreshToken() ?: return Result.failure(Exception("No refresh token available"))
        
        return try {
            val response = authApi.refreshAccessToken(
                authorization = getBasicAuthHeader(),
                refreshToken = refreshToken
            )
            
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                saveAuthData(authResponse)
                Result.success(authResponse)
            } else {
                clearAuthData()
                Result.failure(Exception("Failed to refresh token: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            clearAuthData()
            Result.failure(e)
        }
    }
    
    private fun saveAuthData(authResponse: EpicAuthResponse) {
        try {
            sharedPreferences.edit().apply {
                putString(KEY_ACCESS_TOKEN, authResponse.accessToken)
                authResponse.refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
                authResponse.accountId?.let { putString(KEY_ACCOUNT_ID, it) }
                putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + (authResponse.expiresIn * 1000L))
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving auth data", e)
        }
    }
    
    fun getAccessToken(): String? {
        return try {
            sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token", e)
            null
        }
    }
    
    private fun getRefreshToken(): String? {
        return try {
            sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting refresh token", e)
            null
        }
    }
    
    fun getAccountId(): String? {
        return try {
            sharedPreferences.getString(KEY_ACCOUNT_ID, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting account ID", e)
            null
        }
    }
    
    fun isLoggedIn(): Boolean {
        return try {
            val token = getAccessToken()
            val expiresAt = sharedPreferences.getLong(KEY_EXPIRES_AT, 0)
            token != null && System.currentTimeMillis() < expiresAt
        } catch (e: Exception) {
            Log.e(TAG, "Error checking login status", e)
            false
        }
    }
    
    fun clearAuthData() {
        try {
            sharedPreferences.edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing auth data", e)
        }
    }
}
