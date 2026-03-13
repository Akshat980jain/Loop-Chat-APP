package com.loopchat.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.loopchat.app.MainActivity
import com.loopchat.app.data.IncomingCallManager
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.ui.screens.AuthScreen
import com.loopchat.app.ui.screens.CallHistoryScreen
import com.loopchat.app.ui.screens.CallScreen
import com.loopchat.app.ui.screens.EnhancedChatScreen
import com.loopchat.app.ui.screens.HomeScreen
import com.loopchat.app.ui.screens.IncomingCallScreen
import com.loopchat.app.ui.screens.ProfileScreen
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loopchat.app.ui.components.PrivacySettingsScreen
import com.loopchat.app.ui.components.SecuritySettingsScreen
import com.loopchat.app.ui.components.BlockedUsersScreen
import com.loopchat.app.ui.components.DeviceManagementScreen
import com.loopchat.app.ui.components.ActiveSessionsScreen
import com.loopchat.app.ui.viewmodels.SettingsViewModel

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
    object Call : Screen("call/{calleeId}/{callType}/{isIncoming}") {
        fun createRoute(calleeId: String, callType: String, isIncoming: Boolean = false) = 
            "call/$calleeId/$callType/$isIncoming"
    }
    object CallHistory : Screen("call_history/{contactUserId}/{contactName}") {
        fun createRoute(contactUserId: String, contactName: String) = 
            "call_history/$contactUserId/${java.net.URLEncoder.encode(contactName, "UTF-8")}"
    }
    object IncomingCall : Screen("incoming_call")
    object Profile : Screen("profile")
    
    // Phase 2: Privacy & Security Screens
    object PrivacySettings : Screen("privacy_settings")
    object SecuritySettings : Screen("security_settings")
    object BlockedUsers : Screen("blocked_users")
    object DeviceManagement : Screen("device_management")
    object ActiveSessions : Screen("active_sessions")
}

