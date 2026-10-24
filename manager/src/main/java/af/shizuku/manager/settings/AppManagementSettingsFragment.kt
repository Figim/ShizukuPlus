package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.database.AppContextManager
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class AppManagementSettingsFragment : BaseSettingsFragment() {
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

        // App descriptions and enhancement suggestions shown in the Apps list.
        findPreference<Preference>("update_app_database")?.setOnPreferenceClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val url = java.net.URL("https://raw.githubusercontent.com/thejaustin/ShizukuPlus/master/database/apps.json")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    val content =
                        try {
                            connection.instanceFollowRedirects = true
                            connection.requestMethod = "GET"
                            connection.connectTimeout = 10_000
                            connection.readTimeout = 10_000

                            val responseCode = connection.responseCode
                            if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                                throw java.io.IOException("HTTP $responseCode from GitHub")
                            }

                            connection.inputStream.use { it.bufferedReader().readText() }
                        } finally {
                            connection.disconnect()
                        }
                    withContext(Dispatchers.Main) {
                        AppContextManager.updateDatabase(content)
                        notifyUser(R.string.settings_update_app_database_success)
                    }
                } catch (e: Exception) {
                    Timber.w("update app database failed", e)
                    withContext(Dispatchers.Main) {
                        notifyUser(R.string.settings_update_app_database_error)
                    }
                }
            }
            true
        }
    }
}
