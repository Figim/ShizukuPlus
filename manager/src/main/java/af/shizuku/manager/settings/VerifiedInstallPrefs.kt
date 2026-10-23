package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Verified installation (local signatures, F-Droid index, VirusTotal, Pithus) and Package governor.
 * Lives under Startup & behavior > Security; kept here so the rows' wiring moves with them.
 */
object VerifiedInstallPrefs {
    private const val VIRUSTOTAL_KEY_URL = "https://www.virustotal.com/gui/my-apikey"
    private const val PITHUS_KEY_URL = "https://beta.pithus.org"

    fun attach(fragment: BaseSettingsFragment) {
        // Package governor is a Plus feature: sync once the switch has saved its new value.
        fragment
            .findPreference<TwoStatePreference>("package_governor_plus_enabled")
            ?.setOnPreferenceChangeListener { _, _ ->
                fragment.view?.post { ShizukuSettings.syncAllPlusFeaturesToServer() }
                true
            }

        // Local signature matching and F-Droid verification — pure local, auto-persist is sufficient

        // VirusTotal — requires API key before enabling
        fragment.findPreference<TwoStatePreference>("verify_apk_virustotal")?.setOnPreferenceChangeListener { pref, newValue ->
            val enabling = newValue as Boolean
            if (enabling && ShizukuSettings.getVirusTotalApiKey().isBlank()) {
                showApiKeyDialog(
                    fragment,
                    title = fragment.getString(R.string.verify_virustotal_key_title),
                    hint = fragment.getString(R.string.verify_api_key_hint),
                    currentKey = "",
                    getKeyUrl = VIRUSTOTAL_KEY_URL,
                    onSave = { key ->
                        if (key.isBlank()) {
                            (pref as TwoStatePreference).isChecked = false
                            Toast.makeText(fragment.requireContext(), R.string.verify_virustotal_key_required, Toast.LENGTH_SHORT).show()
                        } else {
                            ShizukuSettings.setVirusTotalApiKey(key)
                            (pref as TwoStatePreference).isChecked = true
                        }
                    },
                    onCancel = { (pref as TwoStatePreference).isChecked = false },
                )
                false
            } else {
                true
            }
        }

        // VirusTotal API key management preference (shown when VT is enabled)
        updateApiKeyManageSummary(fragment, "virustotal_api_key_manage", ShizukuSettings.getVirusTotalApiKey())
        fragment.findPreference<Preference>("virustotal_api_key_manage")?.setOnPreferenceClickListener {
            showApiKeyDialog(
                fragment,
                title = fragment.getString(R.string.verify_virustotal_key_title),
                hint = fragment.getString(R.string.verify_api_key_hint),
                currentKey = ShizukuSettings.getVirusTotalApiKey(),
                getKeyUrl = VIRUSTOTAL_KEY_URL,
                onSave = { key ->
                    ShizukuSettings.setVirusTotalApiKey(key)
                    updateApiKeyManageSummary(fragment, "virustotal_api_key_manage", key)
                },
                onCancel = {},
            )
            true
        }

        // Pithus — free public API; toggle works without a key
        fragment
            .findPreference<TwoStatePreference>("verify_apk_pithus")
            ?.setOnPreferenceChangeListener { _, _ -> true }

        // Pithus optional API key management
        updateApiKeyManageSummary(fragment, "pithus_api_key_manage", ShizukuSettings.getPithusApiKey())
        fragment.findPreference<Preference>("pithus_api_key_manage")?.setOnPreferenceClickListener {
            showApiKeyDialog(
                fragment,
                title = fragment.getString(R.string.verify_pithus_key_title),
                hint = fragment.getString(R.string.verify_api_key_hint),
                currentKey = ShizukuSettings.getPithusApiKey(),
                getKeyUrl = PITHUS_KEY_URL,
                onSave = { key ->
                    ShizukuSettings.setPithusApiKey(key)
                    updateApiKeyManageSummary(fragment, "pithus_api_key_manage", key)
                },
                onCancel = {},
            )
            true
        }
    }

    private fun updateApiKeyManageSummary(
        fragment: BaseSettingsFragment,
        key: String,
        apiKey: String,
    ) {
        val summary =
            if (apiKey.isNotBlank()) {
                fragment.getString(R.string.verify_api_key_configured)
            } else {
                fragment.getString(R.string.verify_api_key_hint)
            }
        fragment.findPreference<Preference>(key)?.setCustomSummary(summary)
    }

    private fun showApiKeyDialog(
        fragment: BaseSettingsFragment,
        title: String,
        hint: String,
        currentKey: String,
        getKeyUrl: String? = null,
        onSave: (String) -> Unit,
        onCancel: () -> Unit,
    ) {
        val ctx = fragment.context ?: return
        val dp16 = (16 * fragment.resources.displayMetrics.density).toInt()
        val input =
            EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                this.hint = hint
                if (currentKey.isNotBlank()) setText(currentKey)
            }
        val container =
            FrameLayout(ctx).apply {
                setPadding(dp16, dp16 / 2, dp16, 0)
                addView(input)
            }
        val builder =
            MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(android.R.string.ok) { _, _ -> onSave(input.text.toString().trim()) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
                .setOnCancelListener { onCancel() }
        if (getKeyUrl != null) {
            builder.setNeutralButton(R.string.verify_get_api_key) { _, _ ->
                fragment.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(getKeyUrl)))
            }
        }
        builder.show()
    }
}
