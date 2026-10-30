package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.utils.MotionUtils.applySpringTouch
import af.shizuku.manager.utils.MotionUtils.springTo
import af.shizuku.manager.utils.springTo
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
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
            // Rows are recycled between preferences: remember who owns this view now, so a click
            // never animates a row that has since been bound to another setting.
            holder.itemView.setTag(R.id.tag_switch_row_owner, this)
            // The row's own ripple background, captured once per view before we replace it.
            if (holder.itemView.getTag(R.id.tag_switch_row_default_bg) == null) {
                holder.itemView.setTag(R.id.tag_switch_row_default_bg, holder.itemView.background ?: NoBackground)
            }
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
            val insetH = (10f * density).toInt()
            val insetV = (2f * density).toInt()

            when (style) {
                "card" -> {
                    if (checked) {
                        val color = activeRowColor(view, MaterialR.attr.colorSurfaceContainerHigh)
                        val outline = MaterialColors.getColor(view, MaterialR.attr.colorOutlineVariant)
                        val bg =
                            GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = 16f * density
                                setColor(color)
                                setStroke((1f * density).toInt(), outline)
                            }
                        view.background = withRipple(view, InsetDrawable(bg, insetH, insetV, insetH, insetV))
                    } else {
                        view.background = defaultBackground(view)
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
                        view.background = withRipple(view, InsetDrawable(bg, insetH, insetV, insetH, insetV))
                    } else {
                        view.background = defaultBackground(view)
                    }
                }
                "expressive" -> {
                    if (checked) {
                        val color = activeRowColor(view, MaterialR.attr.colorSurfaceContainerLow)
                        val outline = MaterialColors.getColor(view, MaterialR.attr.colorOutlineVariant)
                        val bg =
                            GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = 16f * density
                                setColor(color)
                                setStroke((1f * density).toInt(), ColorUtils.setAlphaComponent(outline, 80))
                            }
                        view.background = withRipple(view, InsetDrawable(bg, insetH, insetV, insetH, insetV))
                    } else {
                        view.background = defaultBackground(view)
                    }
                }
                else -> {
                    // minimal: clean default selectable row
                    view.background = defaultBackground(view)
                }
            }
        }

        override fun onClick() {
            val willBeChecked = !isChecked
            super.onClick()
            if (isChecked == willBeChecked) {
                val row = lastItemView?.get()
                if (row == null || row.getTag(R.id.tag_switch_row_owner) !== this) {
                    // Our last view now belongs to another row: rebind ours instead of animating it.
                    notifyChanged()
                    return
                }
                row.let { v ->
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

/**
 * Background for an enabled row. On AMOLED themes the surface-container ramp is black (or nearly),
 * so an enabled row became a black block on the slightly lighter card. Use a soft secondary
 * container tint there instead, as the Compose controls do (controlContainer).
 */
private fun activeRowColor(
    view: android.view.View,
    containerAttr: Int,
): Int {
    val container = MaterialColors.getColor(view, containerAttr)
    if (ColorUtils.calculateLuminance(container) > 0.01) return container
    val tonal = MaterialColors.getColor(view, MaterialR.attr.colorSecondaryContainer)
    return ColorUtils.setAlphaComponent(tonal, 110)
}

private object NoBackground

/** The row's original (ripple) background, as a fresh copy. */
private fun defaultBackground(view: android.view.View): android.graphics.drawable.Drawable? {
    val original = view.getTag(R.id.tag_switch_row_default_bg) as? android.graphics.drawable.Drawable ?: return null
    return original.constantState?.newDrawable(view.resources)?.mutate() ?: original
}

/** [fill] under the row's ripple, so enabled rows keep their touch feedback. */
private fun withRipple(
    view: android.view.View,
    fill: android.graphics.drawable.Drawable,
): android.graphics.drawable.Drawable {
    val ripple = defaultBackground(view) ?: return fill
    return android.graphics.drawable.LayerDrawable(arrayOf(fill, ripple))
}

/** The morphing background layer of a two-tone icon, if the icon uses one. */
private fun morphShapeOf(iv: ImageView): af.shizuku.manager.utils.MorphShapeDrawable? =
    ((iv.drawable as? android.graphics.drawable.LayerDrawable)?.getDrawable(0)) as? af.shizuku.manager.utils.MorphShapeDrawable
