package com.epicstore.app.network

import com.epicstore.app.model.GamesResponse
import com.epicstore.app.model.CatalogResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Path

interface EpicGamesApi {
    
    @GET("library/api/public/items")
    suspend fun getLibraryGames(
        @Header("Authorization") authorization: String,
        @Query("includeMetadata") includeMetadata: Boolean = true
    ): Response<GamesResponse>
    
    @GET("catalog/api/shared/namespace/{namespace}/bulk/items")
    suspend fun getCatalogInfo(
        @Header("Authorization") authorization: String,
        @Path("namespace") namespace: String,
        @Query("id") catalogItemId: String,
        @Query("country") country: String = "BR",
        @Query("locale") locale: String = "pt-BR"
    ): Response<Map<String, CatalogResponse>>
}
