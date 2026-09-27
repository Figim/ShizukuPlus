package af.shizuku.manager.home

import af.shizuku.manager.R
import af.shizuku.manager.adb.AdbPairingAccessibilityService
import af.shizuku.manager.utils.EnvironmentUtils
import af.shizuku.manager.utils.SettingsPage
import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.TypefaceSpan
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun Context.showAccessibilityDialog() {
    if (isAccessibilityEnabled()) {
        showNavigateDialog()
        return
    }

    val hasWriteSecureSettings = (checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
    if (hasWriteSecureSettings) {
        if (enableAccessibilityService()) {
            showNavigateDialog()
            return
        }
    }

    // Attempt automatic elevation via root if available on device
    if (EnvironmentUtils.isRooted()) {
        try {
            val process =
                Runtime.getRuntime().exec(
                    arrayOf(
                        "su",
                        "-c",
                        "cmd appops set $packageName ACCESS_RESTRICTED_SETTINGS allow " +
                            "&& pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
                    ),
                )
            process.waitFor()
            if (enableAccessibilityService()) {
                showNavigateDialog()
                return
            }
        } catch (_: Throwable) {
        }
    }

    val installer = packageManager.getInstallerPackageName(packageName)
    val isInstalledByPlayOrAdb = (installer == "com.android.vending") || (installer == null)
    val isRestricted = !isInstalledByPlayOrAdb && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)

    if (isRestricted) {
        showPermissionDialog()
    } else {
        showEnableDialog()
    }
}

private fun Context.showPermissionDialog() {
    val permissionName = "ACCESS_RESTRICTED_SETTINGS"
    val permissionCommand = "adb shell cmd appops set $packageName $permissionName allow"
    val styledPermissionCommand =
        SpannableString(permissionCommand).apply {
            setSpan(TypefaceSpan("monospace"), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    MaterialAlertDialogBuilder(this)
        .setTitle(android.R.string.dialog_alert_title)
        .setMessage(
            TextUtils.expandTemplate(
                getString(R.string.dialog_adb_pairing_accessibility_permission),
                permissionName,
                styledPermissionCommand,
            ),
        ).setPositiveButton(R.string.accessibility_action_app_info) { _, _ ->
            try {
                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                startActivity(intent)
            } catch (_: Throwable) {
            }
        }.setNeutralButton(R.string.action_continue) { _, _ -> showEnableDialog() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

private fun Context.showEnableDialog() {
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_adb_pairing_title)
        .setMessage(R.string.dialog_adb_pairing_accessibility_enable)
        .setPositiveButton(R.string.enable) { _, _ ->
            SettingsPage.Accessibility.launch(this)
        }.setNegativeButton(android.R.string.cancel, null)
        .show()
}

private fun Context.showNavigateDialog() {
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_adb_pairing_title)
        .setMessage(R.string.dialog_adb_pairing_accessibility_navigate)
        .setPositiveButton(R.string.development_settings) { _, _ ->
            SettingsPage.Developer.HighlightWirelessDebugging.launch(this)
        }.setNegativeButton(android.R.string.cancel, null)
        .show()
}

private fun Context.getEnabledAccessibilityServices(): List<String>? {
    val enabledServices =
        Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
    return enabledServices?.split(":")
}

private fun Context.isAccessibilityEnabled(): Boolean {
    val accessibilityServiceName = "$packageName/${AdbPairingAccessibilityService::class.java.canonicalName}"
    return getEnabledAccessibilityServices()?.any { it.equals(accessibilityServiceName) } ?: false
}

private fun Context.enableAccessibilityService(): Boolean {
    if (isAccessibilityEnabled()) return true

    val accessibilityServiceName = "$packageName/${AdbPairingAccessibilityService::class.java.canonicalName}"
    val enabledServices = getEnabledAccessibilityServices()
    val newServices =
        if (enabledServices.isNullOrEmpty()) {
            accessibilityServiceName
        } else {
            enabledServices.joinToString(":") + ":$accessibilityServiceName"
        }

    return try {
        Settings.Secure.putString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            newServices,
        )
        Settings.Secure.putInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            1,
        )
        isAccessibilityEnabled()
    } catch (_: Throwable) {
        false
    }
}
