package af.shizuku.manager.app

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar

object SnackbarHelper {
    private var snackbar: Snackbar? = null

    fun show(
        context: Context,
        view: View,
        msg: String,
        duration: Int = Snackbar.LENGTH_SHORT,
        actionText: String? = null,
        action: (() -> Unit)? = null,
        onDismiss: ((event: Int) -> Unit)? = null,
    ) {
        dismiss() // Dismiss any existing snackbar
        val newSnackbar = Snackbar.make(view, msg, duration).setDuration(duration)
        if (action != null) {
            newSnackbar.setAction(actionText ?: context.getString(android.R.string.ok)) { action() }
        }
        if (onDismiss != null) {
            newSnackbar.addCallback(
                object : Snackbar.Callback() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        onDismiss(event)
                    }
                },
            )
        }
        ThemeHelper.applySnackbarTheme(context, newSnackbar)

        // Make the snackbar an elevated pill with rounded corners
        val density = context.resources.displayMetrics.density
        val snackView = newSnackbar.view
        val shape =
            ShapeAppearanceModel
                .builder()
                .setAllCornerSizes(16f * density)
                .build()
        val bgDrawable =
            MaterialShapeDrawable(shape).apply {
                val surfaceContainer =
                    ThemeHelper.resolveColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh)
                fillColor =
                    ColorStateList.valueOf(
                        if (surfaceContainer != 0 && surfaceContainer != Color.BLACK) {
                            surfaceContainer
                        } else {
                            ThemeHelper.resolveColor(context, R.attr.colorPrimaryContainer)
                        },
                    )
                elevation = 6f * density
                if (ShizukuSettings.isHighContrastEnabled() || ShizukuSettings.isAmoledPlusEnabled()) {
                    strokeColor = ColorStateList.valueOf(Color.argb(80, 255, 255, 255))
                    strokeWidth = 1f * density
                }
            }
        ViewCompat.setBackground(snackView, bgDrawable)

        (snackView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            val marginH = (16 * density).toInt()
            lp.leftMargin = marginH
            lp.rightMargin = marginH
            lp.bottomMargin = (12 * density).toInt()
            snackView.layoutParams = lp
        }

        // Sit above the optional floating nav bar instead of underneath/over it (#498).
        val navRoot =
            view.rootView?.findViewById<View>(R.id.floating_nav_root)
                ?: (context as? Activity)?.findViewById<View>(R.id.floating_nav_root)
        if (navRoot != null && ShizukuSettings.isFloatingNavBarEnabled()) {
            newSnackbar.anchorView = navRoot
        }

        newSnackbar.show()
        snackbar = newSnackbar

        // If the floating nav bar was being measured or added concurrently, bind anchor
        view.post {
            val liveNav =
                view.rootView?.findViewById<View>(R.id.floating_nav_root)
                    ?: (context as? Activity)?.findViewById<View>(R.id.floating_nav_root)
            if (liveNav != null && ShizukuSettings.isFloatingNavBarEnabled()) {
                updateAnchor(liveNav)
            }
        }
    }

    fun updateAnchor(anchorView: View?) {
        snackbar?.let { sb ->
            if (sb.anchorView != anchorView) {
                sb.anchorView = anchorView
                sb.view.requestLayout()
            }
        }
    }

    fun dismiss() {
        snackbar?.dismiss()
        snackbar = null
    }
}
