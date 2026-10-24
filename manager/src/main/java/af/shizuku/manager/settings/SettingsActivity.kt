package af.shizuku.manager.settings

import af.shizuku.core.ui.AppActivity
import af.shizuku.manager.R
import af.shizuku.manager.settings.compose.SettingsScreen
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.*
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

@OptIn(ExperimentalMaterial3Api::class)
class SettingsActivity :
    AppActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private var currentTitle by mutableStateOf("")
    private var searchResults by mutableStateOf<List<SettingsSearchEngine.SettingItem>>(emptyList())
    var themeVersion by mutableStateOf(0)

    private var preferenceScrollState: TopAppBarState? = null
    private var _isScrollIdle by mutableStateOf(true)

    fun getExpandedHeaderHeightPx(): Int {
        val isOneUi =
            af.shizuku.manager.ShizukuSettings
                .isOneUiThemeEnabled()
        val isOneHanded =
            af.shizuku.manager.ShizukuSettings
                .isOneHandedModeEnabled()
        val isExpanded =
            isOneUi ||
                isOneHanded ||
                af.shizuku.manager.ShizukuSettings
                    .isExpandedHeadersEnabled()
        val dp = if (isExpanded) expandedHeaderHeightDp(resources.configuration.screenHeightDp) else 64f
        return (dp * resources.displayMetrics.density).toInt()
    }

    fun onPreferenceListScrolled(
        dy: Int,
        canScrollUp: Boolean = true,
        scrollOffset: Int = 0,
    ) {
        val state = preferenceScrollState ?: return
        val limit = state.heightOffsetLimit
        when {
            dy > 0 -> state.heightOffset = (state.heightOffset - dy).coerceAtLeast(limit)
            // Scrolling up only reveals the header as the list reaches the top: it follows the
            // list's own offset, so it is fully expanded exactly when the list is back at the top
            // (the list's top padding is the expanded header height). Only adding the last dy
            // here left a collapsed header over an empty gap.
            dy < 0 && !canScrollUp -> state.heightOffset = 0f
            dy < 0 ->
                state.heightOffset =
                    maxOf(state.heightOffset, (-scrollOffset).toFloat().coerceIn(limit, 0f))
        }
        state.contentOffset -= dy
        _isScrollIdle = false
    }

    fun onPreferenceListScrollIdle() {
        _isScrollIdle = true
    }

    fun onThemeChanged() {
        themeVersion++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AppActivity.onCreate() already calls enableEdgeToEdge() (which sets
        // setDecorFitsSystemWindows=false) based on the user's setting and Android version.
        // Repeating it unconditionally here was inconsistent with HomeActivity and caused
        // mismatched window state during the Explode transition, crashing on Android 16 (#483).

        SettingsSearchEngine.init(this)

        currentTitle = getString(R.string.settings_title)

        setContent {
            val tv = themeVersion
            af.shizuku.core.ui.compose.AppTheme(
                isExpressiveMotion =
                    af.shizuku.manager.ShizukuSettings
                        .isExpressiveAnimationsEnabled(),
                isBlackNightTheme =
                    af.shizuku.manager.app.ThemeHelper
                        .isBlackNightTheme(this),
                isAmoledPlus =
                    af.shizuku.manager.ShizukuSettings
                        .isAmoledPlusEnabled(),
                isOneUi =
                    af.shizuku.manager.ShizukuSettings
                        .isOneUiThemeEnabled(),
                isBoldTypography =
                    af.shizuku.manager.ShizukuSettings
                        .isBoldTypographyEnabled(),
                isHighContrast =
                    af.shizuku.manager.ShizukuSettings
                        .isHighContrastEnabled(),
                isRoundedEdges =
                    af.shizuku.manager.ShizukuSettings
                        .isRoundedEdgesEnabled(),
                themeVersion = tv,
            ) {
                val customName =
                    af.shizuku.manager.ShizukuSettings
                        .getCustomAppName(this)
                val displayTitle =
                    if (currentTitle == getString(R.string.settings_title) && customName != getString(R.string.app_name)) {
                        "$customName Settings"
                    } else {
                        currentTitle
                    }
                SettingsScreen(
                    title = displayTitle,
                    showNavigateUp =
                        !(
                            af.shizuku.manager.widget.FloatingNavBar
                                .isTopLevel(this@SettingsActivity) &&
                                currentTitle == getString(R.string.settings_title)
                        ),
                    onNavigateUp = {
                        if (!onSupportNavigateUp()) {
                            finish()
                        }
                    },
                    onNavigateToSetting = { item -> navigateToSetting(item) },
                    searchResults = searchResults,
                    onSearchQueryChanged = { query ->
                        if (query.isBlank()) {
                            searchResults = emptyList()
                        } else {
                            searchResults = SettingsSearchEngine.search(this, query)
                        }
                    },
                    onContainerCreated = {
                        // FM restores the fragment into its internal state during super.onCreate(),
                        // before the Compose AndroidView container exists. findFragmentById returns
                        // non-null in that case, but the fragment's view was never created (the
                        // container wasn't in the hierarchy when FM tried to attach it). Checking
                        // view == null catches this: the fragment exists in FM's state but has no
                        // live view, so we replace it to force a fresh attach.
                        val existing = supportFragmentManager.findFragmentById(R.id.fragment_container)
                        if (existing == null || existing.view == null) {
                            supportFragmentManager
                                .beginTransaction()
                                .replace(R.id.fragment_container, SettingsFragment())
                                .commitNow()
                        }
                    },
                    isScrollIdle = _isScrollIdle,
                    onScrollStateCreated = { preferenceScrollState = it },
                )
            }
        }
    }

    private fun navigateToSetting(item: SettingsSearchEngine.SettingItem) {
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, item.fragmentClass)
        fragment.arguments =
            Bundle().apply {
                putString("highlight_key", item.key)
            }

        applySharedAxis(fragment)
        supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        // Title is updated by the fragment's onResume → updateTitle(); no need to set it here.
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        val fragmentName = pref.fragment ?: return false
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, fragmentName)
        fragment.arguments = pref.extras

        applySharedAxis(fragment)
        supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()

        currentTitle = pref.title?.toString() ?: currentTitle
        return true
    }

    /**
     * Experimental "Material screen transitions": settings pages slide on the shared X axis
     * (forward in, back out) instead of cutting.
     */
    private fun applySharedAxis(next: androidx.fragment.app.Fragment) {
        if (!af.shizuku.manager.ShizukuSettings
                .isExperimentalScreenTransitionsEnabled()
        ) {
            return
        }
        val axis = com.google.android.material.transition.MaterialSharedAxis.X
        next.enterTransition =
            com.google.android.material.transition
                .MaterialSharedAxis(axis, true)
        next.returnTransition =
            com.google.android.material.transition
                .MaterialSharedAxis(axis, false)
        supportFragmentManager.findFragmentById(R.id.fragment_container)?.apply {
            exitTransition =
                com.google.android.material.transition
                    .MaterialSharedAxis(axis, true)
            reenterTransition =
                com.google.android.material.transition
                    .MaterialSharedAxis(axis, false)
        }
    }

    fun updateTitle(title: String) {
        currentTitle = title
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        return super.onSupportNavigateUp()
    }
}

/**
 * Expanded settings header height, shared by the Compose header and the preference list's top
 * padding so the two can never drift apart. One UI / one-handed keep a tall reach-friendly header;
 * plain "expanded headers" use the M3 large top app bar range instead of a third of the screen.
 */
internal fun expandedHeaderHeightDp(screenHeightDp: Int): Float {
    val reachMode =
        af.shizuku.manager.ShizukuSettings
            .isOneUiThemeEnabled() ||
            af.shizuku.manager.ShizukuSettings
                .isOneHandedModeEnabled()
    return if (reachMode) {
        (screenHeightDp * 0.30f).coerceIn(200f, 290f)
    } else {
        (screenHeightDp * 0.22f).coerceIn(152f, 200f)
    }
}
