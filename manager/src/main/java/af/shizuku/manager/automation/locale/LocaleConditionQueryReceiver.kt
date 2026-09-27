package af.shizuku.manager.automation.locale

import af.shizuku.manager.automation.locale.LocalePluginContract.RESULT_CONDITION_SATISFIED
import af.shizuku.manager.automation.locale.LocalePluginContract.RESULT_CONDITION_UNSATISFIED
import af.shizuku.manager.utils.ShizukuStateMachine
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * com.twofortyfouram.locale.intent.action.QUERY_CONDITION handler. Must answer synchronously via
 * setResultCode() on this ordered broadcast - Tasker blocks waiting for the result code, so no
 * async work belongs here.
 */
class LocaleConditionQueryReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        resultCode =
            if (ShizukuStateMachine.isRunning()) {
                RESULT_CONDITION_SATISFIED
            } else {
                RESULT_CONDITION_UNSATISFIED
            }
    }
}
