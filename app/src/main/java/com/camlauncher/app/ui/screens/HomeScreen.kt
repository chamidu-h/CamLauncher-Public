package com.camlauncher.app.ui.screens

import android.os.PowerManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.camlauncher.app.data.RecordingState
import com.camlauncher.app.data.TriggerType
import com.camlauncher.app.data.ScreenMode
import com.camlauncher.app.service.RecordingService
import com.camlauncher.app.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val recordingState by RecordingService.stateFlow.collectAsState()
    val isRecording = recordingState == RecordingState.RECORDING
    
    val settingsStore = remember { com.camlauncher.app.data.SettingsStore(context) }
    val triggerType by settingsStore.triggerType.collectAsState(initial = com.camlauncher.app.data.TriggerType.VOLUME_DOUBLE_PRESS)
    val displayType by settingsStore.screenMode.collectAsState(initial = com.camlauncher.app.data.ScreenMode.BLACK)
    val recordingMode by settingsStore.recordingMode.collectAsState(initial = com.camlauncher.app.data.RecordingMode.VIDEO)
    val storageLocation by settingsStore.storageLocation.collectAsState(initial = com.camlauncher.app.data.StorageLocation.INTERNAL)
    val customStorageUri by settingsStore.customStorageUri.collectAsState(initial = null)

    // Re-check permissions status every time the screen resumes (user returns from settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var hasAudio by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var hasOverlay by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Show setup dialog
    var showSetupDialog by remember { mutableStateOf(false) }

    // Pulse animation for recording state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val buttonColor by animateColorAsState(
        targetValue = if (isRecording) RecordingActive else Primary,
        animationSpec = tween(300),
        label = "buttonColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
                )
            )
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CamLauncher",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Row {
                val appTheme by settingsStore.appTheme.collectAsState(initial = com.camlauncher.app.data.AppTheme.SYSTEM)
                val scope = rememberCoroutineScope()
                
                IconButton(onClick = {
                    val nextTheme = when (appTheme) {
                        com.camlauncher.app.data.AppTheme.SYSTEM -> com.camlauncher.app.data.AppTheme.LIGHT
                        com.camlauncher.app.data.AppTheme.LIGHT -> com.camlauncher.app.data.AppTheme.DARK
                        com.camlauncher.app.data.AppTheme.DARK -> com.camlauncher.app.data.AppTheme.SYSTEM
                    }
                    scope.launch {
                        settingsStore.setAppTheme(nextTheme)
                    }
                }) {
                    val icon = when (appTheme) {
                        com.camlauncher.app.data.AppTheme.SYSTEM -> Icons.Filled.BrightnessAuto
                        com.camlauncher.app.data.AppTheme.LIGHT -> Icons.Filled.LightMode
                        com.camlauncher.app.data.AppTheme.DARK -> Icons.Filled.DarkMode
                    }
                    Icon(
                        icon,
                        contentDescription = "Toggle Theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status text
            val showWarning = !isAccessibilityEnabled || !hasCamera || !hasAudio || !hasOverlay
            
            if (!showWarning) {
                Text(
                    text = if (isRecording) "Recording..." else "Ready",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isRecording) RecordingActive else Success,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isRecording) {
                        "Trigger again to stop"
                    } else {
                        triggerType.description
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Setup Required",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Warning,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Please complete the setup to enable recording",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Big record button
            Box(contentAlignment = Alignment.Center) {
                // Outer pulse ring (only when recording)
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(RecordingActive.copy(alpha = pulseAlpha))
                    )
                }

                // Button ring
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 4.dp,
                            color = buttonColor.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        buttonColor,
                                        buttonColor.copy(alpha = 0.8f)
                                    )
                                )
                            )
                            .clickable {
                                if (isRecording) RecordingService.stopRecording(context) else RecordingService.startRecording(context)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                            contentDescription = if (isRecording) "Stop" else "Record",
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick tips
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Tips",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val triggerIcon = when(triggerType) {
                        TriggerType.VOLUME_DOUBLE_PRESS, 
                        TriggerType.VOLUME_TRIPLE_PRESS -> Icons.AutoMirrored.Filled.VolumeDown
                        TriggerType.SHAKE -> Icons.Filled.Vibration
                        TriggerType.BUTTON -> Icons.Filled.TouchApp
                    }

                    TipRow(
                        icon = triggerIcon,
                        text = triggerType.description
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 2. Mode-specific display tip
                    if (recordingMode == com.camlauncher.app.data.RecordingMode.VIDEO) {
                        val displayIcon = when(displayType) {
                            ScreenMode.BLACK -> Icons.Filled.ScreenLockPortrait
                            ScreenMode.MINIMAL_HUD -> Icons.Filled.PictureInPicture
                            ScreenMode.NORMAL -> Icons.Filled.Smartphone
                        }
                        val displayText = when(displayType) {
                            ScreenMode.BLACK -> "Recording in background"
                            ScreenMode.MINIMAL_HUD -> "Minimal HUD shows over the screen"
                            ScreenMode.NORMAL -> "Standard recording mode (screen on)"
                        }
                        
                        TipRow(
                            icon = displayIcon,
                            text = displayText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    } else if (recordingMode == com.camlauncher.app.data.RecordingMode.PHOTO_BURST) {
                        TipRow(
                            icon = Icons.Filled.PhotoCamera,
                            text = "Photo Burst mode active"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    } else if (recordingMode == com.camlauncher.app.data.RecordingMode.AUDIO_ONLY) {
                        TipRow(
                            icon = Icons.Filled.Mic,
                            text = "Audio Only mode active"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 3. Storage location tip
                    val storageText = when (storageLocation) {
                        com.camlauncher.app.data.StorageLocation.INTERNAL -> "Recordings saved to Movies/CamLauncher"
                        com.camlauncher.app.data.StorageLocation.SD_CARD -> "Recordings saved to SD Card"
                        com.camlauncher.app.data.StorageLocation.CUSTOM -> {
                            val folderName = customStorageUri?.let { com.camlauncher.app.data.StorageHelper.getFolderName(context, it) } ?: "Custom Folder"
                            "Saved to: $folderName"
                        }
                    }

                    TipRow(
                        icon = Icons.Filled.FolderOpen,
                        text = storageText
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Setup required banner
            if (showWarning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clickable { showSetupDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Warning.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Warning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Setup Required",
                                style = MaterialTheme.typography.titleSmall,
                                color = Warning,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Tap here to allow permissions and complete setup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Show enabled status
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Success.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Success,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Trigger active — ready to record!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Success,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Setup guide dialog
    if (showSetupDialog) {
        SetupInstructionsDialog(
            triggerType = triggerType,
            onDismiss = { showSetupDialog = false }
        )
    }
}

@Composable
private fun SetupInstructionsDialog(
    triggerType: TriggerType,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State for permissions
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var hasAudio by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var hasOverlay by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true) }
    var hasAccessibility by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var hasBatteryExemption by remember { mutableStateOf(com.camlauncher.app.data.OemDeviceHelper.isBatteryOptimized(context)) }
    
    // For notifications
    val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var hasNotification by remember { mutableStateOf(if (needsNotification) ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true) }

    // OEM detection
    val oemType = remember { com.camlauncher.app.data.OemDeviceHelper.getOemType() }
    val oemSteps = remember { com.camlauncher.app.data.OemDeviceHelper.getSetupSteps(context) }
    
    // NEW: State to control the Prominent Disclosure Dialogs
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var showOverlayDisclosure by remember { mutableStateOf(false) }

    // Update state on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                hasAccessibility = isAccessibilityServiceEnabled(context)
                hasBatteryExemption = com.camlauncher.app.data.OemDeviceHelper.isBatteryOptimized(context)
                if (needsNotification) {
                    hasNotification = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launchers
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCamera = granted }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasAudio = granted }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasNotification = granted }



    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text("Setup Instructions", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text( // Moved title down so it's vertically padded properly within alert
                    "Required Permissions",
                    style = MaterialTheme.typography.titleSmall,
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                PermissionItem(
                    icon = Icons.Filled.CameraAlt,
                    title = "Camera",
                    description = "Required to record video",
                    isGranted = hasCamera,
                    onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }
                )
                
                PermissionItem(
                    icon = Icons.Filled.Mic,
                    title = "Microphone",
                    description = "Required to record audio",
                    isGranted = hasAudio,
                    onClick = { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )

                if (needsNotification) {
                    PermissionItem(
                        icon = Icons.Filled.Notifications,
                        title = "Notifications",
                        description = "Required to stay running in background",
                        isGranted = hasNotification,
                        onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }

                PermissionItem(
                    icon = Icons.Filled.Layers,
                    title = "Display over apps",
                    description = "Required for recording status HUD",
                    isGranted = hasOverlay,
                    onClick = { 
                        // CHANGE: Open the disclosure dialog first
                        showOverlayDisclosure = true
                    }
                )

                PermissionItem(
                    icon = Icons.Filled.Accessibility,
                    title = "Accessibility",
                    description = "Detects volume button press out of app",
                    isGranted = hasAccessibility,
                    // CHANGE THIS ONCLICK
                    onClick = { showAccessibilityDisclosure = true } 
                )

                // Battery optimization (standard Android — all devices)
                PermissionItem(
                    icon = Icons.Filled.BatteryAlert,
                    title = "Battery Unrestricted",
                    description = "Prevents system from killing background recording",
                    isGranted = hasBatteryExemption,
                    onClick = {
                        try {
                            val intent = com.camlauncher.app.data.OemDeviceHelper.getSetupSteps(context)
                                .find { it.title.contains("Battery", ignoreCase = true) }
                                ?.createIntent?.invoke(context)
                                ?: Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Device-specific OEM setup section
                if (oemSteps.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${oemType.displayName} Setup",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                "Your device may restrict background apps. Complete these steps for reliable operation:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            oemSteps.forEachIndexed { index, step ->
                                SetupStep(
                                    number = index + 1,
                                    text = "${step.title}\n${step.description}",
                                    onClick = {
                                        try {
                                            context.startActivity(step.createIntent(context))
                                        } catch (_: Exception) {
                                            context.startActivity(
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.parse("package:${context.packageName}"))
                                            )
                                        }
                                    }
                                )
                                if (index < oemSteps.lastIndex) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // DELETED: The generic "Policy note" Card has been removed 
                // to make the UI much cleaner. Specific dialogs handle this.
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Done")
            }
        }
    )

    // Render Accessibility Disclosure
    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onDismiss = { showAccessibilityDisclosure = false },
            onAgree = {
                showAccessibilityDisclosure = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )
    }

    // NEW: Render Overlay Disclosure
    if (showOverlayDisclosure) {
        OverlayDisclosureDialog(
            onDismiss = { showOverlayDisclosure = false },
            onAgree = {
                showOverlayDisclosure = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }
            }
        )
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isGranted) Success.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) Success else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        if (isGranted) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Granted",
                tint = Success,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Button(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SetupStep(number: Int, text: String, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (onClick != null) {
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Primary)
            }
        }
    }
}

@Composable
private fun TipRow(
    icon: ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OverlayDisclosureDialog(
    onDismiss: () -> Unit,
    onAgree: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.Layers,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Display Over Apps",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // The exact wording required to justify SYSTEM_ALERT_WINDOW
                Text(
                    text = "CamLauncher requires the 'Display over other apps' permission to show a persistent recording status overlay (HUD).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "This ensures you always know when the camera or microphone is active, prioritizing your privacy and awareness. We do not use this permission to interfere with other apps or display ads.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAgree,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Agree & Continue", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun AccessibilityDisclosureDialog(
    onDismiss: () -> Unit,
    onAgree: () -> Unit
) {
    val context = LocalContext.current
    val isAndroid13OrAbove = Build.VERSION.SDK_INT >= 33

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.Accessibility,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Accessibility Permission",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // The exact wording required by Google Play Policies
                Text(
                    text = "CamLauncher requires the Accessibility Service API to detect hardware volume button presses while the app is in the background. This allows you to trigger emergency recordings instantly. CamLauncher does not use the Accessibility Service to collect, store, or share your personal data or observe your screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Visual reinforcement of privacy
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Primary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "100% Offline. No data is ever transmitted or shared.",
                            style = MaterialTheme.typography.labelMedium,
                            color = Primary
                        )
                    }
                }

                if (isAndroid13OrAbove) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Android 13+ Restricted Setting",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Because this app is sideloaded, Android will grey out the Accessibility toggle. Click 'Unlock Settings' below, tap the 3 dots in the top right corner of the App Info page, and select 'Allow restricted settings' before continuing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Unlock Settings")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAgree,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Enable Accessibility", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val serviceName = "${context.packageName}/com.camlauncher.app.service.GestureTriggerService"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(serviceName)
}
