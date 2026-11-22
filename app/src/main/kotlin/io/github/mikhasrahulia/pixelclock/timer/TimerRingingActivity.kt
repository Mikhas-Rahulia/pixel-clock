package io.github.mikhasrahulia.pixelclock.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import io.github.mikhasrahulia.pixelclock.ui.components.AppIcons
import io.github.mikhasrahulia.pixelclock.ui.theme.PixelClockTheme

class TimerRingingActivity : ComponentActivity() {
    private var timerId by mutableStateOf("")
    private var timerLabel by mutableStateOf("")
    private var timerOrigin by mutableLongStateOf(0L)

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TimerService.ACTION_TIMER_ALERT_CLOSED) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        updateFromIntent(intent)

        ContextCompat.registerReceiver(
            this,
            closeReceiver,
            IntentFilter(TimerService.ACTION_TIMER_ALERT_CLOSED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            val stat = if (timerOrigin > 0L) {
                TimerManager.getInstance(this).hourStat(timerOrigin)
            } else null

            PixelClockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TimerDoneScreen(
                        label = timerLabel,
                        durationLabel = if (timerOrigin > 0L) TimerModel.formatDuration(timerOrigin) else "",
                        statText = if (stat != null && stat.starts >= 2) {
                            stringResource(R.string.timer_hour_stat, stat.completedMinutes, stat.starts)
                        } else null,
                        onSnooze = { sendAction(TimerService.ACTION_ADD_FIVE_MIN) },
                        onStop = { sendAction(TimerService.ACTION_STOP_ALERT) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateFromIntent(intent)
    }

    private fun updateFromIntent(intent: Intent?) {
        timerId = intent?.getStringExtra(TimerService.EXTRA_TIMER_ID).orEmpty()
        timerLabel = intent?.getStringExtra(TimerService.EXTRA_TIMER_LABEL).orEmpty()
        timerOrigin = intent?.getLongExtra(TimerService.EXTRA_TIMER_ORIGIN, 0L) ?: 0L
    }

    private fun sendAction(action: String) {
        sendBroadcast(
            Intent(this, TimerReceiver::class.java).apply {
                this.action = action
                putExtra(TimerService.EXTRA_TIMER_ID, timerId)
            }
        )
        finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closeReceiver) }
        super.onDestroy()
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
    BackHandler { /* A back swipe should not hide the ringing alert. */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Icon(
            imageVector = AppIcons.HourglassBottom,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = label.ifBlank { stringResource(R.string.time_up) },
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
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (statText != null) {
            Spacer(modifier = Modifier.height(14.dp))
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
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(34.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(AppIcons.Stop, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.stop_timer),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        FilledTonalButton(
            onClick = onSnooze,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(29.dp)
        ) {
            Icon(AppIcons.Snooze, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.snooze_five),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
