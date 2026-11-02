package io.github.mikhasrahulia.pixelclock.timer

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.mikhasrahulia.pixelclock.R
import io.github.mikhasrahulia.pixelclock.data.TimerModel
import io.github.mikhasrahulia.pixelclock.ui.theme.PixelClockTheme

class TimerRingingActivity : ComponentActivity() {
    private var timerId: String = ""
    private var ringtone: Ringtone? = null

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TimerService.ACTION_TIMER_ALERT_CLOSED) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnAndKeyguard()
        enableEdgeToEdge()
        timerId = intent.getStringExtra(TimerService.EXTRA_TIMER_ID).orEmpty()
        val label = intent.getStringExtra(TimerService.EXTRA_TIMER_LABEL).orEmpty()
        val origin = intent.getLongExtra(TimerService.EXTRA_TIMER_ORIGIN, 0L)
        startAlarmSound()

        ContextCompat.registerReceiver(
            this,
            closeReceiver,
            IntentFilter(TimerService.ACTION_TIMER_ALERT_CLOSED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val stat = if (origin > 0L) {
            TimerManager.getInstance(this).hourStat(origin)
        } else null

        setContent {
            PixelClockTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    TimerDoneScreen(
                        label = label,
                        durationLabel = if (origin > 0L) TimerModel.formatDuration(origin) else "",
                        statText = if (stat != null && stat.starts >= 2) {
                            stringResource(R.string.timer_hour_stat, stat.completedMinutes, stat.starts)
                        } else null,
                        onSnooze = { send(TimerService.ACTION_ADD_FIVE_MIN) },
                        onStop = { send(TimerService.ACTION_STOP_ALERT) }
                    )
                }
            }
        }
    }

    private fun send(action: String) {
        val intent = Intent(this, TimerService::class.java).apply {
            this.action = action
            putExtra(TimerService.EXTRA_TIMER_ID, timerId)
        }
        startService(intent)
        finish()
    }

    override fun onDestroy() {
        stopAlarmSound()
        try {
            unregisterReceiver(closeReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun startAlarmSound() {
        if (ringtone?.isPlaying == true) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val usage = if (alarmVolume > 0) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        try {
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = attributes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
            Log.d("TimerRing", "playing=${ringtone?.isPlaying} alarmVolume=$alarmVolume")
        } catch (e: Exception) {
            Log.e("TimerRing", "sound failed", e)
        }
    }

    private fun stopAlarmSound() {
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
        ringtone = null
    }

    private fun turnScreenOnAndKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguard.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun TimerDoneScreen(
    label: String,
    durationLabel: String,
    statText: String?,
    onSnooze: () -> Unit,
    onStop: () -> Unit
) {
    BackHandler { /* A back swipe should not kill the alert. */ }

        Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Icon(
            imageVector = Icons.Rounded.HourglassBottom,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (label.isNotBlank()) label else stringResource(R.string.time_up),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (label.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.time_up),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (durationLabel.isNotBlank()) {
            Text(
                text = durationLabel,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (statText != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = statText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onSnooze,
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp),
            shape = RoundedCornerShape(38.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Rounded.Snooze, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.snooze_five),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.stop_timer),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
