package af.shizuku.manager.utils

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

/**
 * M3 Expressive shape set for icon containers, built on androidx.graphics.shapes so any two shapes
 * can be morphed into each other. All polygons are normalized to the unit square.
 */
object ExpressiveShapes {
    const val OFF = "off"

    /** Picker order; [OFF] means "use the regular Shape style". */
    val KEYS = listOf(OFF, "cookie", "clover", "sunny", "gem", "flower")

    private val cache = HashMap<String, RoundedPolygon>()

    val circle: RoundedPolygon by lazy { RoundedPolygon.circle(numVertices = 12).normalized() }

    fun polygon(key: String): RoundedPolygon =
        cache.getOrPut(key) {
            when (key) {
                // 9-scallop "cookie" from the M3E shape set.
                "cookie" -> RoundedPolygon.star(9, innerRadius = 0.82f, rounding = CornerRounding(0.3f, smoothing = 0.5f))
                "clover" -> RoundedPolygon.star(4, innerRadius = 0.45f, rounding = CornerRounding(0.42f, smoothing = 0.3f))
                "sunny" -> RoundedPolygon.star(8, innerRadius = 0.86f, rounding = CornerRounding(0.12f))
                "gem" -> RoundedPolygon(6, rounding = CornerRounding(0.3f, smoothing = 0.6f))
                "flower" -> RoundedPolygon.star(6, innerRadius = 0.62f, rounding = CornerRounding(0.36f, smoothing = 0.4f))
                else -> RoundedPolygon.circle(numVertices = 12)
            }.normalized()
        }

    /** Path for [key] (or circle -> key at [progress]) scaled into [width] x [height]. */
    fun path(
        key: String,
        width: Float,
        height: Float,
        progress: Float = 1f,
        into: Path = Path(),
    ): Path {
        into.rewind()
        val target = polygon(key)
        if (progress >= 1f) target.toPath(into) else Morph(circle, target).toPath(progress, into)
        into.transform(Matrix().apply { setScale(width, height) })
        return into
    }
}

/**
 * Icon-container background drawn from an [ExpressiveShapes] polygon. [progress] morphs from a
 * plain circle (0) to the target shape (1), so switch rows can morph their icon on toggle.
 */
class MorphShapeDrawable(
    color: Int,
    val shapeKey: String,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val path = Path()

    var progress: Float = 1f
        set(value) {
            val v = value.coerceIn(0f, 1.15f) // a touch of overshoot for spring animations
            if (field != v) {
                field = v
                invalidateSelf()
            }
        }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        ExpressiveShapes.path(shapeKey, b.width().toFloat(), b.height().toFloat(), progress.coerceAtMost(1f), path)
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        if (progress > 1f) {
            // Overshoot reads as a quick "pop" past full size before settling.
            val s = progress
            canvas.scale(s, s, b.width() / 2f, b.height() / 2f)
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    override fun onBoundsChange(bounds: Rect) = invalidateSelf()

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
