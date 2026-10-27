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
 * Material 3 Expressive connected button groups for swipe gesture actions.
 * Replaces legacy modal popup dropdowns with immediate, tactile inline selections
 * that update the gesture simulation card in real-time.
 */
class SwipeActionsConnectedPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs),
        SharedPreferences.OnSharedPreferenceChangeListener {
        data class ActionOption(
            val value: String,
            val labelRes: Int,
            val iconRes: Int,
        )

        private val actions =
            listOf(
                ActionOption("open_app", R.string.settings_action_open_app, R.drawable.ic_outline_play_arrow_24),
                ActionOption("app_info", R.string.settings_action_app_info, R.drawable.ic_outline_info_24),
                ActionOption("toggle_permission", R.string.settings_action_toggle_permission, R.drawable.ic_shield_24),
                ActionOption("hide_from_list", R.string.settings_action_hide_from_list, R.drawable.ic_visibility_off_24),
                ActionOption("none", R.string.settings_action_none, R.drawable.ic_close_24),
            )

        private val swipeRight = mutableStateOf("open_app")
        private val swipeLeft = mutableStateOf("app_info")

        init {
            layoutResource = R.layout.preference_compose
            isSelectable = false
            syncActions()
        }

        private fun syncActions() {
            val p = ShizukuSettings.getPreferences() ?: return
            swipeRight.value = p.getString("swipe_right_action", "open_app") ?: "open_app"
            swipeLeft.value = p.getString("swipe_left_action", "app_info") ?: "app_info"
        }

        override fun onAttached() {
            super.onAttached()
            ShizukuSettings.getPreferences()?.registerOnSharedPreferenceChangeListener(this)
            syncActions()
        }

        override fun onDetached() {
            super.onDetached()
            ShizukuSettings.getPreferences()?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(
            prefs: SharedPreferences?,
            key: String?,
        ) {
            if (key == "swipe_right_action" || key == "swipe_left_action") {
                syncActions()
            }
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            val cv =
                holder.findViewById(R.id.preference_compose_view) as? ComposeView
                    ?: holder.itemView as? ComposeView ?: return
            cv.setPreferenceContent {
                SwipeActionsCardContent(
                    actions = actions,
                    selectedRight = swipeRight.value,
                    selectedLeft = swipeLeft.value,
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
                )
            }
        }
    }

@Composable
private fun SwipeActionsCardContent(
    actions: List<SwipeActionsConnectedPreference.ActionOption>,
    selectedRight: String,
    selectedLeft: String,
    onSelectRight: (String) -> Unit,
    onSelectLeft: (String) -> Unit,
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
            val swipeRightText = stringResource(R.string.settings_swipe_right)
            val swipeLeftText = stringResource(R.string.settings_swipe_left)

            // Swipe Right Section
            ActionRowSection(
                title = swipeRightText,
                directionHint = "→ $swipeRightText",
                iconRes = R.drawable.ic_swipe_24,
                actions = actions,
                selectedValue = selectedRight,
                onSelect = onSelectRight,
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp,
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Swipe Left Section
            ActionRowSection(
                title = swipeLeftText,
                directionHint = "← $swipeLeftText",
                iconRes = R.drawable.ic_swipe_24,
                actions = actions,
                selectedValue = selectedLeft,
                onSelect = onSelectLeft,
            )
        }
    }
}

@Composable
private fun ActionRowSection(
    title: String,
    directionHint: String,
    iconRes: Int,
    actions: List<SwipeActionsConnectedPreference.ActionOption>,
    selectedValue: String,
    onSelect: (String) -> Unit,
) {
    val view = LocalView.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = directionHint,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Connected horizontal button strip
    val scrollState = rememberScrollState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        actions.forEach { action ->
            val isSelected = action.value == selectedValue

            // Material 3 Expressive spring-driven shape morphing & size physics
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.0f else 0.94f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "pillScale",
            )
            val pillCorner by animateDpAsState(
                targetValue = if (isSelected) 22.dp else 10.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "pillCorner",
            )
            val iconBgCorner by animateDpAsState(
                targetValue = if (isSelected) 6.dp else 12.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "iconBgCorner",
            )
            val hPadding by animateDpAsState(
                targetValue = if (isSelected) 13.dp else 9.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "hPadding",
            )
            val bgColor by animateColorAsState(
                targetValue =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
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

            val shape = RoundedCornerShape(pillCorner)
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
                        }.padding(horizontal = hPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(iconBgCorner))
                            .background(contentColor.copy(alpha = if (isSelected) 0.16f else 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(action.iconRes),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(action.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 2,
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
