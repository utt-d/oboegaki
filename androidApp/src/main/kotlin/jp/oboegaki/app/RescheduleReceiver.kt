package jp.oboegaki.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val finished = AtomicBoolean(false)
        fun finishOnce() {
            if (finished.compareAndSet(false, true)) {
                runCatching { pendingResult.finish() }
            }
        }

        try {
            val app = OboegakiApplication.from(context)
            val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                workScope.launch {
                    try {
                        app.repository.rescheduleAllReminders()
                    } catch (_: Throwable) {
                        // A lifecycle broadcast must never take down the receiver process.
                    } finally {
                        finishOnce()
                        runCatching { workScope.cancel() }
                    }
                }
            } catch (_: Throwable) {
                runCatching { workScope.cancel() }
                finishOnce()
            }
        } catch (_: Throwable) {
            finishOnce()
        }
    }
}
