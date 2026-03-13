package com.loopchat.app.data

import android.content.Context
import android.util.Log
import com.loopchat.app.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "SettingsRepository"

/**
 * Repository for managing user settings with local persistence and cloud sync.
 */
class SettingsRepository(private val context: Context) {
    
    private val dataStore = SettingsDataStore(context)
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
    }
    
    // Expose DataStore flows for UI
    val darkMode = dataStore.darkMode
    val messageNotifications = dataStore.messageNotifications
    val callNotifications = dataStore.callNotifications
    val vibrationEnabled = dataStore.vibrationEnabled
    val lastSeenVisibility = dataStore.lastSeenVisibility
    val readReceipts = dataStore.readReceipts
    
    // Setting update methods (save locally and sync to cloud)
    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.setDarkMode(enabled)
        syncSettingToCloud("dark_mode", enabled)
    }
    
    suspend fun setMessageNotifications(enabled: Boolean) {
        dataStore.setMessageNotifications(enabled)
        syncSettingToCloud("message_notifications", enabled)
    }
    
    suspend fun setCallNotifications(enabled: Boolean) {
        dataStore.setCallNotifications(enabled)
        syncSettingToCloud("call_notifications", enabled)
    }
    
    suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.setVibrationEnabled(enabled)
        syncSettingToCloud("vibration_enabled", enabled)
    }
    
    suspend fun setLastSeenVisibility(visibility: String) {
        dataStore.setLastSeenVisibility(visibility)
        syncSettingToCloud("last_seen_visibility", visibility)
    }
    
    suspend fun setReadReceipts(enabled: Boolean) {
        dataStore.setReadReceipts(enabled)
        syncSettingToCloud("read_receipts", enabled)
    }
    
    /**
     * Sync a single setting to cloud
     */
    private suspend fun syncSettingToCloud(key: String, value: Any) {
        val accessToken = SupabaseClient.getAccessToken() ?: return
        val userId = SupabaseClient.currentUserId ?: return
        
        try {
            // Try to upsert the setting
            val response = httpClient.post("${BuildConfig.SUPABASE_URL}/rest/v1/user_settings") {
                contentType(ContentType.Application.Json)
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates")
                setBody(mapOf(
                    "user_id" to userId,
                    key to value
                ))
            }
            
            if (response.status.isSuccess()) {
                Log.d(TAG, "Setting $key synced to cloud")
            } else {
                Log.w(TAG, "Failed to sync $key: ${response.status}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing $key to cloud: ${e.message}")
            // Settings are still saved locally, will sync later
        }
    }
    
    /**
     * Load settings from cloud and update local store
     */
    suspend fun loadFromCloud() {
        val accessToken = SupabaseClient.getAccessToken() ?: return
        val userId = SupabaseClient.currentUserId ?: return
        
        try {
            val response = httpClient.get("${BuildConfig.SUPABASE_URL}/rest/v1/user_settings") {
                parameter("user_id", "eq.$userId")
                parameter("select", "*")
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val settings: List<UserSettings> = response.body()
                if (settings.isNotEmpty()) {
                    val cloudSettings = settings.first()
                    
                    // Update local store with cloud values
                    dataStore.setDarkMode(cloudSettings.darkMode)
                    dataStore.setMessageNotifications(cloudSettings.messageNotifications)
                    dataStore.setCallNotifications(cloudSettings.callNotifications)
                    dataStore.setVibrationEnabled(cloudSettings.vibrationEnabled)
                    dataStore.setLastSeenVisibility(cloudSettings.lastSeenVisibility)
                    dataStore.setReadReceipts(cloudSettings.readReceipts)
                    
                    Log.d(TAG, "Settings loaded from cloud")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading settings from cloud: ${e.message}")
        }
    }
    
    /**
     * Sync all local settings to cloud
     */
    suspend fun syncAllToCloud() {
        val accessToken = SupabaseClient.getAccessToken() ?: return
        val userId = SupabaseClient.currentUserId ?: return
        val settings = dataStore.allSettings.first()
        
        try {
            val response = httpClient.post("${BuildConfig.SUPABASE_URL}/rest/v1/user_settings") {
                contentType(ContentType.Application.Json)
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates")
                setBody(settings + ("user_id" to userId))
            }
            
            if (response.status.isSuccess()) {
                Log.d(TAG, "All settings synced to cloud")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing all settings: ${e.message}")
        }
    }
    
    /**
     * Clear all local settings (for logout)
     */
    suspend fun clearLocalSettings() {
        dataStore.clearAll()
        Log.d(TAG, "Local settings cleared")
    }
    
    /**
     * Perform logout - clear session and settings
     */
    suspend fun logout(): Boolean {
        return try {
            // Clear local settings
            clearLocalSettings()
            
            // Sign out from Supabase
            SupabaseClient.signOut(context)
            
            Log.d(TAG, "Logout successful")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Logout error: ${e.message}")
            false
        }
    }
}

@Serializable
data class UserSettings(
    val id: String = "",
    @SerialName("user_id")
    val userId: String = "",
    @SerialName("dark_mode")
    val darkMode: Boolean = false,
    @SerialName("message_notifications")
    val messageNotifications: Boolean = true,
    @SerialName("call_notifications")
    val callNotifications: Boolean = true,
    @SerialName("vibration_enabled")
    val vibrationEnabled: Boolean = true,
    @SerialName("last_seen_visibility")
    val lastSeenVisibility: String = "everyone",
    @SerialName("read_receipts")
    val readReceipts: Boolean = true
)
