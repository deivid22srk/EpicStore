package com.epicstore.app.download

import android.util.Log
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

class ManifestParser {
    companion object {
        private const val TAG = "ManifestParser"
        private const val HEADER_MAGIC = 0x44BEC00C
        
        fun parse(data: ByteArray): ParsedManifest {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            
            val magic = buffer.int.toUInt()
            if (magic != HEADER_MAGIC.toUInt()) {
                throw IllegalArgumentException("Invalid manifest header magic: ${magic.toString(16)}")
            }
            
            val headerSize = buffer.int
            val sizeUncompressed = buffer.int
            val sizeCompressed = buffer.int
            val shaHash = ByteArray(20)
            buffer.get(shaHash)
            val storedAs = buffer.get().toInt()
            val version = buffer.int
            
            Log.d(TAG, "Manifest version: $version, compressed: ${storedAs and 0x1}")
            
            val remainingData = ByteArray(data.size - headerSize)
            buffer.get(remainingData)
            
            val manifestData = if ((storedAs and 0x1) != 0) {
                Log.d(TAG, "Decompressing manifest...")
                decompressZlib(remainingData)
            } else {
                remainingData
            }
            
            val dataBuffer = ByteBuffer.wrap(manifestData).order(ByteOrder.LITTLE_ENDIAN)
            
            val meta = readManifestMeta(dataBuffer)
            Log.d(TAG, "App: ${meta.appName}, Version: ${meta.buildVersion}")
            
            val chunkDataList = readChunkDataList(dataBuffer, version)
            Log.d(TAG, "Total chunks: ${chunkDataList.size}")
            
            val fileManifestList = readFileManifestList(dataBuffer)
            Log.d(TAG, "Total files: ${fileManifestList.size}")
            
            val customFields = readCustomFields(dataBuffer)
            
            return ParsedManifest(meta, chunkDataList, fileManifestList, customFields)
        }
        
        private fun decompressZlib(data: ByteArray): ByteArray {
            val inflater = Inflater()
            inflater.setInput(data)
            val outputStream = java.io.ByteArrayOutputStream(data.size * 2)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                outputStream.write(buffer, 0, count)
            }
            inflater.end()
            return outputStream.toByteArray()
        }
        
        private fun readFString(buffer: ByteBuffer): String {
            val length = buffer.int
            
            return when {
                length < 0 -> {
                    val actualLength = -length * 2
                    val bytes = ByteArray(actualLength - 2)
                    buffer.get(bytes)
                    buffer.position(buffer.position() + 2)
                    String(bytes, Charsets.UTF_16LE)
                }
                length > 0 -> {
                    val bytes = ByteArray(length - 1)
                    buffer.get(bytes)
                    buffer.position(buffer.position() + 1)
                    String(bytes, Charsets.US_ASCII)
                }
                else -> ""
            }
        }
        
        private fun readManifestMeta(buffer: ByteBuffer): ManifestMeta {
            val startPos = buffer.position()
            val metaSize = buffer.int
            val dataVersion = buffer.get().toInt()
            val featureLevel = buffer.int
            val isFileData = buffer.get().toInt() == 1
            val appId = buffer.int
            val appName = readFString(buffer)
            val buildVersion = readFString(buffer)
            val launchExe = readFString(buffer)
            val launchCommand = readFString(buffer)
            
            val prereqCount = buffer.int
            val prereqIds = mutableListOf<String>()
            repeat(prereqCount) {
                prereqIds.add(readFString(buffer))
            }
            
            val prereqName = readFString(buffer)
            val prereqPath = readFString(buffer)
            val prereqArgs = readFString(buffer)
            
            var buildId = ""
            if (dataVersion >= 1) {
                buildId = readFString(buffer)
            }
            
            var uninstallActionPath = ""
            var uninstallActionArgs = ""
            if (dataVersion >= 2) {
                uninstallActionPath = readFString(buffer)
                uninstallActionArgs = readFString(buffer)
            }
            
            val endPos = buffer.position()
            val readSize = endPos - startPos
            if (readSize != metaSize) {
                Log.w(TAG, "Did not read entire manifest metadata! Expected $metaSize, read $readSize")
                buffer.position(startPos + metaSize)
            }
            
            return ManifestMeta(
                appId, appName, buildVersion, launchExe, launchCommand,
                prereqIds, prereqName, prereqPath, prereqArgs, buildId,
                uninstallActionPath, uninstallActionArgs
            )
        }
        
