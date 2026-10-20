package af.shizuku.manager.home.compose

import af.shizuku.manager.R
import af.shizuku.manager.ShizukuSettings
import af.shizuku.manager.database.ActivityEventType
import af.shizuku.manager.database.ActivityLogManager
import af.shizuku.manager.database.ActivityLogRecord
import af.shizuku.manager.utils.AppIconCache
import af.shizuku.manager.utils.HapticUtils
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

private fun queryLiveServerPatchVersion(): Int {
    return try {
        val binder = Shizuku.getBinder() ?: return -1
        val data = android.os.Parcel.obtain()
        val reply = android.os.Parcel.obtain()
        try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            if (binder.transact(10004, data, reply, 0)) {
                reply.readException()
                reply.readInt()
            } else {
                -1
            }
        } finally {
            reply.recycle()
            data.recycle()
        }
    } catch (_: Throwable) {
        -1
    }
}

@Composable
fun ServerMetricsScreen() {
    val context = LocalContext.current
    var isServerRunning by remember { mutableStateOf<Boolean?>(null) }
    var isStockServer by remember { mutableStateOf(false) }
    var uptimeText by remember { mutableStateOf("00:00:00") }
    var clientCountText by remember { mutableStateOf(context.getString(R.string.server_clients_connected, 0)) }
    var memoryProgress by remember { mutableFloatStateOf(0f) }
    var memoryDetails by remember { mutableStateOf("0 MB / 0 MB") }
    var serverPatchVersion by remember { mutableIntStateOf(-1) }
    var serverUid by remember { mutableIntStateOf(-1) }
    var seContext by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
                isServerRunning = alive

                if (alive) {
                    serverUid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
                    val cachedPatch = runCatching { Shizuku.getServerPatchVersion() }.getOrDefault(-1)
                    serverPatchVersion = if (cachedPatch >= 0) cachedPatch else queryLiveServerPatchVersion()
                    seContext = runCatching { Shizuku.getSELinuxContext() }.getOrNull()

                    val binder = Shizuku.getBinder()
                    if (binder != null) {
                        val shizukuService =
                            moe.shizuku.server.IShizukuService.Stub
                                .asInterface(binder)

                        var ai: af.shizuku.server.IAICorePlus? = runCatching { shizukuService.aiCorePlus }.getOrNull()
                        if (ai == null && (serverPatchVersion > 0 || Shizuku.isCustomApiEnabled())) {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    shizukuService.updatePlusFeatureEnabled("ai_core_plus", true)
                                }
                                ShizukuSettings.syncAllPlusFeaturesToServer()
                            }
                            ai = runCatching { shizukuService.aiCorePlus }.getOrNull()
                        }

                        val stats = runCatching { ai?.serverStats }.getOrNull()
                        if (stats != null) {
                            isStockServer = false
                            val uptimeMs = stats.getLong("uptime_ms")
                            val seconds = (uptimeMs / 1000) % 60
                            val minutes = (uptimeMs / (1000 * 60)) % 60
                            val hours = (uptimeMs / (1000 * 60 * 60)) % 24
                            val days = (uptimeMs / (1000 * 60 * 60 * 24))
                            uptimeText =
                                if (days > 0) {
                                    String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds)
                                } else {
                                    String.format("%02d:%02d:%02d", hours, minutes, seconds)
                                }

                            clientCountText =
                                context.getString(R.string.server_clients_connected, stats.getInt("client_count"))

                            val maxRaw = stats.getLong("mem_max")
                            val total = stats.getLong("mem_total")
                            val free = stats.getLong("mem_free")
                            val used = (total - free).coerceAtLeast(0L)
                            val max = if (maxRaw == Long.MAX_VALUE || maxRaw <= 0) total else maxRaw

                            memoryProgress = if (max > 0) (used.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f

                            val formatSize = { bytes: Long ->
                                val kb = bytes / 1024
                                val mb = kb / 1024
                                val gb = mb / 1024
                                if (gb > 0) {
                                    "$gb GB"
                                } else if (mb > 0) {
                                    "$mb MB"
                                } else {
                                    "$kb KB"
                                }
                            }
                            val maxStr =
                                if (maxRaw == Long.MAX_VALUE) {
                                    context.getString(R.string.server_memory_uncapped)
                                } else {
                                    formatSize(max)
                                }
                            memoryDetails = "${formatSize(used)} / $maxStr (Max Allowed)"
                        } else {
                            isStockServer = (serverPatchVersion <= 0)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
            }
            delay(1000)
        }
    }

    val isOneHanded = ShizukuSettings.isOneHandedModeEnabled()
    val isOneUi = ShizukuSettings.isOneUiThemeEnabled()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val oneHandedTop = if (isOneHanded || isOneUi) (screenHeightDp * 0.22f).dp else 0.dp

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (oneHandedTop > 0.dp) {
            Spacer(modifier = Modifier.height(oneHandedTop))
        }
        // Server State Banner
        when {
            isServerRunning == false -> {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_server_error_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.server_metrics_server_not_running),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.server_metrics_server_not_running_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
            isServerRunning == true -> {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_server_ok_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.server_metrics_server_running),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            val modeLabel = if (serverUid == 0) "Root" else "ADB"
                            StatusBadge(modeLabel, MaterialTheme.colorScheme.primary)

                            if (serverPatchVersion >= 0) {
                                StatusBadge(stringResource(R.string.server_metrics_patch_label, serverPatchVersion), MaterialTheme.colorScheme.secondary)
                            }
                            if (serverUid >= 0) {
                                StatusBadge("uid=$serverUid", MaterialTheme.colorScheme.tertiary)
                            }
                            if (!seContext.isNullOrEmpty()) {
                                StatusBadge(seContext!!, MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }

        if (isStockServer) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_outline_info_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.server_metrics_stock_server),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.server_metrics_stock_server_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        } else if (isServerRunning == true) {
            // Metrics Diagnostic Cards
            MetricCard(
                title = stringResource(R.string.server_uptime),
                value = uptimeText,
                iconRes = R.drawable.ic_history_24,
            )

            MetricCard(
                title = stringResource(R.string.active_connections),
                value = clientCountText,
                iconRes = R.drawable.ic_server_24,
            )

            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.server_memory_usage),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "${(memoryProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { memoryProgress },
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(memoryDetails, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    color: Color,
) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = color.copy(alpha = 0.18f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    iconRes: Int? = null,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                )
            }
        }
    }
}

