package com.epicstore.app.database

import androidx.room.*
import com.epicstore.app.model.DownloadState
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    
    @Query("SELECT * FROM downloads WHERE appName = :appName")
    suspend fun getDownload(appName: String): DownloadState?
    
    @Query("SELECT * FROM downloads WHERE appName = :appName")
    fun getDownloadFlow(appName: String): Flow<DownloadState?>
    
    @Query("SELECT * FROM downloads")
    fun getAllDownloadsFlow(): Flow<List<DownloadState>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadState)
    
    @Update
    suspend fun updateDownload(download: DownloadState)
    
    @Query("DELETE FROM downloads WHERE appName = :appName")
    suspend fun deleteDownload(appName: String)
    
    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}
