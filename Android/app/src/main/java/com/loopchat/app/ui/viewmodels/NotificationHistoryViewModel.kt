package com.loopchat.app.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loopchat.app.data.AppNotification
import com.loopchat.app.data.NotificationsRepository
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.Instant

class NotificationHistoryViewModel : ViewModel() {
    private val repository = NotificationsRepository()
    
    var notifications by mutableStateOf<List<AppNotification>>(emptyList())
        private set
        
    var isLoading by mutableStateOf(false)
        private set
        
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val result = repository.fetchNotifications()
            if (result.isSuccess) {
                notifications = result.getOrDefault(emptyList())
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Failed to load"
            }
            isLoading = false
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            val result = repository.markAsRead(notificationId)
            if (result.isSuccess) {
                notifications = notifications.map {
                    if (it.id == notificationId) it.copy(isRead = true) else it
                }
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val result = repository.markAllAsRead()
            if (result.isSuccess) {
                notifications = notifications.map { it.copy(isRead = true) }
            }
        }
    }

    fun getRelativeTime(timestamp: String): String {
        return try {
            val past = Instant.parse(timestamp)
            val now = Instant.now()
            val minutes = ChronoUnit.MINUTES.between(past, now)
            val hours = ChronoUnit.HOURS.between(past, now)
            val days = ChronoUnit.DAYS.between(past, now)

            when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "\$minutes m"
                hours < 24 -> "\$hours h"
                days < 7 -> "\$days d"
                else -> {
                    val formatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
                    formatter.format(past)
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
}
