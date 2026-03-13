package com.loopchat.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension to create DataStore
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

/**
 * Local persistence for user settings using Android DataStore.
 * Settings are stored locally and synced with Supabase when online.
 */
class SettingsDataStore(private val context: Context) {
    
    companion object {
        // Appearance
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        
        // Notifications
        private val MESSAGE_NOTIFICATIONS = booleanPreferencesKey("message_notifications")
        private val CALL_NOTIFICATIONS = booleanPreferencesKey("call_notifications")
        private val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        
        // Privacy
        private val LAST_SEEN_VISIBILITY = stringPreferencesKey("last_seen_visibility")
        private val READ_RECEIPTS = booleanPreferencesKey("read_receipts")
        
        // Sync status
        private val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
    }
    
    private val dataStore = context.settingsDataStore
    
    // Dark Mode
    val darkMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: false
    }
    
    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DARK_MODE] = enabled
        }
    }
    
    // Message Notifications
    val messageNotifications: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[MESSAGE_NOTIFICATIONS] ?: true
    }
    
    suspend fun setMessageNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[MESSAGE_NOTIFICATIONS] = enabled
        }
    }
    
    // Call Notifications
    val callNotifications: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[CALL_NOTIFICATIONS] ?: true
    }
    
    suspend fun setCallNotifications(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[CALL_NOTIFICATIONS] = enabled
        }
    }
    
    // Vibration
    val vibrationEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[VIBRATION_ENABLED] ?: true
    }
    
    suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[VIBRATION_ENABLED] = enabled
        }
    }
    
    // Last Seen Visibility
    val lastSeenVisibility: Flow<String> = dataStore.data.map { prefs ->
        prefs[LAST_SEEN_VISIBILITY] ?: "everyone"
    }
    
    suspend fun setLastSeenVisibility(visibility: String) {
        dataStore.edit { prefs ->
            prefs[LAST_SEEN_VISIBILITY] = visibility
        }
    }
    
    // Read Receipts
    val readReceipts: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[READ_RECEIPTS] ?: true
    }
    
    suspend fun setReadReceipts(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[READ_RECEIPTS] = enabled
        }
    }
    
    // Get all settings as a map for syncing
    val allSettings: Flow<Map<String, Any>> = dataStore.data.map { prefs ->
        mapOf(
            "dark_mode" to (prefs[DARK_MODE] ?: false),
            "message_notifications" to (prefs[MESSAGE_NOTIFICATIONS] ?: true),
            "call_notifications" to (prefs[CALL_NOTIFICATIONS] ?: true),
            "vibration_enabled" to (prefs[VIBRATION_ENABLED] ?: true),
            "last_seen_visibility" to (prefs[LAST_SEEN_VISIBILITY] ?: "everyone"),
            "read_receipts" to (prefs[READ_RECEIPTS] ?: true)
        )
    }
    
    // Clear all settings (for logout)
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
