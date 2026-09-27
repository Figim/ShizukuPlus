package af.shizuku.manager.settings

import af.shizuku.core.ui.AppActivity
import android.os.Bundle

// Extends AppActivity (not plain AppCompatActivity) so onApplyUserThemeResource/
// computeUserThemeKey actually run - see AdbPairingDialogActivity for the full explanation.
class BugReportDialogActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BugReportDialog().show(supportFragmentManager, "BugReportDialog")
    }
}
