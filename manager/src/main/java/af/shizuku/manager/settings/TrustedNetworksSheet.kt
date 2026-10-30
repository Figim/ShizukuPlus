package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.automation.AutomationService
import af.shizuku.manager.utils.HapticUtils
import af.shizuku.manager.utils.NetworkTrust
import af.shizuku.manager.utils.TcpNetworkGuard
import af.shizuku.manager.utils.WifiNetworks
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Picks trusted Wi-Fi networks from the connected one and the ones saved on the device, instead of
 * typing names. Typing stays available for networks that aren't saved here. Serves both lists:
 * Wireless ADB's network protection and the App connection firewall automation.
 */
class TrustedNetworksSheet : BottomSheetDialogFragment() {
    /** Where a list of trusted networks lives, and what has to react when it changes. */
    enum class Target(
        val titleRes: Int,
        val subtitleRes: Int,
    ) {
        TCP(R.string.settings_tcp_trusted_networks_label, R.string.trusted_networks_tcp_subtitle) {
            override fun read() = NetworkTrust.trustedSsids()

            override fun write(
                context: Context,
                ssids: Set<String>,
            ) {
                NetworkTrust.setTrustedSsids(ssids)
                TcpNetworkGuard.enforce(context)
            }
        },
        AUTOMATION(R.string.settings_automation_trusted_networks, R.string.settings_automation_trusted_networks_desc) {
            override fun read() = ShizukuSettings.getTrustedNetworksSet()

            override fun write(
                context: Context,
                ssids: Set<String>,
            ) {
                // Stored comma-separated, so a comma can't be part of a name here.
                ShizukuSettings.setTrustedNetworks(ssids.joinToString(","))
                if (ShizukuSettings.hasAnyAutomationRulesConfigured()) {
                    AutomationService.startIfNeeded(context)
                } else {
                    AutomationService.stopIfRunning(context)
                }
            }
        }, ;

        abstract fun read(): Set<String>

        abstract fun write(
            context: Context,
            ssids: Set<String>,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val target = Target.valueOf(requireArguments().getString(ARG_TARGET) ?: Target.TCP.name)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PreferenceComposeTheme {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(bottom = 16.dp),
                    ) {
                        SheetHeader(stringResource(target.titleRes), stringResource(target.subtitleRes))
                        Content(target)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun Content(target: Target) {
        val context = LocalContext.current
        val view = LocalView.current
        var trusted by remember { mutableStateOf(target.read()) }
        var snapshot by remember { mutableStateOf<WifiNetworks.Snapshot?>(null) }
        LaunchedEffect(Unit) { snapshot = WifiNetworks.load(context.applicationContext) }

        fun setTrusted(
            ssid: String,
            on: Boolean,
        ) {
            if (on) HapticUtils.toggleOn(view) else HapticUtils.toggleOff(view)
            trusted = if (on) trusted + ssid else trusted - ssid
            target.write(context.applicationContext, trusted)
        }

        val loaded = snapshot
        if (loaded == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return
        }
        Column(modifier = Modifier.padding(horizontal = 8.dp).animateContentSize()) {
            loaded.current?.let { current ->
                NetworkRow(
                    ssid = current,
                    subtitle = stringResource(R.string.trusted_networks_connected),
                    highlighted = true,
                    checked = current in trusted,
                    onCheckedChange = { setTrusted(current, it) },
                )
            }
            // Saved networks first, then names added by hand that aren't saved here, so every
            // trusted name can be switched off again.
            val others = (loaded.saved + trusted.sortedWith(String.CASE_INSENSITIVE_ORDER)).distinct() - setOfNotNull(loaded.current)
            if (others.isNotEmpty()) SheetSectionLabel(stringResource(R.string.trusted_networks_saved_label))
            others.forEach { ssid ->
                NetworkRow(
                    ssid = ssid,
                    subtitle = if (ssid in loaded.saved) null else stringResource(R.string.trusted_networks_not_saved),
                    highlighted = false,
                    checked = ssid in trusted,
                    onCheckedChange = { setTrusted(ssid, it) },
                )
            }
            if (!loaded.viaShell) {
                Text(
                    text = stringResource(R.string.trusted_networks_needs_service),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            AddByName(onAdd = { name ->
                HapticUtils.segmentTick(view)
                trusted = trusted + name
                target.write(context.applicationContext, trusted)
            })
        }
    }

    @Composable
    private fun NetworkRow(
        ssid: String,
        subtitle: String?,
        highlighted: Boolean,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        val colors = MaterialTheme.colorScheme
        SheetRow(
            iconRes = R.drawable.ic_wifi_24,
            iconContainer = if (highlighted) colors.primaryContainer else colors.secondaryContainer,
            iconTint = if (highlighted) colors.onPrimaryContainer else colors.onSecondaryContainer,
            title = ssid,
            subtitle = subtitle,
            modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        ) {
            Switch(checked = checked, onCheckedChange = null)
        }
    }

    @Composable
    private fun AddByName(onAdd: (String) -> Unit) {
        val colors = MaterialTheme.colorScheme
        var open by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf("") }
        val clean = name.trim().replace(",", "")
        val submit = {
            if (clean.isNotEmpty()) {
                onAdd(clean)
                name = ""
                open = false
            }
        }
        SheetRow(
            iconRes = R.drawable.ic_edit_24,
            iconContainer = colors.surfaceContainerHighest,
            iconTint = colors.onSurfaceVariant,
            title = stringResource(R.string.trusted_networks_type_name),
            subtitle = null,
            modifier = Modifier.toggleable(value = open, role = Role.Button) { open = it },
        ) {}
        AnimatedVisibility(visible = open) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_tcp_ssid_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(enabled = clean.isNotEmpty(), onClick = submit) {
                    Text(stringResource(R.string.trusted_networks_add))
                }
            }
        }
    }

    companion object {
        private const val ARG_TARGET = "target"
        private const val TAG = "trusted_networks"

        fun show(
            fm: FragmentManager,
            target: Target,
        ) {
            if (fm.isStateSaved || fm.findFragmentByTag(TAG) != null) return
            TrustedNetworksSheet()
                .apply { arguments = Bundle().apply { putString(ARG_TARGET, target.name) } }
                .show(fm, TAG)
        }
    }
}
