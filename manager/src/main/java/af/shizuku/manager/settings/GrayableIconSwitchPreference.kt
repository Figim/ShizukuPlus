package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.utils.MotionUtils.applySpringTouch
import af.shizuku.manager.utils.MotionUtils.springTo
import af.shizuku.manager.utils.springTo
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.R as MaterialR

open class GrayableIconSwitchPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : SwitchPreferenceCompat(context, attrs) {
        private var lastItemView: java.lang.ref.WeakReference<android.view.View>? = null

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            lastItemView = java.lang.ref.WeakReference(holder.itemView)
            holder.itemView.applySpringTouch(scale = 0.985f)

            val toggleStyle = ShizukuSettings.getToggleStyle()
            val switchView = holder.findViewById(R.id.switchWidget) as? MaterialSwitch
            if (switchView != null) {
                if (toggleStyle == "minimal") {
                    switchView.thumbIconDrawable = null
                } else {
                    switchView.setThumbIconResource(R.drawable.m3_switch_thumb_icon)
                }
            }

            // Scannable row typography & container state
            val titleView = holder.findViewById(android.R.id.title) as? TextView
            titleView?.let {
                it.typeface = Typeface.create(Typeface.DEFAULT, if (isChecked) Typeface.BOLD else Typeface.NORMAL)
            }

            updateRowBackground(holder.itemView, isChecked, toggleStyle)

            holder.itemView
                .findViewById<ImageView>(android.R.id.icon)
                ?.apply {
                    alpha = if (isChecked) 1.0f else 0.38f
                    scaleX = if (isChecked) 1.0f else 0.90f
                    scaleY = if (isChecked) 1.0f else 0.90f
                    morphShapeOf(this)?.progress = if (!ShizukuSettings.isIconMorphOnToggle() || isChecked) 1f else 0f
                }
        }

        private fun updateRowBackground(
            view: android.view.View,
            checked: Boolean,
            style: String,
        ) {
            val density = view.resources.displayMetrics.density
            when (style) {
                "card" -> {
                    if (checked) {
                        val color = MaterialColors.getColor(view, MaterialR.attr.colorSurfaceContainerHigh)
                        val outline = MaterialColors.getColor(view, MaterialR.attr.colorOutlineVariant)
                        val bg =
                            GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = 16f * density
                                setColor(color)
                                setStroke((1f * density).toInt(), outline)
                            }
                        view.background = bg
                    } else {
                        view.background = null
                    }
                }
                "vibrant" -> {
                    if (checked) {
                        val color = MaterialColors.getColor(view, MaterialR.attr.colorPrimaryContainer)
                        val bg =
                            GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = 16f * density
                                setColor(ColorUtils.setAlphaComponent(color, 70))
                            }
                        view.background = bg
                    } else {
                        view.background = null
                    }
                }
                else -> {
                    // expressive / minimal: clean default selectable row
                }
            }
        }

        override fun onClick() {
            val willBeChecked = !isChecked
            super.onClick()
            if (isChecked == willBeChecked) {
                lastItemView?.get()?.let { v ->
                    if (isChecked) {
                        af.shizuku.manager.utils.HapticUtils
                            .toggleOn(v)
                    } else {
                        af.shizuku.manager.utils.HapticUtils
                            .toggleOff(v)
                    }
                    updateRowBackground(v, isChecked, ShizukuSettings.getToggleStyle())
                    v.findViewById<TextView>(android.R.id.title)?.let { tv ->
                        tv.typeface = Typeface.create(Typeface.DEFAULT, if (isChecked) Typeface.BOLD else Typeface.NORMAL)
                    }
                    // Icon container morphs circle <-> chosen M3E shape on a spring.
                    v.findViewById<ImageView>(android.R.id.icon)?.let { iv ->
                        val morph = morphShapeOf(iv)
                        if (morph != null && ShizukuSettings.isIconMorphOnToggle()) {
                            morph.springTo(if (isChecked) 1f else 0f)
                        }
                    }
                    // Icon scale springs (may bounce); its dimming fades without overshoot.
                    v.findViewById<ImageView>(android.R.id.icon)?.let { icon ->
                        val scale = if (isChecked) 1.0f else 0.90f
                        icon.springTo(SpringAnimation.SCALE_X, scale, MaterialR.attr.motionSpringFastSpatial)
                        icon.springTo(SpringAnimation.SCALE_Y, scale, MaterialR.attr.motionSpringFastSpatial)
                        icon.springTo(SpringAnimation.ALPHA, if (isChecked) 1.0f else 0.38f, MaterialR.attr.motionSpringFastEffects)
                    }
                }
            }
        }
    }

/** The morphing background layer of a two-tone icon, if the icon uses one. */
private fun morphShapeOf(iv: ImageView): af.shizuku.manager.utils.MorphShapeDrawable? =
    ((iv.drawable as? android.graphics.drawable.LayerDrawable)?.getDrawable(0)) as? af.shizuku.manager.utils.MorphShapeDrawable
