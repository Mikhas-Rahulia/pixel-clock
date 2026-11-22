package io.github.mikhasrahulia.pixelclock.alarm

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
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.mikhasrahulia.pixelclock.R
import io.github.mikhasrahulia.pixelclock.data.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var volumeRampJob: Job? = null
    private var hapticLoopJob: Job? = null
    private var autoSilenceJob: Job? = null
    private var isStopped = false
    private var isDucked = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isDucked = true
                runCatching { mediaPlayer?.setVolume(0.15f, 0.15f) }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                isDucked = false
                runCatching { mediaPlayer?.setVolume(1.0f, 1.0f) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "pixelclock:alarm_wakelock"
            ).apply {
                acquire(AUTO_SILENCE_MILLIS + 30_000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP_ALARM) {
            cleanUpAndStop()
            return START_NOT_STICKY
        }

        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID).orEmpty()
        val alarmLabel = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL).orEmpty()
        val alarm = AlarmRepository.getInstance(applicationContext).getAlarmById(alarmId)

        val notification = buildAlarmNotification(alarmId, alarmLabel)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        val soundUri = alarm?.soundUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        startAlarmAudio(soundUri)

        if (alarm?.vibrate != false) {
            startPixelHaptics()
        }

        scheduleAutoSilence()
        return START_NOT_STICKY
    }

    private fun scheduleAutoSilence() {
        autoSilenceJob?.cancel()
        autoSilenceJob = scope.launch {
            delay(AUTO_SILENCE_MILLIS)
            sendBroadcast(Intent(AlarmScheduler.ACTION_ALARM_DISMISSED).setPackage(packageName))
            cleanUpAndStop()
        }
    }

    private fun startAlarmAudio(customUri: Uri?) {
        if (mediaPlayer?.isPlaying == true || fallbackRingtone?.isPlaying == true) return

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setHapticChannelsMuted(false) // Pixel audio-coupled haptics
            .build()

        runCatching {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        }

        val alertUri: Uri = customUri
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        try {
            val mp = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(audioAttributes)
                setVolume(0.08f, 0.08f)
                isLooping = true
                setOnPreparedListener { player ->
                    if (!isStopped) {
                        player.start()
                        startVolumeCrescendo(player)
                    }
                }
                setOnErrorListener { _, _, _ ->
                    startFallbackRingtone(alertUri, audioAttributes)
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer failed, falling back to Ringtone", e)
            startFallbackRingtone(alertUri, audioAttributes)
        }
    }

    private fun startVolumeCrescendo(player: MediaPlayer) {
        volumeRampJob?.cancel()
        volumeRampJob = scope.launch {
            val steps = 24
            for (i in 1..steps) {
                delay(800L)
                if (isStopped) break
                if (!isDucked) {
                    val t = i.toFloat() / steps
                    val gain = (0.08f + 0.92f * t * t).coerceIn(0.08f, 1.0f)
                    runCatching { player.setVolume(gain, gain) }
                }
            }
        }
    }

    private fun startFallbackRingtone(alertUri: Uri, audioAttributes: AudioAttributes) {
        if (isStopped || fallbackRingtone?.isPlaying == true) return
        val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: alertUri
        runCatching {
            fallbackRingtone = RingtoneManager.getRingtone(applicationContext, fallbackUri)?.apply {
                isLooping = true
                this.audioAttributes = audioAttributes
                play()
            }
        }
    }

    /**
     * Pixel 7 Pro X-axis Linear Resonant Actuator (LRA) haptics.
     * Uses crisp VibrationEffect.Composition primitives with USAGE_ALARM so DND/Bedtime never mutes it.
     */
    private fun startPixelHaptics() {
        if (hapticLoopJob?.isActive == true) return
        val vib = (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        vibrator = vib
        val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)

        val supportsPrimitives = vib.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            VibrationEffect.Composition.PRIMITIVE_THUD,
            VibrationEffect.Composition.PRIMITIVE_TICK
        )

        if (supportsPrimitives) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.75f, 0)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 60)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.9f, 140)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 140)
                .compose()
            hapticLoopJob = scope.launch {
                while (isActive && !isStopped) {
                    runCatching { vib.vibrate(effect, attrs) }
                    delay(1450L)
                }
            }
        } else if (vib.hasAmplitudeControl()) {
            val timings = longArrayOf(0, 120, 80, 260, 120, 340, 680)
            val amplitudes = intArrayOf(0, 110, 0, 210, 0, 255, 0)
            runCatching {
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0), attrs)
            }
        } else {
            runCatching {
                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 500), 0), attrs)
            }
        }
    }

    private fun buildAlarmNotification(alarmId: String, label: String): Notification {
        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId.hashCode() + 500,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_ALARM_DISMISS
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            alarmId.hashCode() + 501,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_ALARM_SNOOZE
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            alarmId.hashCode() + 502,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = label.ifBlank { getString(R.string.tab_alarm) }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(getString(R.string.alarm_ringing))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_notif_snooze, getString(R.string.snooze), snoozePendingIntent)
            .addAction(R.drawable.ic_notif_stop, getString(R.string.dismiss), dismissPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tab_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.alarm_ringing)
            setSound(null, null)
            enableVibration(false) // Managed directly with Pixel LRA USAGE_ALARM
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun cleanUpAndStop() {
        if (isStopped) return
        isStopped = true
        volumeRampJob?.cancel()
        hapticLoopJob?.cancel()
        autoSilenceJob?.cancel()

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

        runCatching { fallbackRingtone?.stop() }
        fallbackRingtone = null

        runCatching {
            audioFocusRequest?.let { req ->
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.abandonAudioFocusRequest(req)
            }
        }
        audioFocusRequest = null

        runCatching { vibrator?.cancel() }
        vibrator = null

        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        cleanUpAndStop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        const val CHANNEL_ID = "pixel_clock_alarm_channel_v3"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP_ALARM = "io.github.mikhasrahulia.pixelclock.ACTION_STOP_ALARM"
        private const val AUTO_SILENCE_MILLIS = 15 * 60 * 1000L
    }
}
