package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.utils.HapticUtils
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Expressive Material 3 compound card preference wrapping all update controls:
 * - Update status & manual check action
 * - Channel switcher (Stable vs Dev)
 * - Automatic update check toggle
 * - Automatic install toggle
 */
class UpdateSettingsPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Preference(context, attrs) {
        private var cardContainer: MaterialCardView? = null
        private var textStatus: TextView? = null
        private var btnCheckUpdate: MaterialButton? = null
        private var progressCheckUpdate: ProgressBar? = null
        private var toggleChannel: MaterialButtonToggleGroup? = null
        private var switchAutoUpdate: MaterialSwitch? = null
        private var rowAutoInstall: View? = null
        private var switchAutoInstall: MaterialSwitch? = null

        var onCheckClickListener: (() -> Unit)? = null
        var onChannelChangeListener: ((newChannel: String) -> Unit)? = null
        var onAutoUpdateChangeListener: ((Boolean) -> Unit)? = null
        var onAutoInstallChangeListener: ((Boolean) -> Unit)? = null

        var isChecking: Boolean = false
            set(value) {
                field = value
                updateCheckingUi()
            }

        var statusText: CharSequence? = null
            set(value) {
                field = value
                textStatus?.text = value
            }

        var channel: String = "stable"
            set(value) {
                field = value
                updateChannelUi()
            }

        var isAutoUpdateEnabled: Boolean = true
            set(value) {
                field = value
                updateAutoUpdateUi()
            }

        var isAutoInstallEnabled: Boolean = false
            set(value) {
                field = value
                updateAutoInstallUi()
            }

        init {
            layoutResource = R.layout.preference_update_card
            isSelectable = false
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            holder.itemView.tag = "standalone_card"

            cardContainer = holder.findViewById(R.id.card_update_container) as? MaterialCardView
            textStatus = holder.findViewById(R.id.text_update_status) as? TextView
            btnCheckUpdate = holder.findViewById(R.id.btn_check_update) as? MaterialButton
            progressCheckUpdate = holder.findViewById(R.id.progress_check_update) as? ProgressBar
            toggleChannel = holder.findViewById(R.id.toggle_update_channel) as? MaterialButtonToggleGroup
            switchAutoUpdate = holder.findViewById(R.id.switch_auto_update) as? MaterialSwitch
            rowAutoInstall = holder.findViewById(R.id.row_auto_install)
            switchAutoInstall = holder.findViewById(R.id.switch_auto_install) as? MaterialSwitch

            if (!ShizukuSettings.isRoundedEdgesEnabled()) {
                cardContainer?.radius = 0f
            }

            textStatus?.text = statusText ?: context.getString(R.string.settings_check_for_update_summary)

            btnCheckUpdate?.setOnClickListener {
                HapticUtils.tap(it)
                onCheckClickListener?.invoke()
            }

            updateCheckingUi()
            updateChannelUi()

            toggleChannel?.clearOnButtonCheckedListeners()
            toggleChannel?.addOnButtonCheckedListener { group, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val newChannel = if (checkedId == R.id.btn_channel_dev) "dev" else "stable"
                if (newChannel != channel) {
                    HapticUtils.segmentTick(group)
                    onChannelChangeListener?.invoke(newChannel)
                }
            }

            switchAutoUpdate?.setOnCheckedChangeListener(null)
            switchAutoUpdate?.isChecked = isAutoUpdateEnabled
            switchAutoUpdate?.setOnCheckedChangeListener { view, checked ->
                if (checked) HapticUtils.toggleOn(view) else HapticUtils.toggleOff(view)
                isAutoUpdateEnabled = checked
                updateAutoInstallRowState()
                onAutoUpdateChangeListener?.invoke(checked)
            }

            switchAutoInstall?.setOnCheckedChangeListener(null)
            switchAutoInstall?.isChecked = isAutoInstallEnabled
            switchAutoInstall?.setOnCheckedChangeListener { view, checked ->
                if (checked) HapticUtils.toggleOn(view) else HapticUtils.toggleOff(view)
                isAutoInstallEnabled = checked
                onAutoInstallChangeListener?.invoke(checked)
            }

            updateAutoInstallRowState()
        }

        private fun updateCheckingUi() {
            if (isChecking) {
                btnCheckUpdate?.isEnabled = false
                btnCheckUpdate?.visibility = View.INVISIBLE
                progressCheckUpdate?.visibility = View.VISIBLE
            } else {
                btnCheckUpdate?.isEnabled = true
                btnCheckUpdate?.visibility = View.VISIBLE
                progressCheckUpdate?.visibility = View.GONE
            }
        }

        private fun updateChannelUi() {
            val targetId = if (channel == "dev") R.id.btn_channel_dev else R.id.btn_channel_stable
            toggleChannel?.let { group ->
                if (group.checkedButtonId != targetId) {
                    group.check(targetId)
                }
            }
        }

        private fun updateAutoUpdateUi() {
            switchAutoUpdate?.let { switch ->
                if (switch.isChecked != isAutoUpdateEnabled) {
                    switch.isChecked = isAutoUpdateEnabled
                }
            }
            updateAutoInstallRowState()
        }

        private fun updateAutoInstallUi() {
            switchAutoInstall?.let { switch ->
                if (switch.isChecked != isAutoInstallEnabled) {
                    switch.isChecked = isAutoInstallEnabled
                }
            }
        }

        private fun updateAutoInstallRowState() {
            val enabled = isAutoUpdateEnabled
            rowAutoInstall?.isEnabled = enabled
            rowAutoInstall?.alpha = if (enabled) 1.0f else 0.4f
            switchAutoInstall?.isEnabled = enabled
        }
    }