@Composable
fun LoopChatNavigation(
    initialCallData: MainActivity.CallNavigationData? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Track if we've checked for existing session
    var isSessionChecked by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(false) }
    
    // Observe incoming calls
    val incomingCallData by IncomingCallManager.incomingCall.collectAsState()
    
    // Check for existing session on startup
    LaunchedEffect(Unit) {
        SupabaseClient.initialize(context)
        isAuthenticated = SupabaseClient.isAuthenticated
        isSessionChecked = true
        
        // If already authenticated, navigate to Home and start listening for calls
        if (isAuthenticated) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Auth.route) { inclusive = true }
            }
            // Start listening for incoming calls
            IncomingCallManager.startListening(context)
        }
    }
    
    // Handle initial call data passed from notification (via CallService)
    LaunchedEffect(initialCallData, isSessionChecked, isAuthenticated) {
        if (initialCallData != null && isSessionChecked && isAuthenticated) {
            // Navigate directly to call screen
            navController.navigate(
                Screen.Call.createRoute(
                    calleeId = initialCallData.callerId,
                    callType = initialCallData.callType,
                    isIncoming = initialCallData.isIncoming
                )
            )
        }
    }
    
    // Navigate to incoming call screen when there's an incoming call
    LaunchedEffect(incomingCallData) {
        incomingCallData?.let { callData ->
            // Only navigate if not already on incoming call or call screen
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != Screen.IncomingCall.route && 
                currentRoute != Screen.Call.route) {
                navController.navigate(Screen.IncomingCall.route)
            }
        }
    }
    
    // Determine start destination based on session state
    val startDestination = Screen.Auth.route
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                    // Start listening for incoming calls after auth
                    IncomingCallManager.startListening(context)
                }
            )
        }
        
        composable(Screen.Home.route) {
            HomeScreen(
                onConversationClick = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                },
                onCallHistoryClick = { contactUserId, contactName ->
                    navController.navigate(Screen.CallHistory.createRoute(contactUserId, contactName))
                },
                onNavigate = { route ->
                    navController.navigate(route)
                },
                onLogout = {
                    // Stop listening for calls and navigate to auth
                    IncomingCallManager.stopListening()
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
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
                onCallClick = { calleeId, callType ->
                    navController.navigate(Screen.Call.createRoute(calleeId, callType))
                }
            )
        }
        
        composable(
            route = Screen.Call.route,
            arguments = listOf(
                navArgument("calleeId") { type = NavType.StringType },
                navArgument("callType") { type = NavType.StringType },
                navArgument("isIncoming") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val calleeId = backStackEntry.arguments?.getString("calleeId") ?: ""
            val callType = backStackEntry.arguments?.getString("callType") ?: "audio"
            val isIncoming = backStackEntry.arguments?.getBoolean("isIncoming") ?: false
            
            val useInitialData = isIncoming && initialCallData?.callerId == calleeId
            
            CallScreen(
                calleeId = calleeId,
                callType = callType,
                isIncoming = isIncoming,
                initialCallId = if (useInitialData) initialCallData?.callId else null,
                initialRoomUrl = if (useInitialData) initialCallData?.roomUrl else null,
                initialCalleeToken = if (useInitialData) initialCallData?.calleeToken else null,
                onEndCall = { navController.popBackStack() }
            )
        }
        
        // Call history screen for a specific contact
        composable(
            route = Screen.CallHistory.route,
            arguments = listOf(
                navArgument("contactUserId") { type = NavType.StringType },
                navArgument("contactName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val contactUserId = backStackEntry.arguments?.getString("contactUserId") ?: ""
            val contactName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("contactName") ?: "Unknown",
                "UTF-8"
            )
            CallHistoryScreen(
                contactUserId = contactUserId,
                contactName = contactName,
                onBackClick = { navController.popBackStack() },
                onMessageClick = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                },
                onCallClick = { calleeId, callType ->
                    navController.navigate(Screen.Call.createRoute(calleeId, callType))
                }
            )
        }
        
        // Incoming call screen
        composable(Screen.IncomingCall.route) {
            val callData = incomingCallData
            if (callData != null) {
                IncomingCallScreen(
                    callerName = callData.callerProfile?.fullName 
                        ?: callData.callerProfile?.username 
                        ?: "Unknown Caller",
                    callerAvatar = callData.callerProfile?.avatarUrl,
                    callType = callData.call.callType,
                    onAccept = {
                        // Navigate to call screen with caller as the "callee" for the screen
                        // isIncoming = true since this is an accepted incoming call
                        navController.navigate(
                            Screen.Call.createRoute(
                                calleeId = callData.call.callerId, 
                                callType = callData.call.callType,
                                isIncoming = true
                            )
                        ) {
                            popUpTo(Screen.IncomingCall.route) { inclusive = true }
                        }
                    },
                    onReject = {
                        navController.popBackStack()
                    }
                )
            } else {
                // No incoming call, go back
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
        
        composable(Screen.PrivacySettings.route) {
            val viewModel: SettingsViewModel = viewModel()
            // Load settings on enter
            LaunchedEffect(Unit) { viewModel.loadAllSettings() }
            
            PrivacySettingsScreen(
                settings = viewModel.privacySettings,
                onUpdateSettings = { viewModel.updatePrivacySettings(it) },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.SecuritySettings.route) {
            val viewModel: SettingsViewModel = viewModel()
            // Load settings on enter
            LaunchedEffect(Unit) { viewModel.loadAllSettings() }
            
            SecuritySettingsScreen(
                settings = viewModel.securitySettings,
                onEnableTwoStep = { pin, email -> viewModel.enableTwoStep(pin, email) },
                onDisableTwoStep = { viewModel.disableTwoStep() },
                onEnableBiometric = { viewModel.enableBiometric() },
                onDisableBiometric = { viewModel.disableBiometric() },
                onActiveSessionsClick = { navController.navigate(Screen.ActiveSessions.route) },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.BlockedUsers.route) {
            val viewModel: SettingsViewModel = viewModel()
            // Load settings on enter
            LaunchedEffect(Unit) { viewModel.loadAllSettings() }
            
            BlockedUsersScreen(
                blockedUsers = viewModel.blockedUsers,
                onUnblockUser = { viewModel.unblockUser(it) },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.DeviceManagement.route) {
            val viewModel: SettingsViewModel = viewModel()
            // Load settings on enter
            LaunchedEffect(Unit) { viewModel.loadAllSettings() }
            
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "device_id"
            
            DeviceManagementScreen(
                devices = viewModel.devices,
                currentDeviceId = deviceId,
                onRemoveDevice = { viewModel.removeDevice(it) },
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable(Screen.ActiveSessions.route) {
            val viewModel: SettingsViewModel = viewModel()
            // Load sessions on enter
            LaunchedEffect(Unit) { viewModel.loadAllSettings() }
            
            ActiveSessionsScreen(
                sessions = viewModel.sessions,
                isLoading = viewModel.isLoading,
                onRevokeSession = { viewModel.revokeSession(it) },
                onRevokeAllOthers = { viewModel.revokeAllOtherSessions() },
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
