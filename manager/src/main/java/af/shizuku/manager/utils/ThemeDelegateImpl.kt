package af.shizuku.manager.utils

import af.shizuku.core.ui.ThemeDelegate
import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.app.ThemeHelper
import android.app.UiModeManager
import android.content.Context
import android.content.res.Resources.Theme
import android.os.Build
import rikka.core.res.isNight

class ThemeDelegateImpl : ThemeDelegate {
    override fun getThemeKey(context: Context): String {
        val customAccent = ShizukuSettings.getPreferences().getString("custom_accent", "DEFAULT")
        return ThemeHelper.getTheme(context) + ThemeHelper.isUsingSystemColor() + customAccent +
            ShizukuSettings.isRoundedEdgesEnabled() +
            ShizukuSettings.isExpressiveShapesEnabled() + ShizukuSettings.getShapeStyle() +
            ShizukuSettings.getIconStyle() + ShizukuSettings.getIconColorMode() +
            ShizukuSettings.isOneUiThemeEnabled() + ShizukuSettings.isOneHandedModeEnabled() +
            ShizukuSettings.isBoldTypographyEnabled() + ShizukuSettings.isExpandedHeadersEnabled() +
            ShizukuSettings.getCornerRoundingOverride() + ShizukuSettings.isAmoledPlusEnabled() +
            ShizukuSettings.isHighContrastEnabled() + ShizukuSettings.isExpressiveAnimationsEnabled() +
            contrastLevel(context)
    }

    override fun isUsingSystemColor(): Boolean = ThemeHelper.isUsingSystemColor()

    override fun onApplyUserThemeResource(
        context: Context,
        theme: Theme,
        isDecorView: Boolean,
    ) {
        if (ThemeHelper.isUsingSystemColor()) {
            if (context.resources.configuration.isNight()) {
                theme.applyStyle(R.style.ThemeOverlay_DynamicColors_Dark, true)
            } else {
                theme.applyStyle(R.style.ThemeOverlay_DynamicColors_Light, true)
            }
        } else {
            // Generated schemes (themes_accents.xml): light Standard/Medium/High, then dark.
            // Default's standard entry is @null - the base theme already is that scheme.
            val accentArray =
                when (ShizukuSettings.getPreferences().getString("custom_accent", "DEFAULT")) {
                    "VIOLET" -> R.array.theme_overlay_accent_violet
                    "GREEN" -> R.array.theme_overlay_accent_green
                    "CRIMSON" -> R.array.theme_overlay_accent_crimson
                    "OCEAN" -> R.array.theme_overlay_accent_ocean
                    "AMBER" -> R.array.theme_overlay_accent_amber
                    "ROSE" -> R.array.theme_overlay_accent_rose
                    "SLATE" -> R.array.theme_overlay_accent_slate
                    "CYAN" -> R.array.theme_overlay_accent_cyan
                    "NEON" -> R.array.theme_overlay_accent_neon
                    else -> R.array.theme_overlay_accent_default
                }
            val index = (if (context.resources.configuration.isNight()) 3 else 0) + contrastLevel(context)
            val styles = context.resources.obtainTypedArray(accentArray)
            val styleRes = styles.getResourceId(index, 0)
            styles.recycle()
            if (styleRes != 0) {
                theme.applyStyle(styleRes, true)
            }
        }

        // Rounded edges off: no shape style here - ThemeOverlay.Shape.Sharp is applied last,
        // after One UI and the corner rounding override, since both set corner tokens too.
        val roundedEdges = ShizukuSettings.isRoundedEdgesEnabled()
        if (roundedEdges && !ShizukuSettings.isExpressiveShapesEnabled()) {
            // shape_style's preference entry is UI-disabled while expressive_shapes is off, so
            // it doesn't apply here regardless of its stored value - flatten to plain Material3.
            theme.applyStyle(R.style.ThemeOverlay_Shapes_Standard, true)
        } else if (roundedEdges) {
            val shapeStyleRes =
                when (ShizukuSettings.getShapeStyle()) {
                    "zen" -> 0 // "zen": keep the base Material3Expressive corner scale
                    "classic" -> R.style.ThemeOverlay_Shape_Classic
                    "squircle" -> R.style.ThemeOverlay_Shape_Squircle
                    "cut" -> R.style.ThemeOverlay_Shape_Cut
                    else -> R.style.ThemeOverlay_Shape_Modern // "modern" (default)
                }
            if (shapeStyleRes != 0) {
                theme.applyStyle(shapeStyleRes, true)
            }
        }

        // Bold typography overlay applied before One UI so One UI's values win when both are on.
        if (ShizukuSettings.isBoldTypographyEnabled()) {
            theme.applyStyle(R.style.ThemeOverlay_Typography_Bold, true)
        }

        // One UI theme overlay is structure-only (shapes/typography, see themes_overlay.xml) -
        // no color attrs, so it composes with whichever color source (dynamic/custom accent/
        // default) was applied above instead of competing with it, and needs no day/night variant.
        if (ShizukuSettings.isOneUiThemeEnabled()) {
            theme.applyStyle(R.style.ThemeOverlay_OneUI, true)
        }

        // Corner rounding overlay applied last so it wins over both shape_style and One UI corners.
        // "default" = Modern's 32dp ExtraLarge (already set by shape overlay); "system" = no overlay.
        val cornerOverlayRes =
            when (ShizukuSettings.getCornerRoundingOverride()) {
                "gentle" -> R.style.ThemeOverlay_CornerRounding_Gentle
                "standard" -> R.style.ThemeOverlay_CornerRounding_Standard
                "large" -> R.style.ThemeOverlay_CornerRounding_Large
                "maximum" -> R.style.ThemeOverlay_CornerRounding_Maximum
                else -> 0
            }
        if (cornerOverlayRes != 0) {
            theme.applyStyle(cornerOverlayRes, true)
        }

        if (!roundedEdges) {
            theme.applyStyle(R.style.ThemeOverlay_Shape_Sharp, true)
        }

        // System colours have no generated High contrast scheme, so the switch strengthens outlines.
        if (ThemeHelper.isUsingSystemColor() && ShizukuSettings.isHighContrastEnabled()) {
            theme.applyStyle(R.style.ThemeOverlay_HighContrast, true)
        }

        if (!ShizukuSettings.isExpressiveAnimationsEnabled()) {
            theme.applyStyle(R.style.ThemeOverlay_Motion_Standard, true)
        }

        val blackStyle = ThemeHelper.getThemeStyleRes(context)
        theme.applyStyle(blackStyle, true)
        // AMOLED's dim outlines only at standard contrast; above it the scheme's outlines stay.
        if (contrastLevel(context) == 0) {
            when (blackStyle) {
                R.style.ThemeOverlay_Black -> theme.applyStyle(R.style.ThemeOverlay_Black_Outlines, true)
                R.style.ThemeOverlay_Black_Plus -> theme.applyStyle(R.style.ThemeOverlay_Black_Plus_Outlines, true)
            }
        }
    }

    /**
     * 0 standard, 1 medium, 2 high. The in-app High contrast switch wins; otherwise Android 14+'s
     * contrast setting (-1..1), with MDC's thresholds: 1/3 and up is medium, 2/3 and up is high.
     */
    private fun contrastLevel(context: Context): Int {
        if (ShizukuSettings.isHighContrastEnabled()) return 2
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return 0
        val contrast = context.getSystemService(UiModeManager::class.java)?.contrast ?: 0f
        return when {
            contrast >= 2f / 3f -> 2
            contrast >= 1f / 3f -> 1
            else -> 0
        }
    }
}
