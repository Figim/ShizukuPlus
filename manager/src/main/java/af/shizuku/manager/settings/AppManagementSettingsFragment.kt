package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.database.AppContextManager
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import kotlinx.coroutines.launch

class AppManagementSettingsFragment : BaseSettingsFragment() {
    private var checkingDatabase = false

    private fun updateDatabaseSummary(pref: Preference) {
        val last = ShizukuSettings.getLastDbUpdate()
        val count = AppContextManager.appCount()
        pref.summary =
            if (last > 0) {
                val checked =
                    android.text.format.DateUtils
                        .getRelativeTimeSpanString(last, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)
                getString(R.string.settings_update_app_database_status, count, checked)
            } else {
                getString(R.string.settings_update_app_database_status_never, count)
            }
    }

    override fun getTitle(): CharSequence? = getString(R.string.settings_main_nav_app_interactions_title)

    override fun onCreateSettingsPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_app_management, rootKey)

        // Swipe gestures — auto-persist via SharedPrefs; notify server of change
        findPreference<rikka.preference.SimpleMenuPreference>("swipe_right_action")
            ?.setOnPreferenceChangeListener { _, _ ->
                ShizukuSettings.syncAllPlusFeaturesToServer()
                true
            }
        findPreference<rikka.preference.SimpleMenuPreference>("swipe_left_action")
            ?.setOnPreferenceChangeListener { _, _ ->
                ShizukuSettings.syncAllPlusFeaturesToServer()
                true
            }

        // Long-press action toggles — auto-persist via SharedPrefs; notify server
        listOf("lp_open_app", "lp_app_info", "lp_toggle_permission", "lp_hide_from_list").forEach { key ->
            findPreference<TwoStatePreference>(key)
                ?.setOnPreferenceChangeListener { _, _ ->
                    ShizukuSettings.syncAllPlusFeaturesToServer()
                    true
                }
        }

        // App descriptions and SU-path tips shown in Apps and Root compatibility; edited on GitHub.
        findPreference<Preference>("update_app_database")?.let { pref ->
            updateDatabaseSummary(pref)
            pref.setOnPreferenceClickListener {
                if (checkingDatabase) return@setOnPreferenceClickListener true
                view?.let {
                    af.shizuku.manager.utils.HapticUtils
                        .segmentTick(it)
                }
                checkingDatabase = true
                pref.summary = getString(R.string.settings_update_app_database_checking)
                lifecycleScope.launch {
                    val result =
                        af.shizuku.manager.worker.RemoteDbSyncWorker
                            .sync()
                    checkingDatabase = false
                    if (!isAdded) return@launch
                    updateDatabaseSummary(pref)
                    when (result) {
                        is af.shizuku.manager.worker.RemoteDbSyncWorker.Companion.SyncResult.Updated -> {
                            view?.let {
                                af.shizuku.manager.utils.HapticUtils
                                    .success(it)
                            }
                            notifyUser(resources.getQuantityString(R.plurals.settings_update_app_database_updated, result.apps, result.apps))
                        }
                        af.shizuku.manager.worker.RemoteDbSyncWorker.Companion.SyncResult.UpToDate -> {
                            view?.let {
                                af.shizuku.manager.utils.HapticUtils
                                    .success(it)
                            }
                            notifyUser(R.string.settings_update_app_database_up_to_date)
                        }
                        is af.shizuku.manager.worker.RemoteDbSyncWorker.Companion.SyncResult.Failed ->
                            notifyUser(R.string.settings_update_app_database_error, long = true)
                    }
                }
                true
            }
        }
    }
}
