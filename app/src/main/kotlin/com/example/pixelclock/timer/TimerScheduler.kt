package com.example.pixelclock.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.pixelclock.MainActivity
import com.example.pixelclock.data.TimerModel

/**
 * Real AlarmManager timers. They survive process death, Doze and reboot.
 */
class TimerScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(timer: TimerModel) {
        if (!timer.isRunning || timer.isPaused || timer.hasFired || timer.targetEndTimeMillis <= 0L) {
            cancel(timer.id)
            return
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
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode(timer.id) + 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(timer.targetEndTimeMillis, showIntent),
                    pending
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, timer.targetEndTimeMillis, pending)
            }
            Log.d(TAG, "Scheduled timer ${timer.id} at ${timer.targetEndTimeMillis}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm not allowed", e)
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.targetEndTimeMillis, pending)
            } catch (ex: Exception) {
                ex.printStackTrace()
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
        const val ACTION_TIMER_FIRE = "com.example.pixelclock.ACTION_TIMER_FIRE"
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
