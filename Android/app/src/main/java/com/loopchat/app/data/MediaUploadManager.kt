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
import java.net.URLConnection
import android.webkit.MimeTypeMap
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
            httpClient = httpClient,
            forcedMimeType = "image/jpeg"
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
        httpClient: HttpClient,
        forcedMimeType: String? = null
    ): Result<MediaUploadResult> = withContext(Dispatchers.IO) {
        try {
            val accessToken = SupabaseClient.getAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))
            
            // Get file info
            val fileInfo = getFileInfo(context, mediaUri)
            // Use forced MIME type if provided (e.g. after compression we know it's image/jpeg)
            val finalMimeType = forcedMimeType ?: fileInfo.mimeType
            val finalExtension = if (forcedMimeType != null) getExtensionFromMimeType(finalMimeType) else fileInfo.extension
            val fileName = "${UUID.randomUUID()}.$finalExtension"
            val filePath = if (folder.isNotEmpty()) "$folder/$fileName" else fileName
            
            Log.d("MediaUpload", "Uploading: mime=$finalMimeType, ext=$finalExtension, path=$filePath")
            
            // Read file bytes — for file:// URIs, read directly from the file
            val fileBytes = if (mediaUri.scheme == "file") {
                java.io.File(mediaUri.path!!).readBytes()
            } else {
                context.contentResolver.openInputStream(mediaUri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure(Exception("Failed to read file"))
            }
            
            // Upload to Supabase Storage
            val uploadUrl = "$SUPABASE_URL/storage/v1/object/$bucket/$filePath"
            
            val response = httpClient.post(uploadUrl) {
                header("apikey", SUPABASE_KEY)
                header("Authorization", "Bearer $accessToken")
                setBody(fileBytes)
                contentType(ContentType.parse(finalMimeType))
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
        var name = "file"
        var size = 0L
        var mimeType = "application/octet-stream"
        
        // For file:// URIs, read info directly from the File object
        if (uri.scheme == "file") {
            val file = java.io.File(uri.path!!)
            name = file.name   // e.g. "compressed_abc123.jpg"
            size = file.length()
            Log.d("MediaUpload", "file:// URI detected, name=$name, size=$size")
        } else {
            // For content:// URIs, use ContentResolver
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (nameIndex >= 0) name = it.getString(nameIndex) ?: "file"
                    if (sizeIndex >= 0) size = it.getLong(sizeIndex)
                }
            }
        }
        
        // Try content resolver first
        var resolvedType = context.contentResolver.getType(uri)
        
        // Fallback 1: MimeTypeMap using file extension from name
        if (resolvedType == null || resolvedType == "application/octet-stream") {
            val fileExtension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(name.replace(" ", "%20"))
            if (!fileExtension.isNullOrEmpty()) {
                val mappedType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase())
                if (mappedType != null) {
                    resolvedType = mappedType
                }
            }
        }
        
        // Fallback 2: URLConnection guess from name
        if (resolvedType == null || resolvedType == "application/octet-stream") {
            val guessedType = URLConnection.guessContentTypeFromName(name)
            if (guessedType != null) {
                resolvedType = guessedType
            }
        }
        
        // Final fallback if all fails for an image/video/etc
        if (resolvedType == null || resolvedType == "application/octet-stream") {
            if (name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true)) resolvedType = "image/jpeg"
            else if (name.endsWith(".png", ignoreCase = true)) resolvedType = "image/png"
            else if (name.endsWith(".gif", ignoreCase = true)) resolvedType = "image/gif"
            else if (name.endsWith(".mp4", ignoreCase = true)) resolvedType = "video/mp4"
            else if (name.endsWith(".pdf", ignoreCase = true)) resolvedType = "application/pdf"
        }
        
        mimeType = resolvedType ?: "application/octet-stream"
        
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
     * Compress image before upload (optimized)
     */
    suspend fun compressImage(
        context: Context,
        imageUri: Uri,
        quality: Int = 80,
        maxWidth: Int = 1920,
        maxHeight: Int = 1920
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            // 1. Decode bounds
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(imageUri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, options)
            }
            
            // 2. Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false
            
            // 3. Decode scaled bitmap
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext Result.failure(Exception("Failed to decode image"))
            
            // 4. Compress to file
            val tempFile = File(context.cacheDir, "compressed_${UUID.randomUUID()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            }
            
            bitmap.recycle() // Free memory
            
            Result.success(Uri.fromFile(tempFile))
        } catch (e: Exception) {
            Log.e("MediaUpload", "Compression failed", e)
            Result.failure(e)
        }
    }
    
    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
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
