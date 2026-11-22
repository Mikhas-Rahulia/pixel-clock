package io.github.mikhasrahulia.pixelclock.ui.timer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mikhasrahulia.pixelclock.ui.components.AppIcons
import kotlinx.coroutines.delay
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mikhasrahulia.pixelclock.R
import io.github.mikhasrahulia.pixelclock.data.TimerModel
import io.github.mikhasrahulia.pixelclock.timer.TimerManager
import io.github.mikhasrahulia.pixelclock.ui.components.PredictiveBackLayer
import io.github.mikhasrahulia.pixelclock.ui.components.autoFocusModifier
import kotlinx.coroutines.isActive
import java.util.Locale

@Composable
fun TimerScreen(
    timerManager: TimerManager,
    modifier: Modifier = Modifier
) {
    val timers by timerManager.timers.collectAsStateWithLifecycle()
    var isAddingTimer by remember { mutableStateOf(false) }
    var editingTimerLabel by remember { mutableStateOf<TimerModel?>(null) }

    val showKeypad = timers.isEmpty() || isAddingTimer
    BackHandler(enabled = isAddingTimer && timers.isNotEmpty()) {
        isAddingTimer = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.tab_timer),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            AnimatedContent(
                targetState = showKeypad,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)))
                        .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessMedium)))
                },
                label = "timerContent"
            ) { keypad ->
                if (keypad) {
                    GoogleClockKeypadInput(
                        timerManager = timerManager,
                        onStartTimer = { durationMillis, label ->
                            timerManager.createAndStartTimer(durationMillis, label)
                            isAddingTimer = false
                        },
                        onCancel = if (timers.isNotEmpty()) {
                            { isAddingTimer = false }
                        } else null
                    )
                } else {
                    ActiveTimersList(
                        timers = timers,
                        onStart = { timerManager.startTimer(it) },
                        onPause = { timerManager.pauseTimer(it) },
                        onReset = { timerManager.resetTimer(it) },
                        onDelete = { timerManager.deleteTimer(it) },
                        onAddFiveMin = { timerManager.addFiveMinutes(it) },
                        onEditLabel = { timer -> editingTimerLabel = timer }
                    )
                }
            }
        }

        // FAB + when active timers are running and keypad is not shown
        if (!showKeypad && timers.isNotEmpty()) {
            FloatingActionButton(
                onClick = { isAddingTimer = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
                    .size(64.dp),
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = AppIcons.Add,
                    contentDescription = stringResource(R.string.add_timer),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }

    editingTimerLabel?.let { timerToEdit ->
        var tempLabel by remember(timerToEdit.id) { mutableStateOf(timerToEdit.label) }
        val colorScheme = MaterialTheme.colorScheme

        PredictiveBackLayer(onDismiss = { editingTimerLabel = null }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.label),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = tempLabel,
                        onValueChange = { tempLabel = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.timer_name_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(autoFocusModifier()),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { editingTimerLabel = null }) {
                            Text(stringResource(R.string.cancel), color = colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    timerManager.updateLabel(timerToEdit.id, tempLabel)
                                    editingTimerLabel = null
                                }
                        ) {
                            Text(
                                stringResource(R.string.save),
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Google Clock's M3E keypad input view (Screenshot 2).
 * Restriction: ONLY 5 minutes preset chip at start.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GoogleClockKeypadInput(
    timerManager: TimerManager,
    onStartTimer: (Long, String) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var digits by remember { mutableStateOf("") }
    var timerLabel by remember { mutableStateOf("") }

    val padded = digits.padStart(6, '0')
    val hours = padded.substring(0, 2).toInt()
    val minutes = padded.substring(2, 4).toInt()
    val seconds = padded.substring(4, 6).toInt()
    val totalSeconds = hours * 3600L + minutes * 60L + seconds
    val hasTime = totalSeconds > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Digits Readout (00  00  00) with labels below
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                // Segment 1: Hours
                TimeSegmentColumn(
                    value = padded.substring(0, 2),
                    label = stringResource(R.string.hours_label),
                    hasInput = digits.length >= 5
                )
                Spacer(modifier = Modifier.width(20.dp))
                // Segment 2: Minutes
                TimeSegmentColumn(
                    value = padded.substring(2, 4),
                    label = stringResource(R.string.mins_label),
                    hasInput = digits.length >= 3
                )
                Spacer(modifier = Modifier.width(20.dp))
                // Segment 3: Seconds
                TimeSegmentColumn(
                    value = padded.substring(4, 6),
                    label = stringResource(R.string.secs_label),
                    hasInput = digits.isNotEmpty()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            val colorScheme = MaterialTheme.colorScheme
            // Preset Chip: 5 minutes quick-start
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        onStartTimer(300_000L, timerLabel.ifBlank { context.getString(R.string.timer_for_five_min) })
                    }
            ) {
                Text(
                    text = "5 min",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Start pill button
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (hasTime) colorScheme.primary else colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(enabled = hasTime) {
                        if (hasTime) {
                            onStartTimer(totalSeconds * 1000L, timerLabel)
                            digits = ""
                            timerLabel = ""
                        }
                    }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.timer_start),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (hasTime) colorScheme.onPrimary else colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        val numpadScheme = MaterialTheme.colorScheme
        // Tactile tonal Numpad Grid matching Expressive Material 3 style
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("00", "0", "del")
            )

            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { key ->
                        val isDel = key == "del"
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isDel) numpadScheme.surfaceContainerHigh else numpadScheme.surfaceContainer,
                            modifier = Modifier
                                .weight(1f)
                                .height(62.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .combinedClickable(
                                    onClick = {
                                        when (key) {
                                            "del" -> {
                                                if (digits.isNotEmpty()) {
                                                    digits = digits.dropLast(1)
                                                }
                                            }
                                            "00" -> {
                                                if (digits.isNotEmpty() && digits.length < 5) {
                                                    digits += "00"
                                                } else if (digits.length == 5) {
                                                    digits += "0"
                                                }
                                            }
                                            else -> {
                                                if (digits.length < 6) {
                                                    if (digits.isNotEmpty() || key != "0") {
                                                        digits += key
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (key == "del") {
                                            digits = ""
                                        }
                                    }
                                )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDel) {
                                    Icon(
                                        imageVector = AppIcons.Backspace,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = numpadScheme.onSurface,
                                        modifier = Modifier.size(26.dp)
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontSize = 30.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = numpadScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (onCancel != null) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun TimeSegmentColumn(
    value: String,
    label: String,
    hasInput: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (hasInput) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Running Timers Display (Screenshot 3).
 * Restriction: ONLY 5 minutes (+5:00) button at end.
 */
@Composable
fun ActiveTimersList(
    timers: List<TimerModel>,
    onStart: (String) -> Unit,
    onPause: (String) -> Unit,
    onReset: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddFiveMin: (String) -> Unit,
    onEditLabel: (TimerModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(timers, key = { it.id }) { timer ->
            RunningTimerCard(
                timer = timer,
                onStart = { onStart(timer.id) },
                onPause = { onPause(timer.id) },
                onReset = { onReset(timer.id) },
                onDelete = { onDelete(timer.id) },
                onAddFiveMin = { onAddFiveMin(timer.id) },
                onEditLabel = { onEditLabel(timer) }
            )
        }
    }
}

@Composable
fun RunningTimerCard(
    timer: TimerModel,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onAddFiveMin: () -> Unit,
    onEditLabel: () -> Unit
) {
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var nowWall by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // LTPO-friendly adaptive refresh: 1Hz on timers >2m (lets Pixel 7 Pro drop to 10Hz),
    // 10Hz on medium timers, 30Hz in final 10s. Zero 120Hz busy-loop battery drain.
    LaunchedEffect(timer.isRunning, timer.isPaused, timer.targetElapsedRealtimeMillis, timer.targetEndTimeMillis) {
        if (timer.isRunning && !timer.isPaused) {
            while (isActive) {
                val e = SystemClock.elapsedRealtime()
                val w = System.currentTimeMillis()
                nowElapsed = e
                nowWall = w
                val rem = timer.calculateCurrentRemaining(w, e)
                if (rem <= 0L) break
                val stepMs = when {
                    rem <= 10_000L -> 33L
                    timer.initialDurationMillis <= 120_000L -> 100L
                    else -> ((rem % 1000L).takeIf { it > 20L } ?: 1000L)
                }
                delay(stepMs)
            }
        }
    }

    val currentRemaining = timer.calculateCurrentRemaining(nowWall, nowElapsed)
    val progress = if (timer.initialDurationMillis > 0) {
        (currentRemaining.toFloat() / timer.initialDurationMillis.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val isActiveRunning = timer.isRunning && !timer.isPaused
    val colorScheme = MaterialTheme.colorScheme
    val timerTitle = if (timer.label.isNotBlank()) timer.label else stringResource(R.string.tab_timer)

    val cardBg = if (isActiveRunning) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainerLow
    val trackColor = colorScheme.surfaceContainerHighest
    val arcColor = if (isActiveRunning) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val timeTextColor = if (isActiveRunning) colorScheme.onSurface else colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Row inside card: Label + Clear (X) button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onEditLabel() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = timerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = AppIcons.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Center: Balanced Circular Progress Ring
            Box(
                modifier = Modifier
                    .size(236.dp)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 10.dp.toPx()
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = strokeWidth)
                    )
                    val sweepAngle = 360f * progress
                    drawArc(
                        color = arcColor,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                val totalSecs = (currentRemaining.coerceAtLeast(0L) + 999) / 1000
                val hours = totalSecs / 3600
                val minutes = (totalSecs % 3600) / 60
                val seconds = totalSecs % 60

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (hours > 0) {
                        Text(
                            text = String.format(Locale.getDefault(), "%02d", hours),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-1).sp
                            ),
                            color = timeTextColor
                        )
                    }
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = if (hours > 0) 52.sp else 64.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        ),
                        color = timeTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Unified Bottom Action Row: Play/Pause primary pill, +5:00 tonal pill, Reset circular button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.primary,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            if (isActiveRunning) onPause() else onStart()
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isActiveRunning) AppIcons.Pause else AppIcons.PlayArrow,
                            contentDescription = if (isActiveRunning) stringResource(R.string.pause) else stringResource(R.string.resume),
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(if (isActiveRunning) R.string.pause else R.string.resume),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .weight(0.8f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onAddFiveMin() }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+5:00",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHighest)
                        .clickable { onReset() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Refresh,
                        contentDescription = stringResource(R.string.reset),
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
