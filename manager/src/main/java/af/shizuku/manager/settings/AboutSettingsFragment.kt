package af.shizuku.manager.settings

import af.shizuku.manager.BuildConfig
import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.home.ChangelogDialogFragment
import af.shizuku.manager.update.UpdateChecker
import af.shizuku.manager.update.UpdateManager
import af.shizuku.manager.utils.CustomTabsHelper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

class AboutSettingsFragment : BaseSettingsFragment() {
    companion object {
        private const val TAG = "AboutSettingsFragment"
        private const val KEY_CHECK_FOR_UPDATE = "check_for_update"
        private const val KEY_UPDATE_CHANNEL = "update_channel"
        private const val RELEASES_URL = "https://github.com/thejaustin/ShizukuPlus/releases"
    }

    private val updateManager: UpdateManager by inject()
    private var versionClickCount = 0
    private var isCheckingForUpdate = false

    override fun getTitle(): CharSequence? = getString(R.string.settings_about)

    override fun onCreateSettingsPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_about, rootKey)
        val context = requireContext()

        val navDevOptions = findPreference<Preference>("nav_developer_options")
        navDevOptions?.isVisible = ShizukuSettings.isDevOptionsUnlocked()

        findPreference<Preference>("version")?.apply {
            setCustomSummary(BuildConfig.VERSION_NAME)
            setOnPreferenceClickListener {
                if (ShizukuSettings.isDevOptionsUnlocked()) {
                    Toast.makeText(context, R.string.settings_developer_options_revealed, Toast.LENGTH_SHORT).show()
                    return@setOnPreferenceClickListener true
                }

                versionClickCount++
                if (versionClickCount >= 7) {
                    ShizukuSettings.setDevOptionsUnlocked(true)
                    SettingsSearchEngine.reset()
                    navDevOptions?.isVisible = true
                    Toast.makeText(context, R.string.settings_developer_options_revealed, Toast.LENGTH_SHORT).show()
                    versionClickCount = 0
                } else if (versionClickCount > 2) {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.settings_developer_options_click_more, 7 - versionClickCount),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                true
            }
        }

        setupUpdateCardPreference()

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

        findPreference<Preference>("feature_tour")?.setOnPreferenceClickListener {
            val intent =
                Intent(requireContext(), af.shizuku.manager.onboarding.OnboardingActivity::class.java).apply {
                    putExtra(af.shizuku.manager.onboarding.OnboardingActivity.EXTRA_NEW_FEATURES_ONLY, true)
                }
            startActivity(intent)
            true
        }

        findPreference<Preference>("source_code")?.setOnPreferenceClickListener {
            CustomTabsHelper.launchUrlOrCopy(context, "https://github.com/thejaustin/ShizukuPlus")
            true
        }

        findPreference<Preference>("open_source_licenses")?.setOnPreferenceClickListener {
            CustomTabsHelper.launchUrlOrCopy(
                requireContext(),
                "https://github.com/thejaustin/ShizukuPlus/blob/main/OPEN_SOURCE_LICENSES.md",
            )
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateLastCheckSummary()
    }

    private fun setupUpdateCardPreference() {
        updateLastCheckSummary()

        findPreference<Preference>(KEY_CHECK_FOR_UPDATE)?.setOnPreferenceClickListener {
            if (!isCheckingForUpdate) checkForUpdate()
            true
        }

        findPreference<SegmentedControlPreference>(KEY_UPDATE_CHANNEL)?.let { channelPref ->
            channelPref.setOnPreferenceChangeListener { _, newValue ->
                if (newValue != "dev") return@setOnPreferenceChangeListener true
                // Dev builds can be unstable: only switch once the user confirms.
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.update_channel_dev_warning_title)
                    .setMessage(R.string.update_channel_dev_warning_message)
                    .setPositiveButton(R.string.update_channel_dev) { _, _ ->
                        channelPref.value = "dev"
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
                false
            }
        }

        findPreference<TwoStatePreference>(ShizukuSettings.Keys.KEY_AUTO_UPDATE_ENABLED)
            ?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == false) {
                    findPreference<TwoStatePreference>(ShizukuSettings.Keys.KEY_AUTO_INSTALL_ENABLED)?.isChecked = false
                }
                true
            }

        findPreference<TwoStatePreference>(ShizukuSettings.Keys.KEY_AUTO_INSTALL_ENABLED)
            ?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true && !updateManager.canRequestPackageInstalls()) {
                    showPermissionRequiredDialog()
                    false
                } else {
                    true
                }
            }
    }

    private fun updateLastCheckSummary() {
        val pref = findPreference<Preference>(KEY_CHECK_FOR_UPDATE) ?: return
        val lastCheck = ShizukuSettings.getLastUpdateCheckTime()
        val status =
            when {
                ShizukuSettings.wasLastUpdateCheckFailed() && lastCheck > 0 -> {
                    val date =
                        UpdateChecker.formatPublishedDate(
                            java.text
                                .SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                                .format(java.util.Date(lastCheck)),
                        )
                    "$date · ${getString(R.string.update_last_check_failed)}"
                }
                ShizukuSettings.wasLastUpdateCheckFailed() ->
                    getString(R.string.update_last_check_failed)
                lastCheck > 0 -> {
                    val date =
                        UpdateChecker.formatPublishedDate(
                            java.text
                                .SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                                .format(java.util.Date(lastCheck)),
                        )
                    "${getString(R.string.settings_last_check_title)}: $date"
                }
                else ->
                    getString(R.string.settings_check_for_update_summary)
            }
        pref.setCustomSummary(status)
    }

    private fun checkForUpdate() {
        isCheckingForUpdate = true
        findPreference<Preference>(KEY_CHECK_FOR_UPDATE)?.setCustomSummary(getString(R.string.update_checking))
        val channel = ShizukuSettings.getUpdateChannel()
        lifecycleScope.launch {
            try {
                when (val result = UpdateChecker.checkForUpdate(channel)) {
                    is UpdateChecker.CheckResult.UpdateAvailable -> {
                        if (isAdded) {
                            ShizukuSettings.setLastUpdateCheckTime(System.currentTimeMillis())
                            ShizukuSettings.setLastUpdateCheckFailed(false)
                            isCheckingForUpdate = false
                            updateLastCheckSummary()
                            showUpdateAvailableDialog(result.info)
                        }
                    }
                    is UpdateChecker.CheckResult.UpToDate -> {
                        if (isAdded) {
                            ShizukuSettings.setLastUpdateCheckTime(System.currentTimeMillis())
                            ShizukuSettings.setLastUpdateCheckFailed(false)
                            isCheckingForUpdate = false
                            updateLastCheckSummary()
                            showUpToDateDialog()
                        }
                    }
                    is UpdateChecker.CheckResult.NetworkError -> {
                        if (isAdded) {
                            ShizukuSettings.setLastUpdateCheckFailed(true)
                            isCheckingForUpdate = false
                            updateLastCheckSummary()
                            showErrorDialog()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Unexpected error checking for update")
                if (isAdded) {
                    ShizukuSettings.setLastUpdateCheckFailed(true)
                    isCheckingForUpdate = false
                    updateLastCheckSummary()
                    showErrorDialog()
                }
            }
        }
    }

    private fun showUpdateAvailableDialog(info: UpdateChecker.UpdateInfo) {
        val context = context ?: return
        val devBadge = if (info.isPrerelease) " ⚠ Dev" else ""
        val builder =
            MaterialAlertDialogBuilder(context)
                .setTitle(getString(R.string.update_available_title) + devBadge)
                .setNegativeButton(R.string.update_later, null)
                .setNeutralButton(R.string.update_release_notes) { _, _ ->
                    val activity = activity as? androidx.fragment.app.FragmentActivity ?: return@setNeutralButton
                    activity.lifecycleScope.launch {
                        val releases =
                            try {
                                UpdateChecker.fetchReleasesSince(sinceVersionCode = 0, maxReleases = 25)
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to fetch releases for in-app changelog")
                                emptyList()
                            }
                        if (isAdded && !isDetached) {
                            ChangelogDialogFragment
                                .newInstance(releases, info.versionName)
                                .show(activity.supportFragmentManager, ChangelogDialogFragment.TAG)
                        }
                    }
                }

        if (info.requiresManualDownload) {
            builder
                .setMessage(getString(R.string.update_available_manual_message, info.versionName))
                .setPositiveButton(R.string.update_view_on_github) { _, _ -> openReleasesPage() }
        } else {
            builder
                .setMessage(getString(R.string.update_available_message, info.versionName))
                .setPositiveButton(R.string.update_download) { _, _ ->
                    updateManager.downloadUpdate(info.downloadUrl, info.versionName, manual = true)
                }
        }

        builder.show()
    }

    private fun showUpToDateDialog() {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_up_to_date_title)
            .setMessage(getString(R.string.update_up_to_date_message, BuildConfig.VERSION_NAME))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showErrorDialog() {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_error_title)
            .setMessage(R.string.update_error_message)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.update_view_on_github) { _, _ -> openReleasesPage() }
            .show()
    }

    private fun showPermissionRequiredDialog() {
        val context = context ?: return
        try {
            val intent =
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
            startActivity(intent)
        } catch (_: Exception) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.update_permission_required_title)
                .setMessage(R.string.update_permission_required_message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    private fun openReleasesPage() {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: android.content.ActivityNotFoundException) {
            Timber.w(e, "No activity found to handle releases URL")
        }
    }
}
