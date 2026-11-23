package io.github.mikhasrahulia.pixelclock.ui.timer

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mikhasrahulia.pixelclock.R
import io.github.mikhasrahulia.pixelclock.data.TimerModel
import io.github.mikhasrahulia.pixelclock.timer.TimerManager
import io.github.mikhasrahulia.pixelclock.ui.components.AppIcons
import io.github.mikhasrahulia.pixelclock.ui.components.PredictiveBackLayer
import io.github.mikhasrahulia.pixelclock.ui.components.autoFocusModifier
import io.github.mikhasrahulia.pixelclock.ui.theme.ExpressiveMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun TimerScreen(
    timerManager: TimerManager,
    modifier: Modifier = Modifier
) {
    val timers by timerManager.timers.collectAsStateWithLifecycle()
    val view = LocalView.current

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.tab_timer),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            AnimatedContent(
                targetState = showKeypad,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    (fadeIn(ExpressiveMotion.fastEffects()) +
                        scaleIn(
                            initialScale = 0.95f,
                            animationSpec = ExpressiveMotion.defaultSpatial()
                        ))
                        .togetherWith(fadeOut(ExpressiveMotion.fastEffects()))
                },
                label = "timerContent"
            ) { keypad ->
                if (keypad) {
                    ExpressiveTimerKeypad(
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
                        timerManager = timerManager,
                        onStart = { timerManager.startTimer(it) },
                        onPause = { timerManager.pauseTimer(it) },
                        onReset = { timerManager.resetTimer(it) },
                        onDelete = { timerManager.deleteTimer(it) },
                        onAddOneMin = { timerManager.addOneMinute(it) },
                        onAddFiveMin = { timerManager.addFiveMinutes(it) },
                        onEditLabel = { timer -> editingTimerLabel = timer }
                    )
                }
            }
        }

        if (!showKeypad && timers.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    isAddingTimer = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 16.dp)
                    .size(64.dp),
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = AppIcons.Add,
                    contentDescription = stringResource(R.string.add_timer),
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }

    editingTimerLabel?.let { timerToEdit ->
        var tempLabel by remember(timerToEdit.id) { mutableStateOf(timerToEdit.label) }
        val colorScheme = MaterialTheme.colorScheme

        PredictiveBackLayer(onDismiss = { editingTimerLabel = null }) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.label),
                        style = MaterialTheme.typography.titleLarge,
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
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { editingTimerLabel = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                timerManager.updateLabel(timerToEdit.id, tempLabel)
                                editingTimerLabel = null
                            },
                            shape = RoundedCornerShape(20.dp)
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
 * Material 3 Expressive Timer Keypad:
 * Monumental tabular readout, quick-start preset pills, tactile pill keys with Pixel haptics,
 * and a vibrant animated Start CTA.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveTimerKeypad(
    onStartTimer: (Long, String) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    var digits by remember { mutableStateOf("") }

    val padded = digits.padStart(6, '0')
    val hours = padded.substring(0, 2).toInt()
    val minutes = padded.substring(2, 4).toInt()
    val seconds = padded.substring(4, 6).toInt()
    val totalSeconds = hours * 3600L + minutes * 60L + seconds
    val hasTime = totalSeconds > 0

    val startBg by animateColorAsState(
        targetValue = if (hasTime) colorScheme.primary else colorScheme.surfaceContainerHigh,
        animationSpec = ExpressiveMotion.fastEffects(),
        label = "startBg"
    )
    val startFg by animateColorAsState(
        targetValue = if (hasTime) colorScheme.onPrimary else colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        animationSpec = ExpressiveMotion.fastEffects(),
        label = "startFg"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Tabular 00 : 00 : 00 Readout + Quick Presets
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center
            ) {
                TimeSegmentColumn(
                    value = padded.substring(0, 2),
                    label = stringResource(R.string.hours_label),
                    hasInput = digits.length >= 5
                )
                TimeColonSeparator(active = digits.length >= 5)
                TimeSegmentColumn(
                    value = padded.substring(2, 4),
                    label = stringResource(R.string.mins_label),
                    hasInput = digits.length >= 3
                )
                TimeColonSeparator(active = digits.length >= 3)
                TimeSegmentColumn(
                    value = padded.substring(4, 6),
                    label = stringResource(R.string.secs_label),
                    hasInput = digits.isNotEmpty()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Preset Chips (1, 5, 10, 15 min)
            val minsUnit = stringResource(R.string.mins_unit)
            val presets = listOf(1, 5, 10, 15)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presets.forEach { mins ->
                    val isFeatured = mins == 5
                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            val label = if (mins == 5) {
                                context.getString(R.string.timer_for_five_min)
                            } else {
                                "$mins $minsUnit"
                            }
                            onStartTimer(mins * 60_000L, label)
                        },
                        shape = RoundedCornerShape(50),
                        color = if (isFeatured) colorScheme.secondaryContainer else colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "$mins $minsUnit",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isFeatured) colorScheme.onSecondaryContainer else colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // 2. Expressive Pill Numpad Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { key ->
                        val isDel = key == "del"
                        val keyBg = if (isDel) colorScheme.tertiaryContainer else colorScheme.surfaceContainerHigh
                        val keyFg = if (isDel) colorScheme.onTertiaryContainer else colorScheme.onSurface

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = keyBg,
                            modifier = Modifier
                                .weight(1f)
                                .height(62.dp)
                                .clip(RoundedCornerShape(50))
                                .combinedClickable(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        when (key) {
                                            "del" -> if (digits.isNotEmpty()) digits = digits.dropLast(1)
                                            "00" -> {
                                                if (digits.isNotEmpty() && digits.length < 5) digits += "00"
                                                else if (digits.length == 5) digits += "0"
                                            }
                                            else -> {
                                                if (digits.length < 6 && (digits.isNotEmpty() || key != "0")) {
                                                    digits += key
                                                }
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (key == "del" && digits.isNotEmpty()) {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            digits = ""
                                        }
                                    }
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isDel) {
                                    Icon(
                                        imageVector = AppIcons.Backspace,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = keyFg,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = keyFg
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Expressive Primary Start CTA + Optional Cancel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                onClick = {
                    if (hasTime) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onStartTimer(totalSeconds * 1000L, "")
                        digits = ""
                    }
                },
                enabled = hasTime,
                shape = RoundedCornerShape(28.dp),
                color = startBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = AppIcons.PlayArrow,
                        contentDescription = null,
                        tint = startFg,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.timer_start),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = startFg
                    )
                }
            }

            if (onCancel != null) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeColonSeparator(active: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    Text(
        text = ":",
        style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp),
        color = if (active) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

@Composable
fun TimeSegmentColumn(
    value: String,
    label: String,
    hasInput: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val digitColor by animateColorAsState(
        targetValue = if (hasInput) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.34f),
        animationSpec = ExpressiveMotion.fastEffects(),
        label = "segmentDigitColor"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 54.sp,
                lineHeight = 58.sp
            ),
            color = digitColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ActiveTimersList(
    timers: List<TimerModel>,
    timerManager: TimerManager,
    onStart: (String) -> Unit,
    onPause: (String) -> Unit,
    onReset: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddOneMin: (String) -> Unit,
    onAddFiveMin: (String) -> Unit,
    onEditLabel: (TimerModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 104.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(timers, key = { it.id }) { timer ->
            RunningTimerCard(
                timer = timer,
                timerManager = timerManager,
                onStart = { onStart(timer.id) },
                onPause = { onPause(timer.id) },
                onReset = { onReset(timer.id) },
                onDelete = { onDelete(timer.id) },
                onAddOneMin = { onAddOneMin(timer.id) },
                onAddFiveMin = { onAddFiveMin(timer.id) },
                onEditLabel = { onEditLabel(timer) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

/**
 * Material 3 Expressive Active Timer Card:
 * - Animated wavy progress ring when running that smoothly flattens to a crisp circle when paused.
 * - Shape-morphing Play/Pause control (rounded-rectangle when running <-> full pill when paused).
 * - Quick +1:00 and +5:00 tonal pills + circular Reset action.
 */
@Composable
fun RunningTimerCard(
    timer: TimerModel,
    timerManager: TimerManager,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onAddOneMin: () -> Unit,
    onAddFiveMin: () -> Unit,
    onEditLabel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var nowWall by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var wavePhase by remember { mutableFloatStateOf(0f) }

    val isActiveRunning = timer.isRunning && !timer.isPaused

    // Adaptive refresh: smooth wave motion while active on screen, zero work when paused.
    LaunchedEffect(timer.isRunning, timer.isPaused, timer.targetElapsedRealtimeMillis, timer.targetEndTimeMillis) {
        if (timer.isRunning && !timer.isPaused) {
            var lastTick = SystemClock.elapsedRealtime()
            while (isActive) {
                val e = SystemClock.elapsedRealtime()
                val w = System.currentTimeMillis()
                val dt = ((e - lastTick).coerceIn(0L, 200L)) / 1000f
                lastTick = e
                nowElapsed = e
                nowWall = w
                wavePhase = (wavePhase + dt * 2.2f) % (2f * PI.toFloat())
                val rem = timer.calculateCurrentRemaining(w, e)
                if (rem <= 0L) break
                val stepMs = when {
                    rem <= 10_000L -> 33L
                    timer.initialDurationMillis <= 300_000L -> 50L
                    else -> 80L
                }
                delay(stepMs)
            }
        }
    }

    val currentRemaining = timer.calculateCurrentRemaining(nowWall, nowElapsed)
    val rawProgress = if (timer.initialDurationMillis > 0) {
        (currentRemaining.toFloat() / timer.initialDurationMillis.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedWaveAmplitude by animateFloatAsState(
        targetValue = if (isActiveRunning && rawProgress > 0.02f) 1f else 0f,
        animationSpec = ExpressiveMotion.defaultSpatial(),
        label = "waveAmp"
    )
    val cardBg by animateColorAsState(
        targetValue = if (isActiveRunning) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainerLow,
        animationSpec = ExpressiveMotion.fastEffects(),
        label = "timerCardBg"
    )
    val cardBorder by animateColorAsState(
        targetValue = if (isActiveRunning) colorScheme.primary.copy(alpha = 0.24f) else colorScheme.outlineVariant.copy(alpha = 0.2f),
        animationSpec = ExpressiveMotion.fastEffects(),
        label = "timerCardBorder"
    )
    val arcColor by animateColorAsState(
        targetValue = if (isActiveRunning) colorScheme.primary else colorScheme.outline,
        animationSpec = ExpressiveMotion.fastEffects(),
        label = "timerArcColor"
    )
    val playPauseCorner by animateDpAsState(
        targetValue = if (isActiveRunning) 18.dp else 28.dp,
        animationSpec = ExpressiveMotion.fastSpatial(),
        label = "playPauseCorner"
    )
    val playPauseBg by animateColorAsState(
        targetValue = if (isActiveRunning) colorScheme.primaryContainer else colorScheme.primary,
        animationSpec = ExpressiveMotion.fastEffects(),
        label = "playPauseBg"
    )
    val playPauseFg by animateColorAsState(
        targetValue = if (isActiveRunning) colorScheme.onPrimaryContainer else colorScheme.onPrimary,
        animationSpec = ExpressiveMotion.fastEffects(),
        label = "playPauseFg"
    )

    val timerTitle = timer.label.ifBlank { stringResource(R.string.tab_timer) }
    val trackColor = colorScheme.surfaceContainerHighest
    val timeTextColor = if (isActiveRunning) colorScheme.onSurface else colorScheme.onSurfaceVariant

    val hourStat = remember(timer.originDurationMillis, timer.isRunning, timer.isPaused) {
        if (timer.originDurationMillis > 0L) timerManager.hourStat(timer.originDurationMillis) else null
    }
    val statText = if (hourStat != null && hourStat.starts >= 2) {
        stringResource(R.string.timer_hour_stat, hourStat.completedMinutes, hourStat.starts)
    } else null

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header row: editable label pill + delete icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onEditLabel,
                    shape = RoundedCornerShape(50),
                    color = colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = timerTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurface
                        )
                        Icon(
                            imageVector = AppIcons.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        onDelete()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = stringResource(R.string.delete),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expressive Wavy Circular Countdown Ring
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                val wavePath = remember { Path() }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 9.dp.toPx()
                    val maxWaveAmp = 3.2.dp.toPx() * animatedWaveAmplitude
                    val radius = size.minDimension / 2f - strokeWidth / 2f - 4.dp.toPx()
                    val cx = center.x
                    val cy = center.y

                    // Track circle
                    drawCircle(
                        color = trackColor,
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )

                    // Expressive wavy progress arc
                    if (rawProgress > 0.002f) {
                        val sweepRad = (2.0 * PI * rawProgress)
                        val startRad = -PI / 2.0
                        val waveCycles = 14.0
                        val steps = max(24, (140 * rawProgress).toInt())
                        wavePath.reset()
                        for (i in 0..steps) {
                            val frac = i.toDouble() / steps.toDouble()
                            val angle = startRad + sweepRad * frac
                            // Softly taper wave amplitude at both arc endpoints for clean round caps
                            val edgeTaper = sin(PI * frac).toFloat().coerceIn(0f, 1f)
                            val r = radius + maxWaveAmp * edgeTaper * sin(waveCycles * angle - wavePhase).toFloat()
                            val x = cx + r * cos(angle).toFloat()
                            val y = cy + r * sin(angle).toFloat()
                            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                        }
                        drawPath(
                            path = wavePath,
                            color = arcColor,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
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
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp),
                            color = timeTextColor.copy(alpha = 0.85f)
                        )
                    }
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = if (hours > 0) 48.sp else 58.sp,
                            lineHeight = if (hours > 0) 52.sp else 62.sp
                        ),
                        color = timeTextColor
                    )
                    if (statText != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = statText,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Expressive Controls Row: Morphing Play/Pause + (+1:00) + (+5:00) + Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        if (isActiveRunning) onPause() else onStart()
                    },
                    shape = RoundedCornerShape(playPauseCorner),
                    color = playPauseBg,
                    modifier = Modifier
                        .weight(1.35f)
                        .height(54.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isActiveRunning) AppIcons.Pause else AppIcons.PlayArrow,
                            contentDescription = stringResource(if (isActiveRunning) R.string.pause else R.string.resume),
                            tint = playPauseFg,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(if (isActiveRunning) R.string.pause else R.string.resume),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = playPauseFg
                        )
                    }
                }

                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onAddOneMin()
                    },
                    shape = RoundedCornerShape(50),
                    color = colorScheme.secondaryContainer,
                    modifier = Modifier
                        .weight(0.75f)
                        .height(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.add_one_min),
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSecondaryContainer
                        )
                    }
                }

                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onAddFiveMin()
                    },
                    shape = RoundedCornerShape(50),
                    color = colorScheme.secondaryContainer,
                    modifier = Modifier
                        .weight(0.75f)
                        .height(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.add_five_min),
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSecondaryContainer
                        )
                    }
                }

                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onReset()
                    },
                    shape = CircleShape,
                    color = colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
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
}
