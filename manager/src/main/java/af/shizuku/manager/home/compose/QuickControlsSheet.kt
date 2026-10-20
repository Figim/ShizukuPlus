package af.shizuku.manager.home.compose

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.home.SystemHubActivity
import af.shizuku.manager.settings.ServiceDoctorActivity
import af.shizuku.manager.settings.SettingsActivity
import af.shizuku.manager.utils.HapticUtils
import af.shizuku.manager.utils.NetworkTrust
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickControlsSheet(
    onDismiss: () -> Unit,
    onStateChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val isOled =
        (
            af.shizuku.manager.app.ThemeHelper
                .isBlackNightTheme(context) ||
                ShizukuSettings.isAmoledPlusEnabled()
        ) &&
            isDark
    val sheetBackground = if (isOled) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val cardBackground = if (isOled) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
    val cardOutline = if (isOled) Color(0xFF333333) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    var isAmoledPlus by remember { mutableStateOf(ShizukuSettings.isAmoledPlusEnabled()) }
    var isFloatingNav by remember { mutableStateOf(ShizukuSettings.isFloatingNavBarEnabled()) }
    var showActivityLog by remember { mutableStateOf(ShizukuSettings.showActivityLogHome()) }
    var isAnimations by remember { mutableStateOf(ShizukuSettings.isExpressiveAnimationsEnabled()) }
    var isOneHanded by remember { mutableStateOf(ShizukuSettings.isOneHandedModeEnabled()) }
    var isNetworkGuard by remember { mutableStateOf(NetworkTrust.mode() != NetworkTrust.MODE_OFF) }

    val isRunning =
        af.shizuku.manager.utils.ShizukuStateMachine
            .isRunning()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBackground,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = if (isOled) Color(0xFF444444) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.quick_controls_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.quick_controls_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Section 1: Service & Stability
            Text(
                text = stringResource(R.string.quick_controls_section_service),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = cardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, cardOutline),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(if (isRunning) R.drawable.ic_server_ok_24 else R.drawable.ic_server_error_24),
                            contentDescription = null,
                            tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isRunning) "Shizuku Server Running" else "Shizuku Server Stopped",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                onDismiss()
                                context.startActivity(Intent(context, ServiceDoctorActivity::class.java))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_shield_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Doctor", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                context.startActivity(Intent(context, SystemHubActivity::class.java))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_server_ok_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("System Hub", fontSize = 13.sp)
                        }
                    }
                }
            }

            // Section 2: Essential Features
            Text(
                text = stringResource(R.string.quick_controls_section_features),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = cardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, cardOutline),
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp, horizontal = 16.dp)) {
                    QuickSwitchRow(
                        title = "Network Guard",
                        summary = "Restrict server access to trusted connections",
                        checked = isNetworkGuard,
                        onCheckedChange = { checked ->
                            isNetworkGuard = checked
                            val mode = if (checked) NetworkTrust.MODE_OPEN else NetworkTrust.MODE_OFF
                            ShizukuSettings
                                .getPreferences()
                                ?.edit()
                                ?.putString(NetworkTrust.KEY_MODE, mode)
                                ?.putBoolean(NetworkTrust.KEY_AUTO_CLOSE, checked)
                                ?.apply()
                            onStateChanged()
                        },
                    )

                    HorizontalDivider(color = cardOutline, thickness = 0.5.dp)

                    QuickSwitchRow(
                        title = "Floating Nav Bar",
                        summary = "Bottom pill navigation across manager screens",
                        checked = isFloatingNav,
                        onCheckedChange = { checked ->
                            isFloatingNav = checked
                            ShizukuSettings.setFloatingNavBarEnabled(checked)
                            onStateChanged()
                        },
                    )

                    HorizontalDivider(color = cardOutline, thickness = 0.5.dp)

                    QuickSwitchRow(
                        title = "Activity Log on Home",
                        summary = "Display privileged activity feed card on dashboard",
                        checked = showActivityLog,
                        onCheckedChange = { checked ->
                            showActivityLog = checked
                            ShizukuSettings
                                .getPreferences()
                                ?.edit()
                                ?.putBoolean(ShizukuSettings.Keys.KEY_SHOW_ACTIVITY_LOG_HOME, checked)
                                ?.apply()
                            onStateChanged()
                        },
                    )
                }
            }

            // Section 3: Appearance & Nav
            Text(
                text = stringResource(R.string.quick_controls_section_appearance),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = cardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, cardOutline),
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp, horizontal = 16.dp)) {
                    QuickSwitchRow(
                        title = "AMOLED+ True Black",
                        summary = "Force containers and background to pure black",
                        checked = isAmoledPlus,
                        onCheckedChange = { checked ->
                            isAmoledPlus = checked
                            ShizukuSettings
                                .getPreferences()
                                ?.edit()
                                ?.putBoolean(ShizukuSettings.Keys.KEY_AMOLED_PLUS, checked)
                                ?.putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, true)
                                ?.apply()
                            onStateChanged()
                        },
                    )

                    HorizontalDivider(color = cardOutline, thickness = 0.5.dp)

                    QuickSwitchRow(
                        title = "Expressive Animations",
                        summary = "Spring-based physics and fluid touch reactions",
                        checked = isAnimations,
                        onCheckedChange = { checked ->
                            isAnimations = checked
                            ShizukuSettings
                                .getPreferences()
                                ?.edit()
                                ?.putBoolean(ShizukuSettings.Keys.KEY_EXPRESSIVE_ANIMATIONS, checked)
                                ?.apply()
                            onStateChanged()
                        },
                    )

                    HorizontalDivider(color = cardOutline, thickness = 0.5.dp)

                    QuickSwitchRow(
                        title = "One-Handed Mode",
                        summary = "Bring content into thumb reach with generous header space",
                        checked = isOneHanded,
                        onCheckedChange = { checked ->
                            isOneHanded = checked
                            ShizukuSettings
                                .getPreferences()
                                ?.edit()
                                ?.putBoolean(ShizukuSettings.Keys.KEY_ONE_HANDED_MODE, checked)
                                ?.apply()
                            onStateChanged()
                        },
                    )
                }
            }

            // Open Full Settings Button
            OutlinedButton(
                onClick = {
                    onDismiss()
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_outline_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.quick_controls_btn_settings))
            }
        }
    }
}

@Composable
private fun QuickSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    val next = !checked
                    if (next) HapticUtils.toggleOn(view) else HapticUtils.toggleOff(view)
                    onCheckedChange(next)
                }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { next ->
                if (next) HapticUtils.toggleOn(view) else HapticUtils.toggleOff(view)
                onCheckedChange(next)
            },
        )
    }
}
