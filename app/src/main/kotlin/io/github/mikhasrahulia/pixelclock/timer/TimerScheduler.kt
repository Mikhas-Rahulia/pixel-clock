package io.github.mikhasrahulia.pixelclock.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import io.github.mikhasrahulia.pixelclock.data.TimerModel

/**
 * Monotonic AlarmManager wakeups for timers (ELAPSED_REALTIME_WAKEUP).
 * Immune to NTP/timezone adjustments and does not hijack the Pixel AOD "Next Alarm" slot.
 */
class TimerScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(timer: TimerModel) {
        if (!timer.isRunning || timer.isPaused || timer.hasFired) {
            cancel(timer.id)
            return
        }
        val triggerElapsed = when {
            timer.targetElapsedRealtimeMillis > 0L -> timer.targetElapsedRealtimeMillis
            timer.targetEndTimeMillis > 0L -> {
                val rem = (timer.targetEndTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                SystemClock.elapsedRealtime() + rem
            }
            else -> {
                cancel(timer.id)
                return
            }
        }

        val fireIntent = Intent(context, TimerReceiver::class.java).apply {
            action = ACTION_TIMER_FIRE
            putExtra(EXTRA_TIMER_ID, timer.id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(timer.id),
            fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerElapsed,
                pending
            )
            Log.d(TAG, "Scheduled timer ${timer.id} at elapsed=$triggerElapsed")
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm not allowed", e)
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerElapsed,
                    pending
                )
            }
        }
    }

    fun cancel(timerId: String) {
        val intent = Intent(context, TimerReceiver::class.java).apply {
            action = ACTION_TIMER_FIRE
            putExtra(EXTRA_TIMER_ID, timerId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(timerId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    private fun requestCode(timerId: String): Int = timerId.hashCode()

    companion object {
        const val TAG = "TimerScheduler"
        const val ACTION_TIMER_FIRE = "io.github.mikhasrahulia.pixelclock.ACTION_TIMER_FIRE"
        const val EXTRA_TIMER_ID = "extra_timer_id"

        @Volatile
        private var instance: TimerScheduler? = null

        fun getInstance(context: Context): TimerScheduler {
            return instance ?: synchronized(this) {
                instance ?: TimerScheduler(context.applicationContext).also { instance = it }
            }
        }
    }
}
