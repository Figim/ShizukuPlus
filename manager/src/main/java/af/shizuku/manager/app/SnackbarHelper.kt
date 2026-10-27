package af.shizuku.manager.app

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import android.app.Activity
import android.content.Context
import android.view.View
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
        // MDC's default snackbar is the M3 spec: inverse-surface container, inverse-primary
        // action, corners from the theme's shape scale. A surface-coloured custom pill used to
        // replace it and sank into cards on AMOLED.

        // Sit above the optional floating nav bar instead of underneath/over it (#498).
        val navRoot =
            view.rootView?.findViewById<View>(R.id.floating_nav_root)
                ?: (context as? Activity)?.findViewById<View>(R.id.floating_nav_root)
        if (navRoot != null && ShizukuSettings.isFloatingNavBarEnabled() && navRoot.visibility == View.VISIBLE) {
            newSnackbar.anchorView = navRoot
        }

        // Ensure snackbar text never clips or truncates with ellipses
        val textView =
            newSnackbar.view.findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_text)
        textView?.isSingleLine = false
        textView?.maxLines = 5
        textView?.ellipsize = null

        newSnackbar.show()
        snackbar = newSnackbar

        // If the floating nav bar was being measured or added concurrently, bind anchor
        // and ensure bottom margin clears floating nav bar when anchorView is not honored by FrameLayout
        view.post {
            val liveNav =
                view.rootView?.findViewById<View>(R.id.floating_nav_root)
                    ?: (context as? Activity)?.findViewById<View>(R.id.floating_nav_root)
            if (liveNav != null && ShizukuSettings.isFloatingNavBarEnabled() && liveNav.visibility == View.VISIBLE) {
                updateAnchor(liveNav)
                val navHeight = if (liveNav.height > 0) liveNav.height else (liveNav.layoutParams?.height ?: 0)
                val navBottom = (liveNav.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                val extraMargin = (12 * context.resources.displayMetrics.density).toInt()
                val targetBottom = navHeight + navBottom + extraMargin
                (newSnackbar.view.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
                    if (lp.bottomMargin < targetBottom) {
                        lp.bottomMargin = targetBottom
                        newSnackbar.view.layoutParams = lp
                    }
                }
            }
        }
    }

    fun updateAnchor(anchorView: View?) {
        snackbar?.let { sb ->
            if (sb.anchorView != anchorView) {
                sb.anchorView = anchorView
                if (anchorView != null && anchorView.visibility == View.VISIBLE) {
                    val navHeight = if (anchorView.height > 0) anchorView.height else (anchorView.layoutParams?.height ?: 0)
                    val navBottom = (anchorView.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                    val extraMargin = (12 * anchorView.resources.displayMetrics.density).toInt()
                    val targetBottom = navHeight + navBottom + extraMargin
                    (sb.view.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
                        if (lp.bottomMargin < targetBottom) {
                            lp.bottomMargin = targetBottom
                        }
                    }
                }
                sb.view.requestLayout()
            }
        }
    }

    fun dismiss() {
        snackbar?.dismiss()
        snackbar = null
    }
}
