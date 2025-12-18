package com.epicstore.app.network

import com.epicstore.app.model.EpicAuthResponse
import com.epicstore.app.model.DeviceCodeResponse
import com.epicstore.app.model.ExchangeCodeResponse
import com.epicstore.app.model.DeviceAuthResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface EpicAuthApi {
    
    @FormUrlEncoded
    @POST("account/api/oauth/token")
    suspend fun getClientToken(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String
    ): Response<EpicAuthResponse>
    
    @POST("account/api/oauth/deviceAuthorization")
    suspend fun createDeviceCode(
        @Header("Authorization") authorization: String
    ): Response<DeviceCodeResponse>
    
    @FormUrlEncoded
    @POST("account/api/oauth/token")
    suspend fun getDeviceToken(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String,
        @Field("device_code") deviceCode: String
    ): Response<EpicAuthResponse>
    
    @GET("account/api/oauth/exchange")
    suspend fun getExchangeCode(
        @Header("Authorization") authorization: String
    ): Response<ExchangeCodeResponse>
    
    @FormUrlEncoded
    @POST("account/api/oauth/token")
    suspend fun exchangeCode(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String,
        @Field("exchange_code") exchangeCode: String
    ): Response<EpicAuthResponse>
    
    @POST("account/api/public/account/{accountId}/deviceAuth")
    suspend fun createDeviceAuth(
        @Header("Authorization") authorization: String,
        @Path("accountId") accountId: String
    ): Response<DeviceAuthResponse>
    
    @FormUrlEncoded
    @POST("account/api/oauth/token")
    suspend fun deviceAuthLogin(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String,
        @Field("account_id") accountId: String,
        @Field("device_id") deviceId: String,
        @Field("secret") secret: String
    ): Response<EpicAuthResponse>
    
    @FormUrlEncoded
    @POST("account/api/oauth/token")
    suspend fun refreshAccessToken(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): Response<EpicAuthResponse>
}
