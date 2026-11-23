package io.github.mikhasrahulia.pixelclock

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mikhasrahulia.pixelclock.data.AlarmModel
import io.github.mikhasrahulia.pixelclock.data.TimerModel
import io.github.mikhasrahulia.pixelclock.timer.TimerManager
import io.github.mikhasrahulia.pixelclock.ui.alarm.AlarmEditorSheetContent
import io.github.mikhasrahulia.pixelclock.ui.alarm.ExpressiveAlarmCard
import io.github.mikhasrahulia.pixelclock.ui.components.AppIcons
import io.github.mikhasrahulia.pixelclock.ui.components.AppTab
import io.github.mikhasrahulia.pixelclock.ui.components.ExpressiveNavBar
import io.github.mikhasrahulia.pixelclock.ui.theme.PixelClockTheme
import io.github.mikhasrahulia.pixelclock.ui.timer.ExpressiveTimerKeypad
import io.github.mikhasrahulia.pixelclock.ui.timer.RunningTimerCard
import java.time.LocalDate

/**
 * Debug-only showcase activity for capturing clean documentation screenshots
 * using static in-memory demo data (never reads or writes real user alarms).
 */
class DemoShowcaseActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val scene = intent.getStringExtra("scene") ?: "alarms"
        val today = LocalDate.now()
        val parity = AlarmModel.currentWeekParity(today)

        val demoAlarms = listOf(
            AlarmModel(
                id = "demo-1",
                hour = 7,
                minute = 0,
                label = "Lecture · Group A",
                isEnabled = true,
                daysOfWeek = setOf(1, 3, 5),
                isBiWeekly = true,
                weekParity = parity
            ),
            AlarmModel(
                id = "demo-2",
                hour = 8,
                minute = 30,
                label = "Morning standup",
                isEnabled = true,
                daysOfWeek = setOf(1, 2, 3, 4, 5),
                pauseFrom = today.plusDays(2).toString(),
                pauseUntil = today.plusDays(5).toString()
            ),
            AlarmModel(
                id = "demo-3",
                hour = 10,
                minute = 15,
                label = "Weekend coffee",
                isEnabled = false,
                daysOfWeek = setOf(6, 7)
            )
        )

        setContent {
            PixelClockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (scene) {
                        "alarms" -> DemoAlarmsScene(demoAlarms, selectedId = null)
                        "editor" -> DemoEditorScene(demoAlarms)
                        "timer_keypad" -> DemoTimerKeypadScene()
                        "timer_running" -> DemoTimerRunningScene()
                        else -> DemoAlarmsScene(demoAlarms, selectedId = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoAlarmsScene(alarms: List<AlarmModel>, selectedId: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 6.dp, start = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tab_alarm),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = AppIcons.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "07:00 · 4h 52m",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 112.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        ExpressiveAlarmCard(
                            alarm = alarm,
                            minuteTick = 1L,
                            isSelected = alarm.id == selectedId,
                            onClick = {},
                            onToggleEnabled = {}
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 16.dp)
                    .size(64.dp),
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(30.dp))
            }
        }

        ExpressiveNavBar(
            selectedTab = AppTab.ALARM,
            onTabSelected = {},
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoEditorScene(alarms: List<AlarmModel>) {
    Box(modifier = Modifier.fillMaxSize()) {
        DemoAlarmsScene(alarms, selectedId = alarms.first().id)
        Surface(
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxSize()
        ) {}
        Surface(
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                AlarmEditorSheetContent(
                    alarm = alarms.first(),
                    minuteTick = 1L,
                    onEditTime = {},
                    onUpdateAlarm = {},
                    onUpdateLabelOnly = {},
                    onShiftCycle = {},
                    onPickDate = {},
                    onPickPause = {},
                    onDelete = {},
                    onDone = {}
                )
            }
        }
    }
}

@Composable
private fun DemoTimerKeypadScene() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.tab_timer),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp, start = 4.dp)
            )
            ExpressiveTimerKeypad(
                onStartTimer = { _, _ -> },
                onCancel = null
            )
        }
        ExpressiveNavBar(
            selectedTab = AppTab.TIMER,
            onTabSelected = {},
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun DemoTimerRunningScene() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember { TimerManager.getInstance(context) }
    val nowWall = remember { System.currentTimeMillis() }
    val nowElapsed = remember { SystemClock.elapsedRealtime() }
    val demoTimer = remember {
        TimerModel(
            id = "demo-timer-1",
            initialDurationMillis = 15 * 60_000L,
            originDurationMillis = 15 * 60_000L,
            remainingMillis = 10 * 60_000L + 42_000L,
            isRunning = true,
            isPaused = false,
            label = "Focus session",
            targetEndTimeMillis = nowWall + 10 * 60_000L + 42_000L,
            targetElapsedRealtimeMillis = nowElapsed + 10 * 60_000L + 42_000L
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.tab_timer),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp, bottom = 10.dp, start = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                RunningTimerCard(
                    timer = demoTimer,
                    timerManager = manager,
                    onStart = {},
                    onPause = {},
                    onReset = {},
                    onDelete = {},
                    onAddOneMin = {},
                    onAddFiveMin = {},
                    onEditLabel = {}
                )
            }
            FloatingActionButton(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 16.dp)
                    .size(64.dp),
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(30.dp))
            }
        }
        ExpressiveNavBar(
            selectedTab = AppTab.TIMER,
            onTabSelected = {},
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        )
    }
}