        private fun readChunkDataList(buffer: ByteBuffer, manifestVersion: Int): List<ChunkInfo> {
            val startPos = buffer.position()
            val size = buffer.int
            val version = buffer.get().toInt()
            val count = buffer.int
            
            val chunks = MutableList(count) { ChunkInfo() }
            
            for (chunk in chunks) {
                val g0 = buffer.int
                val g1 = buffer.int
                val g2 = buffer.int
                val g3 = buffer.int
                chunk.guid = listOf(g0, g1, g2, g3)
            }
            
            for (chunk in chunks) {
                chunk.hash = buffer.long
            }
            
            for (chunk in chunks) {
                val shaBytes = ByteArray(20)
                buffer.get(shaBytes)
                chunk.shaHash = shaBytes
            }
            
            for (chunk in chunks) {
                chunk.groupNum = buffer.get().toInt() and 0xFF
            }
            
            for (chunk in chunks) {
                chunk.windowSize = buffer.int
            }
            
            for (chunk in chunks) {
                chunk.fileSize = buffer.long
            }
            
            val endPos = buffer.position()
            val readSize = endPos - startPos
            if (readSize != size) {
                Log.w(TAG, "Did not read entire chunk data list! Expected $size, read $readSize")
                buffer.position(startPos + size)
            }
            
            for (chunk in chunks) {
                chunk.manifestVersion = manifestVersion
            }
            
            return chunks
        }
        
        private fun readFileManifestList(buffer: ByteBuffer): List<FileManifest> {
            val startPos = buffer.position()
            val size = buffer.int
            val version = buffer.get().toInt()
            val count = buffer.int
            
            val files = MutableList(count) { FileManifest() }
            
            for (file in files) {
                file.filename = readFString(buffer)
            }
            
            for (file in files) {
                file.symlinkTarget = readFString(buffer)
            }
            
            for (file in files) {
                val hashBytes = ByteArray(20)
                buffer.get(hashBytes)
                file.hash = hashBytes
            }
            
            for (file in files) {
                file.flags = buffer.get().toInt() and 0xFF
            }
            
            for (file in files) {
                val tagCount = buffer.int
                val tags = mutableListOf<String>()
                repeat(tagCount) {
                    tags.add(readFString(buffer))
                }
                file.installTags = tags
            }
            
            for (file in files) {
                val partCount = buffer.int
                val parts = mutableListOf<ChunkPart>()
                repeat(partCount) {
                    val partStart = buffer.position()
                    val partSize = buffer.int
                    val g0 = buffer.int
                    val g1 = buffer.int
                    val g2 = buffer.int
                    val g3 = buffer.int
                    val guid = listOf(g0, g1, g2, g3)
                    val offset = buffer.int
                    val cpSize = buffer.int
                    
                    val fileOffset = parts.sumOf { it.size }
                    parts.add(ChunkPart(guid, offset, cpSize, fileOffset))
                    
                    val partEnd = buffer.position()
                    val diff = partEnd - partStart - partSize
                    if (diff > 0) {
                        Log.w(TAG, "Did not read chunk part correctly, skipping $diff bytes")
                        buffer.position(buffer.position() + diff)
                    }
                }
                file.chunkParts = parts
                file.fileSize = parts.sumOf { it.size.toLong() }
            }
            
            if (version >= 1) {
                for (file in files) {
                    val hasMd5 = buffer.int
                    if (hasMd5 != 0) {
                        val md5 = ByteArray(16)
                        buffer.get(md5)
                        file.hashMd5 = md5
                    }
                }
                
                for (file in files) {
                    file.mimeType = readFString(buffer)
                }
            }
            
            if (version >= 2) {
                for (file in files) {
                    val sha256 = ByteArray(32)
                    buffer.get(sha256)
                    file.hashSha256 = sha256
                }
            }
            
            val endPos = buffer.position()
            val readSize = endPos - startPos
            if (readSize != size) {
                Log.w(TAG, "Did not read entire file manifest list! Expected $size, read $readSize")
                buffer.position(startPos + size)
            }
            
            return files
        }
        
