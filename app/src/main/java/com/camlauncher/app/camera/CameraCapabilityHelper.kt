package com.camlauncher.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.media.CamcorderProfile
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.core.content.ContextCompat
import com.camlauncher.app.data.VideoFps
import com.camlauncher.app.data.VideoQuality
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object CameraCapabilityHelper {

    suspend fun getCapabilities(context: Context, lensFacing: Int = CameraSelector.LENS_FACING_BACK): CameraCapabilities = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                
                // Find the requested camera info
                val cameraInfo = provider.availableCameraInfos.firstOrNull {
                    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        CameraSelector.DEFAULT_FRONT_CAMERA.filter(listOf(it)).isNotEmpty()
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA.filter(listOf(it)).isNotEmpty()
                    }
                }

                if (cameraInfo == null) {
                    cont.resume(CameraCapabilities.empty())
                    return@addListener
                }

                // Get supported video qualities
                val cameraQualities = QualitySelector.getSupportedQualities(cameraInfo)
                val supportedQualities = mutableListOf<VideoQuality>()
                
                if (cameraQualities.contains(Quality.SD)) supportedQualities.add(VideoQuality.QUALITY_480P)
                if (cameraQualities.contains(Quality.HD)) supportedQualities.add(VideoQuality.QUALITY_720P)
                if (cameraQualities.contains(Quality.FHD)) supportedQualities.add(VideoQuality.QUALITY_1080P)
                if (cameraQualities.contains(Quality.UHD)) supportedQualities.add(VideoQuality.QUALITY_2160P)
                
                // Fallback if none found
                if (supportedQualities.isEmpty()) {
                    supportedQualities.add(VideoQuality.QUALITY_1080P)
                }

                // Resolve numeric camera ID for CamcorderProfile probing
                val cameraId = try {
                    val camera2Info = Camera2CameraInfo.from(cameraInfo)
                    camera2Info.cameraId.toIntOrNull() ?: 0
                } catch (e: Exception) { 0 }

                // Build per-quality FPS map by probing CamcorderProfile
                val fpsForQuality = mutableMapOf<VideoQuality, List<VideoFps>>()
                for (quality in supportedQualities) {
                    val fpsList = mutableListOf(VideoFps.FPS_30) // 30fps baseline
                    val has60 = when (quality) {
                        VideoQuality.QUALITY_2160P -> false // No standard 60fps 4K profile exists
                        VideoQuality.QUALITY_1080P -> {
                            @Suppress("DEPRECATION")
                            CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH_SPEED_1080P)
                        }
                        VideoQuality.QUALITY_720P -> {
                            @Suppress("DEPRECATION")
                            CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH_SPEED_720P)
                        }
                        VideoQuality.QUALITY_480P -> {
                            @Suppress("DEPRECATION")
                            CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH_SPEED_480P)
                        }
                    }
                    if (has60) fpsList.add(VideoFps.FPS_60)
                    fpsForQuality[quality] = fpsList.sortedByDescending { it.fps }
                }

                cont.resume(CameraCapabilities(
                    supportedQualities = supportedQualities.distinct().sortedBy { it.height }.reversed(),
                    fpsForQuality = fpsForQuality
                ))
            } catch (e: Exception) {
                cont.resume(CameraCapabilities.empty())
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun getUltraWideCameraSelector(provider: ProcessCameraProvider): CameraSelector? {
        val ultraWideInfo = provider.availableCameraInfos.firstOrNull { info ->
            if (info.lensFacing != CameraSelector.LENS_FACING_BACK) return@firstOrNull false
            try {
                val camera2Info = Camera2CameraInfo.from(info)
                val focalLengths = camera2Info.getCameraCharacteristic(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                )
                val minFocal = focalLengths?.minOrNull() ?: Float.MAX_VALUE
                minFocal < 3.0f // Ultra-wide lenses are typically 1.5mm - 2.5mm
            } catch (e: Exception) { false }
        }
        return ultraWideInfo?.let { info ->
            CameraSelector.Builder().addCameraFilter { _ -> listOf(info) }.build()
        }
    }
}

data class CameraCapabilities(
    val supportedQualities: List<VideoQuality>,
    val fpsForQuality: Map<VideoQuality, List<VideoFps>>
) {
    /** Flat list of all FPS values supported at any quality (backward compat) */
    val supportedFps: List<VideoFps>
        get() = fpsForQuality.values.flatten().distinct().sortedByDescending { it.fps }

    /** Get available FPS options for a specific quality */
    fun getFpsForQuality(quality: VideoQuality): List<VideoFps> =
        fpsForQuality[quality] ?: listOf(VideoFps.FPS_30)

    companion object {
        fun empty() = CameraCapabilities(
            supportedQualities = listOf(VideoQuality.QUALITY_1080P, VideoQuality.QUALITY_720P),
            fpsForQuality = mapOf(
                VideoQuality.QUALITY_1080P to listOf(VideoFps.FPS_30),
                VideoQuality.QUALITY_720P to listOf(VideoFps.FPS_30)
            )
        )
    }
}
