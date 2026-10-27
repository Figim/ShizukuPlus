package af.shizuku.manager.update

import af.shizuku.manager.utils.SettingsBackupManager
import android.content.Context
import android.os.Environment
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.io.File

object UpdateInstaller {
    private const val TAG = "UpdateInstaller"
    const val AUTO_BACKUP_FILENAME = "shizuku_plus_auto_backup.json"

    fun getBackupFile(context: Context): File? {
        val appExtDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val primaryFile = if (appExtDir != null) File(appExtDir, AUTO_BACKUP_FILENAME) else null
        if (primaryFile?.exists() == true) return primaryFile

        val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val fallbackPublic = File(publicDownloadDir, AUTO_BACKUP_FILENAME)
        if (fallbackPublic.exists()) return fallbackPublic

        val internalFile = File(context.filesDir, AUTO_BACKUP_FILENAME)
        if (internalFile.exists()) return internalFile

        return primaryFile ?: fallbackPublic
    }

    /**
     * Performs a seamless background update when standard in-place installation is
     * blocked (e.g. signature mismatch between Debug and Release builds, or corrupted install).
     *
     * Automatically backs up all preferences and app authorization state to a persistent location,
     * detaches a shell process via Shizuku (UID 2000) or Root, removes the conflicting package,
     * installs the new APK, auto-grants permissions, and relaunches the app so the backup is
     * immediately imported on startup.
     */
    fun forceUpdateWithShizuku(
        context: Context,
        apkFile: File,
    ): Boolean {
        val hasShizuku = Shizuku.pingBinder()
        val isRoot =
            runCatching {
                Shell.getShell().isRoot
            }.getOrDefault(false)

        if (!hasShizuku && !isRoot) {
            Timber.tag(TAG).w("No root or Shizuku available for force update.")
            return false
        }

        return try {
            val packageName = context.packageName
            val backupJson = SettingsBackupManager.export(context)
            val apkBytes = apkFile.readBytes()

            val script =
                """
                #!/system/bin/sh
                sleep 1
                pm uninstall $packageName
                pm install -r -d /data/local/tmp/update.apk
                INSTALL_STATUS=${'$'}?
                if [ ${'$'}INSTALL_STATUS -eq 0 ]; then
                    pm grant $packageName android.permission.POST_NOTIFICATIONS 2>/dev/null
                    pm grant $packageName android.permission.WRITE_SECURE_SETTINGS 2>/dev/null
                    appops set $packageName SYSTEM_ALERT_WINDOW allow 2>/dev/null
                    appops set $packageName GET_USAGE_STATS allow 2>/dev/null

                    if [ -f /data/local/tmp/shizukuplus_settings.json ]; then
                        DEST_DIR="/sdcard/Android/data/$packageName/files/Download"
                        mkdir -p "${'$'}DEST_DIR" 2>/dev/null
                        cp /data/local/tmp/shizukuplus_settings.json "${'$'}DEST_DIR/$AUTO_BACKUP_FILENAME" 2>/dev/null
                        cp /data/local/tmp/shizukuplus_settings.json "/sdcard/Download/$AUTO_BACKUP_FILENAME" 2>/dev/null
                        rm -f /data/local/tmp/shizukuplus_settings.json
                    fi
                    am start -n $packageName/af.shizuku.manager.MainActivity
                fi
                rm -f /data/local/tmp/update.apk /data/local/tmp/force_update.sh
                """.trimIndent()

            if (isRoot) {
                // Root shell path: libsu writes directly to /data/local/tmp
                File("/data/local/tmp/update.apk").writeBytes(apkBytes)
                File("/data/local/tmp/shizukuplus_settings.json").writeText(backupJson)
                val scriptFile = File("/data/local/tmp/force_update.sh")
                scriptFile.writeText(script)
                Shell
                    .cmd(
                        "chmod 644 /data/local/tmp/update.apk /data/local/tmp/shizukuplus_settings.json && " +
                            "chmod 755 /data/local/tmp/force_update.sh && " +
                            "nohup /system/bin/sh /data/local/tmp/force_update.sh >/dev/null 2>&1 &",
                    ).exec()
                Timber.tag(TAG).i("Detached force update initiated via Root.")
                true
            } else {
                // Shizuku (UID 2000 shell) path: pipe data safely over stdin
                val apkProc =
                    Shizuku.newProcess(
                        arrayOf("sh", "-c", "cat > /data/local/tmp/update.apk && chmod 644 /data/local/tmp/update.apk"),
                        null,
                        null,
                    ) ?: return false
                apkProc.outputStream.use { it.write(apkBytes) }
                apkProc.waitFor()

                val backupProc =
                    Shizuku.newProcess(
                        arrayOf("sh", "-c", "cat > /data/local/tmp/shizukuplus_settings.json && chmod 644 /data/local/tmp/shizukuplus_settings.json"),
                        null,
                        null,
                    ) ?: return false
                backupProc.outputStream.use { it.write(backupJson.toByteArray(Charsets.UTF_8)) }
                backupProc.waitFor()

                val scriptProc =
                    Shizuku.newProcess(
                        arrayOf("sh", "-c", "cat > /data/local/tmp/force_update.sh && chmod 755 /data/local/tmp/force_update.sh"),
                        null,
                        null,
                    ) ?: return false
                scriptProc.outputStream.use { it.write(script.toByteArray(Charsets.UTF_8)) }
                scriptProc.waitFor()

                val execProc =
                    Shizuku.newProcess(
                        arrayOf("sh", "-c", "nohup /system/bin/sh /data/local/tmp/force_update.sh >/dev/null 2>&1 &"),
                        null,
                        null,
                    )
                execProc?.waitFor()
                Timber.tag(TAG).i("Detached force update initiated via Shizuku.")
                true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to force update with Shizuku/Root")
            false
        }
    }
}
