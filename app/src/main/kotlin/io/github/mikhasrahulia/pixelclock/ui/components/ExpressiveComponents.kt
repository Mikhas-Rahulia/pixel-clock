package io.github.mikhasrahulia.pixelclock.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.floor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mikhasrahulia.pixelclock.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class AppTab(@StringRes val titleRes: Int, val icon: ImageVector) {
    ALARM(R.string.tab_alarm, Icons.Rounded.Alarm),
    TIMER(R.string.tab_timer, Icons.Rounded.HourglassBottom)
}

class ModalGate {
    var depth by mutableIntStateOf(0)
        private set

    fun enter() {
        depth++
    }

    fun exit() {
        depth = (depth - 1).coerceAtLeast(0)
    }
}

val LocalModalGate = staticCompositionLocalOf { ModalGate() }

/**
 * Alarm: the clock hand makes one full lap every time the tab is selected.
 * Timer: the hourglass turns over and the sand falls through the neck on every selection.
 *
 * Each selection adds one step to a counter; the animated value follows the
 * counter. A switch that happens mid-animation therefore keeps going instead of
 * being cancelled, so the animation plays on every switch.
 */
@Composable
private fun NavTabIcon(tab: AppTab, selected: Boolean, tint: Color, description: String) {
    var steps by remember { mutableIntStateOf(0) }
    var wasSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !wasSelected) steps++
        wasSelected = selected
    }
    when (tab) {
        AppTab.ALARM -> {
            val sweep by animateFloatAsState(
                targetValue = steps * 360f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                label = "alarmSweep"
            )
            Canvas(
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = description }
            ) {
                val stroke = 2.2.dp.toPx()
                val c = center
                val radius = size.minDimension / 2f - 3.dp.toPx()
                drawCircle(tint, radius = radius, center = c, style = Stroke(width = stroke))
                // Little bells on top, like an alarm clock.
                listOf(-135f, -45f).forEach { deg ->
                    val rad = Math.toRadians(deg.toDouble())
                    val p = Offset(
                        c.x + ((radius + 1.5.dp.toPx()) * Math.cos(rad)).toFloat(),
                        c.y + ((radius + 1.5.dp.toPx()) * Math.sin(rad)).toFloat()
                    )
                    drawCircle(tint, radius = 2.4.dp.toPx(), center = p)
                }
                // Fixed short hand plus the long hand that makes the lap.
                drawLine(tint, c, Offset(c.x + radius * 0.38f, c.y), strokeWidth = stroke, cap = StrokeCap.Round)
                rotate(degrees = sweep - 90f, pivot = c) {
                    drawLine(tint, c, Offset(c.x + radius * 0.72f, c.y), strokeWidth = stroke, cap = StrokeCap.Round)
                }
            }
        }
        AppTab.TIMER -> {
            // One step = half a turn of the glass, then the sand falls through the neck.
            val pos by animateFloatAsState(
                targetValue = steps.toFloat(),
                animationSpec = tween(durationMillis = 700, easing = LinearEasing),
                label = "hourglassPos"
            )
            Canvas(
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = description }
            ) {
                val u = size.minDimension / 24f
                fun pt(x: Float, y: Float) = Offset(x * u, y * u)
                val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val pivot = pt(12f, 12f)

                // Glass outline: two caps and the two bulbs meeting at the neck.
                fun drawGlass() {
                    val body = Path().apply {
                        moveTo(pt(6f, 4f).x, pt(6f, 4f).y)
                        lineTo(pt(18f, 4f).x, pt(18f, 4f).y)
                        lineTo(pt(12f, 12f).x, pt(12f, 12f).y)
                        lineTo(pt(18f, 20f).x, pt(18f, 20f).y)
                        lineTo(pt(6f, 20f).x, pt(6f, 20f).y)
                        lineTo(pt(12f, 12f).x, pt(12f, 12f).y)
                        close()
                    }
                    drawPath(body, tint, style = stroke)
                    drawLine(tint, pt(4f, 2.8f), pt(20f, 2.8f), strokeWidth = 2f * u, cap = StrokeCap.Round)
                    drawLine(tint, pt(4f, 21.2f), pt(20f, 21.2f), strokeWidth = 2f * u, cap = StrokeCap.Round)
                }

                // Sand in the upper bulb: fills from the neck upwards, height h in 0..8.
                fun drawTopSand(h: Float) {
                    if (h <= 0.01f) return
                    val y = 12f - h
                    val hw = 6f * (12f - y) / 8f
                    val sand = Path().apply {
                        moveTo(pt(12f - hw, y).x, pt(12f - hw, y).y)
                        lineTo(pt(12f + hw, y).x, pt(12f + hw, y).y)
                        lineTo(pt(12f, 12f).x, pt(12f, 12f).y)
                        close()
                    }
                    drawPath(sand, tint)
                }

                // Sand in the lower bulb: fills from the bottom up, height h in 0..8.
                fun drawBottomSand(h: Float) {
                    if (h <= 0.01f) return
                    val y = 20f - h
                    val hw = 6f * (y - 12f) / 8f
                    val sand = Path().apply {
                        moveTo(pt(12f - hw, y).x, pt(12f - hw, y).y)
                        lineTo(pt(12f + hw, y).x, pt(12f + hw, y).y)
                        lineTo(pt(18f, 20f).x, pt(18f, 20f).y)
                        lineTo(pt(6f, 20f).x, pt(6f, 20f).y)
                        close()
                    }
                    drawPath(sand, tint)
                }

                val frac = pos - floor(pos)
                if (frac < 0.5f) {
                    // Turning: the glass and the sand in it rotate half a turn.
                    val t = frac / 0.5f
                    val delta = 180f * t * t * (3f - 2f * t)
                    rotate(degrees = delta, pivot = pivot) {
                        drawGlass()
                        drawBottomSand(8f)
                    }
                } else {
                    // Falling: sand drains from the upper bulb through the neck (accelerating).
                    val x = (frac - 0.5f) / 0.5f
                    val g = 1f - x * x
                    drawGlass()
                    drawTopSand(8f * g)
                    drawBottomSand(8f * (1f - g))
                    if (g in 0.02f..0.98f) {
                        drawLine(
                            tint,
                            pt(12f, 12f),
                            pt(12f, 20f - 8f * (1f - g)),
                            strokeWidth = 1.1f * u,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun ExpressiveNavBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF35343C) else Color.Transparent,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
                label = "tabColor"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFFE3E1E9) else Color(0xFF8E8D96),
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
                label = "tabTextColor"
            )
            val tabScale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.94f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                label = "tabScale"
            )
            val tabTitle = stringResource(tab.titleRes)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .scale(tabScale)
                    .clip(RoundedCornerShape(24.dp))
                    .background(containerColor)
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    NavTabIcon(
                        tab = tab,
                        selected = isSelected,
                        tint = contentColor,
                        description = tabTitle
                    )
                    if (isSelected) {
                        Text(
                            text = tabTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Same-window dialog with a strong predictive back: the card shrinks and
 * slides early in the gesture so the screen underneath is obvious.
 */
@Composable
fun PredictiveBackLayer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val gate = LocalModalGate.current
        DisposableEffect(Unit) {
            gate.enter()
            onDispose { gate.exit() }
        }

        val progress = remember { Animatable(0f) }
        var fromLeft by remember { mutableStateOf(true) }
        PredictiveBackHandler(enabled = true) { events ->
            try {
                events.collect { event ->
                    fromLeft = event.swipeEdge != BackEventCompat.EDGE_RIGHT
                    progress.snapTo(event.progress.coerceIn(0f, 1f))
                }
                onDismiss()
            } catch (e: CancellationException) {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.62f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                throw e
            }
        }

        // Springy entrance: the card pops up from slightly smaller and slightly lower.
        val entrance = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            entrance.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow)
            )
        }
        val emphasized = run {
            val raw = progress.value
            1f - (1f - raw) * (1f - raw)
        }
        val scrimClick = remember { MutableInteractionSource() }
        val cardClick = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.62f * (1f - emphasized * 1.4f).coerceIn(0f, 1f) * entrance.value.coerceIn(0f, 1f)
                    )
                )
                .clickable(interactionSource = scrimClick, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .graphicsLayer {
                        val pop = entrance.value
                        val scale = (0.86f + 0.14f * pop) - 0.38f * emphasized
                        scaleX = scale
                        scaleY = scale
                        translationX = (if (fromLeft) 120.dp else (-120).dp).toPx() * emphasized
                        translationY = 72.dp.toPx() * emphasized + 56.dp.toPx() * (1f - pop)
                        alpha = (pop.coerceIn(0f, 1f)) * (1f - 0.2f * emphasized)
                        shape = RoundedCornerShape((28 + 28 * emphasized).dp)
                        clip = true
                    }
                    .clickable(interactionSource = cardClick, indication = null, onClick = {})
            ) {
                content()
            }
        }
    }
}

