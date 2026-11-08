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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
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

@Composable
fun TimerScreen(
    timerManager: TimerManager,
    modifier: Modifier = Modifier
) {
    val timers by timerManager.timers.collectAsState()
    var isAddingTimer by remember { mutableStateOf(false) }
    var editingTimerLabel by remember { mutableStateOf<TimerModel?>(null) }

    val showKeypad = timers.isEmpty() || isAddingTimer

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
                containerColor = Color(0xFF35343C),
                contentColor = Color(0xFFE3E1E9)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.add_timer),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }

    editingTimerLabel?.let { timerToEdit ->
        var tempLabel by remember(timerToEdit.id) { mutableStateOf(timerToEdit.label) }

        PredictiveBackLayer(onDismiss = { editingTimerLabel = null }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF1E1E24),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.label),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE3E1E9)
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
                            Text(stringResource(R.string.cancel), color = Color(0xFFE3E1E9))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFFFFFFFF),
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
                                color = Color(0xFF191C20),
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

            // Preset Chip: ONLY 5 minutes at start! (Constraint satisfied)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF25242C),
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
                    color = Color(0xFFE3E1E9),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Big Start pill button: Rozpocznij
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (hasTime) Color(0xFF35343C) else Color(0xFF1E1D24),
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
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasTime) Color(0xFFE3E1E9) else Color(0xFF5A5862)
                    )
                }
            }
        }

        // Clean Numpad Grid (No circular backgrounds!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(width = 80.dp, height = 56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
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
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (key == "del") {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = Color(0xFFE3E1E9),
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = Color(0xFFE3E1E9)
                                )
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
                    color = Color(0xFF8E8D96)
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 58.sp,
                fontWeight = FontWeight.Normal
            ),
            color = if (hasInput) Color(0xFFFFFFFF) else Color(0xFF52515A)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF8E8D96)
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
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Butter-smooth 120 FPS continuous display frame synchronization
    LaunchedEffect(timer.isRunning, timer.isPaused, timer.targetEndTimeMillis) {
        if (timer.isRunning && !timer.isPaused && timer.targetEndTimeMillis > 0) {
            while (isActive) {
                withFrameMillis {
                    currentTimeMillis = System.currentTimeMillis()
                }
            }
        }
    }

    val currentRemaining = if (timer.isRunning && !timer.isPaused && timer.targetEndTimeMillis > 0) {
        maxOf(0L, timer.targetEndTimeMillis - currentTimeMillis)
    } else {
        timer.remainingMillis
    }

    val progress = if (timer.initialDurationMillis > 0) {
        (currentRemaining.toFloat() / timer.initialDurationMillis.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val formattedTime = timer.formattedTime(if (timer.isRunning && !timer.isPaused) currentTimeMillis else System.currentTimeMillis())
    val timerTitle = if (timer.label.isNotBlank()) timer.label else stringResource(R.string.timer_for_five_min)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1E1E24),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
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
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEditLabel() }
                        .padding(4.dp)
                ) {
                    Text(
                        text = timerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE3E1E9)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF8E8D96)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.cancel),
                        tint = Color(0xFFC6C5D0),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Center: Circular Progress Ring (Screenshot 3)
            Box(
                modifier = Modifier
                    .size(290.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 12.dp.toPx()
                    // Track
                    drawCircle(
                        color = Color(0xFF383740),
                        style = Stroke(width = strokeWidth)
                    )
                    // Progress arc
                    val sweepAngle = 360f * progress
                    drawArc(
                        color = Color(0xFFFFFFFF),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 68.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        ),
                        color = Color(0xFFFFFFFF)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    IconButton(
                        onClick = if (timer.isRunning && !timer.isPaused) onPause else onStart,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = if (timer.isRunning && !timer.isPaused) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (timer.isRunning) stringResource(R.string.pause) else stringResource(R.string.resume),
                            tint = Color(0xFFFFFFFF),
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom row inside card: +5:00 pill button on left, Reset circular button on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wide pill button: +5:00 (Constraint satisfied)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF2C2B33),
                    modifier = Modifier
                        .weight(1f)
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
                            color = Color(0xFFE3E1E9)
                        )
                    }
                }

                // Reset button: Circular Refresh icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2B33))
                        .clickable { onReset() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.reset),
                        tint = Color(0xFFE3E1E9),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
