package com.example.pixelclock.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimerScheduler.ACTION_TIMER_FIRE) return
        val timerId = intent.getStringExtra(TimerScheduler.EXTRA_TIMER_ID) ?: return
        Log.d(TAG, "System timer fired: $timerId")
        val pending = goAsync()
        try {
            TimerManager.getInstance(context).onSystemAlarm(timerId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deliver timer", e)
        } finally {
            pending.finish()
        }
    }

    companion object {
        const val TAG = "TimerReceiver"
    }
}
