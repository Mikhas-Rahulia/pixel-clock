package io.github.mikhasrahulia.pixelclock.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.mikhasrahulia.pixelclock.R
import io.github.mikhasrahulia.pixelclock.data.TimerModel
import io.github.mikhasrahulia.pixelclock.ui.components.AppIcons
import io.github.mikhasrahulia.pixelclock.ui.components.ScallopedShape
import io.github.mikhasrahulia.pixelclock.ui.theme.PixelClockTheme

private val TimerDoneBadgeShape = ScallopedShape(lobes = 10, depthFraction = 0.085f)

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
    val view = LocalView.current

    val infiniteTransition = rememberInfiniteTransition(label = "timerDone")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Box(
            modifier = Modifier.size(112.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(pulseScale)
                    .rotate(rotation)
                    .clip(TimerDoneBadgeShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
            )
            Icon(
                imageVector = AppIcons.HourglassBottom,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(46.dp)
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = label.ifBlank { stringResource(R.string.time_up) },
            style = MaterialTheme.typography.headlineMedium,
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
                    fontSize = 78.sp,
                    lineHeight = 82.sp,
                    letterSpacing = (-2).sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (statText != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = statText,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onStop()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(36.dp),
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
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onSnooze()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
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
