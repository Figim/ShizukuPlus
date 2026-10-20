package af.shizuku.manager.home.compose

import af.shizuku.manager.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHubScreen(
    onBackClick: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Hub") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(painterResource(id = R.drawable.ic_back_24), contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        val navClearance =
            if (af.shizuku.manager.ShizukuSettings
                    .isFloatingNavBarEnabled()
            ) {
                112.dp
            } else {
                0.dp
            }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
        ) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Metrics") },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Activity Log") },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    // Bottom clearance goes inside each page's scroll content, not on this
                    // Column: padding here leaves an empty band that shows the bare window
                    // background (a black bar in AMOLED) and stops content short of the nav bar.
                    0 -> ServerMetricsScreen(bottomInset = innerPadding.calculateBottomPadding() + navClearance)
                    1 -> ActivityLogScreen(bottomInset = innerPadding.calculateBottomPadding() + navClearance)
                }
            }
        }
    }
}
