package com.epicstore.app.model

import com.google.gson.annotations.SerializedName

data class ManifestResponse(
    @SerializedName("elements")
    val elements: List<ManifestElement>?
)

data class ManifestElement(
    @SerializedName("appName")
    val appName: String,
    
    @SerializedName("labelName")
    val labelName: String,
    
    @SerializedName("buildVersion")
    val buildVersion: String?,
    
    @SerializedName("catalogItemId")
    val catalogItemId: String,
    
    @SerializedName("namespace")
    val namespace: String,
    
    @SerializedName("manifests")
    val manifests: List<ManifestInfo>?
)

data class ManifestInfo(
    @SerializedName("uri")
    val uri: String,
    
    @SerializedName("queryParams")
    val queryParams: List<QueryParam>?
)

data class QueryParam(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("value")
    val value: String
)

data class ChunkDownloadInfo(
    val guid: String,
    val hash: String,
    val groupNum: Int,
    val url: String,
    val size: Long
)

data class DownloadProgress(
    val gameName: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val totalChunks: Int,
    val downloadedChunks: Int,
    val downloadSpeed: Long,
    val progress: Int
)