private enum class ActivityLogSort(
    val labelRes: Int,
) {
    NEWEST(R.string.changelog_sort_newest),
    OLDEST(R.string.changelog_sort_oldest),
    BY_APP(R.string.activity_log_sort_by_app),
    BY_TYPE(R.string.activity_log_sort_by_type),
    MOST_ACTIVE(R.string.activity_log_sort_most_active),
}

private enum class ActivityLogGrouping(
    val labelRes: Int,
) {
    DATE(R.string.activity_log_group_by_date),
    APP(R.string.activity_log_group_by_app),
    TYPE(R.string.activity_log_group_by_type),
    NONE(R.string.activity_log_group_none),
}

private data class LogGroup(
    val key: String,
    val primaryRecord: ActivityLogRecord,
    val occurrences: List<ActivityLogRecord>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    showTopBar: Boolean = false,
    onBackClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val rawLogs by ActivityLogManager.logs.collectAsState(initial = emptyList())

    var selectedTypes by remember { mutableStateOf<Set<ActivityEventType>>(emptySet()) }
    var currentSort by remember { mutableStateOf(ActivityLogSort.NEWEST) }
    var currentGrouping by remember { mutableStateOf(ActivityLogGrouping.DATE) }
    var stackRepetitive by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupingMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val expandedKeys = remember { mutableStateListOf<String>() }

    // Pre-resolve app labels for filtering / sorting
    val appLabels = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(rawLogs) {
        val pm = context.packageManager
        withContext(Dispatchers.IO) {
            val packages = rawLogs.map { it.packageName }.distinct()
            for (pkg in packages) {
                if (!appLabels.containsKey(pkg)) {
                    val label =
                        try {
                            val ai = pm.getApplicationInfo(pkg, 0)
                            AppIconCache.getLabel(context, ai)
                        } catch (_: Exception) {
                            pkg
                        }
                    appLabels[pkg] = label
                }
            }
        }
    }

    // Process logs: Filter -> Group/Stack -> Sort
    val processedGroups =
        remember(rawLogs, selectedTypes, currentSort, stackRepetitive, searchQuery, appLabels) {
            // 1. Filter by event type
            var filtered =
                if (selectedTypes.isEmpty()) {
                    rawLogs
                } else {
                    rawLogs.filter { it.eventType in selectedTypes }
                }

            // 2. Filter by search query
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                filtered =
                    filtered.filter { record ->
                        record.packageName.lowercase().contains(q) ||
                            record.action.lowercase().contains(q) ||
                            record.appName.lowercase().contains(q) ||
                            (appLabels[record.packageName]?.lowercase()?.contains(q) == true)
                    }
            }

            // 3. Stacking
            val groups: List<LogGroup> =
                if (stackRepetitive) {
                    val map = linkedMapOf<String, MutableList<ActivityLogRecord>>()
                    for (record in filtered) {
                        val key = "${record.packageName}|${record.eventType}|${record.action}"
                        map.getOrPut(key) { mutableListOf() }.add(record)
                    }
                    map.map { (key, records) ->
                        val primary = records.maxByOrNull { it.timestamp } ?: records.first()
                        val others = records.filter { it != primary }.sortedByDescending { it.timestamp }
                        LogGroup(
                            key = key,
                            primaryRecord = primary,
                            occurrences = others,
                        )
                    }
                } else {
                    filtered.mapIndexed { index, record ->
                        LogGroup(
                            key = "${record.timestamp}_${record.packageName}_$index",
                            primaryRecord = record,
                            occurrences = emptyList(),
                        )
                    }
                }

            // 4. Sort
            when (currentSort) {
                ActivityLogSort.NEWEST -> groups.sortedByDescending { it.primaryRecord.timestamp }
                ActivityLogSort.OLDEST -> groups.sortedBy { it.primaryRecord.timestamp }
                ActivityLogSort.BY_APP ->
                    groups.sortedWith(
                        compareBy(
                            { appLabels[it.primaryRecord.packageName]?.lowercase() ?: it.primaryRecord.appName.lowercase() },
                            { -it.primaryRecord.timestamp },
                        ),
                    )
                ActivityLogSort.BY_TYPE ->
                    groups.sortedWith(
                        compareBy(
                            { it.primaryRecord.eventType.ordinal },
                            { -it.primaryRecord.timestamp },
                        ),
                    )
                ActivityLogSort.MOST_ACTIVE ->
                    groups.sortedWith(
                        compareBy(
                            { -(it.occurrences.size + 1) },
                            { -it.primaryRecord.timestamp },
                        ),
                    )
            }
        }

    val groupedSections: Map<String, List<LogGroup>> =
        remember(processedGroups, currentGrouping, appLabels) {
            when (currentGrouping) {
                ActivityLogGrouping.DATE -> {
                    processedGroups.groupBy { getDateSectionTitle(context, it.primaryRecord.timestamp) }
                }
                ActivityLogGrouping.APP -> {
                    processedGroups.groupBy {
                        appLabels[it.primaryRecord.packageName]
                            ?: it.primaryRecord.appName.ifEmpty { it.primaryRecord.packageName }
                    }
                }
                ActivityLogGrouping.TYPE -> {
                    processedGroups.groupBy {
                        val labelRes =
                            when (it.primaryRecord.eventType) {
                                ActivityEventType.SERVICE_START -> R.string.activity_log_type_start
                                ActivityEventType.SERVICE_STOP -> R.string.activity_log_type_stop
                                ActivityEventType.CONNECTION -> R.string.activity_log_type_connection
                                ActivityEventType.PERMISSION -> R.string.activity_log_type_permission
                                ActivityEventType.APP_MANAGEMENT -> R.string.activity_log_type_app_mgmt
                                ActivityEventType.WATCHDOG -> R.string.activity_log_type_watchdog
                                ActivityEventType.SYSTEM -> R.string.activity_log_type_system
                                ActivityEventType.OTHER -> R.string.activity_log_type_other
                            }
                        context.getString(labelRes)
                    }
                }
                ActivityLogGrouping.NONE -> {
                    mapOf("" to processedGroups)
                }
            }
        }

    val dateFormat =
        remember {
            DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault(),
            )
        }

    val isOneHanded = ShizukuSettings.isOneHandedModeEnabled()
    val isOneUi = ShizukuSettings.isOneUiThemeEnabled()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val oneHandedTop = if (isOneHanded || isOneUi) (screenHeightDp * 0.18f).dp else 0.dp

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.activity_log_clear_confirm_title)) },
            text = { Text(stringResource(R.string.activity_log_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ActivityLogManager.clear()
                        expandedKeys.clear()
                        showClearDialog = false
                    },
                ) {
                    Text(stringResource(R.string.settings_activity_log_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    val content = @Composable {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = if (showTopBar) 0.dp else oneHandedTop),
        ) {
            // Search Bar
            AnimatedVisibility(visible = isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = { Text(stringResource(R.string.activity_log_search_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_24),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close_24),
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(100.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
            }

            // Filter & Control Chips Row (Morphe style)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Search toggle chip
                FilterChip(
                    selected = isSearchActive,
                    onClick = {
                        HapticUtils.segmentTick(view)
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    },
                    label = { Text(stringResource(R.string.cd_settings_search)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_24),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )

                // Grouping Dropdown Chip
                Box {
                    FilterChip(
                        selected = currentGrouping != ActivityLogGrouping.NONE,
                        onClick = {
                            HapticUtils.tap(view)
                            showGroupingMenu = true
                        },
                        label = { Text(stringResource(currentGrouping.labelRes)) },
                        trailingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_outline_expand_more_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    DropdownMenu(
                        expanded = showGroupingMenu,
                        onDismissRequest = { showGroupingMenu = false },
                    ) {
                        ActivityLogGrouping.values().forEach { grouping ->
                            DropdownMenuItem(
                                text = { Text(stringResource(grouping.labelRes)) },
                                onClick = {
                                    HapticUtils.segmentTick(view)
                                    currentGrouping = grouping
                                    showGroupingMenu = false
                                },
                            )
                        }
                    }
                }

                // Sort Dropdown Chip
                Box {
                    FilterChip(
                        selected = false,
                        onClick = {
                            HapticUtils.tap(view)
                            showSortMenu = true
                        },
                        label = { Text(stringResource(currentSort.labelRes)) },
                        trailingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_outline_expand_more_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        ActivityLogSort.values().forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(stringResource(sort.labelRes)) },
                                onClick = {
                                    HapticUtils.segmentTick(view)
                                    currentSort = sort
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }

                // Stacking Chip (Morphe style)
                FilterChip(
                    selected = stackRepetitive,
                    onClick = {
                        HapticUtils.segmentTick(view)
                        stackRepetitive = !stackRepetitive
                    },
                    label = { Text(stringResource(R.string.activity_log_stack_activities)) },
                )

                // Export Logs Chip
                FilterChip(
                    selected = false,
                    onClick = {
                        HapticUtils.tap(view)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text =
                            rawLogs.joinToString("\n") { r ->
                                "[${dateFormat.format(Date(r.timestamp))}] [${r.eventType}] ${r.appName} (${r.packageName}): ${r.action}"
                            }
                        clipboard.setPrimaryClip(ClipData.newPlainText("Activity Log", text))
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.activity_log_export_copied, rawLogs.size),
                                Toast.LENGTH_SHORT,
                            ).show()
                    },
                    label = { Text(stringResource(R.string.activity_log_export)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )

                // Clear Logs Chip
                FilterChip(
                    selected = false,
                    onClick = {
                        HapticUtils.tap(view)
                        showClearDialog = true
                    },
                    label = { Text(stringResource(R.string.settings_activity_log_clear)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete_24),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )

                // "All" Chip
                FilterChip(
                    selected = selectedTypes.isEmpty(),
                    onClick = {
                        HapticUtils.segmentTick(view)
                        selectedTypes = emptySet()
                    },
                    label = { Text(stringResource(R.string.activity_log_filter_all)) },
                )

                // Multi-select Event Type Chips
                ActivityEventType.values().forEach { type ->
                    val labelRes =
                        when (type) {
                            ActivityEventType.SERVICE_START -> R.string.activity_log_type_start
                            ActivityEventType.SERVICE_STOP -> R.string.activity_log_type_stop
                            ActivityEventType.CONNECTION -> R.string.activity_log_type_connection
                            ActivityEventType.PERMISSION -> R.string.activity_log_type_permission
                            ActivityEventType.APP_MANAGEMENT -> R.string.activity_log_type_app_mgmt
                            ActivityEventType.WATCHDOG -> R.string.activity_log_type_watchdog
                            ActivityEventType.SYSTEM -> R.string.activity_log_type_system
                            ActivityEventType.OTHER -> R.string.activity_log_type_other
                        }
                    val isSelected = type in selectedTypes
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            HapticUtils.segmentTick(view)
                            selectedTypes =
                                if (isSelected) {
                                    selectedTypes - type
                                } else {
                                    selectedTypes + type
                                }
                        },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            // Main Timeline Content
            if (processedGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(R.drawable.ic_history_24),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.empty_state_title_activity_log_empty),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.empty_state_description_activity_log_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                ) {
                    groupedSections.forEach { (sectionTitle, sectionGroups) ->
                        if (currentGrouping != ActivityLogGrouping.NONE && sectionTitle.isNotEmpty()) {
                            val totalEvents = sectionGroups.sumOf { it.occurrences.size + 1 }
                            item(key = "header_${currentGrouping.name}_$sectionTitle") {
                                TimelineDateHeader(
                                    title = "$sectionTitle ($totalEvents)",
                                )
                            }
                        }

                        itemsIndexed(
                            items = sectionGroups,
                            key = { _, group -> group.key },
                        ) { index, group ->
                            val isExpanded = expandedKeys.contains(group.key)
                            ActivityLogTimelineRow(
                                group = group,
                                dateFormat = dateFormat,
                                isFirst = index == 0,
                                isLast = index == sectionGroups.lastIndex,
                                isLatest = index == 0 && currentSort == ActivityLogSort.NEWEST && currentGrouping == ActivityLogGrouping.NONE,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    if (isExpanded) {
                                        expandedKeys.remove(group.key)
                                    } else {
                                        expandedKeys.add(group.key)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTopBar) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_show_activity_log_home)) },
                    navigationIcon = {
                        if (onBackClick != null) {
                            IconButton(onClick = onBackClick) {
                                Icon(painterResource(R.drawable.ic_back_24), contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (rawLogs.isNotEmpty()) {
                            // Export logs action
                            IconButton(
                                onClick = {
                                    HapticUtils.tap(view)
                                    val exported = exportLogsToText(rawLogs, dateFormat)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Activity Log", exported))
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.activity_log_export_copied, rawLogs.size),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_copy),
                                    contentDescription = stringResource(R.string.activity_log_export),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Clear logs action
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete_24),
                                    contentDescription = stringResource(R.string.settings_activity_log_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                content()
            }
        }
    } else {
        content()
    }
}

@Composable
private fun TimelineDateHeader(title: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 0.7.dp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActivityLogTimelineRow(
    group: LogGroup,
    dateFormat: DateFormat,
    isFirst: Boolean,
    isLast: Boolean,
    isLatest: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    val record = group.primaryRecord
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(if (ShizukuSettings.isExpressiveAnimationsEnabled()) 260 else 0),
        label = "chevronRotation",
    )

    var appName by remember(record.packageName) {
        mutableStateOf(record.appName.ifEmpty { record.packageName })
    }
    var iconBitmap by remember(record.packageName) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }

    LaunchedEffect(record.packageName) {
        val pm = context.packageManager
        val sizePx = with(density) { 36.dp.roundToPx() }
        val ai =
            withContext(Dispatchers.IO) {
                try {
                    pm.getApplicationInfo(record.packageName, 0)
                } catch (_: Exception) {
                    null
                }
            } ?: return@LaunchedEffect
        appName = AppIconCache.getLabel(context, ai)
        val bitmap =
            withContext(Dispatchers.IO) {
                AppIconCache.getOrLoadBitmap(context, ai, ai.uid / 100000, sizePx)
            }
        if (bitmap != null) iconBitmap = bitmap.asImageBitmap()
    }

    val typeColor =
        when (record.eventType) {
            ActivityEventType.SERVICE_START -> MaterialTheme.colorScheme.primary
            ActivityEventType.SERVICE_STOP -> MaterialTheme.colorScheme.error
            ActivityEventType.CONNECTION -> MaterialTheme.colorScheme.secondary
            ActivityEventType.PERMISSION -> MaterialTheme.colorScheme.tertiary
            ActivityEventType.APP_MANAGEMENT -> MaterialTheme.colorScheme.secondary
            ActivityEventType.WATCHDOG -> MaterialTheme.colorScheme.error
            ActivityEventType.SYSTEM -> MaterialTheme.colorScheme.outline
            ActivityEventType.OTHER -> MaterialTheme.colorScheme.outline
        }

    val typeLabel =
        when (record.eventType) {
            ActivityEventType.SERVICE_START -> stringResource(R.string.activity_log_type_start)
            ActivityEventType.SERVICE_STOP -> stringResource(R.string.activity_log_type_stop)
            ActivityEventType.CONNECTION -> stringResource(R.string.activity_log_type_connection)
            ActivityEventType.PERMISSION -> stringResource(R.string.activity_log_type_permission)
            ActivityEventType.APP_MANAGEMENT -> stringResource(R.string.activity_log_type_app_mgmt)
            ActivityEventType.WATCHDOG -> stringResource(R.string.activity_log_type_watchdog)
            ActivityEventType.SYSTEM -> stringResource(R.string.activity_log_type_system)
            ActivityEventType.OTHER -> stringResource(R.string.activity_log_type_other)
        }

    val railColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val isExpressive = ShizukuSettings.isExpressiveAnimationsEnabled()
    val haloTransition = rememberInfiniteTransition(label = "haloTransition")
    val pulseScale by
        if (isLatest && isExpressive) {
            haloTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.5f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1400),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "haloScale",
            )
        } else {
            remember { mutableFloatStateOf(1f) }
        }

    val totalCount = group.occurrences.size + 1
    val isStacked = group.occurrences.isNotEmpty()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        // Vertical connecting timeline rail matching Morphe timeline design
        Canvas(
            modifier =
                Modifier
                    .width(28.dp)
                    .defaultMinSize(minHeight = 76.dp),
        ) {
            val cx = size.width / 2f
            val nodeY = 22.dp.toPx()
            val railStroke = 2.dp.toPx()

            // Continuous connecting rail
            val startY = if (isFirst) nodeY else 0f
            val endY = if (isLast) nodeY else size.height
            if (endY > startY) {
                drawLine(
                    color = railColor,
                    start = Offset(cx, startY),
                    end = Offset(cx, endY),
                    strokeWidth = railStroke,
                )
            }

            // Outer glowing halo (matching Changelog TimelineRailView 10dp * scale)
            val haloRadius = 10.dp.toPx() * if (isLatest) pulseScale else 1f
            drawCircle(
                color = typeColor.copy(alpha = if (isLatest) 0.32f else 0.16f),
                radius = haloRadius,
                center = Offset(cx, nodeY),
            )

            // Outer colored border / ring (6dp outer ring)
            drawCircle(
                color = typeColor,
                radius = 6.dp.toPx(),
                center = Offset(cx, nodeY),
            )

            // Inner center dot / nested indicator for stacked events
            if (isStacked) {
                // Nested concentric indicator for repetitive activity stack
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = 3.dp.toPx(),
                    center = Offset(cx, nodeY),
                )
            } else if (!isLatest) {
                drawCircle(
                    color = railColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(cx, nodeY),
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // M3E expressive Card with interactive elevation & animated expansion
        Card(
            shape = RoundedCornerShape(18.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                ),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(bottom = 10.dp)
                    .animateContentSize(
                        animationSpec = tween(if (isExpressive) 260 else 0),
                    ).clickable {
                        HapticUtils.segmentTick(view)
                        onToggleExpand()
                    },
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val bitmap = iconBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_system_icon),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = typeColor,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = formatRelativeTime(record.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    // Occurrence badge if stacked (e.g. 5x)
                    if (isStacked) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "${totalCount}x",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Event type status badge
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = typeColor.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = typeColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    // Interactive chevron indicator
                    Icon(
                        painter = painterResource(R.drawable.ic_outline_expand_more_24),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(22.dp)
                                .graphicsLayer(rotationZ = chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = record.action,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Expandable details (package name, copy button, stacked occurrences)
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                    ) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = record.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    HapticUtils.tap(view)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Package Name", record.packageName))
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.activity_log_copied, record.packageName),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_copy),
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Exact time
                        Text(
                            text = dateFormat.format(Date(record.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 2.dp),
                        )

                        // If stacked repetitive events: show accordion sub-timeline of occurrences
                        if (isStacked) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.activity_log_stacked_count, totalCount),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                group.occurrences.forEachIndexed { idx, occ ->
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 4.dp, top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "#${totalCount - 1 - idx}: ${dateFormat.format(Date(occ.timestamp))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = formatRelativeTime(occ.timestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0L)
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun getDateSectionTitle(
    context: Context,
    timestamp: Long,
): String {
    val cal = Calendar.getInstance()
    val currentDay = cal.get(Calendar.DAY_OF_YEAR)
    val currentYear = cal.get(Calendar.YEAR)

    cal.timeInMillis = timestamp
    val targetDay = cal.get(Calendar.DAY_OF_YEAR)
    val targetYear = cal.get(Calendar.YEAR)

    return when {
        currentYear == targetYear && currentDay == targetDay ->
            context.getString(R.string.activity_log_date_today)
        currentYear == targetYear && currentDay - targetDay == 1 ->
            context.getString(R.string.activity_log_date_yesterday)
        currentYear == targetYear && currentDay - targetDay in 2..6 ->
            context.getString(R.string.activity_log_date_earlier_week)
        else ->
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun exportLogsToText(
    records: List<ActivityLogRecord>,
    dateFormat: DateFormat,
): String {
    val sb = StringBuilder()
    sb.appendLine("=== Shizuku+ Activity Log Export (${records.size} events) ===")
    records.sortedByDescending { it.timestamp }.forEach { record ->
        val timeStr = dateFormat.format(Date(record.timestamp))
        val typeStr = record.eventType.name
        val app = record.appName.ifEmpty { record.packageName }
        sb.appendLine("[$timeStr] [$typeStr] $app (${record.packageName}) - ${record.action}")
    }
    return sb.toString()
}
