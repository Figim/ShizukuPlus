package af.shizuku.manager.utils

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import androidx.annotation.AttrRes
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.R as MaterialR
import com.google.android.material.motion.MotionUtils as MaterialMotion

/**
 * The app's one source of M3 motion tokens: the theme's springs (Expressive, or Standard when
 * "Expressive animations" is off - see ThemeOverlay.Motion.Standard) and the M3 easing curves for
 * the few timed animations that remain.
 */
object MotionUtils {
    /** M3 emphasized decelerate - elements entering the screen. */
    val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    /** M3 emphasized accelerate - elements leaving the screen. */
    val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    /** M3 standard - small utility movement that stays on screen. */
    val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** M3 standard accelerate - utility elements leaving. */
    val STANDARD_ACCELERATE = PathInterpolator(0.3f, 0f, 1f, 1f)

    /**
     * Resolves one of the theme's `motionSpring*` attrs, e.g. [MaterialR.attr.motionSpringFastSpatial].
     * Spatial springs may overshoot; use an Effects spring for alpha and colour. A context without
     * a Material3 theme gets a plain no-bounce spring instead of MDC's exception.
     */
    fun spring(
        context: Context,
        @AttrRes attr: Int,
    ): SpringForce =
        try {
            MaterialMotion.resolveThemeSpringForce(context, attr, 0)
        } catch (e: IllegalArgumentException) {
            SpringForce()
                .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
        }

    /**
     * Applies a spring-based scale down/up effect on touch.
     */
    fun View.applySpringTouch(scale: Float = 0.97f) {
        // Single spring per axis; animateToFinalPosition() composes smoothly mid-animation
        // (no ViewPropertyAnimator discontinuity when the finger lifts before the press-in completes).
        val force = spring(context, MaterialR.attr.motionSpringFastSpatial)
        val springX =
            SpringAnimation(this, SpringAnimation.SCALE_X).apply {
                spring = SpringForce(1.0f).setStiffness(force.stiffness).setDampingRatio(force.dampingRatio)
            }
        val springY =
            SpringAnimation(this, SpringAnimation.SCALE_Y).apply {
                spring = SpringForce(1.0f).setStiffness(force.stiffness).setDampingRatio(force.dampingRatio)
            }

        setOnTouchListener { v, event ->
            // Unlike every other animation/haptic call site in the app, this touch feedback
            // used to fire unconditionally on every card tap, ignoring the user's Expressive
            // Animations toggle.
            if (!af.shizuku.manager.ShizukuSettings
                    .isExpressiveAnimationsEnabled()
            ) {
                return@setOnTouchListener false
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    springX.animateToFinalPosition(scale)
                    springY.animateToFinalPosition(scale)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    springX.animateToFinalPosition(1.0f)
                    springY.animateToFinalPosition(1.0f)
                }
            }
            false
        }
    }
}
