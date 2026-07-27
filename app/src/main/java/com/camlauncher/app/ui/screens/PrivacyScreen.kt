package com.camlauncher.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.camlauncher.app.data.OemDeviceHelper
import com.camlauncher.app.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("User Privacy", "Privacy Policy")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { 
                            Text(
                                title, 
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal 
                            ) 
                        }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTabIndex) {
                    0 -> UserPrivacyContent()
                    1 -> PrivacyPolicyContent()
                }
            }
        }
    }
}

@Composable
fun UserPrivacyContent() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var hasOverlay by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true) }
    var hasAccessibility by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var isBatteryUnrestricted by remember { mutableStateOf(OemDeviceHelper.isBatteryOptimized(context)) }
    var hasNotification by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Refresh permission statuses live when returning from OS settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                hasAccessibility = isAccessibilityEnabled(context)
                isBatteryUnrestricted = OemDeviceHelper.isBatteryOptimized(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotification = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }
                hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                             ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Manage Your Permissions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "CamLauncher operates with 100% user transparency. Review your granted permissions below. To grant or revoke any permission, click 'Manage in OS Settings'.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        PermissionStatusCard(
            title = "Camera",
            description = "Captures video strictly when you trigger a recording session.",
            icon = Icons.Filled.CameraAlt,
            isGranted = hasCamera
        )
        PermissionStatusCard(
            title = "Microphone",
            description = "Captures audio alongside your emergency or background video recordings.",
            icon = Icons.Filled.Mic,
            isGranted = hasMic
        )
        PermissionStatusCard(
            title = "Accessibility Service",
            description = "Detects hardware volume button clicks for background & screen-off emergency triggers. No screen data is read or collected.",
            icon = Icons.Filled.Accessibility,
            isGranted = hasAccessibility
        )
        PermissionStatusCard(
            title = "Display Over Other Apps",
            description = "Shows a visible status indicator (HUD) so you always know when recording is active.",
            icon = Icons.Filled.Layers,
            isGranted = hasOverlay
        )
        PermissionStatusCard(
            title = "Battery Optimization Exempt",
            description = "Prevents OEM background killers from destroying background recording and accessibility services.",
            icon = Icons.Filled.BatteryAlert,
            isGranted = isBatteryUnrestricted
        )
        PermissionStatusCard(
            title = "Notifications",
            description = "Displays persistent foreground status and recording notifications.",
            icon = Icons.Filled.Notifications,
            isGranted = hasNotification
        )
        PermissionStatusCard(
            title = "Location (Optional)",
            description = "Optionally embeds geographic coordinates into media metadata locally on your device.",
            icon = Icons.Filled.LocationOn,
            isGranted = hasLocation
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Manage in OS Settings", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PermissionStatusCard(title: String, description: String, icon: ImageVector, isGranted: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = if (isGranted) Primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isGranted) "Granted" else "Denied",
                    color = if (isGranted) Primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun PrivacyPolicyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("CamLauncher Privacy Policy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text("Last Updated: July 2026\n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        PolicySection(
            "1. 100% Offline & Local Data Privacy",
            "CamLauncher is engineered strictly as a privacy-first, offline utility. We do not collect, transmit, share, sell, or upload any personal data, media, location, or usage statistics to remote servers or third parties. All video, photo, and audio recordings remain 100% local on your device's internal storage or your designated Custom Storage location."
        )

        PolicySection(
            "2. Prominent Disclosure: Accessibility Service API",
            "CamLauncher utilizes Android's Accessibility Service API strictly to support hands-free emergency recording triggers:\n\n" +
            "• Purpose: Detect physical hardware volume button double/triple press gestures while the screen is off or the app is in the background.\n" +
            "• Data Privacy Guarantee: The Accessibility Service does NOT read screen content, track keystrokes, observe user interactions with other applications, or transmit any data off your device.\n" +
            "• Optional Use: Accessibility is only required if you choose to enable volume button trigger modes."
        )

        PolicySection(
            "3. Foreground Service & Battery Optimization",
            "To guarantee reliable emergency recording and prevent unexpected process termination during background operations:\n\n" +
            "• Special-Use Foreground Services (MonitorService & RecordingService) maintain high process priority to capture video/audio without interruption.\n" +
            "• Battery Optimization Exemption allows CamLauncher to bypass aggressive OEM process killers (such as Xiaomi MIUI/HyperOS, Samsung One UI, Huawei EMUI, ColorOS, and Funtouch OS) so emergency triggers remain active when needed."
        )

        PolicySection(
            "4. Hardware Permissions (Camera, Microphone & Overlay)",
            "• Camera & Microphone: Used exclusively to capture video and audio when explicitly initiated by the user or an active trigger gesture.\n" +
            "• Display Over Other Apps: Displays a small floating Heads-Up Display (HUD) indicator so you are always aware when a recording is in progress."
        )

        PolicySection(
            "5. Location Data (Optional Geotagging)",
            "If location access is granted, CamLauncher can optionally embed your current GPS coordinates into media EXIF/metadata tags. This data is stored strictly inside the media file on your local device and is never transmitted anywhere."
        )

        PolicySection(
            "6. User Control & Permission Revocation",
            "You retain full control over all permissions. You can inspect or revoke any permission at any time directly through the 'User Privacy' tab or via your Android OS System Settings."
        )
    }
}

@Composable
fun PolicySection(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val serviceName = "${context.packageName}/com.camlauncher.app.service.GestureTriggerService"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(serviceName)
}
