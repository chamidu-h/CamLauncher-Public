package com.camlauncher.app.data

/**
 * Supported trigger gestures for starting/stopping recording.
 */
enum class TriggerType(
    val displayName: String, 
    val description: String, 
    val requiresAccessibility: Boolean = true
) {
    VOLUME_DOUBLE_PRESS("Volume Double-Press", "Quick double-press volume down", true),
    VOLUME_TRIPLE_PRESS("Volume Triple-Press", "Quick triple-press volume down", true),
    SHAKE("Shake Device", "Give your phone 3 quick shakes (waves)", true),
    
    // NEW ADDITION
    BUTTON("In-App Button Only", "Use the on-screen button to record", false)
}

/**
 * Recording modes supported by CamLauncher.
 */
enum class RecordingMode(val displayName: String, val description: String) {
    VIDEO("Video", "Record video with audio from the rear camera"),
    AUDIO_ONLY("Audio Only", "Record high-quality audio without camera"),
    PHOTO_BURST("Photo Burst", "Capture rapid-fire photos on trigger")
}

/**
 * Video quality presets.
 */
enum class VideoQuality(val displayName: String, val width: Int, val height: Int) {
    QUALITY_480P("480p", 854, 480),
    QUALITY_720P("720p", 1280, 720),
    QUALITY_1080P("1080p", 1920, 1080),
    QUALITY_2160P("4K (2160p)", 3840, 2160)
}

/**
 * Video recording framerates.
 */
enum class VideoFps(val displayName: String, val fps: Int) {
    FPS_30("30 fps", 30),
    FPS_60("60 fps", 60)
}

/**
 * Screen behavior during recording.
 */
enum class ScreenMode(val displayName: String) {
    BLACK("Background"),
    MINIMAL_HUD("Minimal HUD"),
    NORMAL("Screen On (Normal)")
}

/**
 * Maximum recording duration options.
 */
enum class MaxDuration(val displayName: String, val seconds: Long) {
    ONE_MIN("1 minute", 60),
    FIVE_MIN("5 minutes", 300),
    FIFTEEN_MIN("15 minutes", 900),
    THIRTY_MIN("30 minutes", 1800),
    UNLIMITED("Unlimited", Long.MAX_VALUE)
}

/**
 * Current state of the recording engine.
 */
enum class RecordingState {
    IDLE,
    MONITORING,
    STARTING,
    RECORDING,
    STOPPING,
    ERROR
}

/**
 * Storage location for recordings.
 */
enum class StorageLocation(val displayName: String) {
    INTERNAL("Internal Storage"),
    SD_CARD("SD Card (if available)"),
    CUSTOM("Custom Folder...") // ADDED
}

/**
 * Auto-chunking interval for crash-safe segmented recording.
 */
enum class ChunkInterval(val displayName: String, val seconds: Long) {
    ONE_MIN("1 minute", 60),
    FIVE_MIN("5 minutes", 300),
    TEN_MIN("10 minutes", 600),
    FIFTEEN_MIN("15 minutes", 900),
    THIRTY_MIN("30 minutes", 1800)
}

/**
 * Zoom levels for video recording.
 */
enum class DefaultZoom(val displayName: String, val targetRatio: Float) {
    ULTRA_WIDE("Ultra Wide (0.5x)", 0.5f),
    NORMAL("Normal (1x)", 1.0f),
    ZOOM_2X("Zoom (2x)", 2.0f)
}

/**
 * Default camera lens facing direction.
 */
enum class DefaultCamera(val displayName: String) {
    REAR("Rear Camera"),
    FRONT("Front Camera")
}