        private fun readCustomFields(buffer: ByteBuffer): Map<String, String> {
            if (!buffer.hasRemaining()) {
                return emptyMap()
            }
            
            val startPos = buffer.position()
            val size = buffer.int
            val version = buffer.get().toInt()
            val count = buffer.int
            
            val keys = mutableListOf<String>()
            repeat(count) {
                keys.add(readFString(buffer))
            }
            
            val values = mutableListOf<String>()
            repeat(count) {
                values.add(readFString(buffer))
            }
            
            val endPos = buffer.position()
            val readSize = endPos - startPos
            if (readSize != size) {
                Log.w(TAG, "Did not read entire custom fields! Expected $size, read $readSize")
                buffer.position(startPos + size)
            }
            
            return keys.zip(values).toMap()
        }
    }
}

data class ParsedManifest(
    val meta: ManifestMeta,
    val chunks: List<ChunkInfo>,
    val files: List<FileManifest>,
    val customFields: Map<String, String>
) {
    fun getTotalSize(): Long = files.sumOf { it.fileSize }
    fun getTotalChunks(): Int = chunks.size
}

data class ManifestMeta(
    val appId: Int,
    val appName: String,
    val buildVersion: String,
    val launchExe: String,
    val launchCommand: String,
    val prereqIds: List<String>,
    val prereqName: String,
    val prereqPath: String,
    val prereqArgs: String,
    val buildId: String,
    val uninstallActionPath: String,
    val uninstallActionArgs: String
)

data class ChunkInfo(
    var guid: List<Int> = emptyList(),
    var hash: Long = 0,
    var shaHash: ByteArray = ByteArray(0),
    var groupNum: Int = 0,
    var windowSize: Int = 0,
    var fileSize: Long = 0,
    var manifestVersion: Int = 18
) {
    fun getGuidStr(): String {
        return guid.joinToString("-") { "%08X".format(it) }
    }
    
    fun getPath(): String {
        val chunkDir = when {
            manifestVersion >= 15 -> "ChunksV4"
            manifestVersion >= 6 -> "ChunksV3"
            manifestVersion >= 3 -> "ChunksV2"
            else -> "Chunks"
        }
        val guidStr = guid.joinToString("") { "%08X".format(it) }
        return "$chunkDir/%02d/%016X_$guidStr.chunk".format(groupNum, hash)
    }
}

data class FileManifest(
    var filename: String = "",
    var symlinkTarget: String = "",
    var hash: ByteArray = ByteArray(0),
    var flags: Int = 0,
    var installTags: List<String> = emptyList(),
    var chunkParts: List<ChunkPart> = emptyList(),
    var fileSize: Long = 0,
    var hashMd5: ByteArray? = null,
    var mimeType: String = "",
    var hashSha256: ByteArray = ByteArray(0)
) {
    val isReadOnly: Boolean get() = (flags and 0x1) != 0
    val isCompressed: Boolean get() = (flags and 0x2) != 0
    val isExecutable: Boolean get() = (flags and 0x4) != 0
}

data class ChunkPart(
    val guid: List<Int>,
    val offset: Int,
    val size: Int,
    val fileOffset: Int
) {
    fun getGuidStr(): String {
        return guid.joinToString("-") { "%08x".format(it) }
    }
}
