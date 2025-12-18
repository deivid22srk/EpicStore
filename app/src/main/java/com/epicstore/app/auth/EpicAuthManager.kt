package com.epicstore.app.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import com.epicstore.app.model.EpicAuthResponse
import com.epicstore.app.model.DeviceCodeResponse
import com.epicstore.app.model.DeviceAuthResponse
import com.epicstore.app.network.EpicAuthApi
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class EpicAuthManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EpicAuthManager"
        
        private const val SWITCH_TOKEN = "OThmN2U0MmMyZTNhNGY4NmE3NGViNDNmYmI0MWVkMzk6MGEyNDQ5YTItMDAxYS00NTFlLWFmZWMtM2U4MTI5MDFjNGQ3"
        private const val ANDROID_TOKEN = "M2Y2OWU1NmM3NjQ5NDkyYzhjYzI5ZjFhZjA4YThhMTI6YjUxZWU5Y2IxMjIzNGY1MGE2OWVmYTY3ZWY1MzgxMmU="
        private const val LAUNCHER_TOKEN = "MzRhMDJjZjhmNDQxNGUyOWIxNTkyMTg3NmRhMzZmOWE6ZGFhZmJjY2M3Mzc3NDUwMzlkZmZlNTNkOTRmYzc2Y2Y="
        
        private const val OAUTH_BASE_URL = "https://account-public-service-prod03.ol.epicgames.com/"
        
        private const val PREFS_NAME = "epic_auth_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_SECRET = "secret"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
    
    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
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
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(OAUTH_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val authApi = retrofit.create(EpicAuthApi::class.java)
    
    suspend fun startDeviceAuthFlow(): Result<DeviceCodeResponse> {
        return try {
            Log.d(TAG, "Getting client token...")
            val clientTokenResponse = authApi.getClientToken(
                authorization = "basic $SWITCH_TOKEN",
                grantType = "client_credentials"
            )
            
            if (!clientTokenResponse.isSuccessful || clientTokenResponse.body() == null) {
                return Result.failure(Exception("Failed to get client token: ${clientTokenResponse.code()}"))
            }
            
            val clientToken = clientTokenResponse.body()!!.accessToken
            Log.d(TAG, "Client token obtained")
            
            Log.d(TAG, "Creating device code...")
            val deviceCodeResponse = authApi.createDeviceCode(
                authorization = "bearer $clientToken"
            )
            
            if (!deviceCodeResponse.isSuccessful || deviceCodeResponse.body() == null) {
                return Result.failure(Exception("Failed to create device code: ${deviceCodeResponse.code()}"))
            }
            
            Log.d(TAG, "Device code created")
            Result.success(deviceCodeResponse.body()!!)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting device auth flow", e)
            Result.failure(e)
        }
    }
    
    suspend fun pollForDeviceAuthorization(deviceCode: String, maxAttempts: Int = 30): Result<EpicAuthResponse> {
        repeat(maxAttempts) { attempt ->
            try {
                Log.d(TAG, "Polling for authorization... attempt ${attempt + 1}/$maxAttempts")
                
                val response = authApi.getDeviceToken(
                    authorization = "basic $SWITCH_TOKEN",
                    grantType = "device_code",
                    deviceCode = deviceCode
                )
                
                if (response.isSuccessful && response.body() != null) {
                    Log.d(TAG, "Authorization successful!")
                    return Result.success(response.body()!!)
                }
                
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "Polling response: ${response.code()} - $errorBody")
                
                if (errorBody?.contains("authorization_pending") == true || 
                    errorBody?.contains("not_found") == true) {
                    delay(10000)
                } else {
                    return Result.failure(Exception("Authorization failed: ${response.code()}"))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during polling", e)
                delay(10000)
            }
        }
        
        return Result.failure(Exception("Timeout waiting for authorization"))
    }
    
    suspend fun exchangeToAndroidToken(switchAccessToken: String): Result<EpicAuthResponse> {
        return try {
            Log.d(TAG, "Getting exchange code...")
            val exchangeResponse = authApi.getExchangeCode(
                authorization = "bearer $switchAccessToken"
            )
            
            if (!exchangeResponse.isSuccessful || exchangeResponse.body() == null) {
                return Result.failure(Exception("Failed to get exchange code: ${exchangeResponse.code()}"))
            }
            
            val exchangeCode = exchangeResponse.body()!!.code
            Log.d(TAG, "Exchange code obtained")
            
            Log.d(TAG, "Exchanging to Android token...")
            val tokenResponse = authApi.exchangeCode(
                authorization = "basic $ANDROID_TOKEN",
                grantType = "exchange_code",
                exchangeCode = exchangeCode
            )
            
            if (!tokenResponse.isSuccessful || tokenResponse.body() == null) {
                return Result.failure(Exception("Failed to exchange token: ${tokenResponse.code()}"))
            }
            
            Log.d(TAG, "Android token obtained")
            Result.success(tokenResponse.body()!!)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging to Android token", e)
            Result.failure(e)
        }
    }
    
    suspend fun createDeviceAuth(accessToken: String, accountId: String): Result<DeviceAuthResponse> {
        return try {
            Log.d(TAG, "Creating device auth...")
            val response = authApi.createDeviceAuth(
                authorization = "bearer $accessToken",
                accountId = accountId
            )
            
            if (!response.isSuccessful || response.body() == null) {
                return Result.failure(Exception("Failed to create device auth: ${response.code()}"))
            }
            
            val deviceAuth = response.body()!!
            saveDeviceAuth(deviceAuth, accountId)
            Log.d(TAG, "Device auth created and saved")
            
            Result.success(deviceAuth)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating device auth", e)
            Result.failure(e)
        }
    }
    
    suspend fun deviceAuthLogin(): Result<EpicAuthResponse> {
        val deviceId = getDeviceId() ?: return Result.failure(Exception("No device auth saved"))
        val accountId = getAccountId() ?: return Result.failure(Exception("No account ID saved"))
        val secret = getSecret() ?: return Result.failure(Exception("No secret saved"))
        
        return try {
            Log.d(TAG, "Logging in with device auth...")
            val response = authApi.deviceAuthLogin(
                authorization = "basic $ANDROID_TOKEN",
                grantType = "device_auth",
                accountId = accountId,
                deviceId = deviceId,
                secret = secret
            )
            
            if (!response.isSuccessful || response.body() == null) {
                return Result.failure(Exception("Device auth login failed: ${response.code()}"))
            }
            
            val authResponse = response.body()!!
            saveAccessToken(authResponse)
            Log.d(TAG, "Device auth login successful")
            
            Result.success(authResponse)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during device auth login", e)
            Result.failure(e)
        }
    }
    
    suspend fun getLauncherToken(): Result<String> {
        return try {
            val currentToken = getAccessToken()
            if (currentToken == null) {
                val loginResult = deviceAuthLogin()
                if (loginResult.isFailure) {
                    return Result.failure(Exception("Failed to login"))
                }
            }
            
            val token = getAccessToken() ?: return Result.failure(Exception("No token"))
            
            Log.d(TAG, "Getting exchange code for Launcher...")
            val exchangeResponse = authApi.getExchangeCode(
                authorization = "bearer $token"
            )
            
            if (!exchangeResponse.isSuccessful || exchangeResponse.body() == null) {
                return Result.failure(Exception("Failed to get exchange code: ${exchangeResponse.code()}"))
            }
            
            val exchangeCode = exchangeResponse.body()!!.code
            Log.d(TAG, "Exchanging to Launcher token...")
            
            val tokenResponse = authApi.exchangeCode(
                authorization = "basic $LAUNCHER_TOKEN",
                grantType = "exchange_code",
                exchangeCode = exchangeCode
            )
            
            if (!tokenResponse.isSuccessful || tokenResponse.body() == null) {
                return Result.failure(Exception("Failed to exchange to Launcher token: ${tokenResponse.code()}"))
            }
            
            val launcherToken = tokenResponse.body()!!.accessToken
            Log.d(TAG, "Launcher token obtained successfully")
            
            Result.success(launcherToken)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Launcher token", e)
            Result.failure(e)
        }
    }
    
    private fun saveDeviceAuth(deviceAuth: DeviceAuthResponse, accountId: String) {
        sharedPreferences.edit().apply {
            putString(KEY_DEVICE_ID, deviceAuth.deviceId)
            putString(KEY_ACCOUNT_ID, accountId)
            putString(KEY_SECRET, deviceAuth.secret)
            apply()
        }
    }
    
    private fun saveAccessToken(authResponse: EpicAuthResponse) {
        sharedPreferences.edit().apply {
            putString(KEY_ACCESS_TOKEN, authResponse.accessToken)
            authResponse.refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
            authResponse.accountId?.let { putString(KEY_ACCOUNT_ID, it) }
            authResponse.displayName?.let { putString(KEY_DISPLAY_NAME, it) }
            putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + (authResponse.expiresIn * 1000L))
            apply()
        }
    }
    
    fun getAccessToken(): String? = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    
    private fun getDeviceId(): String? = sharedPreferences.getString(KEY_DEVICE_ID, null)
    
    fun getAccountId(): String? = sharedPreferences.getString(KEY_ACCOUNT_ID, null)
    
    private fun getSecret(): String? = sharedPreferences.getString(KEY_SECRET, null)
    
    fun getDisplayName(): String? = sharedPreferences.getString(KEY_DISPLAY_NAME, null)
    
    fun hasDeviceAuth(): Boolean {
        return getDeviceId() != null && getAccountId() != null && getSecret() != null
    }
    
    fun isLoggedIn(): Boolean {
        val token = getAccessToken()
        val expiresAt = sharedPreferences.getLong(KEY_EXPIRES_AT, 0)
        return token != null && System.currentTimeMillis() < expiresAt
    }
    
    fun clearAuthData() {
        sharedPreferences.edit().clear().apply()
    }
}
