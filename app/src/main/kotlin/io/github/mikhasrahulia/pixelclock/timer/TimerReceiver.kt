package io.github.mikhasrahulia.pixelclock.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val timerId = intent.getStringExtra(TimerScheduler.EXTRA_TIMER_ID)
            ?: intent.getStringExtra(TimerService.EXTRA_TIMER_ID)
            .orEmpty()

        Log.d(TAG, "TimerReceiver action=$action timerId=$timerId")

        when (action) {
            TimerScheduler.ACTION_TIMER_FIRE -> {
                if (timerId.isBlank()) return
                acquireHandoffWakeLock(context)
                val pending = goAsync()
                try {
                    TimerManager.getInstance(context).onSystemAlarm(timerId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to deliver timer", e)
                } finally {
                    pending.finish()
                }
            }
            TimerService.ACTION_PAUSE,
            TimerService.ACTION_RESUME,
            TimerService.ACTION_RESET,
            TimerService.ACTION_ADD_FIVE_MIN,
            TimerService.ACTION_ADD_ONE_MIN,
            TimerService.ACTION_STOP_ALERT,
            TimerService.ACTION_DISMISS_NOTIFICATION -> {
                val serviceIntent = Intent(context, TimerService::class.java).apply {
                    this.action = action
                    if (timerId.isNotBlank()) putExtra(TimerService.EXTRA_TIMER_ID, timerId)
                }
                runCatching {
                    if (action == TimerService.ACTION_STOP_ALERT || action == TimerService.ACTION_DISMISS_NOTIFICATION) {
                        // Directly route through TimerManager if service is already stopped
                        if (action == TimerService.ACTION_STOP_ALERT && timerId.isNotBlank()) {
                            TimerManager.getInstance(context).resetTimer(timerId)
                        }
                        context.stopService(Intent(context, TimerService::class.java))
                        context.sendBroadcast(
                            Intent(TimerService.ACTION_TIMER_ALERT_CLOSED).setPackage(context.packageName)
                        )
                    } else {
                        context.startForegroundService(serviceIntent)
                    }
                }.onFailure { Log.e(TAG, "Failed routing timer action $action", it) }
            }
        }
    }

    private fun acquireHandoffWakeLock(context: Context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pixelclock:timer_handoff")
                .acquire(30_000L)
        }
    }

    companion object {
        const val TAG = "TimerReceiver"
    }
}
