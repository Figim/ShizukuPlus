package af.shizuku.manager.settings.compose

import af.shizuku.manager.R
import af.shizuku.manager.settings.SettingsSearchEngine
import android.content.res.ColorStateList
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    title: String,
    onNavigateUp: () -> Unit,
    onNavigateToSetting: (SettingsSearchEngine.SettingItem) -> Unit,
    searchResults: List<SettingsSearchEngine.SettingItem>,
    onSearchQueryChanged: (String) -> Unit,
    onContainerCreated: () -> Unit,
    isScrollIdle: Boolean = true,
    onScrollStateCreated: (TopAppBarState) -> Unit = {},
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun exitSearch() {
        isSearchActive = false
        searchQuery = ""
        onSearchQueryChanged("")
        keyboardController?.hide()
    }

    BackHandler(enabled = isSearchActive) { exitSearch() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    val isOneUi =
        af.shizuku.manager.ShizukuSettings
            .isOneUiThemeEnabled()
    val isOneHanded =
        af.shizuku.manager.ShizukuSettings
            .isOneHandedModeEnabled()
    val isExpandedHeaders =
        isOneUi ||
            isOneHanded ||
            af.shizuku.manager.ShizukuSettings
                .isExpandedHeadersEnabled()
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val isBlackTheme =
        isDarkTheme &&
            af.shizuku.manager.app.ThemeHelper
                .isBlackNightTheme(context)
    val isOled =
        (
            isBlackTheme ||
                af.shizuku.manager.ShizukuSettings
                    .isAmoledPlusEnabled()
        ) &&
            (isDarkTheme || ((context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES))
    val pageBackground =
        if (isOled) {
            Color.Black
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    val barScrolledColor =
        if (isOled) {
            Color.Black
        } else if (af.shizuku.manager.ShizukuSettings
                .isBlurUiEnabled()
        ) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val statusBarPadding = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val expandedHeight =
        if (isExpandedHeaders) {
            (screenHeightDp * 0.38f).dp.coerceIn(240.dp, 340.dp)
        } else {
            64.dp
        }
    val collapsedHeight = 64.dp

    val density = androidx.compose.ui.platform.LocalDensity.current
    val expandedHeightPx = with(density) { (expandedHeight + statusBarPadding).toPx() }
    val collapsedHeightPx = with(density) { (collapsedHeight + statusBarPadding).toPx() }
    val heightOffsetLimit = -(expandedHeightPx - collapsedHeightPx)

    val topAppBarState = rememberTopAppBarState()
    SideEffect {
        if (topAppBarState.heightOffsetLimit != heightOffsetLimit) {
            topAppBarState.heightOffsetLimit = heightOffsetLimit
            topAppBarState.heightOffset = topAppBarState.heightOffset
        }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    LaunchedEffect(Unit) { onScrollStateCreated(scrollBehavior.state) }

    LaunchedEffect(isScrollIdle) {
        if (isScrollIdle) {
            val state = scrollBehavior.state
            val fraction = state.collapsedFraction
            if (fraction > 0.001f && fraction < 0.999f) {
                val target = if (fraction >= 0.5f) state.heightOffsetLimit else 0f
                Animatable(state.heightOffset).animateTo(
                    target,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                ) { state.heightOffset = value }
            }
        }
    }

    val fraction = if (isExpandedHeaders) scrollBehavior.state.collapsedFraction else 1f
    val curvedFraction = FastOutSlowInEasing.transform(fraction)

    Scaffold(
        containerColor = pageBackground,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            if (isSearchActive) {
                // Search mode: back button + inline text field + optional clear button
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { q ->
                                searchQuery = q
                                onSearchQueryChanged(q)
                            },
                            placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                            singleLine = true,
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { exitSearch() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_back_24),
                                contentDescription = stringResource(R.string.cd_navigate_back),
                            )
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                onSearchQueryChanged("")
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close_24),
                                    contentDescription = stringResource(R.string.cd_settings_search_clear),
                                )
                            }
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = pageBackground,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            } else {
                if (isExpandedHeaders) {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    (expandedHeight + statusBarPadding + with(density) { scrollBehavior.state.heightOffset.toDp() })
                                        .coerceAtLeast(collapsedHeight + statusBarPadding),
                                ),
                        color =
                            run {
                                val barAlpha = ((fraction - 0.65f) / 0.35f).coerceIn(0f, 1f)
                                if (af.shizuku.manager.ShizukuSettings
                                        .isBlurUiEnabled()
                                ) {
                                    barScrolledColor.copy(alpha = 0.85f * barAlpha)
                                } else {
                                    barScrolledColor.copy(alpha = barAlpha)
                                }
                            },
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(top = statusBarPadding),
                        ) {
                            // Navigation and Action buttons in the pinned 64dp collapsed zone
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(collapsedHeight)
                                        .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                IconButton(onClick = { onNavigateUp() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_back_24),
                                        contentDescription = stringResource(R.string.cd_navigate_back),
                                    )
                                }
                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_search_24),
                                        contentDescription = stringResource(R.string.cd_settings_search),
                                    )
                                }
                            }

                            // Title and One UI subtitle
                            val titleStartPadding = lerp(start = 24.dp, stop = 56.dp, fraction = curvedFraction)
                            val titleFontSize = lerp(start = if (isOneUi) 36.sp else 30.sp, stop = 20.sp, fraction = curvedFraction)
                            val titleFontWeight = if (curvedFraction > 0.65f) FontWeight.Bold else FontWeight.ExtraBold
                            val titleLetterSpacing = lerp((-0.5).sp, (-0.2).sp, curvedFraction)

                            val horizontalBias = -1f // One UI aligns titles to the start
                            val verticalBias =
                                androidx.compose.ui.util
                                    .lerp(0.65f, -0.7f, curvedFraction)

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(
                                            start = titleStartPadding,
                                            end = 56.dp,
                                            top = lerp(start = 32.dp, stop = 0.dp, fraction = curvedFraction),
                                            bottom = lerp(start = 16.dp, stop = 0.dp, fraction = curvedFraction),
                                        ),
                                contentAlignment = BiasAlignment(horizontalBias, verticalBias),
                            ) {
                                Column {
                                    Text(
                                        text = title,
                                        style =
                                            MaterialTheme.typography.headlineLarge.copy(
                                                fontWeight = titleFontWeight,
                                                fontSize = titleFontSize,
                                                letterSpacing = titleLetterSpacing,
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isOneUi) {
                                        val subtitleAlpha = (1f - curvedFraction * 3f).coerceIn(0f, 1f)
                                        if (subtitleAlpha > 0f) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text =
                                                    if (title == stringResource(R.string.settings_title)) {
                                                        stringResource(R.string.settings_oneui_theme_summary).take(38) + "…"
                                                    } else {
                                                        stringResource(R.string.settings_category_appearance)
                                                    },
                                                style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.Normal,
                                                    ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.graphicsLayer { alpha = subtitleAlpha },
                                            )
                                        }
                                    }
                                }
                            }

                            // Hairline divider when collapsed
                            if (curvedFraction > 0.5f) {
                                val dividerAlpha = ((curvedFraction - 0.5f) / 0.5f).coerceIn(0f, 1f)
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .height(0.8.dp)
                                            .graphicsLayer { alpha = dividerAlpha }
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                                )
                            }
                        }
                    }
                } else {
                    Box {
                        TopAppBar(
                            title = {
                                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            navigationIcon = {
                                IconButton(onClick = { onNavigateUp() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_back_24),
                                        contentDescription = stringResource(R.string.cd_navigate_back),
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_search_24),
                                        contentDescription = stringResource(R.string.cd_settings_search),
                                    )
                                }
                            },
                            colors =
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                    scrolledContainerColor = barScrolledColor,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        )
                        // Hairline divider always visible (bar is always in collapsed appearance)
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(0.8.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        )
                    } // end Box
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Fragment container — always full screen with stable bounds
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        id = R.id.fragment_container
                        clipToPadding = false
                        post { onContainerCreated() }
                    }
                },
                update = { view ->
                    view.visibility = if (isSearchActive) android.view.View.GONE else android.view.View.VISIBLE
                },
                modifier = Modifier.fillMaxSize(),
            )

            // One-handed reach handle indicator when expanded
            if (isOneHanded && fraction < 0.25f) {
                val handleAlpha = (1f - (fraction * 4f)).coerceIn(0f, 1f)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = statusBarPadding + collapsedHeight + 8.dp)
                            .graphicsLayer { alpha = handleAlpha },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    shape =
                                        androidx.compose.foundation.shape
                                            .RoundedCornerShape(2.dp),
                                ),
                    )
                }
            }

            // Inline search results — appears below the search TopAppBar
            val searchFadeMs =
                af.shizuku.manager.ShizukuSettings
                    .scaledAnimationDuration(200L)
                    .toInt()
            AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn(tween(searchFadeMs)),
                exit = fadeOut(tween(searchFadeMs)),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding().coerceAtLeast(0.dp)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(pageBackground),
                ) {
                    if (searchQuery.isBlank()) {
                        // Empty query: just show the background so the fragment is covered
                    } else if (searchResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_search_empty_state),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(searchResults) { item ->
                                SearchResultItem(
                                    item = item,
                                    isBlackTheme = isBlackTheme,
                                    onClick = {
                                        exitSearch()
                                        onNavigateToSetting(item)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultItem(
    item: SettingsSearchEngine.SettingItem,
    isBlackTheme: Boolean = false,
    onClick: () -> Unit,
) {
    // Use Material3's clickable Card overload rather than Modifier.clickable: the latter reads
    // LocalIndication, and on Android 16 / Compose Foundation 1.7+ that threw
    // "clickable only supports IndicationNodeFactory instances provided to LocalIndication"
    // when a legacy Indication was in scope, crashing settings search (#309 / SHIZUKUPLUS-72).
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isBlackTheme) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.iconResId != 0) {
                SafePreferenceIcon(
                    iconResId = item.iconResId,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.category ?: "Settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                if (!item.summary.isNullOrEmpty()) {
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Safely renders an arbitrary Android drawable resource ID (vector, bitmap, XML layer-list,
 * adaptive icon) inside Compose without triggering IllegalArgumentException from painterResource.
 */
@Composable
fun SafePreferenceIcon(
    iconResId: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (iconResId == 0) return
    val tintArgb = tint.toArgb()

    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { imageView ->
            try {
                imageView.setImageResource(iconResId)
                imageView.imageTintList = ColorStateList.valueOf(tintArgb)
            } catch (e: Exception) {
                Timber.w(e, "Failed to load preference icon resource: %d", iconResId)
                imageView.setImageDrawable(null)
            }
        },
        modifier = modifier,
    )
}
