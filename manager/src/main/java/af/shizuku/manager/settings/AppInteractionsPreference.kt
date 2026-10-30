package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.utils.HapticUtils
import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
 * Unified Material 3 Expressive App Interactions Studio.
 * Combines swipe gesture action selectors and long-press contextual action toggles
 * in a space-minimized, tactile card designed to embed cleanly into Appearance settings.
 */
class AppInteractionsPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs),
        SharedPreferences.OnSharedPreferenceChangeListener {
        data class SwipeOption(
            val value: String,
            val labelRes: Int,
            val iconRes: Int,
        )

        data class LongPressOption(
            val key: String,
            val titleRes: Int,
            val iconRes: Int,
            val defaultVal: Boolean = true,
        )

        private val swipeActions =
            listOf(
                SwipeOption("open_app", R.string.settings_action_open_app, R.drawable.ic_outline_play_arrow_24),
                SwipeOption("app_info", R.string.settings_action_app_info, R.drawable.ic_outline_info_24),
                SwipeOption("toggle_permission", R.string.settings_action_toggle_permission, R.drawable.ic_shield_24),
                SwipeOption("hide_from_list", R.string.settings_action_hide_from_list, R.drawable.ic_visibility_off_24),
                SwipeOption("none", R.string.settings_action_none, R.drawable.ic_close_24),
            )

        private val longPressActions =
            listOf(
                LongPressOption("lp_open_app", R.string.settings_lp_open_app, R.drawable.ic_outline_play_arrow_24),
                LongPressOption("lp_app_info", R.string.settings_lp_app_info, R.drawable.ic_outline_info_24),
                LongPressOption("lp_toggle_permission", R.string.settings_lp_toggle_permission, R.drawable.ic_shield_24),
                LongPressOption("lp_hide_from_list", R.string.settings_lp_hide_from_list, R.drawable.ic_visibility_off_24, defaultVal = false),
                LongPressOption("lp_api_scopes", R.string.settings_lp_api_scopes, R.drawable.ic_admin_panel_settings_24, defaultVal = true),
                LongPressOption("lp_select_multiple", R.string.settings_lp_select_multiple, R.drawable.ic_select_all_24, defaultVal = true),
                LongPressOption("lp_freeze_app", R.string.settings_lp_freeze_app, R.drawable.ic_ac_unit_24, defaultVal = true),
                LongPressOption("lp_freeform_resize", R.string.settings_lp_freeform_resize, R.drawable.ic_window_24, defaultVal = false),
            )

        private val swipeRight = mutableStateOf("open_app")
        private val swipeLeft = mutableStateOf("app_info")
        private val longPressStates = mutableStateMapOf<String, Boolean>()

        init {
            layoutResource = R.layout.preference_compose
            isSelectable = false
            syncAll()
        }

        private fun syncAll() {
            val p = ShizukuSettings.getPreferences() ?: return
            swipeRight.value = p.getString("swipe_right_action", "open_app") ?: "open_app"
            swipeLeft.value = p.getString("swipe_left_action", "app_info") ?: "app_info"
            longPressActions.forEach { item ->
                longPressStates[item.key] = p.getBoolean(item.key, item.defaultVal)
            }
        }

        override fun onAttached() {
            super.onAttached()
            ShizukuSettings.getPreferences()?.registerOnSharedPreferenceChangeListener(this)
            syncAll()
        }

        override fun onDetached() {
            super.onDetached()
            ShizukuSettings.getPreferences()?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(
            prefs: SharedPreferences?,
            key: String?,
        ) {
            if (key == "swipe_right_action" || key == "swipe_left_action" || key?.startsWith("lp_") == true) {
                syncAll()
            }
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            val cv =
                holder.findViewById(R.id.preference_compose_view) as? ComposeView
                    ?: holder.itemView as? ComposeView ?: return
            cv.setPreferenceContent {
                AppInteractionsCardContent(
                    swipeActions = swipeActions,
                    selectedRight = swipeRight.value,
                    selectedLeft = swipeLeft.value,
                    longPressActions = longPressActions,
                    longPressStates = longPressStates,
                    onSelectRight = { newAction ->
                        swipeRight.value = newAction
                        ShizukuSettings
                            .getPreferences()
                            ?.edit()
                            ?.putString("swipe_right_action", newAction)
                            ?.apply()
                        ShizukuSettings.syncAllPlusFeaturesToServer()
                    },
                    onSelectLeft = { newAction ->
                        swipeLeft.value = newAction
                        ShizukuSettings
                            .getPreferences()
                            ?.edit()
                            ?.putString("swipe_left_action", newAction)
                            ?.apply()
                        ShizukuSettings.syncAllPlusFeaturesToServer()
                    },
                    onToggleLongPress = { key, newState ->
                        longPressStates[key] = newState
                        ShizukuSettings
                            .getPreferences()
                            ?.edit()
                            ?.putBoolean(key, newState)
                            ?.apply()
                    },
                )
            }
        }
    }

@Composable
private fun AppInteractionsCardContent(
    swipeActions: List<AppInteractionsPreference.SwipeOption>,
    selectedRight: String,
    selectedLeft: String,
    longPressActions: List<AppInteractionsPreference.LongPressOption>,
    longPressStates: Map<String, Boolean>,
    onSelectRight: (String) -> Unit,
    onSelectLeft: (String) -> Unit,
    onToggleLongPress: (String, Boolean) -> Unit,
) {
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
            // Section 1: Swipe Gestures
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_swipe_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_app_management_category_gestures),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_swipe_preview_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Swipe Right Row
            SwipeDirectionRow(
                directionLabel = stringResource(R.string.settings_swipe_right),
                directionArrow = "→",
                actions = swipeActions,
                selectedValue = selectedRight,
                onSelect = onSelectRight,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Swipe Left Row
            SwipeDirectionRow(
                directionLabel = stringResource(R.string.settings_swipe_left),
                directionArrow = "←",
                actions = swipeActions,
                selectedValue = selectedLeft,
                onSelect = onSelectLeft,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 1.dp,
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Section 2: Long-Press Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_outline_play_arrow_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_app_management_category_long_press),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_lp_category_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact 2-column expressive toggle grid for long press
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                longPressActions.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { item ->
                            Box(modifier = Modifier.weight(1f)) {
                                LongPressTogglePill(
                                    item = item,
                                    checked = longPressStates[item.key] ?: item.defaultVal,
                                    onToggle = onToggleLongPress,
                                )
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeDirectionRow(
    directionLabel: String,
    directionArrow: String,
    actions: List<AppInteractionsPreference.SwipeOption>,
    selectedValue: String,
    onSelect: (String) -> Unit,
) {
    val view = LocalView.current
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$directionArrow $directionLabel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Active action badge
            val currentAction = actions.find { it.value == selectedValue }
            if (currentAction != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = stringResource(currentAction.labelRes),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            actions.forEach { action ->
                val isSelected = action.value == selectedValue
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.0f else 0.95f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "pillScale",
                )
                val cornerRadius by animateDpAsState(
                    targetValue = if (isSelected) 16.dp else 10.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "pillCorner",
                )
                val bgColor by animateColorAsState(
                    targetValue =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                        },
                    label = "pillBg",
                )
                val contentColor by animateColorAsState(
                    targetValue =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    label = "pillContent",
                )

                val shape = RoundedCornerShape(cornerRadius)
                Row(
                    modifier =
                        Modifier
                            .scale(scale)
                            .clip(shape)
                            .background(bgColor)
                            .border(
                                width = 1.dp,
                                color =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    } else {
                                        Color.Transparent
                                    },
                                shape = shape,
                            ).clickable {
                                if (!isSelected) {
                                    HapticUtils.segmentTick(view)
                                    onSelect(action.value)
                                }
                            }.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(contentColor.copy(alpha = if (isSelected) 0.16f else 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(action.iconRes),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(action.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "✓",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LongPressTogglePill(
    item: AppInteractionsPreference.LongPressOption,
    checked: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    val view = LocalView.current
    val scale by animateFloatAsState(
        targetValue = if (checked) 1.0f else 0.96f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "lpScale",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (checked) 16.dp else 10.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "lpCorner",
    )
    val bgColor by animateColorAsState(
        targetValue =
            if (checked) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
            },
        label = "lpBg",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (checked) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            },
        label = "lpContent",
    )

    val shape = RoundedCornerShape(cornerRadius)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .clip(shape)
                .background(bgColor)
                .border(
                    width = 1.dp,
                    color =
                        if (checked) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                        } else {
                            Color.Transparent
                        },
                    shape = shape,
                ).clickable {
                    HapticUtils.segmentTick(view)
                    onToggle(item.key, !checked)
                }.padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(contentColor.copy(alpha = if (checked) 0.16f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(item.titleRes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        if (checked) {
            Text(
                text = "✓",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}
