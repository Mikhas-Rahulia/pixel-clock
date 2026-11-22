package io.github.mikhasrahulia.pixelclock.timer

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
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.mikhasrahulia.pixelclock.MainActivity
import io.github.mikhasrahulia.pixelclock.R
import io.github.mikhasrahulia.pixelclock.data.TimerModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hapticLoopJob: Job? = null
    private var autoSilenceJob: Job? = null
    private var activeFinishedTimer: TimerModel? = null
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        val manager = TimerManager.getInstance(applicationContext)
        scope.launch {
            manager.timers.collectLatest {
                if (foregroundStarted) updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
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
                    return START_NOT_STICKY
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
        val keep = activeFinishedTimer != null || snapshot.any { it.isRunning && !it.isPaused && !it.hasFired }
        startInForeground(buildTimerNotification())
        if (!keep) {
            stopForegroundNow()
        }
        return START_NOT_STICKY
    }

    private fun onTimerFinished(timer: TimerModel) {
        val alreadyAudible = ringtone?.isPlaying == true || mediaPlayer?.isPlaying == true
        if (activeFinishedTimer?.id == timer.id && alreadyAudible) {
            startInForeground(buildTimerNotification())
            return
        }
        activeFinishedTimer = timer
        acquireWakeLock()
        startAlertSound()
        startPixelHaptics()
        scheduleAutoSilence(timer.id)
        startInForeground(buildTimerNotification())
    }

    private fun scheduleAutoSilence(timerId: String) {
        autoSilenceJob?.cancel()
        autoSilenceJob = scope.launch {
            delay(AUTO_SILENCE_MILLIS)
            stopAlertSound()
            closeRingingUi()
            TimerManager.getInstance(applicationContext).resetTimer(timerId)
            stopForegroundNow()
        }
    }

    private fun closeRingingUi() {
        sendBroadcast(Intent(ACTION_TIMER_ALERT_CLOSED).setPackage(packageName))
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pixelclock:timer_alert").apply {
                acquire(AUTO_SILENCE_MILLIS + 30_000L)
            }
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
            .setHapticChannelsMuted(false)
            .build()

        runCatching {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        }

        val alertUri: Uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(audioAttributes)
                setVolume(1f, 1f)
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ ->
                    startFallbackRingtone(alertUri, audioAttributes)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer failed, falling back to Ringtone", e)
            startFallbackRingtone(alertUri, audioAttributes)
        }
    }

    private fun startFallbackRingtone(alertUri: Uri, audioAttributes: AudioAttributes) {
        if (ringtone?.isPlaying == true) return
        runCatching {
            ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)?.apply {
                this.audioAttributes = audioAttributes
                isLooping = true
                play()
            }
        }
    }

    private fun startPixelHaptics() {
        if (hapticLoopJob?.isActive == true) return
        val vib = (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        vibrator = vib
        val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)

        val supportsPrimitives = vib.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_THUD
        )
        if (supportsPrimitives) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 0)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.9f, 120)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 120)
                .compose()
            hapticLoopJob = scope.launch {
                while (isActive && activeFinishedTimer != null) {
                    runCatching { vib.vibrate(effect, attrs) }
                    delay(1100L)
                }
            }
        } else {
            runCatching {
                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 450, 250, 450, 400), 0), attrs)
            }
        }
    }

    private fun stopAlertSound() {
        activeFinishedTimer = null
        hapticLoopJob?.cancel()
        autoSilenceJob?.cancel()

        runCatching { ringtone?.stop() }
        ringtone = null

        val mp = mediaPlayer
        mediaPlayer = null
        if (mp != null) {
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: Exception) {
            } finally {
                runCatching { mp.release() }
            }
        }

        runCatching { vibrator?.cancel() }
        vibrator = null

        runCatching {
            audioFocusRequest?.let { req ->
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.abandonAudioFocusRequest(req)
            }
        }
        audioFocusRequest = null

        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun updateNotification() {
        val timers = TimerManager.getInstance(applicationContext).timers.value
        val keep = activeFinishedTimer != null || timers.any { it.isRunning && !it.isPaused && !it.hasFired }
        if (!keep) {
            stopForegroundNow()
            return
        }
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildTimerNotification())
    }

    private fun startInForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        foregroundStarted = true
    }

    private fun stopForegroundNow() {
        foregroundStarted = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
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

        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val running = TimerManager.getInstance(applicationContext).timers.value
            .filter { it.isRunning && !it.isPaused && !it.hasFired }
        val primary = running.minByOrNull { it.calculateCurrentRemaining(now, elapsed) }

        val dismiss = broadcastPending(ACTION_DISMISS_NOTIFICATION, 107, "")
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentIntent(openApp)
            .setDeleteIntent(dismiss)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)

        if (primary != null) {
            val remainingMs = primary.calculateCurrentRemaining(now, elapsed)
            val title = primary.label.ifBlank { getString(R.string.tab_timer) }
            val extraCount = running.size - 1
            val subtitle = if (extraCount > 0) {
                "${getString(R.string.timer_running)} (+$extraCount)"
            } else {
                getString(R.string.timer_running)
            }
            builder.setContentTitle(title)
                .setContentText(subtitle)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(now + remainingMs)
                .addAction(
                    R.drawable.ic_notif_add,
                    getString(R.string.add_five_min),
                    broadcastPending(ACTION_ADD_FIVE_MIN, 103, primary.id)
                )
                .addAction(
                    R.drawable.ic_notif_pause,
                    getString(R.string.pause),
                    broadcastPending(ACTION_PAUSE, 104, primary.id)
                )
                .addAction(
                    R.drawable.ic_notif_stop,
                    getString(R.string.reset),
                    broadcastPending(ACTION_RESET, 106, primary.id)
                )
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
        val snooze = broadcastPending(ACTION_ADD_FIVE_MIN, 121, timer.id)
        val stop = broadcastPending(ACTION_STOP_ALERT, 122, timer.id)
        val title = timer.label.ifBlank { getString(R.string.tab_timer) }

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
            .addAction(R.drawable.ic_notif_snooze, getString(R.string.snooze_five), snooze)
            .addAction(R.drawable.ic_notif_stop, getString(R.string.stop_timer), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun broadcastPending(action: String, request: Int, timerId: String): PendingIntent {
        val intent = Intent(this, TimerReceiver::class.java).apply {
            this.action = action
            if (timerId.isNotBlank()) putExtra(EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getBroadcast(
            this,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        // Clean up unused legacy shade channel if present
        runCatching { manager.deleteNotificationChannel(LEGACY_SHADE_CHANNEL_ID) }

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
            enableVibration(false) // Managed directly with Pixel LRA USAGE_ALARM
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        manager.createNotificationChannel(running)
        manager.createNotificationChannel(alert)
    }

    override fun onDestroy() {
        stopAlertSound()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TimerService"
        const val CHANNEL_ID = "pixel_clock_timer_channel_v7"
        const val ALERT_CHANNEL_ID = "pixel_clock_timer_alert_channel_v7"
        private const val LEGACY_SHADE_CHANNEL_ID = "pixel_clock_timer_alert_shade_v7"
        const val NOTIFICATION_ID = 2002
        private const val AUTO_SILENCE_MILLIS = 10 * 60 * 1000L

        const val ACTION_DISMISS_NOTIFICATION = "io.github.mikhasrahulia.pixelclock.ACTION_DISMISS_NOTIFICATION"
        const val ACTION_ADD_FIVE_MIN = "io.github.mikhasrahulia.pixelclock.ACTION_TIMER_ADD_FIVE_MIN"
        const val ACTION_ADD_ONE_MIN = "io.github.mikhasrahulia.pixelclock.ACTION_TIMER_ADD_ONE_MIN"
        const val ACTION_PAUSE = "io.github.mikhasrahulia.pixelclock.ACTION_TIMER_PAUSE"
        const val ACTION_RESUME = "io.github.mikhasrahulia.pixelclock.ACTION_TIMER_RESUME"
        const val ACTION_RESET = "io.github.mikhasrahulia.pixelclock.ACTION_TIMER_RESET"
        const val ACTION_STOP_ALERT = "io.github.mikhasrahulia.pixelclock.ACTION_TIMER_STOP_ALERT"
        const val ACTION_TIMER_DUE = "io.github.mikhasrahulia.pixelclock.ACTION_TIMER_DUE"
        const val ACTION_TIMER_ALERT_CLOSED = "io.github.mikhasrahulia.pixelclock.ACTION_TIMER_ALERT_CLOSED"
        const val EXTRA_TIMER_ID = "extra_timer_id"
        const val EXTRA_TIMER_LABEL = "extra_timer_label"
        const val EXTRA_TIMER_ORIGIN = "extra_timer_origin"
    }
}
