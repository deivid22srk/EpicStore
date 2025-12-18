package com.epicstore.app.download

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

class FileAssembler(private val baseDir: File) {
    companion object {
        private const val TAG = "FileAssembler"
    }
    
    private val chunkCache = ConcurrentHashMap<String, DecodedChunk>()
    private val openFiles = ConcurrentHashMap<String, RandomAccessFile>()
    
    fun assembleFile(fileManifest: FileManifest, chunkInfoList: List<ChunkInfo>, getChunk: (ChunkInfo) -> DecodedChunk) {
        val file = File(baseDir, fileManifest.filename)
        file.parentFile?.mkdirs()
        
        Log.d(TAG, "Assembling file: ${fileManifest.filename} (${fileManifest.fileSize} bytes, ${fileManifest.chunkParts.size} parts)")
        
        val raf = RandomAccessFile(file, "rw")
        try {
            raf.setLength(fileManifest.fileSize)
            
            val chunkMap = chunkInfoList.associateBy { 
                it.guid.joinToString("-") { g -> "%08x".format(g) }
            }
            
            for (part in fileManifest.chunkParts) {
                val partGuidStr = part.getGuidStr()
                val chunkInfo = chunkMap[partGuidStr] ?: run {
                    Log.w(TAG, "Chunk not found for GUID: $partGuidStr")
                    continue
                }
                
                val chunk = getChunk(chunkInfo)
                
                if (part.offset + part.size > chunk.data.size) {
                    Log.e(TAG, "Invalid chunk part: offset=${part.offset}, size=${part.size}, chunkSize=${chunk.data.size}")
                    continue
                }
                
                raf.seek(part.fileOffset.toLong())
                raf.write(chunk.data, part.offset, part.size)
            }
            
            if (fileManifest.isExecutable) {
                file.setExecutable(true)
            }
            
            Log.d(TAG, "File assembled successfully: ${fileManifest.filename}")
        } finally {
            raf.close()
        }
    }
    
    fun cleanup() {
        openFiles.values.forEach { it.close() }
        openFiles.clear()
        chunkCache.clear()
    }
}
