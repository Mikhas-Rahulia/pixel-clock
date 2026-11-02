package io.github.mikhasrahulia.pixelclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.mikhasrahulia.pixelclock.MainActivity
import io.github.mikhasrahulia.pixelclock.data.AlarmRepository

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: ""
        val alarmLabel = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: ""

        Log.d(TAG, "AlarmReceiver received action: $action, alarmId: $alarmId")

        when (action) {
            AlarmScheduler.ACTION_ALARM_FIRE -> {
                // 1. Start AlarmService (sound & vibration & ongoing notification)
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, alarmLabel)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                // 2. Launch full-screen ringing UI
                val ringIntent = Intent(context, AlarmRingingActivity::class.java).apply {
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, alarmLabel)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(ringIntent)

                // 3. Reschedule repeating alarm or disable one-time alarm
                val repository = AlarmRepository.getInstance(context)
                val alarm = repository.getAlarmById(alarmId)
                if (alarm != null) {
                    if (alarm.isRepeating) {
                        // Re-schedule for the next period (e.g. next day/week/bi-week)
                        AlarmScheduler.getInstance(context).scheduleAlarm(alarm)
                    } else {
                        // One-time alarm: turn off
                        repository.toggleAlarm(alarmId, false)
                    }
                }
            }

            AlarmScheduler.ACTION_ALARM_DISMISS -> {
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    this.action = AlarmService.ACTION_STOP_ALARM
                }
                context.stopService(serviceIntent)
            }

            AlarmScheduler.ACTION_ALARM_SNOOZE -> {
                // Stop current ringing
                val serviceIntent = Intent(context, AlarmService::class.java)
                context.stopService(serviceIntent)

                // Schedule snooze alarm for +10 minutes
                val snoozeTimeMillis = System.currentTimeMillis() + 10 * 60 * 1000L
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                val snoozeFireIntent = Intent(context, AlarmReceiver::class.java).apply {
                    this.action = AlarmScheduler.ACTION_ALARM_FIRE
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, "$alarmLabel (Отложено)")
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    (alarmId + "_snooze").hashCode(),
                    snoozeFireIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val showIntent = PendingIntent.getActivity(
                    context,
                    alarmId.hashCode() + 1001,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(snoozeTimeMillis, showIntent),
                        pendingIntent
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        const val TAG = "AlarmReceiver"
    }
}
