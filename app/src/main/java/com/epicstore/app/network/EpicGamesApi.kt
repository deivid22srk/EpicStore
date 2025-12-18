package com.epicstore.app.network

import com.epicstore.app.model.GamesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface EpicGamesApi {
    
    @GET("library/api/public/items")
    suspend fun getLibraryGames(
        @Header("Authorization") authorization: String,
        @Query("includeMetadata") includeMetadata: Boolean = true
    ): Response<GamesResponse>
}
