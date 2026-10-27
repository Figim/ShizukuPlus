package af.shizuku.manager.settings

import af.shizuku.manager.R
import android.os.Bundle

class AppManagementSettingsFragment : BaseSettingsFragment() {
    override fun getTitle(): CharSequence? = getString(R.string.settings_main_nav_app_interactions_title)

    override fun onCreateSettingsPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_app_management, rootKey)
    }
}
