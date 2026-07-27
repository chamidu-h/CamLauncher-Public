package com.camlauncher.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.camera.core.CameraSelector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.camlauncher.app.data.RecordingState
import com.camlauncher.app.service.RecordingService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecorderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        RecordingService.onRecorderActivityCreated(this)

        setContent {
            MaterialTheme {
                RecorderScreen(
                    onStop = {
                        RecordingService.stopRecording(this@RecorderActivity)
                        finish()
                    },
                    onFlip = {
                        RecordingService.flipCamera()
                    }
                )
            }
        }
        
        lifecycleScope.launch {
            RecordingService.stateFlow.collectLatest { state ->
                if (state == RecordingState.IDLE || state == RecordingState.ERROR) {
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        RecordingService.onRecorderActivityDestroyed(this)
    }
}

@Composable
fun RecorderScreen(onStop: () -> Unit, onFlip: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        var previewView by remember { mutableStateOf<PreviewView?>(null) }
        var currentZoomLevel by remember { mutableStateOf(1.0f) }
        var isZoomMenuExpanded by remember { mutableStateOf(false) }
        val currentLens by RecordingService.currentLensFlow.collectAsState()
        val currentZoomRatio by RecordingService.currentZoomRatioFlow.collectAsState()
        
        // Update local state when remote flow changes
        LaunchedEffect(currentZoomRatio) {
            currentZoomLevel = currentZoomRatio
        }
        
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    RecordingService.attachPreviewView(this)
                }.also { previewView = it }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Timer/Status (Top)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color.Red, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Recording", color = Color.White)
        }
        
        // Controls (Bottom)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flip Camera
            IconButton(
                onClick = onFlip,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Cameraswitch, contentDescription = "Flip Camera", tint = Color.White)
            }
            
            // Stop Button
            IconButton(
                onClick = onStop,
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Red, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            
            
            // Expandable Zoom Menu
            if (currentLens != CameraSelector.DEFAULT_FRONT_CAMERA) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isZoomMenuExpanded,
                        enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom) + androidx.compose.animation.fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            listOf(0.5f, 1.0f, 2.0f).forEach { ratio ->
                                TextButton(
                                    onClick = {
                                        currentZoomLevel = ratio
                                        RecordingService.setLiveZoom(ratio)
                                        isZoomMenuExpanded = false
                                    }
                                ) {
                                    Text(
                                        text = when(ratio) {
                                            0.5f -> ".5x"
                                            2.0f -> "2x"
                                            else -> "1x"
                                        },
                                        color = if (currentZoomRatio == ratio) Color.Yellow else Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { isZoomMenuExpanded = !isZoomMenuExpanded },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom", tint = Color.White)
                    }
                }
            }
        }
    }
}
