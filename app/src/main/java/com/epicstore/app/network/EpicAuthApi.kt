package com.epicstore.app.network

import com.epicstore.app.model.EpicAuthResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface EpicAuthApi {
    
    @FormUrlEncoded
    @POST("account/api/oauth/token")
    suspend fun getAccessToken(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String? = null
    ): Response<EpicAuthResponse>
    
    @FormUrlEncoded
    @POST("account/api/oauth/token")
    suspend fun refreshAccessToken(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): Response<EpicAuthResponse>
    
    @FormUrlEncoded
    @POST("account/api/oauth/token")
    suspend fun getExchangeToken(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String = "exchange_code",
        @Field("exchange_code") exchangeCode: String
    ): Response<EpicAuthResponse>
}
