package com.example.pixelclock.ui.timer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pixelclock.R
import com.example.pixelclock.ui.components.PredictiveBackLayer
import com.example.pixelclock.ui.components.autoFocusModifier
import com.example.pixelclock.data.TimerModel
import com.example.pixelclock.timer.TimerManager

@Composable
fun TimerScreen(
    timerManager: TimerManager,
    modifier: Modifier = Modifier
) {
    val timers by timerManager.timers.collectAsState()
    var isAddingTimer by remember { mutableStateOf(false) }
    var editingTimerLabel by remember { mutableStateOf<TimerModel?>(null) }

    // If there are no active/running timers, show the Google Clock keypad input
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
                    .padding(top = 12.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.tab_timer),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!showKeypad && timers.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { isAddingTimer = true },
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.add_timer), fontWeight = FontWeight.Bold)
                    }
                }
            }

            AnimatedContent(
                targetState = showKeypad,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)))
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
                        onEditLabel = { timer -> editingTimerLabel = timer },
                        timerManager = timerManager
                    )
                }
            }
        }
    }

    // Timer label edit dialog
    editingTimerLabel?.let { timerToEdit ->
        var tempLabel by remember(timerToEdit.id) { mutableStateOf(timerToEdit.label) }

        PredictiveBackLayer(onDismiss = { editingTimerLabel = null }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.label),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
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
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                timerManager.updateLabel(timerToEdit.id, tempLabel)
                                editingTimerLabel = null
                            }
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Google Clock's signature numpad input view with label input and quick chips
 */
@Composable
fun GoogleClockKeypadInput(
    timerManager: TimerManager,
    onStartTimer: (Long, String) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    var digits by remember { mutableStateOf("") }
    var timerLabel by remember { mutableStateOf("") }

    // Parse digits (up to 6 digits: HHMMSS)
    val padded = digits.padStart(6, '0')
    val hours = padded.substring(0, 2).toInt()
    val minutes = padded.substring(2, 4).toInt()
    val seconds = padded.substring(4, 6).toInt()
    val totalSeconds = hours * 3600L + minutes * 60L + seconds
    val hasTime = totalSeconds > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section: Digits readout + Label Input + Quick Chips
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            // Digits Readout (00h 00m 00s)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                TimeSegment(
                    value = padded.substring(0, 2),
                    unit = stringResource(R.string.hours_unit),
                    isActive = hours > 0
                )
                Spacer(modifier = Modifier.width(12.dp))
                TimeSegment(
                    value = padded.substring(2, 4),
                    unit = stringResource(R.string.mins_unit),
                    isActive = hours > 0 || minutes > 0
                )
                Spacer(modifier = Modifier.width(12.dp))
                TimeSegment(
                    value = padded.substring(4, 6),
                    unit = stringResource(R.string.secs_unit),
                    isActive = hours > 0 || minutes > 0 || seconds > 0
                )
            }

            val runs by timerManager.runs.collectAsState()
            val typedStat = remember(runs, totalSeconds) {
                timerManager.hourStat(totalSeconds * 1000L)
            }
            if (typedStat.starts >= 2) {
                Spacer(modifier = Modifier.height(8.dp))
                HourStatChip(typedStat.completedMinutes, typedStat.starts)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Clean single-line optional label field (effortless, no extra buttons or popups!)
            OutlinedTextField(
                value = timerLabel,
                onValueChange = { timerLabel = it },
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(R.string.timer_name_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Label,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(52.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // The ONLY extra button in timer: +5 minutes! Immediately launches or extends timer!
            FilledTonalButton(
                onClick = {
                    val durationToStart = if (hasTime) (totalSeconds + 300L) * 1000L else 300_000L
                    onStartTimer(durationToStart, timerLabel)
                    digits = ""
                    timerLabel = ""
                },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.plus_5_min),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Numpad Grid (3 cols x 4 rows)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable {
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
                            contentAlignment = Alignment.Center
                        ) {
                            if (key == "del") {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Start FAB or Bottom Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (onCancel != null) Arrangement.SpaceBetween else Arrangement.Center
        ) {
            if (onCancel != null) {
                FilledTonalIconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.cancel),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            FloatingActionButton(
                onClick = {
                    if (hasTime) {
                        onStartTimer(totalSeconds * 1000L, timerLabel)
                        digits = ""
                        timerLabel = ""
                    }
                },
                modifier = Modifier.size(76.dp),
                shape = CircleShape,
                containerColor = if (hasTime) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (hasTime) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.resume),
                    modifier = Modifier.size(38.dp)
                )
            }

            if (onCancel != null) {
                Spacer(modifier = Modifier.size(56.dp))
            }
        }
    }
}

@Composable
fun HourStatChip(minutes: Int, starts: Int) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Text(
            text = stringResource(R.string.timer_hour_stat, minutes, starts),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
fun TimeSegment(
    value: String,
    unit: String,
    isActive: Boolean
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
        )
    }
}

