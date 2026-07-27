package com.camlauncher.app.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Detects the device OEM and provides manufacturer-specific setup steps
 * (autostart, battery optimization) with deep-link intents that fall back
 * gracefully when OEM activities are unavailable.
 */
object OemDeviceHelper {

    enum class OemType(val displayName: String) {
        XIAOMI("Xiaomi (MIUI / HyperOS)"),
        SAMSUNG("Samsung (One UI)"),
        HUAWEI("Huawei (EMUI)"),
        OPPO("OPPO (ColorOS)"),
        REALME("Realme (Realme UI)"),
        VIVO("Vivo (Funtouch / OriginOS)"),
        ONEPLUS("OnePlus (OxygenOS)"),
        STOCK("Android")
    }

    data class OemSetupStep(
        val title: String,
        val description: String,
        /** Returns an intent that can be launched; always safe (pre-resolved or fallback). */
        val createIntent: (Context) -> Intent
    )

    fun getOemType(): OemType {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return when {
            manufacturer == "xiaomi" || brand == "redmi" || brand == "poco" -> OemType.XIAOMI
            manufacturer == "samsung" -> OemType.SAMSUNG
            manufacturer == "huawei" || manufacturer == "honor" -> OemType.HUAWEI
            manufacturer == "oppo" -> OemType.OPPO
            manufacturer == "realme" -> OemType.REALME
            manufacturer == "vivo" -> OemType.VIVO
            manufacturer == "oneplus" -> OemType.ONEPLUS
            // Tier 2 / near-stock: Pixel, Nothing, Nubia, and everything else
            else -> OemType.STOCK
        }
    }

    /**
     * Returns true if the app is already whitelisted from battery optimizations.
     */
    fun isBatteryOptimized(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Returns the list of OEM-specific setup steps for the current device.
     * Steps include autostart, battery optimization, and any other OEM-specific
     * requirements. Each step's intent is safe to launch (fallback guaranteed).
     */
    fun getSetupSteps(context: Context): List<OemSetupStep> {
        val oem = getOemType()
        val steps = mutableListOf<OemSetupStep>()

        // --- Autostart (Tier 1 OEMs only) ---
        when (oem) {
            OemType.XIAOMI -> steps.add(OemSetupStep(
                title = "Enable Autostart",
                description = "Allows CamLauncher to start automatically after reboot and prevents accessibility revocation",
                createIntent = { ctx ->
                    resolveOemIntent(ctx,
                        ComponentName("com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    )
                }
            ))
            OemType.HUAWEI -> steps.add(OemSetupStep(
                title = "Enable Autostart",
                description = "Allows CamLauncher to start automatically and stay active in the background",
                createIntent = { ctx ->
                    resolveOemIntent(ctx,
                        ComponentName("com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                    )
                }
            ))
            OemType.OPPO, OemType.REALME -> steps.add(OemSetupStep(
                title = "Enable Autostart",
                description = "Prevents the system from blocking CamLauncher after reboot",
                createIntent = { ctx ->
                    resolveOemIntent(ctx,
                        ComponentName("com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                    )
                }
            ))
            OemType.VIVO -> steps.add(OemSetupStep(
                title = "Enable Autostart",
                description = "Allows CamLauncher to start and run in the background",
                createIntent = { ctx ->
                    resolveOemIntent(ctx,
                        ComponentName("com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                    )
                }
            ))
            // Samsung, OnePlus, STOCK — no autostart concept
            else -> { /* no autostart step */ }
        }

        // --- Battery / Power Optimization ---
        val batteryStep = when (oem) {
            OemType.XIAOMI -> OemSetupStep(
                title = "Battery → No Restrictions",
                description = "Set battery saver to \"No restrictions\" to prevent CamLauncher from being killed",
                createIntent = { ctx ->
                    resolveOemIntent(ctx,
                        ComponentName("com.miui.powerkeeper",
                            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                        // Secondary fallback for newer HyperOS
                        ComponentName("com.miui.securitycenter",
                            "com.miui.securitycenter.MainActivity")
                    )
                }
            )
            OemType.SAMSUNG -> OemSetupStep(
                title = "Battery → Unrestricted",
                description = "Set battery usage to \"Unrestricted\" and remove from Sleeping Apps",
                createIntent = { ctx ->
                    resolveOemIntent(ctx,
                        ComponentName("com.samsung.android.lool",
                            "com.samsung.android.sm.battery.ui.BatteryActivity")
                    )
                }
            )
            OemType.HUAWEI -> OemSetupStep(
                title = "Battery → No Restrictions",
                description = "Disable power-intensive prompt and set to \"No restrictions\"",
                createIntent = { ctx ->
                    resolveOemIntent(ctx,
                        ComponentName("com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity")
                    )
                }
            )
            OemType.OPPO, OemType.REALME -> OemSetupStep(
                title = "Battery → Allow Background",
                description = "Allow background activity to keep CamLauncher running",
                createIntent = { ctx ->
                    resolveOemIntent(ctx,
                        ComponentName("com.coloros.oppoguardelf",
                            "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")
                    )
                }
            )
            OemType.VIVO -> OemSetupStep(
                title = "Battery → No Restrictions",
                description = "Set battery optimization to \"No restrictions\"",
                createIntent = { ctx ->
                    resolveOemIntent(ctx,
                        ComponentName("com.vivo.abe",
                            "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")
                    )
                }
            )
            // OnePlus, STOCK, Pixel, Nothing, Nubia — standard battery optimization
            else -> OemSetupStep(
                title = "Battery → Unrestricted",
                description = "Set battery usage to \"Unrestricted\" for reliable background operation",
                createIntent = { ctx -> createBatteryOptimizationIntent(ctx) }
            )
        }
        steps.add(batteryStep)

        // --- Lock in Recents (Xiaomi-specific, very important for MIUI) ---
        if (oem == OemType.XIAOMI) {
            steps.add(OemSetupStep(
                title = "Lock in Recent Apps",
                description = "Long-press CamLauncher in Recents → tap the lock icon to prevent clearing",
                // This is a manual step — we open App Info as the closest actionable screen
                createIntent = { ctx -> createAppInfoIntent(ctx) }
            ))
        }

        return steps
    }

    // ---- Intent resolution with fallback chain ----

    /**
     * Tries each OEM ComponentName in order; falls back to standard battery settings
     * and finally to App Info if nothing resolves.
     */
    private fun resolveOemIntent(context: Context, vararg components: ComponentName): Intent {
        for (component in components) {
            val intent = Intent().apply { this.component = component }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                return intent
            }
        }
        // Fallback: standard battery optimization settings
        val batteryIntent = createBatteryOptimizationIntent(context)
        if (context.packageManager.resolveActivity(batteryIntent, 0) != null) {
            return batteryIntent
        }
        // Final fallback: App Info
        return createAppInfoIntent(context)
    }

    /**
     * Standard Android battery optimization request intent.
     */
    private fun createBatteryOptimizationIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            createAppInfoIntent(context)
        }
    }

    /**
     * Final fallback — opens the app's system info page.
     */
    private fun createAppInfoIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}
