package af.shizuku.manager.settings

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuLocales
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.ShizukuSettings.Keys.*
import af.shizuku.manager.app.ThemeHelper
import af.shizuku.manager.ktx.toHtml
import af.shizuku.manager.utils.CustomTabsHelper
import af.shizuku.manager.utils.EnvironmentUtils
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import rikka.core.util.ResourceUtils
import rikka.material.app.LocaleDelegate
import java.util.Locale

class PersonalizationSettingsFragment : BaseSettingsFragment() {
    private val iconPickerLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .GetContent(),
        ) { uri ->
            if (uri != null) {
                CustomIconStudioBottomSheet
                    .newInstance(uri)
                    .show(childFragmentManager, "CustomIconStudio")
            }
        }

    private fun applyTheme(requiresRecreate: Boolean = false) {
        if (requiresRecreate) {
            (activity as? af.shizuku.core.ui.AppActivity)?.recreateWithoutTransition()
        } else {
            (activity as? SettingsActivity)?.onThemeChanged()
        }
    }

    override fun getTitle(): CharSequence? = getString(R.string.settings_category_appearance)

    private lateinit var nightModePreference: IntegerSegmentedControlPreference
    private lateinit var darkStylePreference: SegmentedControlPreference
    private lateinit var useSystemColorPreference: TwoStatePreference
    private lateinit var customAccentPreference: Preference
    private lateinit var languagePreference: ListPreference
    private lateinit var translationPreference: Preference
    private lateinit var expressiveShapesPreference: TwoStatePreference
    private lateinit var expressiveAnimationsPreference: TwoStatePreference
    private lateinit var iconStylePreference: SegmentedControlPreference
    private lateinit var iconColorModePreference: SegmentedControlPreference
    private lateinit var shapeStylePreference: SliderPreference
    private lateinit var animationIntensityPreference: SliderPreference
    private lateinit var edgeToEdgePreference: TwoStatePreference
    private lateinit var blurUiPreference: TwoStatePreference
    private lateinit var oneUiThemePreference: TwoStatePreference

    override fun onCreateSettingsPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_personalization, rootKey)
        val context = requireContext()

        // 1. Theme and Color Controls
        nightModePreference = requireNotNull(findPreference(KEY_NIGHT_MODE))
        darkStylePreference = requireNotNull(findPreference("dark_style"))
        useSystemColorPreference = requireNotNull(findPreference(KEY_USE_SYSTEM_COLOR))
        customAccentPreference = requireNotNull(findPreference("custom_accent"))

        nightModePreference.apply {
            value = ShizukuSettings.getNightMode()
            setOnPreferenceChangeListener { _, value ->
                if (value is Int) {
                    if (ShizukuSettings.getNightMode() != value) {
                        AppCompatDelegate.setDefaultNightMode(value)
                        syncDependentVisibility()
                        applyTheme(requiresRecreate = true)
                    }
                }
                true
            }
        }

        // One "Dark style" choice replaces the Black night theme + AMOLED+ switch pair (AMOLED+
        // only ever made sense on top of Black). Same two keys underneath, so nothing resets.
        setChildAvailable(darkStylePreference, ShizukuSettings.getNightMode() != AppCompatDelegate.MODE_NIGHT_NO)
        darkStylePreference.value = currentDarkStyle()
        darkStylePreference.setOnPreferenceChangeListener { _, newValue ->
            val style = newValue as? String ?: return@setOnPreferenceChangeListener false
            ShizukuSettings
                .getPreferences()
                .edit()
                .putBoolean(KEY_BLACK_NIGHT_THEME, style != DARK_STYLE_STANDARD)
                .putBoolean(KEY_AMOLED_PLUS, style == DARK_STYLE_AMOLED)
                .apply()
            if (ResourceUtils.isNightMode(context.resources.configuration)) {
                applyTheme(requiresRecreate = false)
            }
            true
        }

        val systemColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        setChildAvailable(useSystemColorPreference, systemColorAvailable)
        useSystemColorPreference.apply {
            if (systemColorAvailable) {
                isChecked = ThemeHelper.isUsingSystemColor()
                setOnPreferenceChangeListener { _, value ->
                    if (value is Boolean) {
                        if (ThemeHelper.isUsingSystemColor() != value) {
                            // Hide (not grey out) the accent picker while system colors win:
                            // a disabled slider still showed a stale accent name.
                            setChildAvailable(customAccentPreference, !value)
                            applyTheme(requiresRecreate = false)
                        }
                    }
                    true
                }
            }
        }

        customAccentPreference.apply {
            setChildAvailable(this, !ThemeHelper.isUsingSystemColor())
            setCustomSummary(getCustomAccentSummary())
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue is String) {
                    val prefs = ShizukuSettings.getPreferences()
                    if (prefs.getString("custom_accent", "DEFAULT") != newValue) {
                        prefs.edit().putString("custom_accent", newValue).apply()
                        setCustomSummary(getCustomAccentSummary(newValue))
                        applyTheme(requiresRecreate = false)
                    }
                }
                true
            }
        }

        // 2. M3 Expressive Controls
        expressiveShapesPreference = requireNotNull(findPreference(KEY_EXPRESSIVE_SHAPES))
        expressiveAnimationsPreference = requireNotNull(findPreference(KEY_EXPRESSIVE_ANIMATIONS))
        iconStylePreference = requireNotNull(findPreference(KEY_ICON_STYLE))
        iconColorModePreference = requireNotNull(findPreference(KEY_ICON_COLOR_MODE))
        shapeStylePreference = requireNotNull(findPreference(KEY_SHAPE_STYLE))
        animationIntensityPreference = requireNotNull(findPreference(KEY_ANIMATION_INTENSITY))

        // Only meaningful for the Two-Tone icon style — updated dynamically below so the
        // color-mode row appears/disappears without requiring a fragment recreate.
        setChildAvailable(iconColorModePreference, iconStylePreference.value == "twotone")
        setChildAvailable(shapeStylePreference, expressiveShapesPreference.isChecked)
        setChildAvailable(animationIntensityPreference, expressiveAnimationsPreference.isChecked)

        expressiveShapesPreference.setOnPreferenceChangeListener { _, newValue ->
            if (newValue is Boolean) {
                setChildAvailable(shapeStylePreference, newValue)
            }
            applyTheme(requiresRecreate = false)
            refreshIconStyles()
            true
        }

        // M3E morph shapes: only meaningful with expressive shapes on; the toggle-morph switch
        // only matters once a shape is picked.
        val morphShapePreference = findPreference<Preference>("icon_morph_shape")
        val morphTogglePreference = findPreference<Preference>("icon_morph_on_toggle")

        fun syncMorphAvailability(shape: String = ShizukuSettings.getIconMorphShape()) {
            val shapesOn = expressiveShapesPreference.isChecked
            morphShapePreference?.setAvailable(shapesOn)
            morphTogglePreference?.setAvailable(shapesOn && shape != af.shizuku.manager.utils.ExpressiveShapes.OFF)
        }
        syncMorphAvailability()
        morphShapePreference?.setOnPreferenceChangeListener { _, newValue ->
            syncMorphAvailability(newValue as String)
            // Restyle once the new value has been persisted.
            view?.post {
                applyTheme(requiresRecreate = false)
                refreshIconStyles()
            }
            true
        }
        morphTogglePreference?.setOnPreferenceChangeListener { _, _ ->
            view?.post { refreshIconStyles() }
            true
        }

        shapeStylePreference.setOnPreferenceChangeListener { _, _ ->
            applyTheme(requiresRecreate = false)
            refreshIconStyles()
            true
        }

        iconStylePreference.setOnPreferenceChangeListener { _, newValue ->
            // Show/hide the color-mode sub-option immediately rather than on next recreate.
            setChildAvailable(iconColorModePreference, newValue == "twotone")
            applyTheme(requiresRecreate = false)
            refreshIconStyles()
            true
        }

        iconColorModePreference.setOnPreferenceChangeListener { _, _ ->
            applyTheme(requiresRecreate = false)
            refreshIconStyles()
            true
        }

        val roundedEdgesPreference = findPreference<TwoStatePreference>("rounded_edges_enabled")
        val cornerRoundingPreference = findPreference<Preference>("corner_rounding_override")
        val homeCardCornerPreference = findPreference<Preference>("home_card_corner_style")
        val settingsCardCornerPreference = findPreference<Preference>("settings_card_corner_style")

        fun syncCornerRoundingAvailability(enabled: Boolean) {
            cornerRoundingPreference?.let { setChildAvailable(it, enabled) }
            homeCardCornerPreference?.let { setChildAvailable(it, enabled) }
            settingsCardCornerPreference?.let { setChildAvailable(it, enabled) }
        }
        syncCornerRoundingAvailability(roundedEdgesPreference?.isChecked ?: true)

        roundedEdgesPreference?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue is Boolean) {
                syncCornerRoundingAvailability(newValue)
            }
            applyTheme(requiresRecreate = false)
            true
        }

        homeCardCornerPreference?.setOnPreferenceChangeListener { _, _ ->
            applyTheme(requiresRecreate = false)
            true
        }

        settingsCardCornerPreference?.setOnPreferenceChangeListener { _, _ ->
            applyTheme(requiresRecreate = false)
            true
        }

        cornerRoundingPreference?.setOnPreferenceChangeListener { _, _ ->
            applyTheme(requiresRecreate = false)
            true
        }

        findPreference<Preference>("expanded_headers_enabled")?.setOnPreferenceChangeListener { _, _ ->
            applyTheme(requiresRecreate = false)
            true
        }

        expressiveAnimationsPreference.setOnPreferenceChangeListener { _, _ ->
            // isChecked updates after this returns; re-sync once it has.
            view?.post { syncIntensityAvailability() }
            applyTheme(requiresRecreate = false)
            true
        }

        animationIntensityPreference.setOnPreferenceChangeListener { _, _ ->
            applyTheme(requiresRecreate = false)
            true
        }

        // Off / Follow system / Always on replaces "Haptic feedback" + its dependent
        // "Follow system haptics" switch; writes the same two keys.
        findPreference<SegmentedControlPreference>("haptics_mode")?.apply {
            val prefs = ShizukuSettings.getPreferences()
            value =
                when {
                    !prefs.getBoolean(ShizukuSettings.Keys.KEY_HAPTIC_FEEDBACK, true) -> "off"
                    prefs.getBoolean(ShizukuSettings.Keys.KEY_FOLLOW_SYSTEM_HAPTICS, false) -> "system"
                    else -> "always"
                }
            setOnPreferenceChangeListener { _, newValue ->
                val mode = newValue as? String ?: return@setOnPreferenceChangeListener false
                prefs
                    .edit()
                    .putBoolean(ShizukuSettings.Keys.KEY_HAPTIC_FEEDBACK, mode != "off")
                    .putBoolean(ShizukuSettings.Keys.KEY_FOLLOW_SYSTEM_HAPTICS, mode == "system")
                    .apply()
                true
            }
        }

        // Intensity is ignored while following the system animation speed - hide it then.
        syncIntensityAvailability()
        findPreference<TwoStatePreference>("follow_system_animations")?.setOnPreferenceChangeListener { _, _ ->
            view?.post { syncIntensityAvailability() }
            applyTheme(requiresRecreate = false)
            true
        }

        // One UI and one-handed mode always use expanded headers, so the switch does nothing there.
        findPreference<Preference>("expanded_headers_enabled")?.let {
            setChildAvailable(it, !ShizukuSettings.isOneUiThemeEnabled() && !ShizukuSettings.isOneHandedModeEnabled())
        }

        val showStatusOutlinePreference = findPreference<TwoStatePreference>("show_status_card_outline")
        val statusOutlineStylePreference = findPreference<Preference>("status_card_outline_style")
        statusOutlineStylePreference?.let { setChildAvailable(it, showStatusOutlinePreference?.isChecked ?: true) }

        // Experimental (Developer options): hide the whole App icon & name section unless enabled.
        findPreference<Preference>("category_app_identity")?.isVisible =
            ShizukuSettings.isExperimentalAppIdentityEnabled()

        findPreference<ListPreference>("app_icon_alias")?.apply {
            value = ShizukuSettings.getAppIconAlias()
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue is String) {
                    ShizukuSettings.setAppIconAlias(newValue)
                    af.shizuku.manager.utils.AppAliasManager
                        .applyAlias(requireContext(), newValue)
                }
                true
            }
        }

        findPreference<EditTextPreference>("custom_app_name")?.apply {
            // Don't seed `text`: EditTextPreference.setText() persists, which would save this
            // flavor's own name as a "custom" one and peer sync would copy it to the other flavor.
            // Show the effective name in the summary and the dialog instead.
            summaryProvider =
                Preference.SummaryProvider<EditTextPreference> {
                    ShizukuSettings.getCustomAppName(requireContext())
                }
            setOnBindEditTextListener { editText ->
                editText.setText(ShizukuSettings.getCustomAppName(requireContext()))
                editText.setSelection(editText.text.length)
            }
            setOnPreferenceChangeListener { pref, newValue ->
                if (newValue is String) {
                    // Entering the default name (or nothing) clears the custom name.
                    val name = newValue.trim()
                    ShizukuSettings.setCustomAppName(if (name == getString(R.string.app_name)) "" else name)
                    // Re-setting the provider is the public way to refresh a provider summary.
                    pref.summaryProvider = pref.summaryProvider
                }
                // Already persisted above; returning true would write the raw value again.
                false
            }
        }

        findPreference<Preference>("custom_icon_studio")?.setOnPreferenceClickListener {
            try {
                CustomIconStudioBottomSheet
                    .newInstance(null)
                    .show(childFragmentManager, "custom_icon_studio")
            } catch (e: Exception) {
                timber.log.Timber.w(e, "Failed to launch custom icon studio")
            }
            true
        }

        val simulatorPref = findPreference<HomeLayoutSimulatorPreference>("home_layout_simulator")
        simulatorPref?.setFragment(this)

        val switchKeys = listOf("show_activity_log_home", "show_status_card_outline", "show_home_cards_outline")

        for (prefKey in switchKeys) {
            findPreference<TwoStatePreference>(prefKey)?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue is Boolean) {
                    if (prefKey == "show_status_card_outline") {
                        statusOutlineStylePreference?.let { setChildAvailable(it, newValue) }
                    }
                    preferenceManager.sharedPreferences
                        ?.edit()
                        ?.putBoolean(prefKey, newValue)
                        ?.apply()
                    simulatorPref?.let {
                        it.isVisible = false
                        it.isVisible = true
                    }
                }
                true
            }
        }

        findPreference<TwoStatePreference>(KEY_COMPANION_MODE)?.apply {
            isChecked = ShizukuSettings.isCompanionModeEnabled()
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue is Boolean) ShizukuSettings.setCompanionModeEnabled(newValue)
                true
            }
        }

        // 3. Display settings (edge-to-edge, blur)
        edgeToEdgePreference = requireNotNull(findPreference(KEY_EDGE_TO_EDGE))
        edgeToEdgePreference.isChecked = ShizukuSettings.isEdgeToEdgeEnabled()
        // Android 15+ (API 35) enforces E2E for targetSdk-35 apps — the toggle has no effect
        // there, so hide it to avoid confusing users (#483).
        setChildAvailable(edgeToEdgePreference, Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM)
        edgeToEdgePreference.setOnPreferenceChangeListener { _, _ ->
            applyTheme(requiresRecreate = false)
            true
        }

        blurUiPreference = requireNotNull(findPreference(KEY_BLUR_UI))
        blurUiPreference.isChecked = ShizukuSettings.isBlurUiEnabled()
        setChildAvailable(blurUiPreference, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        blurUiPreference.setOnPreferenceChangeListener { _, _ ->
            // window.setBackgroundBlurRadius is set in onCreate; a recreate is needed for it to take
            // effect. The AppBar translucency also needs onPostCreate to re-run (#449).
            applyTheme(requiresRecreate = true)
            true
        }

        oneUiThemePreference = requireNotNull(findPreference(KEY_ONEUI_THEME))
        oneUiThemePreference.isChecked = ShizukuSettings.isOneUiThemeEnabled()
        setChildAvailable(oneUiThemePreference, EnvironmentUtils.isSamsung())
        oneUiThemePreference.setOnPreferenceChangeListener { _, _ ->
            applyTheme(requiresRecreate = false)
            true
        }

        findPreference<TwoStatePreference>(KEY_ROUNDED_EDGES)?.apply {
            isChecked = ShizukuSettings.isRoundedEdgesEnabled()
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue is Boolean) {
                    ShizukuSettings.setRoundedEdgesEnabled(newValue)
                    applyTheme(requiresRecreate = false)
                }
                true
            }
        }

        findPreference<TwoStatePreference>(KEY_ONE_HANDED_MODE)?.setOnPreferenceChangeListener { _, _ ->
            applyTheme(requiresRecreate = false)
            true
        }

        // 4. Language & System
        languagePreference = requireNotNull(findPreference(KEY_LANGUAGE))
        translationPreference = requireNotNull(findPreference(KEY_TRANSLATION))

        languagePreference.setOnPreferenceChangeListener { _, newValue ->
            if (newValue is String) {
                val locale: Locale =
                    if ("SYSTEM" == newValue) {
                        LocaleDelegate.systemLocale
                    } else {
                        Locale.forLanguageTag(newValue)
                    }
                // Immediate fallback in case setApplicationLocales() below doesn't trigger a
                // recreate for some reason; AppActivity.attachBaseContext() re-syncs this from
                // AppCompatDelegate on every activity creation regardless (#429), so this is
                // belt-and-suspenders, not the actual mechanism.
                LocaleDelegate.defaultLocale = locale

                // #429: setApplicationLocales() triggers its own recreate on API <33 (and a
                // config-change/recreate on 33+), so we must NOT also call
                // applyTheme(requiresRecreate = true) here — that would double-recreate the
                // activity or risk a recreate loop.
                val requested =
                    if ("SYSTEM" == newValue) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(newValue)
                    }
                if (AppCompatDelegate.getApplicationLocales() != requested) {
                    AppCompatDelegate.setApplicationLocales(requested)
                } else {
                    // No-op locale change (e.g. re-selecting current language) —
                    // still apply any pending theme-only changes normally.
                    applyTheme(requiresRecreate = false)
                }
            }
            true
        }

        setupLocalePreference(languagePreference)

        translationPreference.apply {
            setCustomSummary(context.getString(R.string.settings_translation_summary, context.getString(R.string.app_name)))
            widgetLayoutResource = R.layout.preference_widget_open_in_new
            setOnPreferenceClickListener {
                CustomTabsHelper.launchUrlOrCopy(context, context.getString(R.string.translation_url))
                true
            }
        }
    }

    /** Intensity needs animations on and isn't used while following the system speed. */
    private fun syncIntensityAvailability() {
        val animationsOn = expressiveAnimationsPreference.isChecked
        val followSystem = findPreference<TwoStatePreference>("follow_system_animations")
        setChildAvailable(animationIntensityPreference, animationsOn && followSystem?.isChecked != true)
        followSystem?.let { setChildAvailable(it, animationsOn) }
    }

    private fun currentDarkStyle(): String =
        when {
            ShizukuSettings.isAmoledPlusEnabled() -> DARK_STYLE_AMOLED
            ThemeHelper.isBlackNightTheme(requireContext()) -> DARK_STYLE_BLACK
            else -> DARK_STYLE_STANDARD
        }

    private fun syncDependentVisibility() {
        val nightOn = ShizukuSettings.getNightMode() != AppCompatDelegate.MODE_NIGHT_NO
        setChildAvailable(darkStylePreference, nightOn)
    }

    /**
     * Toggle a child's availability through its collapsible category when possible, so the
     * category's expand/collapse toggle doesn't override the condition. Falls back to a plain
     * visibility change if the child isn't inside a [CollapsiblePreferenceCategory].
     */
    private fun setChildAvailable(
        pref: Preference,
        available: Boolean,
    ) {
        val key = pref.key
        val category = pref.parent as? CollapsiblePreferenceCategory
        if (key != null && category != null) {
            category.setChildAvailable(key, available)
        } else {
            pref.isVisible = available
        }
    }

    private fun setupLocalePreference(languagePreference: ListPreference) {
        val localeTags = ShizukuLocales.LOCALES
        val displayLocaleTags = ShizukuLocales.DISPLAY_LOCALES

        languagePreference.entryValues = localeTags

        val currentLocaleTag = languagePreference.value
        val currentLocaleIndex = localeTags.indexOf(currentLocaleTag)
        val currentLocale = ShizukuSettings.getLocale()
        val localizedLocales = mutableListOf<CharSequence>()

        for ((index, displayLocale) in displayLocaleTags.withIndex()) {
            if (index == 0) {
                localizedLocales.add(getString(R.string.follow_system))
                continue
            }

            val locale = Locale.forLanguageTag(displayLocale.toString())
            val rawLocaleName =
                if (!TextUtils.isEmpty(locale.script)) {
                    locale.getDisplayScript(locale)
                } else {
                    locale.getDisplayName(locale)
                }
            val localeName =
                if (displayLocale == "az" || displayLocale == "az-AZ") {
                    "Azərbaycan dili"
                } else {
                    rawLocaleName.replaceFirstChar { if (it.isLowerCase()) it.uppercase(locale) else it.toString() }
                }

            val rawLocalizedLocaleName =
                if (!TextUtils.isEmpty(locale.script)) {
                    locale.getDisplayScript(currentLocale)
                } else {
                    locale.getDisplayName(currentLocale)
                }
            val localizedLocaleName = rawLocalizedLocaleName.replaceFirstChar { if (it.isLowerCase()) it.uppercase(currentLocale) else it.toString() }

            localizedLocales.add(
                if (index != currentLocaleIndex) {
                    "$localeName<br><small>$localizedLocaleName</small>".toHtml()
                } else {
                    localizedLocaleName
                },
            )
        }

        languagePreference.entries = localizedLocales.toTypedArray()

        languagePreference.setCustomSummary(
            when {
                TextUtils.isEmpty(currentLocaleTag) || "SYSTEM" == currentLocaleTag -> {
                    getString(R.string.follow_system)
                }
                currentLocaleIndex != -1 -> {
                    val localizedLocale = localizedLocales[currentLocaleIndex]
                    val newLineIndex = localizedLocale.indexOf('\n')
                    if (newLineIndex == -1) {
                        localizedLocale.toString()
                    } else {
                        localizedLocale.subSequence(0, newLineIndex).toString()
                    }
                }
                else -> {
                    ""
                }
            },
        )
    }

    private fun getCustomAccentSummary(value: String? = null): String {
        val currentValue = value ?: ShizukuSettings.getPreferences().getString("custom_accent", "DEFAULT")
        return when (currentValue) {
            "VIOLET" -> getString(R.string.settings_accent_violet_applied)
            "GREEN" -> getString(R.string.settings_accent_green_applied)
            "CRIMSON" -> getString(R.string.settings_accent_crimson_applied)
            "OCEAN" -> getString(R.string.settings_accent_ocean_applied)
            "AMBER" -> getString(R.string.settings_accent_amber_applied)
            "ROSE" -> getString(R.string.settings_accent_rose_applied)
            "SLATE" -> getString(R.string.settings_accent_slate_applied)
            else -> getString(R.string.settings_accent_default_applied)
        }
    }
}

private const val DARK_STYLE_STANDARD = "standard"
private const val DARK_STYLE_BLACK = "black"
private const val DARK_STYLE_AMOLED = "amoled"
