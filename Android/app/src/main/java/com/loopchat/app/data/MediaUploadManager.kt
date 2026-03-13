package com.loopchat.app.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Media upload manager for Supabase Storage
 * Handles image, video, document, and voice message uploads
 */
object MediaUploadManager {
    
    private val SUPABASE_URL = com.loopchat.app.BuildConfig.SUPABASE_URL
    private val SUPABASE_KEY = com.loopchat.app.BuildConfig.SUPABASE_ANON_KEY
    
    /**
     * Upload image to Supabase Storage
     */
    suspend fun uploadImage(
        context: Context,
        imageUri: Uri,
        httpClient: HttpClient
    ): Result<MediaUploadResult> {
        return uploadMedia(
            context = context,
            mediaUri = imageUri,
            bucket = "media",
            folder = "images",
            httpClient = httpClient
        )
    }
    
    /**
     * Upload video to Supabase Storage
     */
    suspend fun uploadVideo(
        context: Context,
        videoUri: Uri,
        httpClient: HttpClient
    ): Result<MediaUploadResult> {
        return uploadMedia(
            context = context,
            mediaUri = videoUri,
            bucket = "media",
            folder = "videos",
            httpClient = httpClient
        )
    }
    
    /**
     * Upload document to Supabase Storage
     */
    suspend fun uploadDocument(
        context: Context,
        documentUri: Uri,
        httpClient: HttpClient
    ): Result<MediaUploadResult> {
        return uploadMedia(
            context = context,
            mediaUri = documentUri,
            bucket = "media",
            folder = "documents",
            httpClient = httpClient
        )
    }
    
    /**
     * Upload voice message to Supabase Storage
     */
    suspend fun uploadVoiceMessage(
        context: Context,
        audioUri: Uri,
        httpClient: HttpClient
    ): Result<MediaUploadResult> {
        return uploadMedia(
            context = context,
            mediaUri = audioUri,
            bucket = "voice-messages",
            folder = "",
            httpClient = httpClient
        )
    }
    
    /**
     * Generic media upload function
     */
    private suspend fun uploadMedia(
        context: Context,
        mediaUri: Uri,
        bucket: String,
        folder: String,
        httpClient: HttpClient
    ): Result<MediaUploadResult> = withContext(Dispatchers.IO) {
        try {
            val accessToken = SupabaseClient.getAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))
            
            // Get file info
            val fileInfo = getFileInfo(context, mediaUri)
            val fileName = "${UUID.randomUUID()}.${fileInfo.extension}"
            val filePath = if (folder.isNotEmpty()) "$folder/$fileName" else fileName
            
            // Read file bytes
            val fileBytes = context.contentResolver.openInputStream(mediaUri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("Failed to read file"))
            
            // Upload to Supabase Storage
            val uploadUrl = "$SUPABASE_URL/storage/v1/object/$bucket/$filePath"
            
            val response = httpClient.post(uploadUrl) {
                header("apikey", SUPABASE_KEY)
                header("Authorization", "Bearer $accessToken")
                setBody(fileBytes)
                contentType(ContentType.parse(fileInfo.mimeType))
            }
            
            if (!response.status.isSuccess()) {
                val error = response.bodyAsText()
                Log.e("MediaUpload", "Upload failed: $error")
                return@withContext Result.failure(Exception("Upload failed: $error"))
            }
            
            // Get public URL
            val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$bucket/$filePath"
            
            Result.success(
                MediaUploadResult(
                    url = publicUrl,
                    fileName = fileInfo.name,
                    fileSize = fileInfo.size,
                    mimeType = fileInfo.mimeType,
                    width = fileInfo.width,
                    height = fileInfo.height
                )
            )
        } catch (e: Exception) {
            Log.e("MediaUpload", "Upload error: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get file information from URI
     */
    private fun getFileInfo(context: Context, uri: Uri): FileInfo {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        var name = "file"
        var size = 0L
        var mimeType = "application/octet-stream"
        
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                
                if (nameIndex >= 0) name = it.getString(nameIndex) ?: "file"
                if (sizeIndex >= 0) size = it.getLong(sizeIndex)
            }
        }
        
        mimeType = context.contentResolver.getType(uri) ?: mimeType
        val extension = getExtensionFromMimeType(mimeType)
        
        // Get dimensions for images/videos
        var width: Int? = null
        var height: Int? = null
        
        if (mimeType.startsWith("image/")) {
            try {
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, options)
                }
                width = options.outWidth
                height = options.outHeight
            } catch (e: Exception) {
                Log.e("MediaUpload", "Failed to get image dimensions", e)
            }
        }
        
        return FileInfo(name, size, mimeType, extension, width, height)
    }
    
    /**
     * Get file extension from MIME type
     */
    private fun getExtensionFromMimeType(mimeType: String): String {
        return when {
            mimeType.startsWith("image/") -> mimeType.substringAfter("image/")
            mimeType.startsWith("video/") -> mimeType.substringAfter("video/")
            mimeType.startsWith("audio/") -> mimeType.substringAfter("audio/")
            mimeType == "application/pdf" -> "pdf"
            mimeType.contains("document") -> "doc"
            else -> "bin"
        }
    }
    
    /**
     * Compress image before upload
     */
    suspend fun compressImage(
        context: Context,
        imageUri: Uri,
        quality: Int = 80
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            
            // Create temp file
            val tempFile = File(context.cacheDir, "compressed_${UUID.randomUUID()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            }
            
            Result.success(Uri.fromFile(tempFile))
        } catch (e: Exception) {
            Log.e("MediaUpload", "Compression failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Generate thumbnail for video
     */
    suspend fun generateVideoThumbnail(
        context: Context,
        videoUri: Uri
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            val bitmap = retriever.getFrameAtTime(0)
            retriever.release()
            
            if (bitmap == null) {
                return@withContext Result.failure(Exception("Failed to generate thumbnail"))
            }
            
            // Save thumbnail
            val tempFile = File(context.cacheDir, "thumb_${UUID.randomUUID()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            }
            
            Result.success(Uri.fromFile(tempFile))
        } catch (e: Exception) {
            Log.e("MediaUpload", "Thumbnail generation failed", e)
            Result.failure(e)
        }
    }
}

/**
 * Result of media upload
 */
data class MediaUploadResult(
    val url: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null
)

/**
 * File information
 */
private data class FileInfo(
    val name: String,
    val size: Long,
    val mimeType: String,
    val extension: String,
    val width: Int? = null,
    val height: Int? = null
)
