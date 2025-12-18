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
    val applicationId: String?
)
