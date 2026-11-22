package io.github.mikhasrahulia.pixelclock.alarm

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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.mikhasrahulia.pixelclock.R
import io.github.mikhasrahulia.pixelclock.ui.components.AppIcons
import io.github.mikhasrahulia.pixelclock.ui.theme.PixelClockTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmRingingActivity : ComponentActivity() {
    private var alarmIdState by mutableStateOf("")
    private var alarmLabelState by mutableStateOf("")

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmScheduler.ACTION_ALARM_DISMISSED) {
                finish()
            }
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
            dismissReceiver,
            IntentFilter(AlarmScheduler.ACTION_ALARM_DISMISSED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            PixelClockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlarmRingingScreen(
                        label = alarmLabelState,
                        onDismiss = {
                            val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
                                action = AlarmScheduler.ACTION_ALARM_DISMISS
                                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmIdState)
                            }
                            sendBroadcast(dismissIntent)
                            finish()
                        },
                        onSnooze = {
                            val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
                                action = AlarmScheduler.ACTION_ALARM_SNOOZE
                                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmIdState)
                                putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, alarmLabelState)
                            }
                            sendBroadcast(snoozeIntent)
                            finish()
                        }
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
        alarmIdState = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID).orEmpty()
        alarmLabelState = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL).orEmpty()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dismissReceiver) }
        super.onDestroy()
    }
}

@Composable
private fun AlarmRingingScreen(
    label: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    BackHandler { /* Prevent back gesture from hiding UI while alarm keeps ringing */ }

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1000L)
        }
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
    val secondsFormatter = remember { DateTimeFormatter.ofPattern(":ss", Locale.getDefault()) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .scale(pulseScale)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = label.ifBlank { stringResource(R.string.tab_alarm) },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentTime.format(timeFormatter),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = currentTime.format(secondsFormatter),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                shape = RoundedCornerShape(34.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.dismiss),
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
                Icon(
                    imageVector = AppIcons.Snooze,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.snooze),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
