package com.loopchat.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.loopchat.app.MainActivity
import android.content.Intent
import android.util.Log
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.loopchat.app.services.CallService
import com.loopchat.app.data.IncomingCallManager
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.BiometricCredentialStore
import com.loopchat.app.ui.screens.*
import com.loopchat.app.ui.components.*
import com.loopchat.app.ui.viewmodels.*
import com.loopchat.app.ui.theme.Background
import com.loopchat.app.ui.theme.Primary
import com.loopchat.app.data.local.LoopChatDatabase
import com.loopchat.app.data.GroupRepository
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
    object Call : Screen("call/{calleeId}/{callType}/{isIncoming}?callId={callId}&roomUrl={roomUrl}&calleeToken={calleeToken}&calleeName={calleeName}&isGroupCall={isGroupCall}&groupId={groupId}") {
        fun createRoute(
            calleeId: String, 
            callType: String, 
            isIncoming: Boolean = false,
            callId: String? = null,
            roomUrl: String? = null,
            calleeToken: String? = null,
            calleeName: String? = null,
            isGroupCall: Boolean = false,
            groupId: String? = null
        ): String {
            val base = "call/$calleeId/$callType/$isIncoming"
            val params = mutableListOf<String>()
            if (callId != null) params.add("callId=$callId")
            if (roomUrl != null) params.add("roomUrl=${java.net.URLEncoder.encode(roomUrl, "UTF-8")}")
            if (calleeToken != null) params.add("calleeToken=$calleeToken")
            if (calleeName != null) params.add("calleeName=${java.net.URLEncoder.encode(calleeName, "UTF-8")}")
            if (isGroupCall) params.add("isGroupCall=true")
            if (groupId != null) params.add("groupId=$groupId")
            return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
        }
    }
    object CallHistory : Screen("call_history/{contactUserId}/{contactName}") {
        fun createRoute(contactUserId: String, contactName: String) = 
            "call_history/$contactUserId/${java.net.URLEncoder.encode(contactName, "UTF-8")}"
    }
    object IncomingCall : Screen("incoming_call")
    object Profile : Screen("profile")
    object UserProfile : Screen("user_profile/{userId}") {
        fun createRoute(userId: String) = "user_profile/$userId"
    }
    object PrivacySettings : Screen("privacy_settings")
    object SecuritySettings : Screen("security_settings")
    object BlockedUsers : Screen("blocked_users")
    object DeviceManagement : Screen("device_management")
    object ActiveSessions : Screen("active_sessions")
    object BiometricSetup : Screen("biometric_setup")
    object Search : Screen("search")
    object Notifications : Screen("notifications")
    object NotificationHistory : Screen("notification_history")
    object Status : Screen("status")
    object MediaGallery : Screen("media_gallery/{conversationId}") {
        fun createRoute(conversationId: String) = "media_gallery/$conversationId"
    }
    object QRScan : Screen("qr_scan")
    object BlockedContacts : Screen("blocked_contacts")
    object GroupCreation : Screen("group_creation")
    object GroupInfo : Screen("group_info/{groupId}") {
        fun createRoute(groupId: String) = "group_info/$groupId"
    }
    object AddGroupMember : Screen("add_group_member/{groupId}") {
        fun createRoute(groupId: String) = "add_group_member/$groupId"
    }
    object StarredMessages : Screen("starred_messages")
}

