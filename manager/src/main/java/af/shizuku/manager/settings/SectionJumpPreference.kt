package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.utils.HapticUtils
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * A row of pill chips at the top of long settings screens - one per collapsible section - so a
 * section can be reached (and expanded) in one tap instead of scrolling past every other group.
 */
class SectionJumpPreference(
    context: Context,
    private val sections: List<Pair<String, CharSequence>>,
    private val onJump: (String) -> Unit,
) : Preference(context) {
    init {
        layoutResource = R.layout.preference_compose
        isSelectable = false
        isPersistent = false
        key = KEY
        // Ahead of everything that kept the default insertion order.
        order = -1
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // Bare row (no card), like a section label.
        holder.itemView.tag = "section_label"
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        val cv = holder.findViewById(R.id.preference_compose_view) as? ComposeView ?: holder.itemView as? ComposeView ?: return
        cv.setPreferenceContent {
            val view = LocalView.current
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(PaddingValues(horizontal = 4.dp, vertical = 4.dp)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sections.forEach { (key, title) ->
                    AssistChip(
                        onClick = {
                            HapticUtils.segmentTick(view)
                            onJump(key)
                        },
                        label = { Text(title.toString(), style = MaterialTheme.typography.labelLarge) },
                        shape = CircleShape,
                        colors =
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        border = null,
                    )
                }
            }
        }
    }

    companion object {
        const val KEY = "section_jump_bar"
        const val MIN_SECTIONS = 4
    }
}
