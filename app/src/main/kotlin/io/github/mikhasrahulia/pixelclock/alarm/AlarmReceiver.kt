package io.github.mikhasrahulia.pixelclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import io.github.mikhasrahulia.pixelclock.data.AlarmRepository
import java.time.LocalDateTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID).orEmpty()
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)

        Log.d(TAG, "AlarmReceiver received action: $action, alarmId: $alarmId")

        when (action) {
            AlarmScheduler.ACTION_ALARM_FIRE -> {
                val now = System.currentTimeMillis()
                if (alarmId.isNotEmpty() && alarmId == lastFiredAlarmId && (now - lastFiredTimestamp) < 3000L) {
                    Log.w(TAG, "Ignoring duplicate alarm fire for $alarmId within 3s")
                    return
                }
                lastFiredAlarmId = alarmId
                lastFiredTimestamp = now

                // Hold a brief WakeLock so Doze cannot suspend the CPU before AlarmService starts
                acquireHandoffWakeLock(context)

                val repository = AlarmRepository.getInstance(context)
                val alarm = repository.getAlarmById(alarmId)
                val alarmLabel = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL)
                    ?.takeIf { it.isNotBlank() }
                    ?: alarm?.label.orEmpty()

                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, alarmLabel)
                    putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
                }
                runCatching {
                    context.startForegroundService(serviceIntent)
                }.onFailure { Log.e(TAG, "Failed to start AlarmService", it) }

                // Reschedule repeating alarm past the current minute (prevents same-minute double-fire race)
                if (!isSnooze && alarm != null) {
                    if (alarm.isRepeating) {
                        AlarmScheduler.getInstance(context)
                            .scheduleAlarm(alarm, from = LocalDateTime.now().plusMinutes(1))
                    } else {
                        repository.toggleAlarm(alarmId, false)
                        repository.flushBlocking()
                    }
                }
            }

            AlarmScheduler.ACTION_ALARM_DISMISS -> {
                context.stopService(Intent(context, AlarmService::class.java))
                context.sendBroadcast(
                    Intent(AlarmScheduler.ACTION_ALARM_DISMISSED).setPackage(context.packageName)
                )
            }

            AlarmScheduler.ACTION_ALARM_SNOOZE -> {
                context.stopService(Intent(context, AlarmService::class.java))
                context.sendBroadcast(
                    Intent(AlarmScheduler.ACTION_ALARM_DISMISSED).setPackage(context.packageName)
                )

                val repository = AlarmRepository.getInstance(context)
                val alarmLabel = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL)
                    ?.takeIf { it.isNotBlank() }
                    ?: repository.getAlarmById(alarmId)?.label.orEmpty()

                val snoozeTimeMillis = System.currentTimeMillis() + SNOOZE_DURATION_MILLIS
                AlarmScheduler.getInstance(context)
                    .scheduleSnooze(alarmId, alarmLabel, snoozeTimeMillis)
            }
        }
    }

    private fun acquireHandoffWakeLock(context: Context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pixelclock:alarm_handoff")
                .acquire(30_000L)
        }
    }

    companion object {
        const val TAG = "AlarmReceiver"
        private const val SNOOZE_DURATION_MILLIS = 10 * 60 * 1000L

        @Volatile
        private var lastFiredAlarmId: String? = null
        @Volatile
        private var lastFiredTimestamp: Long = 0L
    }
}
