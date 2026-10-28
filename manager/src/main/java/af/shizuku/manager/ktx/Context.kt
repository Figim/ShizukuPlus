package af.shizuku.manager.ktx

import af.shizuku.manager.ShizukuApplication
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Pair
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

fun Activity.startWithSceneTransition(
    intent: Intent,
    sharedView: View? = null,
    transitionName: String? = null,
) {
    val bundle =
        if (sharedView != null && !transitionName.isNullOrEmpty()) {
            androidx.core.app.ActivityOptionsCompat
                .makeSceneTransitionAnimation(
                    this,
                    androidx.core.util.Pair(sharedView, transitionName),
                ).toBundle()
        } else {
            androidx.core.app.ActivityOptionsCompat
                .makeSceneTransitionAnimation(this)
                .toBundle()
        }
    startActivity(intent, bundle)
}

fun Activity.startWithSceneTransition(intent: Intent) {
    val options =
        androidx.core.app.ActivityOptionsCompat
            .makeSceneTransitionAnimation(this)
    startActivity(intent, options.toBundle())
}

@ColorInt
fun Context.themeColor(
    @AttrRes attr: Int,
): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return tv.data
}

/**
 * Resolves a `?attr/shapeAppearanceCorner*` theme attribute to its absolute corner size in
 * pixels, so Canvas-drawn (non-MaterialCardView) shapes can follow the Shape Style setting too.
 * Assumes the referenced ShapeAppearance uses an absolute cornerSize (true for every
 * ThemeOverlay.Shape.* variant in this app) — a percentage-based corner would need real bounds.
 */
fun Context.themeCornerSizePx(
    @AttrRes attr: Int,
): Float {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    val shapeAppearanceModel =
        com.google.android.material.shape.ShapeAppearanceModel
            .builder(this, 0, tv.resourceId)
            .build()
    return shapeAppearanceModel.topLeftCornerSize.getCornerSize(android.graphics.RectF())
}

val Context.application: ShizukuApplication
    get() {
        return applicationContext as ShizukuApplication
    }