@Composable
fun LoopChatNavigation(
    initialCallData: MainActivity.CallNavigationData? = null,
    onCallDataConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("loop_chat_security", android.content.Context.MODE_PRIVATE)
    
    var isSessionChecked by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var isAppLocked by remember { mutableStateOf(false) }
    
    val incomingCallData by IncomingCallManager.incomingCall.collectAsState()
    val scope = rememberCoroutineScope()

    // 1. Session Initialization
    LaunchedEffect(Unit) {
        SupabaseClient.initialize(context)
        isAuthenticated = SupabaseClient.isAuthenticated
        isSessionChecked = true
        
        if (isAuthenticated) {
            IncomingCallManager.startListening(context)
            // Initial lock check from preferences
            if (prefs.getBoolean("biometric_lock_enabled", false)) {
                isAppLocked = true
            }
        }
    }

    // 2. Real-time Preference Sync
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "biometric_lock_enabled") {
                val enabled = p.getBoolean(key, false)
                if (!enabled) isAppLocked = false
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // 3. Background Re-lock
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (prefs.getBoolean("biometric_lock_enabled", false) && SupabaseClient.isAuthenticated) {
                    isAppLocked = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 4. Call Handling
    LaunchedEffect(initialCallData, isAuthenticated) {
        if (initialCallData != null && isAuthenticated) {
            navController.navigate(Screen.Call.createRoute(
                calleeId = initialCallData.callerId,
                callType = initialCallData.callType,
                isIncoming = initialCallData.isIncoming,
                callId = initialCallData.callId,
                roomUrl = initialCallData.roomUrl,
                calleeToken = initialCallData.calleeToken,
                calleeName = initialCallData.callerName
            )) {
                if (navController.currentDestination?.route != Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                }
            }
            onCallDataConsumed()
        }
    }

    LaunchedEffect(incomingCallData) {
        incomingCallData?.let {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != Screen.IncomingCall.route && currentRoute != Screen.Call.route) {
                navController.navigate(Screen.IncomingCall.route)
            }
        }
    }

    if (!isSessionChecked) {
        Box(modifier = Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    val startDestination = if (isAuthenticated) Screen.Home.route else Screen.Auth.route

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(Screen.Auth.route) {
                AuthScreen(onAuthSuccess = {
                    isAuthenticated = true
                    navController.navigate(Screen.Home.route) { popUpTo(Screen.Auth.route) { inclusive = true } }
                    IncomingCallManager.startListening(context)
                })
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onConversationClick = { navController.navigate(Screen.Chat.createRoute(it)) },
                    onProfileClick = { navController.navigate(Screen.Profile.route) },
                    onCallHistoryClick = { id, name -> navController.navigate(Screen.CallHistory.createRoute(id, name)) },
                    onNavigate = { navController.navigate(it) },
                    onLogout = {
                        scope.launch {
                            SupabaseClient.signOut(context)
                            IncomingCallManager.stopListening()
                            BiometricCredentialStore.clearCredentials(context)
                            prefs.edit().clear().apply()
                            isAuthenticated = false
                            isAppLocked = false
                            navController.navigate(Screen.Auth.route) { popUpTo(0) { inclusive = true } }
                        }
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onBackClick = { navController.popBackStack() },
                    onLogout = {
                        scope.launch {
                            SupabaseClient.signOut(context)
                            IncomingCallManager.stopListening()
                            BiometricCredentialStore.clearCredentials(context)
                            prefs.edit().clear().apply()
                            isAuthenticated = false
                            isAppLocked = false
                            navController.navigate(Screen.Auth.route) { popUpTo(0) { inclusive = true } }
                        }
                    }
                )
            }

            composable(
                route = Screen.UserProfile.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: ""
                UserProfileScreen(
                    userId = userId, 
                    onBackClick = { navController.popBackStack() },
                    onMessageClick = { targetUserId, _ ->
                        scope.launch {
                            val result = com.loopchat.app.data.SupabaseRepository.createOrGetConversation(targetUserId)
                            result.onSuccess { conversationId ->
                                navController.navigate(Screen.Chat.createRoute(conversationId))
                            }
                        }
                    }
                ) 
            }

            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
                EnhancedChatScreen(
                    conversationId = conversationId,
                    onBackClick = { navController.popBackStack() },
                    onCallClick = { id, type, isGroup, gid -> navController.navigate(Screen.Call.createRoute(id, type, false, null, null, null, null, isGroup, gid)) },
                    onNavigateToProfile = { uid -> navController.navigate(Screen.UserProfile.createRoute(uid)) },
                    onNavigateToGroupInfo = { gid -> navController.navigate(Screen.GroupInfo.createRoute(gid)) },
                    onNavigateToMediaGallery = { cid -> navController.navigate(Screen.MediaGallery.createRoute(cid)) }
                )
            }

            composable(Screen.PrivacySettings.route) {
                val vm: SettingsViewModel = viewModel()
                LaunchedEffect(Unit) { vm.loadAllSettings() }
                PrivacySettingsScreen(settings = vm.privacySettings, onUpdateSettings = { vm.updatePrivacySettings(it) }, onBackClick = { navController.popBackStack() })
            }

            composable(Screen.SecuritySettings.route) {
                val vm: SettingsViewModel = viewModel()
                LaunchedEffect(Unit) { vm.loadAllSettings() }
                SecuritySettingsScreen(
                    settings = vm.securitySettings,
                    errorMessage = vm.errorMessage,
                    isLoading = vm.isLoading,
                    onEnableTwoStep = { pin, email -> vm.enableTwoStep(pin, email) },
                    onDisableTwoStep = { vm.disableTwoStep() },
                    onToggleSecurityNotifications = { vm.toggleSecurityNotifications(it) },
                    onBiometricSetupClick = { navController.navigate(Screen.BiometricSetup.route) },
                    onActiveSessionsClick = { navController.navigate(Screen.ActiveSessions.route) },
                    onClearError = { vm.clearError() },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.BiometricSetup.route) {
                val vm: SettingsViewModel = viewModel()
                LaunchedEffect(Unit) { vm.loadAllSettings() }
                BiometricSetupScreen(
                    settings = vm.securitySettings,
                    onEnableLogin = { vm.enableBiometricLogin(it) },
                    onDisableLogin = { vm.disableBiometricLogin() },
                    onEnableLock = { vm.enableBiometricLock(it) },
                    onDisableLock = { vm.disableBiometricLock(); isAppLocked = false },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.GroupCreation.route) {
                GroupCreationScreen(onNavigateBack = { navController.popBackStack() }, onGroupCreated = { navController.navigate(Screen.Chat.createRoute(it)) })
            }

            composable(
                route = Screen.GroupInfo.route,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { backStackEntry ->
                val gid = backStackEntry.arguments?.getString("groupId") ?: ""
                val groupRepository = GroupRepository(LoopChatDatabase.getDatabase(context))
                val vm: GroupInfoViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return GroupInfoViewModel(groupRepository) as T
                        }
                    }
                )
                GroupInfoScreen(
                    groupId = gid,
                    viewModel = vm,
                    onBackClick = { navController.popBackStack() },
                    onAddMemberClick = { navController.navigate(Screen.AddGroupMember.createRoute(gid)) },
                    onMemberClick = { userId -> navController.navigate(Screen.UserProfile.createRoute(userId)) }
                )
            }

            composable(
                route = Screen.AddGroupMember.route,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { backStackEntry ->
                val gid = backStackEntry.arguments?.getString("groupId") ?: ""
                val groupRepository = GroupRepository(LoopChatDatabase.getDatabase(context))
                val app = context.applicationContext as android.app.Application
                val vm: AddGroupMemberViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return AddGroupMemberViewModel(app, groupRepository) as T
                        }
                    }
                )
                AddGroupMemberScreen(groupId = gid, viewModel = vm, onBackClick = { navController.popBackStack() }, onSuccess = { navController.popBackStack() })
            }
            
            composable(Screen.Status.route) { StatusScreen(onBackClick = { navController.popBackStack() }) }
            composable(Screen.Search.route) { SearchScreen(onBackClick = { navController.popBackStack() }) }
            composable(Screen.Notifications.route) { NotificationsScreen(onBackClick = { navController.popBackStack() }) }
            composable(Screen.StarredMessages.route) { StarredMessagesScreen(onBackClick = { navController.popBackStack() }) }
            composable(Screen.BlockedContacts.route) { BlockedContactsScreen(onBackClick = { navController.popBackStack() }) }
            composable(Screen.NotificationHistory.route) {
                NotificationHistoryScreen(onBackClick = { navController.popBackStack() }, onNavigateAction = { navController.navigate(Screen.Chat.createRoute(it)) })
            }
            composable(
                route = Screen.MediaGallery.route,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) { backStackEntry -> 
                val cid = backStackEntry.arguments?.getString("conversationId") ?: ""
                MediaGalleryScreen(conversationId = cid, onBackClick = { navController.popBackStack() }) 
            }
            composable(Screen.QRScan.route) {
                QRScanScreen(onBackClick = { navController.popBackStack() }, onUserScanned = { targetUserId ->
                    navController.popBackStack()
                    // Navigate to scanned user's profile
                    navController.navigate(Screen.UserProfile.createRoute(targetUserId))
                })
            }
        }

        if (isAppLocked) {
            BiometricLockScreen(
                onUnlocked = { isAppLocked = false },
                onSignOut = {
                    scope.launch {
                        SupabaseClient.signOut(context)
                        BiometricCredentialStore.clearCredentials(context)
                        prefs.edit().clear().apply()
                        isAuthenticated = false
                        isAppLocked = false
                        navController.navigate(Screen.Auth.route) { popUpTo(0) { inclusive = true } }
                    }
                }
            )
        }
    }
}
