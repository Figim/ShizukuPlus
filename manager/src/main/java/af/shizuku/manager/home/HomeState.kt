package af.shizuku.manager.home

import af.shizuku.manager.model.ServiceStatus
import com.airbnb.mvrx.Async
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.Uninitialized

data class HomeState(
    val serviceStatus: Async<ServiceStatus> = Uninitialized,
    val shouldShowBatteryOptimizationSnackbar: Boolean = false,
    // null = not loaded yet (avoids briefly showing "0" on cold start, #424)
    val grantedAppCount: Int? = null,
    val isEditMode: Boolean = false,
    // Port discovered via mDNS TLS_CONNECT; -1 = not yet found
    val discoveredAdbPort: Int = -1,
    val companionInstalled: Boolean = false,
    val compatHubInstalled: Boolean = false,
    val isOriginalShizukuRunning: Boolean = false,
) : MavericksState
