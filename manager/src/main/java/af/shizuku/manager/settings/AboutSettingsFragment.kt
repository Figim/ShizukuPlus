package af.shizuku.manager.settings

import af.shizuku.manager.BuildConfig
import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.home.ChangelogDialogFragment
import af.shizuku.manager.update.UpdateChecker
import af.shizuku.manager.utils.CustomTabsHelper
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import kotlinx.coroutines.launch
import timber.log.Timber

class AboutSettingsFragment : BaseSettingsFragment() {
    private var versionClickCount = 0

    override fun getTitle(): CharSequence? = getString(R.string.settings_about)

    override fun onCreateSettingsPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_about, rootKey)
        val context = requireContext()

        val navDevOptions = findPreference<Preference>("nav_developer_options")
        navDevOptions?.let { setChildAvailable(it, ShizukuSettings.isVectorEnabled()) }

        findPreference<Preference>("version")?.apply {
            summary = BuildConfig.VERSION_NAME
            setOnPreferenceClickListener {
                if (ShizukuSettings.isVectorEnabled()) {
                    Toast.makeText(context, R.string.settings_developer_options_revealed, Toast.LENGTH_SHORT).show()
                    return@setOnPreferenceClickListener true
                }

                versionClickCount++
                if (versionClickCount >= 7) {
                    ShizukuSettings.setVectorEnabled(true)
                    SettingsSearchEngine.reset()
                    navDevOptions?.let { setChildAvailable(it, true) }
                    Toast.makeText(context, R.string.settings_developer_options_revealed, Toast.LENGTH_SHORT).show()
                    versionClickCount = 0
                } else if (versionClickCount > 2) {
                    Toast.makeText(context, context.getString(R.string.settings_developer_options_click_more, 7 - versionClickCount), Toast.LENGTH_SHORT).show()
                }
                true
            }
        }

        findPreference<Preference>("changelog")?.setOnPreferenceClickListener {
            val activity = activity as? androidx.fragment.app.FragmentActivity ?: return@setOnPreferenceClickListener true
            activity.lifecycleScope.launch {
                val currentTag = BuildConfig.VERSION_NAME.removePrefix("Shizuku+ ").trim()
                val releases =
                    try {
                        UpdateChecker.fetchReleasesSince(sinceVersionCode = 0, maxReleases = 25)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to fetch releases for in-app changelog")
                        emptyList()
                    }
                if (isAdded && !isDetached) {
                    ChangelogDialogFragment
                        .newInstance(releases, currentTag)
                        .show(activity.supportFragmentManager, ChangelogDialogFragment.TAG)
                }
            }
            true
        }

        findPreference<Preference>("source_code")?.setOnPreferenceClickListener {
            CustomTabsHelper.launchUrlOrCopy(context, "https://github.com/thejaustin/ShizukuPlus")
            true
        }

        findPreference<Preference>("open_source_licenses")?.setOnPreferenceClickListener {
            CustomTabsHelper.launchUrlOrCopy(requireContext(), "https://github.com/thejaustin/ShizukuPlus/blob/main/OPEN_SOURCE_LICENSES.md")
            true
        }
    }

    private fun setChildAvailable(
        pref: Preference,
        available: Boolean,
    ) {
        val key = pref.key ?: return
        (pref.parent as? CollapsiblePreferenceCategory)?.setChildAvailable(key, available)
            ?: run { pref.isVisible = available }
    }
}
