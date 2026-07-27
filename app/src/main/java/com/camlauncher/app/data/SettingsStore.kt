package com.camlauncher.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cam_launcher_settings")

/**
 * Persists user preferences using Jetpack DataStore.
 */
class SettingsStore(context: Context) {

    private val context: Context = context.applicationContext
    // Keys
    private object Keys {
        val TRIGGER_TYPE = stringPreferencesKey("trigger_type")
        val RECORDING_MODE = stringPreferencesKey("recording_mode")
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val VIDEO_FPS = stringPreferencesKey("video_fps")
        val SCREEN_MODE = stringPreferencesKey("screen_mode")
        val MAX_DURATION = stringPreferencesKey("max_duration")
        val ANTI_POCKET = booleanPreferencesKey("anti_pocket")
        val VIBRATION_ON_TRIGGER = booleanPreferencesKey("vibration_on_trigger")
        val VIBRATION_PERIODIC = booleanPreferencesKey("vibration_periodic")
        val LOCATION_TAGGING = booleanPreferencesKey("location_tagging")
        val STORAGE_LOCATION = stringPreferencesKey("storage_location")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val COOLDOWN_MS = longPreferencesKey("cooldown_ms")
        val APP_THEME = stringPreferencesKey("app_theme")
        val CHUNK_INTERVAL = stringPreferencesKey("chunk_interval")
        val ENABLE_HASHING = booleanPreferencesKey("enable_hashing")
        val VIDEO_STABILIZATION = booleanPreferencesKey("video_stabilization")
        val DEFAULT_ZOOM = stringPreferencesKey("default_zoom")
        val CUSTOM_STORAGE_URI = stringPreferencesKey("custom_storage_uri")
        val DEFAULT_CAMERA = stringPreferencesKey("default_camera")
    }

    // --- Flows ---

    val triggerType: Flow<TriggerType> = context.dataStore.data.map { prefs ->
        prefs[Keys.TRIGGER_TYPE]?.let { TriggerType.valueOf(it) } ?: TriggerType.VOLUME_DOUBLE_PRESS
    }

    val recordingMode: Flow<RecordingMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.RECORDING_MODE]?.let { RecordingMode.valueOf(it) } ?: RecordingMode.VIDEO
    }

    val videoQuality: Flow<VideoQuality> = context.dataStore.data.map { prefs ->
        prefs[Keys.VIDEO_QUALITY]?.let { VideoQuality.valueOf(it) } ?: VideoQuality.QUALITY_1080P
    }

    val videoFps: Flow<VideoFps> = context.dataStore.data.map { prefs ->
        prefs[Keys.VIDEO_FPS]?.let { VideoFps.valueOf(it) } ?: VideoFps.FPS_30
    }

    val screenMode: Flow<ScreenMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.SCREEN_MODE]?.let { ScreenMode.valueOf(it) } ?: ScreenMode.BLACK
    }

    val maxDuration: Flow<MaxDuration> = context.dataStore.data.map { prefs ->
        prefs[Keys.MAX_DURATION]?.let { MaxDuration.valueOf(it) } ?: MaxDuration.FIFTEEN_MIN
    }

    val antiPocketEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ANTI_POCKET] ?: false
    }

    val vibrationOnTrigger: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.VIBRATION_ON_TRIGGER] ?: true
    }

    val vibrationPeriodic: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.VIBRATION_PERIODIC] ?: false
    }

    val locationTagging: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOCATION_TAGGING] ?: false
    }

    val storageLocation: Flow<StorageLocation> = context.dataStore.data.map { prefs ->
        prefs[Keys.STORAGE_LOCATION]?.let { StorageLocation.valueOf(it) } ?: StorageLocation.INTERNAL
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETE] ?: false
    }

    val cooldownMs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.COOLDOWN_MS] ?: 2000L
    }

    val appTheme: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_THEME]?.let { AppTheme.valueOf(it) } ?: AppTheme.SYSTEM
    }

    val chunkInterval: Flow<ChunkInterval> = context.dataStore.data.map { prefs ->
        prefs[Keys.CHUNK_INTERVAL]?.let { ChunkInterval.valueOf(it) } ?: ChunkInterval.TEN_MIN
    }

    /** Evidence integrity: compute SHA-256 hash after each video save. Default OFF. */
    val enableHashing: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ENABLE_HASHING] ?: false
    }

    val videoStabilizationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.VIDEO_STABILIZATION] ?: true // Default to true for best quality
    }

    val defaultZoom: Flow<DefaultZoom> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_ZOOM]?.let { DefaultZoom.valueOf(it) } ?: DefaultZoom.NORMAL
    }

    val customStorageUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_STORAGE_URI]
    }

    val defaultCamera: Flow<DefaultCamera> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_CAMERA]?.let { DefaultCamera.valueOf(it) } ?: DefaultCamera.REAR
    }

    // --- Setters ---

    suspend fun setTriggerType(type: TriggerType) {
        context.dataStore.edit { it[Keys.TRIGGER_TYPE] = type.name }
    }

    suspend fun setRecordingMode(mode: RecordingMode) {
        context.dataStore.edit { it[Keys.RECORDING_MODE] = mode.name }
    }

    suspend fun setVideoQuality(quality: VideoQuality) {
        context.dataStore.edit { it[Keys.VIDEO_QUALITY] = quality.name }
    }

    suspend fun setVideoFps(fps: VideoFps) {
        context.dataStore.edit { it[Keys.VIDEO_FPS] = fps.name }
    }

    suspend fun setScreenMode(mode: ScreenMode) {
        context.dataStore.edit { it[Keys.SCREEN_MODE] = mode.name }
    }

    suspend fun setMaxDuration(duration: MaxDuration) {
        context.dataStore.edit { it[Keys.MAX_DURATION] = duration.name }
    }

    suspend fun setAntiPocket(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANTI_POCKET] = enabled }
    }

    suspend fun setVibrationOnTrigger(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATION_ON_TRIGGER] = enabled }
    }

    suspend fun setVibrationPeriodic(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATION_PERIODIC] = enabled }
    }

    suspend fun setLocationTagging(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOCATION_TAGGING] = enabled }
    }

    suspend fun setStorageLocation(location: StorageLocation) {
        context.dataStore.edit { it[Keys.STORAGE_LOCATION] = location.name }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setCooldownMs(ms: Long) {
        context.dataStore.edit { it[Keys.COOLDOWN_MS] = ms }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.APP_THEME] = theme.name }
    }

    suspend fun setChunkInterval(interval: ChunkInterval) {
        context.dataStore.edit { it[Keys.CHUNK_INTERVAL] = interval.name }
    }

    suspend fun setEnableHashing(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_HASHING] = enabled }
    }

    suspend fun setVideoStabilizationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIDEO_STABILIZATION] = enabled }
    }

    suspend fun setDefaultZoom(zoom: DefaultZoom) {
        context.dataStore.edit { it[Keys.DEFAULT_ZOOM] = zoom.name }
    }

    suspend fun setCustomStorageUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(Keys.CUSTOM_STORAGE_URI)
            else it[Keys.CUSTOM_STORAGE_URI] = uri
        }
    }

    suspend fun setDefaultCamera(camera: DefaultCamera) {
        context.dataStore.edit { it[Keys.DEFAULT_CAMERA] = camera.name }
    }
}
