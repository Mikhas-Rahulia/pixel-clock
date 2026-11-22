package io.github.mikhasrahulia.pixelclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.mikhasrahulia.pixelclock.MainActivity
import io.github.mikhasrahulia.pixelclock.data.AlarmModel
import io.github.mikhasrahulia.pixelclock.data.AlarmRepository
import java.time.LocalDateTime

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(alarm: AlarmModel, from: LocalDateTime = LocalDateTime.now()) {
        if (!alarm.isEnabled) {
            cancelAlarm(alarm.id)
            return
        }

        // Null means this alarm will not ring (e.g. a dated alarm that falls inside its pause)
        val triggerMillis = alarm.nextTriggerMillisOrNull(from) ?: run {
            cancelAlarm(alarm.id)
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.hashCode() + 1000,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerMillis, showIntent),
                pendingIntent
            )
            Log.d(TAG, "Scheduled alarm ${alarm.id} for $triggerMillis")
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission missing", e)
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }
    }

    fun scheduleSnooze(alarmId: String, alarmLabel: String, triggerMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, alarmLabel)
            putExtra(EXTRA_IS_SNOOZE, true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeRequestCode(alarmId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = PendingIntent.getActivity(
            context,
            alarmId.hashCode() + 1001,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerMillis, showIntent),
                pendingIntent
            )
            Log.d(TAG, "Scheduled snooze for $alarmId at $triggerMillis")
        }.onFailure { Log.e(TAG, "Failed to schedule snooze", it) }
    }

    fun cancelAlarm(alarmId: String) {
        cancelRequest(alarmId, alarmId.hashCode())
        cancelRequest(alarmId, snoozeRequestCode(alarmId))
    }

    private fun cancelRequest(alarmId: String, requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Canceled alarm $alarmId (code=$requestCode)")
        }
    }

    fun rescheduleAllAlarms() {
        val repository = AlarmRepository.getInstance(context)
        for (alarm in repository.alarms.value) {
            if (alarm.isEnabled) {
                scheduleAlarm(alarm)
            } else {
                cancelAlarm(alarm.id)
            }
        }
    }

    private fun snoozeRequestCode(alarmId: String): Int = (alarmId + "_snooze").hashCode()

    companion object {
        const val TAG = "AlarmScheduler"
        const val ACTION_ALARM_FIRE = "io.github.mikhasrahulia.pixelclock.ACTION_ALARM_FIRE"
        const val ACTION_ALARM_DISMISS = "io.github.mikhasrahulia.pixelclock.ACTION_ALARM_DISMISS"
        const val ACTION_ALARM_SNOOZE = "io.github.mikhasrahulia.pixelclock.ACTION_ALARM_SNOOZE"
        const val ACTION_ALARM_DISMISSED = "io.github.mikhasrahulia.pixelclock.ACTION_ALARM_DISMISSED"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"

        @Volatile
        private var instance: AlarmScheduler? = null

        fun getInstance(context: Context): AlarmScheduler {
            return instance ?: synchronized(this) {
                instance ?: AlarmScheduler(context.applicationContext).also { instance = it }
            }
        }
    }
}
