package com.camlauncher.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.camlauncher.app.data.LicenseManager
import com.camlauncher.app.data.LicenseResult
import com.camlauncher.app.ui.theme.*
import kotlinx.coroutines.launch

// Premium accent colors for the activation screen
private val GoldAccent = Color(0xFFFFD700)
private val GoldLight = Color(0xFFFFE57F)
private val GoldDark = Color(0xFFB8860B)
private val PremiumPurple = Color(0xFF8B5CF6)
private val PremiumBlue = Color(0xFF3B82F6)
private val InputCardBg = Color(0xFF141424)

@Composable
fun ActivationScreen(
    onActivated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current

    var licenseKey by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }

    // Pulsing animation for the shield icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090914),
                        Color(0xFF0E0E22),
                        Color(0xFF0A0A12)
                    )
                )
            )
    ) {
        // Subtle ambient glowing orbs in background
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-80).dp, y = 60.dp)
                .alpha(glowAlpha * 0.16f)
                .blur(90.dp)
                .background(Primary, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = 180.dp, y = 380.dp)
                .alpha(glowAlpha * 0.14f)
                .blur(90.dp)
                .background(GoldAccent, CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // ── Top Pro Badge ──
            Surface(
                shape = RoundedCornerShape(50),
                color = GoldAccent.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.35f)),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GET LIFETIME LICENSE",
                        color = GoldAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            // ── Hero Icon Container with Glow ──
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.scale(pulseScale)
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .alpha(glowAlpha * 0.45f)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(GoldAccent.copy(alpha = 0.35f), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(GoldAccent, Color(0xFFD97706))
                            ),
                            CircleShape
                        )
                        .border(2.dp, GoldLight.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = Color(0xFF0F0F1A),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── App Title & Subtitle ──
            Text(
                text = "CamLauncher Pro",
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "The ultimate offline background dashcam and emergency video recorder on your mobile phone.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = GoldLight.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── High-Converting 2-Column Comparison Hook Card ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF161524),
                                Color(0xFF0F0E18)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GoldAccent.copy(alpha = 0.35f),
                                Color(0xFF10B981).copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    // Header Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(GoldAccent, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "THE CRUCIAL MOMENT COMPARISON",
                                color = GoldAccent,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2-Column Side-by-Side Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left Column: Default Camera (Neutral Dark Slate)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    Color(0xFF151522),
                                    RoundedCornerShape(14.dp)
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.06f)
                                ) {
                                    Text(
                                        text = "~8s Delay",
                                        color = OnSurfaceVariant,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Default Camera",
                                color = OnSurfaceVariant,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ComparisonItem(icon = Icons.Filled.Remove, tint = OnSurfaceVariant.copy(alpha = 0.6f), text = "Must unlock phone")
                            Spacer(modifier = Modifier.height(5.dp))
                            ComparisonItem(icon = Icons.Filled.Remove, tint = OnSurfaceVariant.copy(alpha = 0.6f), text = "Screen wakes up")
                            Spacer(modifier = Modifier.height(5.dp))
                            ComparisonItem(icon = Icons.Filled.Remove, tint = OnSurfaceVariant.copy(alpha = 0.6f), text = "Misses the moment")
                        }

                        // Right Column: CamLauncher Pro (Trust Emerald Green)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF0C241B),
                                            Color(0xFF0A1C15)
                                        )
                                    ),
                                    RoundedCornerShape(14.dp)
                                )
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF10B981).copy(alpha = 0.65f),
                                            Color(0xFF059669).copy(alpha = 0.25f)
                                        )
                                    ),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "0.2s Instant",
                                        color = Color(0xFF34D399),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "CamLauncher Pro",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ComparisonItem(icon = Icons.Filled.Check, tint = Color(0xFF10B981), text = "Volume/Shake key")
                            Spacer(modifier = Modifier.height(5.dp))
                            ComparisonItem(icon = Icons.Filled.Check, tint = Color(0xFF10B981), text = "100% Screen-off")
                            Spacer(modifier = Modifier.height(5.dp))
                            ComparisonItem(icon = Icons.Filled.Check, tint = Color(0xFF10B981), text = "Evidence secured")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── "WHAT YOU UNLOCK" Section Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "WHAT YOU UNLOCK",
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // ── Key Value Props / Feature Cards ──
            FeatureCard(
                icon = Icons.Filled.Bolt,
                accentColor = GoldAccent,
                title = "Start Recording in Less Than a Second",
                description = "Double-click volume from a locked phone. Recording starts before your screen even wakes. No fumbling, no delay. Completely invisible background recording."
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureCard(
                icon = Icons.Filled.Gavel,
                accentColor = Color(0xFF38BDF8),
                title = "Video Integrity & Location Tagging",
                description = "SHA-256 hash chain seals every video chunk the instant it's written, mathematically proving zero tampering. Automatically tags the exact, precise location where recording was triggered."
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureCard(
                icon = Icons.Filled.HealthAndSafety,
                accentColor = Color(0xFF34D399),
                title = "Never Lose a Single Frame",
                description = "Battery died or phone crashed mid-recording? Rolling chunk recovery saves everything up to the last second. Your recording survives and is automatically recovered."
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureCard(
                icon = Icons.Filled.FrontHand,
                accentColor = Color(0xFFF472B6),
                title = "Zero False Triggers",
                description = "Proximity sensors block accidental triggers when the device is safely inside a pocket or bag."
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Trust & Lifetime Guarantee Badges ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TrustBadge(icon = Icons.Filled.AllInclusive, label = "Pay Once")
                TrustBadge(icon = Icons.Filled.LockOpen, label = "Lifetime Unlocked")
                TrustBadge(icon = Icons.Filled.Devices, label = "3 Activations")
                TrustBadge(icon = Icons.Filled.WifiOff, label = "Fully Offline")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Purchase Card Hook — Primary CTA for users without a key ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2A1E08),
                                Color(0xFF1C1628)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(GoldAccent.copy(alpha = 0.7f), Primary.copy(alpha = 0.4f))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(LicenseManager.GUMROAD_PURCHASE_URL)
                        )
                        context.startActivity(intent)
                    }
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Get Your License Key",
                                color = GoldAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Instant delivery • One payment forever",
                                color = OnSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GoldAccent,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Unlock",
                                    color = Color(0xFF0F0F1A),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFF0F0F1A),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Section Divider / Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.1f)
                )
                Text(
                    text = "  ALREADY HAVE A KEY?  ",
                    color = OnSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── License Key Input Box (Fixed Contrast & Visual Bug) ──
            OutlinedTextField(
                value = licenseKey,
                onValueChange = {
                    licenseKey = it.uppercase()
                    errorMessage = null
                },
                label = { 
                    Text(
                        "License Key", 
                        color = if (errorMessage != null) Error else GoldAccent.copy(alpha = 0.9f)
                    ) 
                },
                placeholder = { 
                    Text(
                        "XXXXXXXX-XXXXXXXX-XXXXXXXX-XXXXXXXX",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 13.sp
                    ) 
                },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                ),
                trailingIcon = {
                    if (licenseKey.isNotEmpty()) {
                        IconButton(onClick = { licenseKey = ""; errorMessage = null }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear",
                                tint = OnSurfaceVariant
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrBlank()) {
                                    licenseKey = clipText.uppercase().trim()
                                    errorMessage = null
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = "Paste key",
                                tint = GoldAccent.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isVerifying,
                isError = errorMessage != null,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Color.White.copy(alpha = 0.5f),
                    focusedContainerColor = InputCardBg,
                    unfocusedContainerColor = Color(0xFF0F0F1C),
                    disabledContainerColor = Color(0xFF0A0A14),
                    cursorColor = GoldAccent,
                    focusedBorderColor = GoldAccent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                    focusedLabelColor = GoldAccent,
                    unfocusedLabelColor = OnSurfaceVariant,
                    errorBorderColor = Error,
                    errorTextColor = Color.White,
                    errorContainerColor = Color(0xFF1C0E14)
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = Error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = errorMessage!!,
                        color = Error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Activate Button ──
            Button(
                onClick = {
                    if (licenseKey.isBlank()) {
                        errorMessage = "Please enter your license key"
                        return@Button
                    }
                    focusManager.clearFocus()
                    isVerifying = true
                    errorMessage = null

                    scope.launch {
                        val result = LicenseManager.verifyAndActivate(context, licenseKey)
                        isVerifying = false
                        when (result) {
                            is LicenseResult.Success -> {
                                showSuccess = true
                            }
                            is LicenseResult.InvalidKey -> {
                                errorMessage = "Invalid license key. Please check and try again."
                            }
                            is LicenseResult.Revoked -> {
                                errorMessage = "This license has been revoked or refunded."
                            }
                            is LicenseResult.NetworkError -> {
                                errorMessage = "No internet connection. Please connect and try again."
                            }
                            is LicenseResult.Error -> {
                                errorMessage = "Verification failed: ${result.message}"
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = !isVerifying && licenseKey.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = Color(0xFF0F0F1A),
                    disabledContainerColor = GoldAccent.copy(alpha = 0.25f),
                    disabledContentColor = Color(0xFF0F0F1A).copy(alpha = 0.4f)
                )
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color(0xFF0F0F1A),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Verifying License...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                } else {
                    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Activate CamLauncher Pro", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Help note ──
            Text(
                text = "License key is delivered instantly to your email. Can be used for up to 3 activations.",
                color = OnSurfaceVariant.copy(alpha = 0.65f),
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Success overlay ──
    if (showSuccess) {
        SuccessOverlay(onDismiss = onActivated)
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF131322),
                RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    accentColor.copy(alpha = 0.14f),
                    CircleShape
                )
                .border(1.dp, accentColor.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.6f),
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun TrustBadge(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GoldAccent.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = OnSurface.copy(alpha = 0.85f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ComparisonItem(
    icon: ImageVector,
    tint: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun SuccessOverlay(onDismiss: () -> Unit) {
    val scale = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        kotlinx.coroutines.delay(1800)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE080812)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale.value)
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Success, Color(0xFF22C55E))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "License Activated!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Welcome to CamLauncher Pro",
                fontSize = 15.sp,
                color = OnSurfaceVariant
            )
        }
    }
}

