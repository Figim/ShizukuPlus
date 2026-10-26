package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.utils.HapticUtils
import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class GesturePreviewPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs),
        SharedPreferences.OnSharedPreferenceChangeListener {
        private val swipeRight = mutableStateOf("open_app")
        private val swipeLeft = mutableStateOf("app_info")

        init {
            layoutResource = R.layout.preference_compose
            isSelectable = false
            syncActions()
        }

        private fun syncActions() {
            val p = ShizukuSettings.getPreferences()
            swipeRight.value = p?.getString("swipe_right_action", "open_app") ?: "open_app"
            swipeLeft.value = p?.getString("swipe_left_action", "app_info") ?: "app_info"
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
                GesturePreviewContent(
                    swipeRight = swipeRight.value,
                    swipeLeft = swipeLeft.value,
                )
            }
        }
    }

@Composable
private fun GesturePreviewContent(
    swipeRight: String,
    swipeLeft: String,
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    val rightLabel = actionName(swipeRight)
    val leftLabel = actionName(swipeLeft)

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Action indicator chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "➔",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text(
                            text = rightLabel,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = leftLabel,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text(
                            text = "⬅",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive swipable item card
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(20.dp)),
            ) {
                // Background action reveal
                val currentOffset = offsetX.value
                val isPastThreshold = kotlin.math.abs(currentOffset) >= 100f
                val revealColor =
                    when {
                        currentOffset > 20f -> MaterialTheme.colorScheme.primaryContainer
                        currentOffset < -20f -> MaterialTheme.colorScheme.secondaryContainer
                        else -> Color.Transparent
                    }

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(revealColor)
                            .padding(horizontal = 16.dp),
                    contentAlignment = if (currentOffset > 0) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    if (currentOffset > 25f) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (isPastThreshold) 0.25f else 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(actionIcon(swipeRight)),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            if (isPastThreshold) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = rightLabel,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    } else if (currentOffset < -25f) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isPastThreshold) {
                                Text(
                                    text = leftLabel,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Box(
                                modifier =
                                    Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (isPastThreshold) 0.25f else 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(actionIcon(swipeLeft)),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                // Foreground mock card that drags
                Surface(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (kotlin.math.abs(offsetX.value) >= 100f) {
                                            HapticUtils.gestureEnd(view)
                                        }
                                        coroutineScope.launch {
                                            offsetX.animateTo(
                                                0f,
                                                animationSpec =
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow,
                                                    ),
                                            )
                                        }
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        coroutineScope.launch {
                                            val prev = offsetX.value
                                            val next = (prev + dragAmount).coerceIn(-240f, 240f)
                                            val wasPast = kotlin.math.abs(prev) >= 100f
                                            val isNowPast = kotlin.math.abs(next) >= 100f
                                            if (isNowPast && !wasPast) {
                                                HapticUtils.gestureThreshold(view)
                                            } else if (!isNowPast && wasPast) {
                                                HapticUtils.tick(view)
                                            }
                                            offsetX.snapTo(next)
                                        }
                                    },
                                )
                            },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_swipe_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.settings_swipe_preview_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun actionName(actionKey: String): String =
    when (actionKey) {
        "open_app" -> stringResource(R.string.settings_action_open_app)
        "app_info" -> stringResource(R.string.settings_action_app_info)
        "toggle_permission" -> stringResource(R.string.settings_action_toggle_permission)
        "hide_from_list" -> stringResource(R.string.settings_action_hide_from_list)
        else -> stringResource(R.string.settings_action_none)
    }

private fun actionIcon(actionKey: String): Int =
    when (actionKey) {
        "open_app" -> R.drawable.ic_outline_play_arrow_24
        "app_info" -> R.drawable.ic_outline_info_24
        "toggle_permission" -> R.drawable.ic_shield_24
        "hide_from_list" -> R.drawable.ic_visibility_off_24
        else -> R.drawable.ic_swipe_24
    }