/**
 * Running Timers Display
 */
@Composable
fun ActiveTimersList(
    timers: List<TimerModel>,
    timerManager: TimerManager,
    onStart: (String) -> Unit,
    onPause: (String) -> Unit,
    onReset: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddFiveMin: (String) -> Unit,
    onEditLabel: (TimerModel) -> Unit
) {
    if (timers.size == 1) {
        // Single timer view: Large Circular Countdown (Google Clock style)
        val timer = timers.first()
        SingleRunningTimerView(
            timer = timer,
            onStart = { onStart(timer.id) },
            onPause = { onPause(timer.id) },
            onReset = { onReset(timer.id) },
            onDelete = { onDelete(timer.id) },
            onAddFiveMin = { onAddFiveMin(timer.id) },
            onEditLabel = { onEditLabel(timer) },
            timerManager = timerManager
        )
    } else {
        // Multiple timers list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp),
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
                    onEditLabel = { onEditLabel(timer) },
                    timerManager = timerManager
                )
            }
        }
    }
}

@Composable
fun SingleRunningTimerView(
    timer: TimerModel,
    timerManager: TimerManager,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onAddFiveMin: () -> Unit,
    onEditLabel: () -> Unit
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Butter-smooth 120 FPS continuous display frame synchronization (Zero stutter / snap!)
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Large Circular Progress Ring (Fluid 120 FPS!)
        Box(
            modifier = Modifier
                .size(310.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                // Background Track
                drawCircle(
                    color = trackColor,
                    style = Stroke(width = strokeWidth)
                )
                // Smooth Animated Progress Arc (Continuously interpolated every display frame)
                val sweepAngle = 360f * progress
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onEditLabel() }
                    .padding(16.dp)
            ) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = if (timer.label.isNotBlank()) timer.label else stringResource(R.string.timer_name_hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (timer.label.isNotBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Action Controls: +1:00, Play/Pause, Reset, Delete
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val runs by timerManager.runs.collectAsState()
            val stat = remember(runs, timer.originDurationMillis, currentTimeMillis / 60_000L) {
                timerManager.hourStat(timer.originDurationMillis, currentTimeMillis)
            }
            if (stat.starts >= 2) {
                HourStatChip(stat.completedMinutes, stat.starts)
                Spacer(modifier = Modifier.height(12.dp))
            }

            FilledTonalButton(
                onClick = onAddFiveMin,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_five_min),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Reset Button
                FilledTonalIconButton(
                    onClick = if (timer.remainingMillis != timer.initialDurationMillis) onReset else onDelete,
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (timer.remainingMillis != timer.initialDurationMillis) Icons.Rounded.Refresh else Icons.Rounded.Delete,
                        contentDescription = if (timer.remainingMillis != timer.initialDurationMillis) stringResource(R.string.reset) else stringResource(R.string.delete),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Play / Pause Large FAB
                FloatingActionButton(
                    onClick = if (timer.isRunning && !timer.isPaused) onPause else onStart,
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = if (timer.isRunning && !timer.isPaused) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (timer.isRunning) stringResource(R.string.pause) else stringResource(R.string.resume),
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Delete Button
                FilledTonalIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RunningTimerCard(
    timer: TimerModel,
    timerManager: TimerManager,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onAddFiveMin: () -> Unit,
    onEditLabel: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer.isRunning, timer.isPaused, timer.targetEndTimeMillis) {
        while (isActive && timer.isRunning && !timer.isPaused) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEditLabel() }
            ) {
                Text(
                    text = timer.formattedTime(if (timer.isRunning && !timer.isPaused) now else System.currentTimeMillis()),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = if (timer.label.isNotBlank()) timer.label else stringResource(R.string.timer_name_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (timer.label.isNotBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                val runs by timerManager.runs.collectAsState()
                val stat = remember(runs, timer.originDurationMillis) {
                    timerManager.hourStat(timer.originDurationMillis)
                }
                if (stat.starts >= 2) {
                    Spacer(modifier = Modifier.height(6.dp))
                    HourStatChip(stat.completedMinutes, stat.starts)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // +5:00
                FilledTonalButton(
                    onClick = onAddFiveMin,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.add_five_min), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play / Pause
                IconButton(
                    onClick = if (timer.isRunning && !timer.isPaused) onPause else onStart
                ) {
                    Icon(
                        imageVector = if (timer.isRunning && !timer.isPaused) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Delete
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