@Composable
fun autoFocusModifier(): Modifier {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    return Modifier.focusRequester(focusRequester)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardTimeFields(state: TimePickerState) {
    var hourText by remember { mutableStateOf(state.hour.toString().padStart(2, '0')) }
    var minuteText by remember { mutableStateOf(state.minute.toString().padStart(2, '0')) }
    val focusModifier = autoFocusModifier()
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = hourText,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(2)
                hourText = digits
                digits.toIntOrNull()?.takeIf { it in 0..23 }?.let { state.hour = it }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = focusModifier.width(96.dp),
            shape = RoundedCornerShape(18.dp)
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        OutlinedTextField(
            value = minuteText,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(2)
                minuteText = digits
                digits.toIntOrNull()?.takeIf { it in 0..59 }?.let { state.minute = it }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp),
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveTimePickerDialog(
    initialHour: Int = 7,
    initialMinute: Int = 0,
    is24Hour: Boolean = true,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour
    )
    // Default to clock menu (dial), not keyboard
    var isKeyboardMode by remember { mutableStateOf(false) }

    PredictiveBackLayer(onDismiss = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.select_time),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { isKeyboardMode = !isKeyboardMode },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isKeyboardMode) Icons.Rounded.Schedule else Icons.Rounded.Keyboard,
                            contentDescription = stringResource(R.string.toggle_mode),
                            tint = Color(0xFFE3E1E9)
                        )
                    }
                }

                if (isKeyboardMode) {
                    TimeInput(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            timeSelectorSelectedContainerColor = Color(0xFF35343C),
                            timeSelectorSelectedContentColor = Color(0xFFFFFFFF),
                            timeSelectorUnselectedContainerColor = Color(0xFF25242C),
                            timeSelectorUnselectedContentColor = Color(0xFF8E8D96)
                        )
                    )
                } else {
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = Color(0xFF28272F),
                            selectorColor = Color(0xFFFFFFFF),
                            containerColor = Color(0xFF1E1E24),
                            clockDialSelectedContentColor = Color(0xFF191C20),
                            clockDialUnselectedContentColor = Color(0xFFE3E1E9),
                            timeSelectorSelectedContainerColor = Color(0xFF35343C),
                            timeSelectorSelectedContentColor = Color(0xFFFFFFFF),
                            timeSelectorUnselectedContainerColor = Color(0xFF25242C),
                            timeSelectorUnselectedContentColor = Color(0xFF8E8D96)
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(18.dp)) {
                        Text(
                            stringResource(R.string.cancel),
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE3E1E9)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFFFFFF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onConfirm(timePickerState.hour, timePickerState.minute) }
                    ) {
                        Text(
                            stringResource(R.string.save),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF191C20),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveDatePickerDialog(
    initialDateMillis: Long? = null,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis ?: System.currentTimeMillis()
    )
    PredictiveBackLayer(onDismiss = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DatePicker(state = datePickerState)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(18.dp)) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val localDate = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                onDateSelected(localDate)
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Pick one day (tap once) or a range (tap first and last day) during which an alarm stays silent.
 * Past days cannot be picked. When a pause already exists, [onClear] resumes the alarm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveDateRangePickerDialog(
    initialStart: LocalDate? = null,
    initialEnd: LocalDate? = null,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onClear: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    val today = remember { LocalDate.now() }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.utcMillis(),
        initialSelectedEndDateMillis = initialEnd?.utcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis >= today.utcMillis()

            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        }
    )
    val startMillis = state.selectedStartDateMillis
    // One tap = one paused day. A second tap makes it a range.
    val endMillis = state.selectedEndDateMillis ?: startMillis

    PredictiveBackLayer(onDismiss = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                Text(
                    text = stringResource(R.string.pause_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)
                )
                Text(
                    text = stringResource(R.string.pause_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
                )
                DateRangePicker(
                    state = state,
                    title = null,
                    showModeToggle = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(460.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onClear != null) {
                        TextButton(
                            onClick = {
                                onClear()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(stringResource(R.string.pause_resume), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(18.dp)) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        enabled = startMillis != null,
                        onClick = {
                            if (startMillis != null && endMillis != null) {
                                onConfirm(startMillis.utcDate(), endMillis.utcDate())
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
