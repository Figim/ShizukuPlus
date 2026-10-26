package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.utils.HapticUtils
import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
 * Material 3 Expressive multi-toggle pill group for related boolean actions.
 * Combines multiple separate switch rows into an interactive, scannable pill cloud.
 */
class MultiToggleGroupPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs),
        SharedPreferences.OnSharedPreferenceChangeListener {
        data class ToggleItem(
            val key: String,
            val titleRes: Int,
            val iconRes: Int,
            val defaultVal: Boolean = true,
        )

        private val items =
            listOf(
                ToggleItem("lp_open_app", R.string.settings_lp_open_app, R.drawable.ic_outline_play_arrow_24),
                ToggleItem("lp_app_info", R.string.settings_lp_app_info, R.drawable.ic_outline_info_24),
                ToggleItem("lp_toggle_permission", R.string.settings_lp_toggle_permission, R.drawable.ic_shield_24),
                ToggleItem("lp_hide_from_list", R.string.settings_lp_hide_from_list, R.drawable.ic_visibility_off_24),
            )

        private val states = mutableStateMapOf<String, Boolean>()

        init {
            layoutResource = R.layout.preference_compose
            isSelectable = false
            syncStates()
        }

        private fun syncStates() {
            val p = ShizukuSettings.getPreferences() ?: return
            items.forEach { item ->
                states[item.key] = p.getBoolean(item.key, item.defaultVal)
            }
        }

        override fun onAttached() {
            super.onAttached()
            ShizukuSettings.getPreferences()?.registerOnSharedPreferenceChangeListener(this)
            syncStates()
        }

        override fun onDetached() {
            super.onDetached()
            ShizukuSettings.getPreferences()?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(
            prefs: SharedPreferences?,
            key: String?,
        ) {
            if (items.any { it.key == key }) {
                syncStates()
            }
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            val cv =
                holder.findViewById(R.id.preference_compose_view) as? ComposeView
                    ?: holder.itemView as? ComposeView ?: return
            cv.setPreferenceContent {
                MultiToggleContent(
                    title = title?.toString() ?: stringResource(R.string.settings_app_management_category_long_press),
                    summary = summary?.toString() ?: stringResource(R.string.settings_lp_category_summary),
                    items = items,
                    states = states,
                    onToggle = { key, newChecked ->
                        states[key] = newChecked
                        ShizukuSettings
                            .getPreferences()
                            ?.edit()
                            ?.putBoolean(key, newChecked)
                            ?.apply()
                        ShizukuSettings.syncAllPlusFeaturesToServer()
                    },
                )
            }
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiToggleContent(
    title: String,
    summary: String,
    items: List<MultiToggleGroupPreference.ToggleItem>,
    states: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit,
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
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { item ->
                    val checked = states[item.key] ?: item.defaultVal

                    val scale by animateFloatAsState(
                        targetValue = if (checked) 1.0f else 0.96f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "pillScale",
                    )
                    val bgColor by animateColorAsState(
                        targetValue =
                            if (checked) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        label = "pillBg",
                    )
                    val contentColor by animateColorAsState(
                        targetValue =
                            if (checked) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            },
                        label = "pillContent",
                    )

                    Row(
                        modifier =
                            Modifier
                                .scale(scale)
                                .clip(RoundedCornerShape(14.dp))
                                .background(bgColor)
                                .border(
                                    width = 1.dp,
                                    color =
                                        if (checked) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        } else {
                                            Color.Transparent
                                        },
                                    shape = RoundedCornerShape(14.dp),
                                ).clickable {
                                    val next = !checked
                                    if (next) {
                                        HapticUtils.toggleOn(view)
                                    } else {
                                        HapticUtils.toggleOff(view)
                                    }
                                    onToggle(item.key, next)
                                }.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(contentColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(15.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = stringResource(item.titleRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                        )

                        if (checked) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "✓",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
