package com.epicstore.app.model

import com.google.gson.annotations.SerializedName

data class EpicAuthResponse(
    @SerializedName("access_token")
    val accessToken: String,
    
    @SerializedName("expires_in")
    val expiresIn: Int,
    
    @SerializedName("expires_at")
    val expiresAt: String?,
    
    @SerializedName("token_type")
    val tokenType: String,
    
    @SerializedName("refresh_token")
    val refreshToken: String?,
    
    @SerializedName("refresh_expires")
    val refreshExpires: Int?,
    
    @SerializedName("refresh_expires_at")
    val refreshExpiresAt: String?,
    
    @SerializedName("account_id")
    val accountId: String?,
    
    @SerializedName("client_id")
    val clientId: String?,
    
    @SerializedName("application_id")
    val applicationId: String?,
    
    @SerializedName("displayName")
    val displayName: String?
)

data class DeviceCodeResponse(
    @SerializedName("device_code")
    val deviceCode: String,
    
    @SerializedName("user_code")
    val userCode: String,
    
    @SerializedName("verification_uri")
    val verificationUri: String,
    
    @SerializedName("verification_uri_complete")
    val verificationUriComplete: String,
    
    @SerializedName("expires_in")
    val expiresIn: Int,
    
    @SerializedName("interval")
    val interval: Int?
)

data class ExchangeCodeResponse(
    @SerializedName("code")
    val code: String,
    
    @SerializedName("expiresInSeconds")
    val expiresInSeconds: Int?
)

data class DeviceAuthResponse(
    @SerializedName("deviceId")
    val deviceId: String,
    
    @SerializedName("accountId")
    val accountId: String,
    
    @SerializedName("secret")
    val secret: String,
    
    @SerializedName("userAgent")
    val userAgent: String?,
    
    @SerializedName("created")
    val created: Map<String, String>?
)
