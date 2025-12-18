package com.epicstore.app.download

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

class ChunkDownloader(private val okHttpClient: OkHttpClient) {
    companion object {
        private const val TAG = "ChunkDownloader"
        private const val CHUNK_HEADER_MAGIC = 0xB1FE3AA2
    }
    
    fun downloadAndDecodeChunk(url: String): DecodedChunk {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "EpicGamesLauncher/11.0.1-14907503+++Portal+Release-Live Windows/10.0.19041.1.256.64bit")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw Exception("Failed to download chunk: ${response.code} - ${response.message}")
            }
            
            val chunkBytes = response.body?.bytes() ?: throw Exception("Empty chunk response")
            
            return decodeChunk(chunkBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading chunk from $url", e)
            throw e
        }
    }
    
    private fun decodeChunk(data: ByteArray): DecodedChunk {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        
        val magic = buffer.int.toUInt()
        if (magic != CHUNK_HEADER_MAGIC.toUInt()) {
            throw IllegalArgumentException("Invalid chunk header magic: ${magic.toString(16)}")
        }
        
        val headerVersion = buffer.int
        val headerSize = buffer.int
        val compressedSize = buffer.int
        
        val guid = IntArray(4) { buffer.int }
        val hash = buffer.long
        val storedAs = buffer.get().toInt()
        
        var shaHash: ByteArray? = null
        var hashType: Int = 0
        if (headerVersion >= 2) {
            shaHash = ByteArray(20)
            buffer.get(shaHash)
            hashType = buffer.get().toInt()
        }
        
        var uncompressedSize = 1024 * 1024
        if (headerVersion >= 3) {
            uncompressedSize = buffer.int
        }
        
        val compressedData = ByteArray(compressedSize)
        buffer.get(compressedData)
        
        val decodedData = if ((storedAs and 0x1) != 0) {
            decompressZlib(compressedData, uncompressedSize)
        } else {
            compressedData
        }
        
        return DecodedChunk(
            guid = guid.toList(),
            hash = hash,
            data = decodedData,
            compressed = (storedAs and 0x1) != 0,
            originalSize = data.size
        )
    }
    
    private fun decompressZlib(data: ByteArray, expectedSize: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val output = ByteArray(expectedSize)
        val resultLength = inflater.inflate(output)
        inflater.end()
        
        if (resultLength != expectedSize) {
            Log.w(TAG, "Decompressed size mismatch: expected $expectedSize, got $resultLength")
        }
        
        return output
    }
}

data class DecodedChunk(
    val guid: List<Int>,
    val hash: Long,
    val data: ByteArray,
    val compressed: Boolean,
    val originalSize: Int
)
