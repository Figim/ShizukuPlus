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

    /** Reinstall copy kept by [SettingsBackupManager.saveAutoBackup] (shell-owned, survives uninstall). */
    const val REINSTALL_BACKUP_DIR = "/data/local/tmp/shizukuplus"
    const val REINSTALL_BACKUP_FILENAME = "settings_backup.json"

    fun reinstallBackupFile(): File = File(REINSTALL_BACKUP_DIR, REINSTALL_BACKUP_FILENAME)

    /**
     * Where the force-update script hands the settings over across its uninstall/reinstall. Only
     * locations other apps can't write: /data/local/tmp (shell/root only) and this app's own
     * external dir. Restored with privileged settings applied as stored.
     */
    fun handoffBackupCandidates(context: Context): List<File> =
        listOfNotNull(
            File("/data/local/tmp", AUTO_BACKUP_FILENAME),
            File("/data/local/tmp", "shizukuplus_settings.json"),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, AUTO_BACKUP_FILENAME) },
        )

    fun trustedBackupCandidates(context: Context): List<File> = handoffBackupCandidates(context) + reinstallBackupFile()

    /**
     * Public Downloads copies (older builds wrote one there). Any app with storage access can
     * create or replace these, so they are never restored automatically and never with
     * privileged settings trusted.
     */
    fun publicBackupCandidates(): List<File> =
        listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), AUTO_BACKUP_FILENAME),
            File("/sdcard/Download", AUTO_BACKUP_FILENAME),
            File("/storage/emulated/0/Download", AUTO_BACKUP_FILENAME),
        )

    private fun File.usable(): Boolean = runCatching { isFile && length() > 0 }.getOrDefault(false)

    const val KEY_RESTORED_BACKUP_FINGERPRINT = "restored_backup_fingerprint"
    private const val AUTO_RESTORE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

    private fun fingerprint(file: File): String = "${file.length()}:${file.lastModified()}"

    /**
     * True once this exact file (size + mtime) was restored. Deleting it isn't enough: the script
     * writes the Android/data copy as shell, so the app often can't delete it, and the same old
     * backup was restored again on every launch, reverting the user's later changes.
     */
    fun wasRestored(
        context: Context,
        file: File,
    ): Boolean =
        runCatching {
            af.shizuku.manager.ShizukuSettings
                .getPreferences()
                .getString(KEY_RESTORED_BACKUP_FINGERPRINT, null)
                ?.split('|')
                ?.contains(fingerprint(file)) == true
        }.getOrDefault(false)

    /** Record [file] as restored, then remove every copy that can be removed. */
    fun consumeBackup(
        context: Context,
        file: File,
    ) {
        runCatching {
            val prefs =
                af.shizuku.manager.ShizukuSettings
                    .getPreferences()
            val seen = prefs.getString(KEY_RESTORED_BACKUP_FINGERPRINT, null)?.split('|').orEmpty()
            val updated = (seen + fingerprint(file)).distinct().takeLast(8).joinToString("|")
            prefs.edit().putString(KEY_RESTORED_BACKUP_FINGERPRINT, updated).commit()
        }
        deleteAllBackupCopies(context)
    }

    /**
     * The backup to restore automatically after a force update, or null: a trusted copy that is
     * recent (the force-update script restores within seconds) and not restored before.
     */
    fun getBackupFile(context: Context): File? {
        val now = System.currentTimeMillis()
        val handoff =
            handoffBackupCandidates(context).firstOrNull {
                it.usable() && now - it.lastModified() in 0L..AUTO_RESTORE_MAX_AGE_MS && !wasRestored(context, it)
            }
        if (handoff != null) return handoff
        // The reinstall copy only on an install that hasn't checked yet (fresh install or data
        // cleared); afterwards it is this install's own, continuously updated snapshot.
        val reinstall = reinstallBackupFile()
        if (!SettingsBackupManager.isRestoreChecked() && reinstall.usable() && !wasRestored(context, reinstall)) {
            return reinstall
        }
        return null
    }

    /**
     * Restores a pending trusted backup (see [getBackupFile]) and marks this install as checked.
     * Returns true only if settings actually changed, so callers show "restored" only then.
     */
    fun restorePendingBackup(context: Context): Boolean {
        try {
            val file = getBackupFile(context) ?: return false
            val json = file.readText()
            if (SettingsBackupManager.matchesCurrent(context, json)) {
                consumeBackup(context, file)
                return false
            }
            if (!SettingsBackupManager.importAndCommit(context, json, trustPrivileged = true)) return false
            consumeBackup(context, file)
            Timber.tag(TAG).i("Restored settings from %s", file.absolutePath)
            return true
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Settings restore failed")
            return false
        } finally {
            SettingsBackupManager.markRestoreChecked()
        }
    }

    fun isTrustedBackupLocation(
        context: Context,
        file: File,
    ): Boolean {
        val path = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        return trustedBackupCandidates(context).any { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) == path }
    }

    /**
     * After a restore, remove every copy: a leftover one was restored again on the next launch
     * (reverting changes made since), and copies hold settings such as scanner API keys.
     */
    fun deleteAllBackupCopies(context: Context) {
        // Not the reinstall copy: saveAutoBackup keeps rewriting it on purpose.
        (handoffBackupCandidates(context) + publicBackupCandidates() + File(context.filesDir, AUTO_BACKUP_FILENAME)).forEach { f ->
            runCatching { if (f.exists()) f.delete() }
        }
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
                    appops set $packageName MANAGE_EXTERNAL_STORAGE allow 2>/dev/null

                    if [ -f /data/local/tmp/shizukuplus_settings.json ]; then
                        cp /data/local/tmp/shizukuplus_settings.json "/data/local/tmp/$AUTO_BACKUP_FILENAME" 2>/dev/null
                        chmod 666 "/data/local/tmp/$AUTO_BACKUP_FILENAME" 2>/dev/null

                        DEST_DIR="/sdcard/Android/data/$packageName/files/Download"
                        mkdir -p "${'$'}DEST_DIR" 2>/dev/null
                        cp /data/local/tmp/shizukuplus_settings.json "${'$'}DEST_DIR/$AUTO_BACKUP_FILENAME" 2>/dev/null

                        for DATA_DIR in "/data/data/$packageName/files" "/data/user/0/$packageName/files"; do
                            mkdir -p "${'$'}DATA_DIR" 2>/dev/null
                            cp /data/local/tmp/shizukuplus_settings.json "${'$'}DATA_DIR/$AUTO_BACKUP_FILENAME" 2>/dev/null
                            chmod 666 "${'$'}DATA_DIR/$AUTO_BACKUP_FILENAME" 2>/dev/null
                        done

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
