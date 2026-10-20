package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.utils.HapticUtils
import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import kotlin.math.roundToInt

/**
 * Material 3 Expressive discrete slider preference backed by Compose.
 *
 * Provides fluid dragging, live label readout badges, and haptic segment ticks
 * as the user slides across discrete steps.
 *
 * XML usage:
 * ```xml
 * <af.shizuku.manager.settings.SliderPreference
 *     android:key="..."
 *     android:title="..."
 *     android:entries="@array/..."
 *     android:entryValues="@array/..."
 *     android:defaultValue="..." />
 * ```
 */
class SliderPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs) {
        var entries: Array<CharSequence> = emptyArray()
        var entryValues: Array<CharSequence> = emptyArray()

        private val selected = mutableStateOf<String?>(null)

        var value: String?
            get() = selected.value
            set(newValue) {
                val changed = selected.value != newValue
                selected.value = newValue
                if (changed) {
                    persistString(newValue)
                    notifyDependencyChange(shouldDisableDependents())
                }
            }

        init {
            layoutResource = R.layout.preference_compose
            isSelectable = false
            context.obtainStyledAttributes(attrs, R.styleable.SliderPreference).apply {
                getTextArray(R.styleable.SliderPreference_android_entries)?.let { entries = it }
                getTextArray(R.styleable.SliderPreference_android_entryValues)?.let { entryValues = it }
                recycle()
            }
        }

        override fun onGetDefaultValue(
            a: TypedArray,
            index: Int,
        ): Any? = a.getString(index)

        override fun onSetInitialValue(defaultValue: Any?) {
            value = getPersistedString(defaultValue as? String)
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            val cv =
                (holder.itemView as? ComposeView)
                    ?: (holder.findViewById(R.id.preference_compose_view) as? ComposeView)
            if (cv == null) {
                timber.log.Timber.w(
                    "SliderPreference: ComposeView not found in holder (itemView is %s)",
                    holder.itemView.javaClass.name,
                )
                return
            }

            val labels = entries.map { it.toString() }
            val values = entryValues.map { it.toString() }
            val enabled = isEnabled

            cv.setPreferenceContent {
                SliderPreferenceRow(
                    title = title,
                    summary = summary,
                    labels = labels,
                    values = values,
                    currentValue = selected.value,
                    enabled = enabled,
                    onValueChanged = { newValue ->
                        if (newValue != selected.value && callChangeListener(newValue)) {
                            value = newValue
                        }
                    },
                )
            }
        }
    }

@Composable
private fun SliderPreferenceRow(
    title: CharSequence?,
    summary: CharSequence?,
    labels: List<String>,
    values: List<String>,
    currentValue: String?,
    enabled: Boolean,
    onValueChanged: (String) -> Unit,
) {
    if (labels.isEmpty() || values.isEmpty()) return

    val view = LocalView.current
    val disabledAlpha = 0.38f
    val currentIndex = values.indexOf(currentValue).coerceAtLeast(0)

    var sliderValue by remember(currentIndex) { mutableFloatStateOf(currentIndex.toFloat()) }
    val displayIndex = sliderValue.roundToInt().coerceIn(0, labels.size - 1)
    val activeLabel = labels.getOrElse(displayIndex) { "" }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // Header row: Title + Live Value Chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!title.isNullOrEmpty()) {
                Text(
                    text = title.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        MaterialTheme.colorScheme.onSurface.let {
                            if (enabled) it else it.copy(alpha = disabledAlpha)
                        },
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = disabledAlpha)
                    },
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text(
                    text = activeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (enabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
                        },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // Discrete M3 Slider
        val maxSteps = (labels.size - 2).coerceAtLeast(0)
        Slider(
            value = sliderValue,
            onValueChange = { newRaw ->
                val prevStep = sliderValue.roundToInt()
                val nextStep = newRaw.roundToInt()
                if (prevStep != nextStep) {
                    HapticUtils.segmentTick(view)
                }
                sliderValue = newRaw
            },
            onValueChangeFinished = {
                val finalIndex = sliderValue.roundToInt().coerceIn(0, values.size - 1)
                sliderValue = finalIndex.toFloat()
                val newValue = values[finalIndex]
                onValueChanged(newValue)
            },
            valueRange = 0f..(labels.size - 1).toFloat(),
            steps = maxSteps,
            enabled = enabled,
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeTickColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
        )

        // Summary (if provided)
        if (!summary.isNullOrEmpty()) {
            Text(
                text = summary.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant.let {
                        if (enabled) it else it.copy(alpha = disabledAlpha)
                    },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
