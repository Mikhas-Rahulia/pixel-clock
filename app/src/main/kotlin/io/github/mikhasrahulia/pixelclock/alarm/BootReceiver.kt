package io.github.mikhasrahulia.pixelclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.mikhasrahulia.pixelclock.data.AlarmRepository
import io.github.mikhasrahulia.pixelclock.timer.TimerManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("BootReceiver", "Received broadcast: $action")
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        if (isBoot ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            AlarmRepository.getInstance(context).ensureLoadedAndMigrated()
            AlarmScheduler.getInstance(context).rescheduleAllAlarms()
            // Never start a specialUse FGS from BOOT_COMPLETED on Android 15/16
            TimerManager.getInstance(context).reconcile(fromBoot = isBoot)
        }
    }
}
