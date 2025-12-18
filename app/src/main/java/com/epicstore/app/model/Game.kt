package com.epicstore.app.model

import com.google.gson.annotations.SerializedName

data class Game(
    @SerializedName("appName")
    val appName: String,
    
    @SerializedName("appTitle")
    val appTitle: String,
    
    @SerializedName("sandboxId")
    val sandboxId: String?,
    
    @SerializedName("namespace")
    val namespace: String?,
    
    @SerializedName("catalogItemId")
    val catalogItemId: String?
)

data class GamesResponse(
    @SerializedName("records")
    val records: List<Game>?
)

data class AssetInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String?,
    
    @SerializedName("keyImages")
    val keyImages: List<KeyImage>?,
    
    @SerializedName("namespace")
    val namespace: String?,
    
    @SerializedName("categories")
    val categories: List<Category>?
)

data class KeyImage(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("url")
    val url: String
)

data class Category(
    @SerializedName("path")
    val path: String
)

data class CatalogResponse(
    @SerializedName("elements")
    val elements: List<AssetInfo>?
)
