package com.camlauncher.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Open-Core stub for LicenseManager.
 * The production implementation interfaces with the DRM & licensing backend.
 */
object LicenseManager {

    const val GUMROAD_PRODUCT_ID = "STUB_PRODUCT_ID"
    const val GUMROAD_PURCHASE_URL = "https://appentric.gumroad.com/l/camlauncher"

    /**
     * Stub check for open-core builds.
     */
    fun isActivated(context: Context): Boolean {
        return false
    }

    /**
     * Stub verification for open-core builds.
     */
    suspend fun verifyAndActivate(
        context: Context,
        licenseKey: String
    ): LicenseResult = withContext(Dispatchers.IO) {
        LicenseResult.Error("License verification is provided in the production release build.")
    }
}

sealed class LicenseResult {
    data class Success(val email: String) : LicenseResult()
    data object InvalidKey : LicenseResult()
    data object Revoked : LicenseResult()
    data object NetworkError : LicenseResult()
    data class Error(val message: String) : LicenseResult()
}
