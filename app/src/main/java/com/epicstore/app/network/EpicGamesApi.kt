package com.epicstore.app.network

import com.epicstore.app.model.AssetInfo
import com.epicstore.app.model.GamesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface EpicGamesApi {
    
    @GET("library/api/public/items")
    suspend fun getLibraryGames(
        @Header("Authorization") authorization: String
    ): Response<GamesResponse>
    
    @GET("catalog/api/shared/namespace/{namespace}/bulk/items")
    suspend fun getCatalogInfo(
        @Header("Authorization") authorization: String,
        @Path("namespace") namespace: String,
        @Query("id") itemIds: String,
        @Query("includeDLCDetails") includeDLCDetails: Boolean = true,
        @Query("includeMainGameDetails") includeMainGameDetails: Boolean = true,
        @Query("country") country: String = "US",
        @Query("locale") locale: String = "en-US"
    ): Response<Map<String, AssetInfo>>
}
