package af.shizuku.manager.settings

import af.shizuku.manager.ShizukuSettings
import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat

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
            holder.itemView
                .findViewById<ImageView>(android.R.id.icon)
                ?.apply {
                    alpha = if (isChecked) 1.0f else 0.38f
                    scaleX = if (isChecked) 1.0f else 0.90f
                    scaleY = if (isChecked) 1.0f else 0.90f
                    morphShapeOf(this)?.progress = if (!ShizukuSettings.isIconMorphOnToggle() || isChecked) 1f else 0f
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
                    // Icon container morphs circle <-> chosen M3E shape on a spring.
                    v.findViewById<ImageView>(android.R.id.icon)?.let { iv ->
                        val morph = morphShapeOf(iv)
                        if (morph != null && ShizukuSettings.isIconMorphOnToggle()) {
                            androidx.dynamicanimation.animation
                                .SpringAnimation(
                                    morph,
                                    object : androidx.dynamicanimation.animation.FloatPropertyCompat<af.shizuku.manager.utils.MorphShapeDrawable>("morph") {
                                        override fun getValue(d: af.shizuku.manager.utils.MorphShapeDrawable) = d.progress

                                        override fun setValue(
                                            d: af.shizuku.manager.utils.MorphShapeDrawable,
                                            value: Float,
                                        ) {
                                            d.progress = value
                                        }
                                    },
                                    if (isChecked) 1f else 0f,
                                ).apply {
                                    spring.dampingRatio = androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                                    spring.stiffness = androidx.dynamicanimation.animation.SpringForce.STIFFNESS_LOW
                                    setStartValue(morph.progress)
                                    if (ShizukuSettings.getAnimationDurationScale() > 0f) start() else morph.progress = if (isChecked) 1f else 0f
                                }
                        }
                    }
                    // Smooth icon alpha and spring scale transition on toggle.
                    v
                        .findViewById<ImageView>(android.R.id.icon)
                        ?.animate()
                        ?.alpha(if (isChecked) 1.0f else 0.38f)
                        ?.scaleX(if (isChecked) 1.0f else 0.90f)
                        ?.scaleY(if (isChecked) 1.0f else 0.90f)
                        ?.setDuration(ShizukuSettings.scaledAnimationDuration(200))
                        ?.setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                        ?.start()
                }
            }
        }
    }

/** The morphing background layer of a two-tone icon, if the icon uses one. */
private fun morphShapeOf(iv: ImageView): af.shizuku.manager.utils.MorphShapeDrawable? =
    ((iv.drawable as? android.graphics.drawable.LayerDrawable)?.getDrawable(0)) as? af.shizuku.manager.utils.MorphShapeDrawable
