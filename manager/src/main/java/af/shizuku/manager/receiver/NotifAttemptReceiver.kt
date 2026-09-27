package af.shizuku.manager.receiver

import af.shizuku.manager.worker.AdbStartWorker
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotifAttemptReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        AdbStartWorker.enqueue(context)
    }
}
