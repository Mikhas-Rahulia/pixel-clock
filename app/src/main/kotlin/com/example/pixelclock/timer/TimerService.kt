package com.example.pixelclock.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.util.Log
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.pixelclock.MainActivity
import com.example.pixelclock.R
import com.example.pixelclock.data.TimerModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TimerService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var activeFinishedTimer: TimerModel? = null
    private var foregroundStarted = false
    private var alertInActivity = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val manager = TimerManager.getInstance(applicationContext)
        scope.launch {
            manager.timers.collectLatest { list ->
                // Ringing starts only from ACTION_TIMER_DUE. A saved "fired" flag must never ring again.
                if (foregroundStarted) updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("TimerService", "onStartCommand action=$action")
        val timerId = intent?.getStringExtra(EXTRA_TIMER_ID).orEmpty()
        val timers = TimerManager.getInstance(applicationContext)

        when (action) {
            ACTION_TIMER_DUE -> {
                val finished = timers.timers.value.find { it.id == timerId && it.hasFired }
                    ?: timers.timers.value.find { it.hasFired && it.remainingMillis == 0L }
                if (finished != null) onTimerFinished(finished)
            }
            ACTION_DISMISS_NOTIFICATION -> {
                if (activeFinishedTimer == null) {
                    if (foregroundStarted) stopForegroundNow() else stopSelf()
                    return START_STICKY
                }
            }
            ACTION_ADD_FIVE_MIN -> {
                val id = timerId.ifBlank { activeFinishedTimer?.id.orEmpty() }
                stopAlertSound()
                closeRingingUi()
                if (id.isNotBlank()) timers.addFiveMinutes(id)
            }
            ACTION_ADD_ONE_MIN -> {
                if (timerId.isNotBlank()) timers.addOneMinute(timerId)
                stopAlertSound()
                closeRingingUi()
            }
            ACTION_PAUSE -> if (timerId.isNotBlank()) timers.pauseTimer(timerId)
            ACTION_RESUME -> if (timerId.isNotBlank()) timers.startTimer(timerId)
            ACTION_RESET -> {
                if (timerId.isNotBlank()) timers.resetTimer(timerId)
                stopAlertSound()
                closeRingingUi()
            }
            ACTION_STOP_ALERT -> {
                val id = timerId.ifBlank { activeFinishedTimer?.id.orEmpty() }
                stopAlertSound()
                closeRingingUi()
                if (id.isNotBlank()) timers.resetTimer(id)
            }
        }

        val snapshot = timers.timers.value
        val keep = activeFinishedTimer != null || snapshot.any { it.hasFired || ((it.isRunning || it.isPaused) && !it.hasFired) }
        // startForegroundService must reach startForeground even if we immediately stop.
        startInForeground(buildTimerNotification())
        if (!keep) {
            stopForegroundNow()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun onTimerFinished(timer: TimerModel) {
        val alreadyAudible = ringtone?.isPlaying == true || mediaPlayer?.isPlaying == true
        if (activeFinishedTimer?.id == timer.id && alreadyAudible) {
            startInForeground(buildTimerNotification())
            return
        }
        activeFinishedTimer = timer
        acquireWakeLock()
        startVibration()
        val shown = launchRinging(timer)
        alertInActivity = shown
        if (!shown) startAlertSound()
        startInForeground(buildTimerNotification())
    }

    private fun launchRinging(timer: TimerModel): Boolean {
        val ring = Intent(this, TimerRingingActivity::class.java).apply {
            putExtra(EXTRA_TIMER_ID, timer.id)
            putExtra(EXTRA_TIMER_LABEL, timer.label)
            putExtra(EXTRA_TIMER_ORIGIN, timer.originDurationMillis)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return try {
            startActivity(ring)
            true
        } catch (e: Exception) {
            Log.e("TimerService", "Ringing screen failed", e)
            false
        }
    }

    private fun closeRingingUi() {
        sendBroadcast(Intent(ACTION_TIMER_ALERT_CLOSED).setPackage(packageName))
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pixelclock:timer_alert").apply {
                acquire(10 * 60 * 1000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startAlertSound() {
        if (ringtone?.isPlaying == true || mediaPlayer?.isPlaying == true) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val usage = if (alarmVolume > 0) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audioAttributes)
                    .build()
                audioManager.requestAudioFocus(audioFocusRequest!!)
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Audio focus failed", e)
        }
        val alertUri: Uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        try {
            ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)?.apply {
                this.audioAttributes = audioAttributes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
            if (ringtone?.isPlaying == true) {
                Log.d("TimerService", "Alarm ringtone playing, alarmVolume=$alarmVolume")
                return
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Ringtone failed", e)
        }
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(audioAttributes)
                setVolume(1f, 1f)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("TimerService", "MediaPlayer failed", e)
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 500, 250, 500, 250)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlertSound() {
        activeFinishedTimer = null
        alertInActivity = false
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
        ringtone = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
            }
        } catch (_: Exception) {
        }
        audioFocusRequest = null
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun updateNotification() {
        val timers = TimerManager.getInstance(applicationContext).timers.value
        val keep = activeFinishedTimer != null || timers.any { it.hasFired || ((it.isRunning || it.isPaused) && !it.hasFired) }
        if (!keep) {
            stopForegroundNow()
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildTimerNotification())
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun stopForegroundNow() {
        Log.d("TimerService", "stopForegroundNow active=$activeFinishedTimer", Throwable())
        foregroundStarted = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun buildTimerNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val finished = activeFinishedTimer
        if (finished != null) return buildFinishedNotification(finished, openApp)

        val timers = TimerManager.getInstance(applicationContext).timers.value
        val primary = timers.firstOrNull { it.isRunning && !it.hasFired } ?: timers.firstOrNull { it.isPaused }
        val dismiss = servicePending(ACTION_DISMISS_NOTIFICATION, 107, "")
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentIntent(openApp)
            .setDeleteIntent(dismiss)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)

        if (primary != null) {
            val formatted = primary.formattedTime()
            val title = if (primary.label.isNotBlank()) "${primary.label}: $formatted" else formatted
            builder.setContentTitle(title)
                .setContentText(if (primary.isRunning) getString(R.string.timer_running) else getString(R.string.pause))
            if (primary.isRunning && primary.targetEndTimeMillis > 0L) {
                builder.setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(primary.targetEndTimeMillis)
            }
            builder.addAction(R.drawable.ic_notif_add, getString(R.string.add_five_min), servicePending(ACTION_ADD_FIVE_MIN, 103, primary.id))
            if (primary.isRunning) {
                builder.addAction(R.drawable.ic_notif_pause, getString(R.string.pause), servicePending(ACTION_PAUSE, 104, primary.id))
            } else {
                builder.addAction(R.drawable.ic_notif_play, getString(R.string.resume), servicePending(ACTION_RESUME, 105, primary.id))
            }
            builder.addAction(R.drawable.ic_notif_stop, getString(R.string.reset), servicePending(ACTION_RESET, 106, primary.id))
        } else {
            builder.setContentTitle(getString(R.string.tab_timer))
        }
        return builder.build()
    }

    private fun buildFinishedNotification(timer: TimerModel, openApp: PendingIntent): Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            120,
            Intent(this, TimerRingingActivity::class.java).apply {
                putExtra(EXTRA_TIMER_ID, timer.id)
                putExtra(EXTRA_TIMER_LABEL, timer.label)
                putExtra(EXTRA_TIMER_ORIGIN, timer.originDurationMillis)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = servicePending(ACTION_ADD_FIVE_MIN, 121, timer.id)
        val stop = servicePending(ACTION_STOP_ALERT, 122, timer.id)
        val title = if (timer.label.isNotBlank()) timer.label else getString(R.string.time_up)
        val views = RemoteViews(packageName, R.layout.notification_timer_done).apply {
            setTextViewText(R.id.timer_alert_title, title)
            setTextViewText(R.id.timer_alert_subtitle, getString(R.string.time_up))
            setTextViewText(R.id.timer_alert_snooze, getString(R.string.snooze_five))
            setTextViewText(R.id.timer_alert_stop, getString(R.string.stop_timer))
            setOnClickPendingIntent(R.id.timer_alert_snooze, snooze)
            setOnClickPendingIntent(R.id.timer_alert_stop, stop)
        }
        // Always a heads-up notification with Snooze / Stop, plus the full-screen screen for a locked phone.
        return NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(getString(R.string.time_up))
            .setContentIntent(openApp)
            .setFullScreenIntent(fullScreen, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setColor(0xFFFFB4A2.toInt())
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun servicePending(action: String, request: Int, timerId: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply {
            this.action = action
            if (timerId.isNotBlank()) putExtra(EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getService(
            this,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val running = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tab_timer),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val alert = NotificationChannel(
            ALERT_CHANNEL_ID,
            getString(R.string.timer_alert_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.time_up)
            setSound(null, null)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setBypassDnd(true)
        }
        val shade = NotificationChannel(
            SHADE_CHANNEL_ID,
            getString(R.string.timer_alert_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager?.createNotificationChannel(running)
        manager?.createNotificationChannel(alert)
        manager?.createNotificationChannel(shade)
    }

    override fun onDestroy() {
        scope.cancel()
        stopAlertSound()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "pixel_clock_timer_channel_v6"
        const val ALERT_CHANNEL_ID = "pixel_clock_timer_alert_channel_v6"
        const val SHADE_CHANNEL_ID = "pixel_clock_timer_alert_shade_v7"
        const val NOTIFICATION_ID = 2002

        const val ACTION_DISMISS_NOTIFICATION = "com.example.pixelclock.ACTION_DISMISS_NOTIFICATION"
        const val ACTION_ADD_FIVE_MIN = "com.example.pixelclock.ACTION_TIMER_ADD_FIVE_MIN"
        const val ACTION_ADD_ONE_MIN = "com.example.pixelclock.ACTION_TIMER_ADD_ONE_MIN"
        const val ACTION_PAUSE = "com.example.pixelclock.ACTION_TIMER_PAUSE"
        const val ACTION_RESUME = "com.example.pixelclock.ACTION_TIMER_RESUME"
        const val ACTION_RESET = "com.example.pixelclock.ACTION_TIMER_RESET"
        const val ACTION_STOP_ALERT = "com.example.pixelclock.ACTION_TIMER_STOP_ALERT"
        const val ACTION_TIMER_DUE = "com.example.pixelclock.ACTION_TIMER_DUE"
        const val ACTION_TIMER_ALERT_CLOSED = "com.example.pixelclock.ACTION_TIMER_ALERT_CLOSED"
        const val EXTRA_TIMER_ID = "extra_timer_id"
        const val EXTRA_TIMER_LABEL = "extra_timer_label"
        const val EXTRA_TIMER_ORIGIN = "extra_timer_origin"
    }
}
