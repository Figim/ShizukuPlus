package af.shizuku.manager.utils

import af.shizuku.manager.admin.DhizukuAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import timber.log.Timber

/**
 * Hides/unhides apps through DevicePolicyManager when Shizuku+ itself is the device or profile
 * owner (#632). DPMS only accepts setApplicationHidden from the app that owns the admin
 * component, so this must run in the manager; the Shizuku server (shell/root uid) can't do it.
 * Every call fails soft so callers can fall back to the server's disable/suspend chain.
 */
object DpmAppHider {
    private fun dpm(context: Context): DevicePolicyManager? = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    private fun admin(context: Context) = ComponentName(context, DhizukuAdminReceiver::class.java)

    @JvmStatic
    fun isAvailable(context: Context): Boolean =
        runCatching {
            val dpm = dpm(context) ?: return false
            dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
        }.getOrDefault(false)

    @JvmStatic
    fun isHidden(
        context: Context,
        packageName: String,
    ): Boolean =
        runCatching {
            dpm(context)?.isApplicationHidden(admin(context), packageName) == true
        }.getOrDefault(false)

    /** Returns true only if DPMS applied the change. */
    @JvmStatic
    fun setHidden(
        context: Context,
        packageName: String,
        hidden: Boolean,
    ): Boolean =
        try {
            dpm(context)?.setApplicationHidden(admin(context), packageName, hidden) == true
        } catch (e: Exception) {
            Timber.w(e, "DPM setApplicationHidden(%s, %b) failed", packageName, hidden)
            false
        }
}
