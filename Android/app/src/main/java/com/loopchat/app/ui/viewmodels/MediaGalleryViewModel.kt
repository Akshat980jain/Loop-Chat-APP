package com.loopchat.app.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.models.Message
import com.loopchat.app.data.MessagingFeaturesRepository
import com.loopchat.app.data.SupabaseClient
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MediaGalleryViewModel : ViewModel() {
    var mediaItems by mutableStateOf<List<Message>>(emptyList())
        private set
    var docItems by mutableStateOf<List<Message>>(emptyList())
        private set
    var linkItems by mutableStateOf<List<Message>>(emptyList())
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    fun loadGallery(conversationId: String) {
        if (conversationId.isBlank()) return
        
        viewModelScope.launch {
            isLoading = true
            try {
                val httpClient = SupabaseClient.httpClient

                val mediaRes = MessagingFeaturesRepository.getMediaGallery(httpClient, conversationId, "media")
                if (mediaRes.isSuccess) mediaItems = mediaRes.getOrDefault(emptyList())
                
                val docsRes = MessagingFeaturesRepository.getMediaGallery(httpClient, conversationId, "docs")
                if (docsRes.isSuccess) docItems = docsRes.getOrDefault(emptyList())
                
                val linksRes = MessagingFeaturesRepository.getMediaGallery(httpClient, conversationId, "links")
                if (linksRes.isSuccess) linkItems = linksRes.getOrDefault(emptyList())
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
    
    fun formatDate(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val instant = java.time.Instant.parse(isoString)
            val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            ""
        }
    }
    
    fun extractLinks(text: String): List<String> {
        val urlRegex = "(?i)\\b(?:(?:https?|ftp)://)(?:\\S+(?::\\S*)?@)?(?:(?!(?:10|127)(?:\\.\\d{1,3}){3})(?!(?:169\\.254|192\\.168)(?:\\.\\d{1,3}){2})(?!172\\.(?:1[6-9]|2\\d|3[0-1])(?:\\.\\d{1,3}){2})(?:[1-9]\\d?|1\\d\\d|2[01]\\d|22[0-3])(?:\\.(?:1?\\d{1,2}|2[0-4]\\d|25[0-5])){2}(?:\\.(?:[1-9]\\d?|1\\d\\d|2[0-4]\\d|25[0-4]))|(?:(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)(?:\\.(?:[a-z\\u00a1-\\uffff0-9]-*)*[a-z\\u00a1-\\uffff0-9]+)*(?:\\.(?:[a-z\\u00a1-\\uffff]{2,}))\\.?)(?::\\d{2,5})?(?:[/?#]\\S*)?\\b".toRegex()
        return urlRegex.findAll(text).map { it.value }.toList()
    }
}
