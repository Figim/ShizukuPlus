package af.shizuku.manager.backup

import af.shizuku.manager.utils.EnvironmentUtils
import af.shizuku.manager.utils.ShizukuStateMachine
import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.ShizukuPlusAPI
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

class BackupViewModel(
    app: Application,
) : AndroidViewModel(app) {
    data class AppEntry(
        val packageName: String,
        val label: String,
        val versionName: String,
        val isSystem: Boolean,
        val allowBackup: Boolean,
        val isFrozen: Boolean = false,
    )

    sealed class UiState {
        object Loading : UiState()

        data class Loaded(
            val apps: List<AppEntry>,
        ) : UiState()

        data class Error(
            val msg: String,
        ) : UiState()

        object ServiceNotRunning : UiState()
    }

    sealed class BackupEvent {
        data class BackupComplete(
            val pkg: String,
            val path: String,
        ) : BackupEvent()

        data class BatchComplete(
            val succeeded: Int,
            val failed: Int,
            val path: String,
        ) : BackupEvent()

        data class RestoreComplete(
            val pkg: String,
        ) : BackupEvent()

        data class FreezeChanged(
            val pkg: String,
            val nowFrozen: Boolean,
        ) : BackupEvent()

        data class Failure(
            val msg: String,
        ) : BackupEvent()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    private val _events = MutableSharedFlow<BackupEvent>()
    val events: SharedFlow<BackupEvent> = _events

    // Packages currently being backed up — drives per-row busy state in the adapter.
    private val _busyPackages = MutableStateFlow<Set<String>>(emptySet())
    val busyPackages: StateFlow<Set<String>> = _busyPackages

    // True while a batch backup is running; disables the "Backup All" menu item.
    private val _batchRunning = MutableStateFlow(false)
    val batchRunning: StateFlow<Boolean> = _batchRunning

    @Volatile private var allApps: List<AppEntry> = emptyList()
    private val _query = MutableStateFlow("")

    fun setQuery(q: String) {
        _query.value = q
        applyFilter()
    }

    private fun applyFilter() {
        if (allApps.isEmpty()) return
        val q = _query.value.trim().lowercase()
        val filtered =
            if (q.isEmpty()) {
                allApps
            } else {
                allApps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
            }
        _state.value = UiState.Loaded(filtered)
    }

    fun loadApps(includeSystem: Boolean = false) {
        _state.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            if (ShizukuStateMachine.get() != ShizukuStateMachine.State.RUNNING) {
                _state.value = UiState.ServiceNotRunning
                return@launch
            }
            try {
                val pm = getApplication<Application>().packageManager
                val bundles = ShizukuPlusAPI.BackupRestorePlus.listInstalledPackages(includeSystem)
                val entries =
                    bundles
                        .mapNotNull { b ->
                            val pkg = b.getString("packageName") ?: return@mapNotNull null
                            val label =
                                try {
                                    val info = pm.getApplicationInfo(pkg, 0)
                                    pm.getApplicationLabel(info).toString()
                                } catch (_: Exception) {
                                    pkg
                                }
                            AppEntry(
                                packageName = pkg,
                                label = label,
                                versionName = b.getString("versionName") ?: "",
                                isSystem = b.getBoolean("isSystem"),
                                // allowBackup defaults true if absent (old server without this key)
                                allowBackup = b.getBoolean("allowBackup", true),
                                // isFrozen now comes from the server bundle — no extra per-app IPC
                                isFrozen = b.getBoolean("isFrozen", false),
                            )
                        }.sortedBy { it.label.lowercase() }
                allApps = entries
                applyFilter()
            } catch (e: Exception) {
                Timber.e(e, "loadApps failed")
                _state.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Back up [entry]'s data. Exactly one of [outputDir] and [safTreeUri] must be non-null.
     *
     * - [outputDir]: write to a per-package subdirectory under this File path (app-private storage).
     * - [safTreeUri]: write to the user-chosen SAF tree (persisted via takePersistableUriPermission)
     *   using [DocumentsContract]. Preferred when the user has configured an export directory so
     *   the output is reachable by file managers and other apps on Android 10+ scoped storage.
     */
    fun backupAppData(
        entry: AppEntry,
        outputDir: File? = null,
        safTreeUri: Uri? = null,
    ) {
        val pkg = entry.packageName
        if (pkg in _busyPackages.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _busyPackages.value = _busyPackages.value + pkg
            var prepared = false
            try {
                ShizukuPlusAPI.BackupRestorePlus.forceStop(pkg)

                // prepareTempDebug is best-effort; Shizuku's privileged API can stream data
                // without debug mode on most paths, so a failure here is not fatal.
                prepared =
                    try {
                        ShizukuPlusAPI.ApkPatcher.prepareTempDebug(pkg)
                    } catch (e: Exception) {
                        Timber.w(e, "prepareTempDebug skipped for $pkg, attempting direct backup")
                        false
                    }

                var backedUpSomething = false
                val cr = getApplication<Application>().contentResolver

                val dataPfd =
                    try {
                        ShizukuPlusAPI.ApkPatcher.streamDataDir(pkg)
                    } catch (e: Exception) {
                        Timber.w(e, "streamDataDir failed for $pkg")
                        null
                    }
                if (dataPfd != null) {
                    if (writeBackupStream(safTreeUri, outputDir, pkg, "data.tar.gz", cr) { out ->
                            dataPfd.use { pfd ->
                                FileInputStream(pfd.fileDescriptor).use { it.copyTo(out) }
                            }
                        }
                    ) {
                        backedUpSomething = true
                    }
                }

                val extPfd =
                    try {
                        ShizukuPlusAPI.BackupRestorePlus.backupExternalData(pkg)
                    } catch (e: Exception) {
                        Timber.w(e, "backupExternalData failed for $pkg")
                        null
                    }
                if (extPfd != null) {
                    if (writeBackupStream(safTreeUri, outputDir, pkg, "external.tar.gz", cr) { out ->
                            extPfd.use { pfd ->
                                FileInputStream(pfd.fileDescriptor).use { it.copyTo(out) }
                            }
                        }
                    ) {
                        backedUpSomething = true
                    }
                }

                val obbPfd =
                    try {
                        ShizukuPlusAPI.BackupRestorePlus.backupObbData(pkg)
                    } catch (e: Exception) {
                        Timber.w(e, "backupObbData failed for $pkg")
                        null
                    }
                if (obbPfd != null) {
                    if (writeBackupStream(safTreeUri, outputDir, pkg, "obb.tar.gz", cr) { out ->
                            obbPfd.use { pfd ->
                                FileInputStream(pfd.fileDescriptor).use { it.copyTo(out) }
                            }
                        }
                    ) {
                        backedUpSomething = true
                    }
                }

                // Resolve SAF URI to a human-readable path for the snackbar; fall back to the raw
                // last path segment if EnvironmentUtils can't map the tree doc ID to a real path.
                val outputDesc =
                    if (safTreeUri != null) {
                        EnvironmentUtils.resolveExportedPath(pkg)
                            ?: safTreeUri.lastPathSegment ?: "backup folder"
                    } else {
                        outputDir?.absolutePath ?: "backup folder"
                    }
                if (backedUpSomething) {
                    _events.emit(BackupEvent.BackupComplete(pkg, outputDesc))
                } else {
                    _events.emit(BackupEvent.Failure("No data could be read for $pkg. The app may block backup access."))
                }
            } catch (e: Exception) {
                Timber.e(e, "Backup failed for $pkg")
                _events.emit(BackupEvent.Failure("Backup failed for $pkg: ${e.message}"))
            } finally {
                if (prepared) {
                    try {
                        ShizukuPlusAPI.ApkPatcher.restoreOriginal(pkg)
                    } catch (ex: Exception) {
                        Timber.w(ex, "restoreOriginal failed for $pkg")
                    }
                }
                _busyPackages.value = _busyPackages.value - pkg
            }
        }
    }

    /**
     * Restores external data for [entry] from [externalTarUri] (a SAF URI pointing to an
     * external.tar.gz file). Uses [ShizukuPlusAPI.BackupRestorePlus.restoreExternalData].
     */
    fun restoreExternalData(
        entry: AppEntry,
        externalTarUri: Uri,
    ) {
        val pkg = entry.packageName
        if (pkg in _busyPackages.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _busyPackages.value = _busyPackages.value + pkg
            try {
                ShizukuPlusAPI.BackupRestorePlus.forceStop(pkg)
                val cr = getApplication<Application>().contentResolver
                val pfd =
                    cr.openFileDescriptor(externalTarUri, "r")
                        ?: run {
                            _events.emit(BackupEvent.Failure("Could not open backup file for $pkg"))
                            return@launch
                        }
                val ok =
                    pfd.use {
                        ShizukuPlusAPI.BackupRestorePlus.restoreExternalData(pkg, it)
                    }
                if (ok) {
                    _events.emit(BackupEvent.RestoreComplete(pkg))
                } else {
                    _events.emit(BackupEvent.Failure("Restore failed for $pkg"))
                }
            } catch (e: Exception) {
                Timber.e(e, "restoreExternalData failed for $pkg")
                _events.emit(BackupEvent.Failure("Restore failed for $pkg: ${e.message}"))
            } finally {
                _busyPackages.value = _busyPackages.value - pkg
            }
        }
    }

    /**
     * Sequentially backs up all currently loaded user apps. Emits [BackupEvent.BatchComplete]
     * when done, or individual [BackupEvent.Failure] events for apps that fail.
     */
    fun backupAll(
        outputDir: File? = null,
        safTreeUri: Uri? = null,
    ) {
        if (_batchRunning.value) return
        val apps = allApps.ifEmpty { return }
        viewModelScope.launch(Dispatchers.IO) {
            _batchRunning.value = true
            var succeeded = 0
            var failed = 0
            val cr = getApplication<Application>().contentResolver
            for (entry in apps) {
                val pkg = entry.packageName
                if (pkg in _busyPackages.value) continue
                _busyPackages.value = _busyPackages.value + pkg
                var prepared = false
                try {
                    ShizukuPlusAPI.BackupRestorePlus.forceStop(pkg)
                    prepared =
                        try {
                            ShizukuPlusAPI.ApkPatcher.prepareTempDebug(pkg)
                        } catch (_: Exception) {
                            false
                        }

                    var backedUpSomething = false
                    val dataPfd =
                        try {
                            ShizukuPlusAPI.ApkPatcher.streamDataDir(pkg)
                        } catch (_: Exception) {
                            null
                        }
                    if (dataPfd != null) {
                        if (writeBackupStream(safTreeUri, outputDir, pkg, "data.tar.gz", cr) { out ->
                                dataPfd.use { pfd -> FileInputStream(pfd.fileDescriptor).use { it.copyTo(out) } }
                            }
                        ) {
                            backedUpSomething = true
                        }
                    }
                    val extPfd =
                        try {
                            ShizukuPlusAPI.BackupRestorePlus.backupExternalData(pkg)
                        } catch (_: Exception) {
                            null
                        }
                    if (extPfd != null) {
                        if (writeBackupStream(safTreeUri, outputDir, pkg, "external.tar.gz", cr) { out ->
                                extPfd.use { pfd -> FileInputStream(pfd.fileDescriptor).use { it.copyTo(out) } }
                            }
                        ) {
                            backedUpSomething = true
                        }
                    }
                    val obbPfd =
                        try {
                            ShizukuPlusAPI.BackupRestorePlus.backupObbData(pkg)
                        } catch (_: Exception) {
                            null
                        }
                    if (obbPfd != null) {
                        if (writeBackupStream(safTreeUri, outputDir, pkg, "obb.tar.gz", cr) { out ->
                                obbPfd.use { pfd -> FileInputStream(pfd.fileDescriptor).use { it.copyTo(out) } }
                            }
                        ) {
                            backedUpSomething = true
                        }
                    }
                    if (backedUpSomething) succeeded++ else failed++
                } catch (e: Exception) {
                    Timber.e(e, "Batch backup failed for $pkg")
                    failed++
                } finally {
                    if (prepared) {
                        try {
                            ShizukuPlusAPI.ApkPatcher.restoreOriginal(pkg)
                        } catch (_: Exception) {
                        }
                    }
                    _busyPackages.value = _busyPackages.value - pkg
                }
            }
            val outputDesc =
                if (safTreeUri != null) {
                    EnvironmentUtils.resolveExportedPath("") ?: safTreeUri.lastPathSegment ?: "backup folder"
                } else {
                    outputDir?.absolutePath ?: "backup folder"
                }
            _events.emit(BackupEvent.BatchComplete(succeeded, failed, outputDesc.trimEnd('/')))
            _batchRunning.value = false
        }
    }

    /**
     * Writes the output of [block] to a file named [fileName] under [pkg]'s backup directory.
     * Returns true if the file was successfully written, false if document creation failed.
     *
     * Uses [safTreeUri] (SAF) when provided; otherwise creates a subdirectory under [outputDir].
     * SAF strategy: tries a per-package subdirectory first; if the provider doesn't support
     * MIME_TYPE_DIR (e.g. the Downloads provider), falls back to a flat "{pkg}_{fileName}"
     * name in the tree root so the write still succeeds.
     */
    private fun writeBackupStream(
        safTreeUri: Uri?,
        outputDir: File?,
        pkg: String,
        fileName: String,
        cr: ContentResolver,
        block: (OutputStream) -> Unit,
    ): Boolean {
        if (safTreeUri != null) {
            val treeDocUri =
                DocumentsContract.buildDocumentUriUsingTree(
                    safTreeUri,
                    DocumentsContract.getTreeDocumentId(safTreeUri),
                )
            // Try to create a per-package subdirectory; some providers (e.g. Downloads) don't
            // support MIME_TYPE_DIR and return null — in that case fall back to a flat name.
            val parentUri =
                try {
                    DocumentsContract.createDocument(
                        cr,
                        treeDocUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        pkg,
                    )
                } catch (_: Exception) {
                    null
                }

            val (targetUri, targetName) =
                if (parentUri != null) {
                    parentUri to fileName
                } else {
                    treeDocUri to "${pkg}_$fileName"
                }

            val fileUri =
                try {
                    DocumentsContract.createDocument(cr, targetUri, "application/octet-stream", targetName)
                } catch (e: Exception) {
                    Timber.w(e, "createDocument failed for $pkg/$targetName")
                    null
                } ?: return false

            cr.openOutputStream(fileUri)?.use { block(it) }
            return true
        } else {
            val pkgDir = File(outputDir!!, pkg).also { it.mkdirs() }
            FileOutputStream(File(pkgDir, fileName)).use { block(it) }
            return true
        }
    }

    fun toggleFreeze(entry: AppEntry) {
        val pkg = entry.packageName
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nowFrozen =
                    if (entry.isFrozen) {
                        ShizukuPlusAPI.BackupRestorePlus.unfreezeApp(pkg)
                        false
                    } else {
                        ShizukuPlusAPI.BackupRestorePlus.freezeApp(pkg)
                        true
                    }
                // Update master list so subsequent filter/search retains the new frozen state.
                allApps = allApps.map { if (it.packageName == pkg) it.copy(isFrozen = nowFrozen) else it }
                applyFilter()
                _events.emit(BackupEvent.FreezeChanged(pkg, nowFrozen))
            } catch (e: Exception) {
                Timber.e(e, "toggleFreeze failed for $pkg")
                _events.emit(BackupEvent.Failure("Freeze toggle failed: ${e.message}"))
            }
        }
    }
}
