package com.camlauncher.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.camlauncher.app.camera.CameraCapabilities
import com.camlauncher.app.camera.CameraCapabilityHelper
import com.camlauncher.app.data.*
import com.camlauncher.app.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val triggerType by settingsStore.triggerType.collectAsState(initial = TriggerType.VOLUME_DOUBLE_PRESS)
    val recordingMode by settingsStore.recordingMode.collectAsState(initial = RecordingMode.VIDEO)
    val videoQuality by settingsStore.videoQuality.collectAsState(initial = VideoQuality.QUALITY_1080P)
    val videoFps by settingsStore.videoFps.collectAsState(initial = VideoFps.FPS_30)
    val screenMode by settingsStore.screenMode.collectAsState(initial = ScreenMode.BLACK)
    val maxDuration by settingsStore.maxDuration.collectAsState(initial = MaxDuration.FIFTEEN_MIN)
    val antiPocket by settingsStore.antiPocketEnabled.collectAsState(initial = false)
    val vibrationOnTrigger by settingsStore.vibrationOnTrigger.collectAsState(initial = true)
    val vibrationPeriodic by settingsStore.vibrationPeriodic.collectAsState(initial = false)
    val locationTagging by settingsStore.locationTagging.collectAsState(initial = false)
    val storageLocation by settingsStore.storageLocation.collectAsState(initial = StorageLocation.INTERNAL)
    val chunkInterval by settingsStore.chunkInterval.collectAsState(initial = ChunkInterval.TEN_MIN)
    val enableHashing by settingsStore.enableHashing.collectAsState(initial = false)
    val videoStabilization by settingsStore.videoStabilizationEnabled.collectAsState(initial = true)
    val defaultZoom by settingsStore.defaultZoom.collectAsState(initial = DefaultZoom.NORMAL)
    val customStorageUri by settingsStore.customStorageUri.collectAsState(initial = null)
    val defaultCamera by settingsStore.defaultCamera.collectAsState(initial = DefaultCamera.REAR)

    val context = LocalContext.current
    var conflicts by remember { mutableStateOf<List<String>>(emptyList()) }
    var cameraCapabilities by remember { mutableStateOf<CameraCapabilities?>(null) }

    // Auto-sync locationTagging setting if OS permission was revoked or missing
    LaunchedEffect(locationTagging) {
        if (locationTagging) {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                settingsStore.setLocationTagging(false)
            }
        }
    }
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        scope.launch { settingsStore.setLocationTagging(granted) }
    }

    val documentTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch {
                settingsStore.setCustomStorageUri(uri.toString())
                settingsStore.setStorageLocation(StorageLocation.CUSTOM)
            }
        }
    }
    
    // Scan for conflicts and hardware capabilities dynamically when camera or quality changes
    LaunchedEffect(defaultCamera, videoQuality) {
        val lensFacing = if (defaultCamera == DefaultCamera.FRONT) {
            androidx.camera.core.CameraSelector.LENS_FACING_FRONT
        } else {
            androidx.camera.core.CameraSelector.LENS_FACING_BACK
        }
        
        val caps = CameraCapabilityHelper.getCapabilities(context, lensFacing)
        cameraCapabilities = caps
        
        // Auto-fallback: If user switches from Rear (4K) to Front (Max 1080p), gracefully downgrade
        val currentQuality = settingsStore.videoQuality.first()
        val currentFps = settingsStore.videoFps.first()
        
        if (!caps.supportedQualities.contains(currentQuality)) {
            settingsStore.setVideoQuality(caps.supportedQualities.firstOrNull() ?: VideoQuality.QUALITY_1080P)
        }
        // Validate FPS against the per-quality map, not the flat global list
        val fpsForCurrentQuality = caps.getFpsForQuality(currentQuality)
        if (!fpsForCurrentQuality.contains(currentFps)) {
            settingsStore.setVideoFps(fpsForCurrentQuality.firstOrNull() ?: VideoFps.FPS_30)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // --- TRIGGER ---
            SettingsSection("Trigger") {

                if (conflicts.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = Warning, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(conflicts[0], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                val triggerIcon = when(triggerType) {
                    TriggerType.VOLUME_DOUBLE_PRESS,
                    TriggerType.VOLUME_TRIPLE_PRESS -> Icons.AutoMirrored.Filled.VolumeDown
                    TriggerType.SHAKE -> Icons.Filled.Vibration
                    TriggerType.BUTTON -> Icons.Filled.TouchApp
                    else -> Icons.Filled.Gesture
                }
                SettingsDropdown(
                    label = "Gesture Type",
                    value = triggerType.displayName,
                    options = TriggerType.entries.map { it.displayName },
                    onSelect = { index ->
                        scope.launch {
                            settingsStore.setTriggerType(TriggerType.entries[index])
                            com.camlauncher.app.widget.TriggerModeWidget.refreshAll(context)
                        }
                    }
                )
                
                Text(
                    text = triggerType.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }

            // --- RECORDING ---
            SettingsSection("Recording") {
                SettingsDropdown(
                    label = "Mode",
                    value = recordingMode.displayName,
                    options = RecordingMode.entries.map { it.displayName },
                    onSelect = { index ->
                        scope.launch { settingsStore.setRecordingMode(RecordingMode.entries[index]) }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsDropdown(
                    label = "Default Camera",
                    value = defaultCamera.displayName,
                    options = DefaultCamera.entries.map { it.displayName },
                    onSelect = { index ->
                        scope.launch { settingsStore.setDefaultCamera(DefaultCamera.entries[index]) }
                    }
                )

                if (recordingMode == RecordingMode.VIDEO) {
                    Spacer(modifier = Modifier.height(8.dp))

                    val qualityOptions = cameraCapabilities?.supportedQualities ?: VideoQuality.entries
                    SettingsDropdown(
                        label = "Video Quality",
                        value = videoQuality.displayName,
                        options = qualityOptions.map { it.displayName },
                        onSelect = { index ->
                            scope.launch { settingsStore.setVideoQuality(qualityOptions[index]) }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val fpsOptions = cameraCapabilities?.getFpsForQuality(videoQuality) ?: VideoFps.entries.toList()
                    SettingsDropdown(
                        label = "Framerate",
                        value = videoFps.displayName,
                        options = fpsOptions.map { it.displayName },
                        onSelect = { index ->
                            scope.launch { settingsStore.setVideoFps(fpsOptions[index]) }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsToggle(
                        label = "Video Stabilization",
                        description = "",
                        checked = videoStabilization,
                        onCheckedChange = {
                            scope.launch { settingsStore.setVideoStabilizationEnabled(it) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                SettingsDropdown(
                    label = "Max Duration",
                    value = maxDuration.displayName,
                    options = MaxDuration.entries.map { it.displayName },
                    onSelect = { index ->
                        scope.launch { settingsStore.setMaxDuration(MaxDuration.entries[index]) }
                    }
                )

                if (recordingMode == RecordingMode.VIDEO) {
                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsDropdown(
                        label = "Auto-Chunking",
                        value = chunkInterval.displayName,
                        options = ChunkInterval.entries.map { it.displayName },
                        onSelect = { index ->
                            scope.launch { settingsStore.setChunkInterval(ChunkInterval.entries[index]) }
                        }
                    )
                    
                    Text(
                        text = "Saves segments every ${chunkInterval.displayName} for crash protection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )

                    if (defaultCamera == DefaultCamera.REAR) {
                        Spacer(modifier = Modifier.height(8.dp))

                        SettingsDropdown(
                            label = "Default Zoom Level",
                            value = defaultZoom.displayName,
                            options = DefaultZoom.entries.map { it.displayName },
                            onSelect = { index ->
                                scope.launch { settingsStore.setDefaultZoom(DefaultZoom.entries[index]) }
                            }
                        )
                    }
                }
            }

            // --- DISPLAY ---
            SettingsSection("Display") {
                SettingsDropdown(
                    label = "Screen During Recording",
                    value = screenMode.displayName,
                    options = ScreenMode.entries.map { it.displayName },
                    onSelect = { index ->
                        scope.launch { settingsStore.setScreenMode(ScreenMode.entries[index]) }
                    }
                )
            }

            // --- SAFETY ---
            SettingsSection("Safety") {
                SettingsToggle(
                    label = "Smart Prevention (Pocket)",
                    description = "Avoid accidental triggers while in pocket or bag",
                    checked = antiPocket,
                    onCheckedChange = {
                        scope.launch { settingsStore.setAntiPocket(it) }
                    }
                )
            }

            // --- FEEDBACK ---
            SettingsSection("Feedback") {
                SettingsToggle(
                    label = "Vibrate on Trigger",
                    description = "Haptic feedback when recording starts/stops",
                    checked = vibrationOnTrigger,
                    onCheckedChange = {
                        scope.launch { settingsStore.setVibrationOnTrigger(it) }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsToggle(
                    label = "Periodic Vibration",
                    description = "Subtle vibration every 15s while recording",
                    checked = vibrationPeriodic,
                    onCheckedChange = {
                        scope.launch { settingsStore.setVibrationPeriodic(it) }
                    }
                )
            }

            // --- STORAGE ---
            SettingsSection("Storage") {
                SettingsDropdown(
                    label = "Save Location",
                    value = storageLocation.displayName,
                    options = StorageLocation.entries.map { it.displayName },
                    onSelect = { index ->
                        val selected = StorageLocation.entries[index]
                        if (selected == StorageLocation.CUSTOM) {
                            documentTreeLauncher.launch(null) // Triggers OS Folder Picker
                        } else {
                            scope.launch { settingsStore.setStorageLocation(selected) }
                        }
                    }
                )

                if (storageLocation == StorageLocation.CUSTOM && customStorageUri != null) {
                    val folderName = try {
                        android.net.Uri.parse(customStorageUri).lastPathSegment ?: "Unknown Folder"
                    } catch (e: Exception) {
                        "Unknown Folder"
                    }
                    Text(
                        text = "Saving to: $folderName",
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary,
                        modifier = Modifier
                            .padding(top = 4.dp, start = 4.dp)
                            .clickable { documentTreeLauncher.launch(null) }
                    )
                }
            }

            // --- METADATA ---
            SettingsSection("Metadata") {
                SettingsToggle(
                    label = "Location Tagging",
                    description = "Embed GPS location in recording metadata",
                    checked = locationTagging,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            locationPermissionLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        } else {
                            scope.launch { settingsStore.setLocationTagging(false) }
                        }
                    }
                )
            }

            // --- EVIDENCE INTEGRITY ---
            SettingsSection("Evidence Integrity") {
                SettingsToggle(
                    label = "Video Hashing",
                    description = "Compute SHA-256 hash for tamper detection",
                    checked = enableHashing,
                    onCheckedChange = {
                        scope.launch { settingsStore.setEnableHashing(it) }
                    }
                )
                if (enableHashing) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "View or share SHA-256 fingerprints from the gallery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // --- ABOUT ---
            SettingsSection("About") {
                Text(
                    text = "CamLauncher v1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Capture the moment. Stay in it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Primary,
                checkedThumbColor = OnPrimary
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                focusedLabelColor = Primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            color = if (option == value) Primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    }
                )
            }
        }
    }
}
