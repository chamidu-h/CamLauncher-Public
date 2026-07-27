package com.camlauncher.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.camlauncher.app.data.RecordingState
import com.camlauncher.app.data.SettingsStore
import com.camlauncher.app.data.TriggerType
import com.camlauncher.app.service.MonitorService
import com.camlauncher.app.service.RecordingService
import com.camlauncher.app.service.ChunkRecoveryHelper
import com.camlauncher.app.ui.screens.HomeScreen
import com.camlauncher.app.ui.screens.OnboardingScreen
import com.camlauncher.app.ui.screens.SettingsScreen
import com.camlauncher.app.ui.screens.GalleryScreen
import com.camlauncher.app.ui.screens.PlayerScreen
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Menu
import com.camlauncher.app.ui.screens.PrivacyScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.layout.padding
import com.camlauncher.app.ui.theme.CamLauncherTheme
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.content.Intent

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore
    
    val intentFlow = MutableStateFlow<Intent?>(null)

    // In-app press detection
    private var lastVolumeDownTime = 0L
    private var lastTriggerTime = 0L
    private var pressCount = 0
    private var activeTriggerType: TriggerType = TriggerType.VOLUME_DOUBLE_PRESS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsStore = SettingsStore(this)

        // Recover any orphaned video chunks left by a previous crash
        lifecycleScope.launch {
            ChunkRecoveryHelper.recoverIfNeeded(this@MainActivity)
        }

        // Sync trigger type for onKeyDown
        // NOTE: Do NOT stop MonitorService here for BUTTON mode.
        // The service callbacks gate based on activeTriggerType.
        // Stopping MonitorService contaminates volume hooks on restart.
        lifecycleScope.launch {
            settingsStore.triggerType.collect {
                activeTriggerType = it

                if (it != TriggerType.BUTTON) {
                    MonitorService.start(this@MainActivity)
                }
            }
        }

        intentFlow.value = intent

        setContent {
            val appTheme by settingsStore.appTheme.collectAsState(initial = com.camlauncher.app.data.AppTheme.SYSTEM)
            CamLauncherTheme(appTheme = appTheme) {
                CamLauncherNavHost(settingsStore = settingsStore)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentFlow.value = intent
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (activeTriggerType != TriggerType.VOLUME_DOUBLE_PRESS && 
                activeTriggerType != TriggerType.VOLUME_TRIPLE_PRESS) {
                return super.onKeyDown(keyCode, event)
            }

            val now = System.currentTimeMillis()
            val cooldown = 2000L // Use a fixed cooldown for foreground

            if (now - lastTriggerTime < cooldown) {
                return super.onKeyDown(keyCode, event)
            }

            val requiredCount = if (activeTriggerType == TriggerType.VOLUME_TRIPLE_PRESS) 3 else 2
            val timeSinceLast = now - lastVolumeDownTime
            lastVolumeDownTime = now

            if (timeSinceLast < GESTURE_WINDOW_MS) {
                pressCount++
                if (pressCount >= requiredCount) {
                    pressCount = 0
                    lastTriggerTime = now
                    if (RecordingService.stateFlow.value == com.camlauncher.app.data.RecordingState.RECORDING) {
                        RecordingService.stopRecording(this)
                    } else {
                        RecordingService.startRecording(this)
                    }
                    return true 
                }
            } else {
                pressCount = 1
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val GESTURE_WINDOW_MS = 300L
    }
}

@Composable
fun CamLauncherNavHost(settingsStore: SettingsStore) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current as? MainActivity

    var onboardingComplete by remember { mutableStateOf<Boolean?>(null) }

    // Check onboarding status
    LaunchedEffect(Unit) {
        onboardingComplete = settingsStore.onboardingComplete.first()
    }

    // Wait until we know onboarding status
    if (onboardingComplete == null) return

    val startDestination = if (onboardingComplete == true) "home" else "onboarding"

    // Launch side-effect for Navigation
    LaunchedEffect(context, onboardingComplete) {
        context?.intentFlow?.collect { currentIntent ->
            if (currentIntent != null) {
                val previewUri = currentIntent.getStringExtra("previewUri")
                val previewType = currentIntent.getStringExtra("previewType")
                if (previewUri != null && previewType != null) {
                    val encodedUri = Uri.encode(previewUri)
                    navController.navigate("gallery?previewUri=$encodedUri&previewType=$previewType") {
                        launchSingleTop = true
                        popUpTo("home") { saveState = true }
                    }
                    // Consume the intent extra to prevent re-navigation
                    currentIntent.removeExtra("previewUri")
                    currentIntent.removeExtra("previewType")
                    context.intentFlow.value = null
                }
            }
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute == "home" || currentRoute?.startsWith("gallery") == true || currentRoute == "privacy") {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "home",
                        onClick = {
                            if (currentRoute != "home") {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentRoute?.startsWith("gallery") == true,
                        onClick = {
                            if (currentRoute?.startsWith("gallery") != true) {
                                navController.navigate("gallery") {
                                    popUpTo("home") { saveState = true }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = "Gallery") },
                        label = { Text("Gallery") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "privacy",
                        onClick = {
                            if (currentRoute != "privacy") {
                                navController.navigate("privacy") {
                                    popUpTo("home") { saveState = true }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.Menu, contentDescription = "Privacy & Policy") },
                        label = { Text("Privacy") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    onComplete = {
                        scope.launch {
                            settingsStore.setOnboardingComplete(true)
                        }
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            composable("home") {
                HomeScreen(
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    }
                )
            }

            composable(
                route = "gallery?previewUri={previewUri}&previewType={previewType}",
                arguments = listOf(
                    androidx.navigation.navArgument("previewUri") { 
                        type = androidx.navigation.NavType.StringType 
                        nullable = true 
                        defaultValue = null
                    },
                    androidx.navigation.navArgument("previewType") { 
                        type = androidx.navigation.NavType.StringType 
                        nullable = true 
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val previewUri = backStackEntry.arguments?.getString("previewUri")
                val previewType = backStackEntry.arguments?.getString("previewType")
                GalleryScreen(
                    previewUriStr = previewUri, 
                    previewType = previewType,
                    onPlayMedia = { uri, type ->
                        val encodedUri = Uri.encode(uri)
                        navController.navigate("player?uri=$encodedUri&type=$type")
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    settingsStore = settingsStore,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = "player?uri={uri}&type={type}",
                arguments = listOf(
                    androidx.navigation.navArgument("uri") { type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("type") { type = androidx.navigation.NavType.StringType }
                )
            ) { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("uri") ?: ""
                val type = backStackEntry.arguments?.getString("type") ?: ""
                PlayerScreen(
                    uriStr = uri,
                    type = type,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("privacy") {
                PrivacyScreen(
                    onBack = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
