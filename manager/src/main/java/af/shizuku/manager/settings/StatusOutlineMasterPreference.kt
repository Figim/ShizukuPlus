package af.shizuku.manager.settings

import af.shizuku.core.ui.AppActivity
import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.utils.HapticUtils
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * Material 3 Expressive Tonal Master Card for home status card outline styling.
 * Consolidates master toggle and connected style options with live mini-card preview.
 */
class StatusOutlineMasterPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs),
        SharedPreferences.OnSharedPreferenceChangeListener {
        private val outlineEnabled = mutableStateOf(true)
        private val outlineStyle = mutableStateOf("material")

        init {
            layoutResource = R.layout.preference_compose
            isSelectable = false
            syncPrefs()
        }

        private fun syncPrefs() {
            val p = ShizukuSettings.getPreferences() ?: return
            outlineEnabled.value = p.getBoolean("show_status_card_outline", true)
            outlineStyle.value = p.getString("status_card_outline_style", "material") ?: "material"
        }

        override fun onAttached() {
            super.onAttached()
            ShizukuSettings.getPreferences()?.registerOnSharedPreferenceChangeListener(this)
            syncPrefs()
        }

        override fun onDetached() {
            super.onDetached()
            ShizukuSettings.getPreferences()?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(
            prefs: SharedPreferences?,
            key: String?,
        ) {
            if (key == "show_status_card_outline" || key == "status_card_outline_style") {
                syncPrefs()
            }
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            val cv =
                holder.findViewById(R.id.preference_compose_view) as? ComposeView
                    ?: holder.itemView as? ComposeView ?: return
            cv.setPreferenceContent {
                StatusOutlineContent(
                    enabled = outlineEnabled.value,
                    style = outlineStyle.value,
                    onToggleEnabled = { newOn ->
                        outlineEnabled.value = newOn
                        ShizukuSettings
                            .getPreferences()
                            ?.edit()
                            ?.putBoolean("show_status_card_outline", newOn)
                            ?.apply()
                        context.findAppActivity()?.let { act ->
                            (act as? SettingsActivity)?.onThemeChanged()
                        }
                    },
                    onSelectStyle = { newStyle ->
                        outlineStyle.value = newStyle
                        ShizukuSettings
                            .getPreferences()
                            ?.edit()
                            ?.putString("status_card_outline_style", newStyle)
                            ?.apply()
                        context.findAppActivity()?.let { act ->
                            (act as? SettingsActivity)?.onThemeChanged()
                        }
                    },
                )
            }
        }
    }

private fun Context.findAppActivity(): AppActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is AppActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun StatusOutlineContent(
    enabled: Boolean,
    style: String,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectStyle: (String) -> Unit,
) {
    val view = LocalView.current

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Master Switch Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_window_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_show_status_card_outline),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_show_status_card_outline_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (checked) HapticUtils.toggleOn(view) else HapticUtils.toggleOff(view)
                        onToggleEnabled(checked)
                    },
                )
            }

            // Progressive Disclosure: Style Options + Live Mini Preview
            AnimatedVisibility(
                visible = enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    // Live Mini-Card Preview
                    val activeStrokeColor =
                        if (style == "status") {
                            Color(0xFF4CAF50) // Material Green running color
                        } else {
                            MaterialTheme.colorScheme.primary
                        }

                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = 2.dp,
                                    color = activeStrokeColor,
                                    shape = RoundedCornerShape(16.dp),
                                ),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(activeStrokeColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_server_ok_24),
                                    contentDescription = null,
                                    tint = activeStrokeColor,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Shizuku is running",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (style == "status") "Status color outline" else "Theme accent outline",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Connected Style Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val options =
                            listOf(
                                Triple("material", "Material Theme", R.drawable.ic_palette_24),
                                Triple("status", "Green / Red Status", R.drawable.ic_server_ok_24),
                            )

                        options.forEach { (key, label, iconRes) ->
                            val isSelected = style == key

                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.0f else 0.97f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "btnScale",
                            )
                            val bgColor by animateColorAsState(
                                targetValue =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                label = "btnBg",
                            )
                            val contentColor by animateColorAsState(
                                targetValue =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                label = "btnContent",
                            )

                            Surface(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .scale(scale)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            if (!isSelected) {
                                                HapticUtils.segmentTick(view)
                                                onSelectStyle(key)
                                            }
                                        },
                                shape = RoundedCornerShape(14.dp),
                                color = bgColor,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(iconRes),
                                        contentDescription = null,
                                        tint = contentColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = contentColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
