package af.shizuku.manager.settings

import af.shizuku.manager.R
import android.os.Bundle

class SettingsFragment : BaseSettingsFragment() {
    override fun getTitle(): CharSequence? = getString(R.string.settings_title)

    override fun onCreateSettingsPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_main, rootKey)
    }
}
