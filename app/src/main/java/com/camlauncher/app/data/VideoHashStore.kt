package com.camlauncher.app.data

import android.content.Context

/**
 * Lightweight SharedPreferences store mapping video URI strings to their SHA-256 hashes.
 * Hashes are stored only when the user has enabled "Evidence Integrity Hashing" in Settings.
 */
class VideoHashStore(context: Context) {

    private val prefs = context.getSharedPreferences("video_hashes", Context.MODE_PRIVATE)

    /**
     * Normalizes a MediaStore URI to a stable key (e.g. "video_123").
     * This avoids issues where the same file is referenced via different authorities
     * (e.g. "external" vs "external_primary").
     */
    private fun normalizeKey(uriStr: String): String {
        val uri = android.net.Uri.parse(uriStr)
        val id = uri.lastPathSegment ?: return uriStr
        // If it's a MediaStore URI, it usually contains /video/ or /audio/
        return when {
            uriStr.contains("/video/") -> "video_$id"
            uriStr.contains("/audio/") -> "audio_$id"
            uriStr.contains("/images/") -> "image_$id"
            else -> uriStr
        }
    }

    fun saveHash(uriStr: String, hash: String) {
        prefs.edit().putString(normalizeKey(uriStr), hash).apply()
    }

    fun getHash(uriStr: String): String? = prefs.getString(normalizeKey(uriStr), null)
}
