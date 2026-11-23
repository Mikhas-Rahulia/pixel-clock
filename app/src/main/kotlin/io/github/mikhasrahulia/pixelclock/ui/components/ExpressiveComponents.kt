package io.github.mikhasrahulia.pixelclock.ui.components

import android.view.HapticFeedbackConstants
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.mikhasrahulia.pixelclock.R
import io.github.mikhasrahulia.pixelclock.ui.theme.ExpressiveMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val BELL_ANGLES = floatArrayOf(-135f, -45f)

enum class AppTab(@StringRes val titleRes: Int, val icon: ImageVector) {
    ALARM(R.string.tab_alarm, AppIcons.Alarm),
    TIMER(R.string.tab_timer, AppIcons.HourglassBottom)
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
 * Material 3 Expressive segmented-list corner helper:
 * outer corners use generous 24.dp radii while adjacent inner edges tighten to 8.dp.
 */
fun groupedCardShape(
    index: Int,
    count: Int,
    outerRadius: Dp = 24.dp,
    innerRadius: Dp = 8.dp
): RoundedCornerShape {
    if (count <= 1) return RoundedCornerShape(outerRadius)
    return when (index) {
        0 -> RoundedCornerShape(
            topStart = outerRadius,
            topEnd = outerRadius,
            bottomStart = innerRadius,
            bottomEnd = innerRadius
        )
        count - 1 -> RoundedCornerShape(
            topStart = innerRadius,
            topEnd = innerRadius,
            bottomStart = outerRadius,
            bottomEnd = outerRadius
        )
        else -> RoundedCornerShape(innerRadius)
    }
}

/**
 * Material 3 Expressive scalloped / soft-cookie shape for badges, empty states, and ringing hero icons.
 */
class ScallopedShape(
    private val lobes: Int = 10,
    private val depthFraction: Float = 0.085f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseRadius = minOf(cx, cy) * (1f - depthFraction)
        val amp = minOf(cx, cy) * depthFraction
        val steps = lobes * 16
        val path = Path()
        for (i in 0..steps) {
            val theta = (2.0 * PI * i) / steps
            val r = baseRadius + amp * cos(lobes * theta).toFloat()
            val x = cx + r * cos(theta).toFloat()
            val y = cy + r * sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Material 3 Expressive Switch with tactile Pixel LRA haptics and a crisp check glyph inside the thumb.
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    checkedTrackColor: Color = MaterialTheme.colorScheme.primary,
    checkedThumbColor: Color = MaterialTheme.colorScheme.onPrimary,
    checkedIconColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedTrackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    uncheckedThumbColor: Color = MaterialTheme.colorScheme.outline
) {
    val view = LocalView.current
    Switch(
        checked = checked,
        onCheckedChange = { next ->
            view.performHapticFeedback(
                if (next) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            )
            onCheckedChange(next)
        },
        modifier = modifier,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    tint = checkedIconColor
                )
            }
        } else null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = checkedThumbColor,
            checkedTrackColor = checkedTrackColor,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = uncheckedThumbColor,
            uncheckedTrackColor = uncheckedTrackColor,
            uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

/**
 * Alarm: the clock hand makes one full lap every time the tab is selected.
 * Timer: the hourglass turns over and the sand falls through the neck on every selection.
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
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                label = "alarmSweep"
            )
            Canvas(
                modifier = Modifier
                    .size(22.dp)
                    .semantics { contentDescription = description }
            ) {
                val stroke = 2.1.dp.toPx()
                val c = center
                val radius = size.minDimension / 2f - 2.8.dp.toPx()
                drawCircle(tint, radius = radius, center = c, style = Stroke(width = stroke))
                BELL_ANGLES.forEach { deg ->
                    val rad = Math.toRadians(deg.toDouble())
                    val p = Offset(
                        c.x + ((radius + 1.4.dp.toPx()) * cos(rad)).toFloat(),
                        c.y + ((radius + 1.4.dp.toPx()) * sin(rad)).toFloat()
                    )
                    drawCircle(tint, radius = 2.2.dp.toPx(), center = p)
                }
                drawLine(tint, c, Offset(c.x + radius * 0.38f, c.y), strokeWidth = stroke, cap = StrokeCap.Round)
                rotate(degrees = sweep - 90f, pivot = c) {
                    drawLine(tint, c, Offset(c.x + radius * 0.72f, c.y), strokeWidth = stroke, cap = StrokeCap.Round)
                }
            }
        }
        AppTab.TIMER -> {
            val pos by animateFloatAsState(
                targetValue = steps.toFloat(),
                animationSpec = tween(durationMillis = 650, easing = LinearEasing),
                label = "hourglassPos"
            )
            val glassPath = remember { Path() }
            val topSandPath = remember { Path() }
            val bottomSandPath = remember { Path() }
            Canvas(
                modifier = Modifier
                    .size(22.dp)
                    .semantics { contentDescription = description }
            ) {
                val u = size.minDimension / 24f
                fun pt(x: Float, y: Float) = Offset(x * u, y * u)
                val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val pivot = pt(12f, 12f)

                fun drawGlass() {
                    glassPath.reset()
                    glassPath.moveTo(pt(6f, 4f).x, pt(6f, 4f).y)
                    glassPath.lineTo(pt(18f, 4f).x, pt(18f, 4f).y)
                    glassPath.lineTo(pt(12f, 12f).x, pt(12f, 12f).y)
                    glassPath.lineTo(pt(18f, 20f).x, pt(18f, 20f).y)
                    glassPath.lineTo(pt(6f, 20f).x, pt(6f, 20f).y)
                    glassPath.lineTo(pt(12f, 12f).x, pt(12f, 12f).y)
                    glassPath.close()
                    drawPath(glassPath, tint, style = stroke)
                    drawLine(tint, pt(4f, 2.8f), pt(20f, 2.8f), strokeWidth = 2f * u, cap = StrokeCap.Round)
                    drawLine(tint, pt(4f, 21.2f), pt(20f, 21.2f), strokeWidth = 2f * u, cap = StrokeCap.Round)
                }

                fun drawTopSand(h: Float) {
                    if (h <= 0.01f) return
                    val y = 12f - h
                    val hw = 6f * (12f - y) / 8f
                    topSandPath.reset()
                    topSandPath.moveTo(pt(12f - hw, y).x, pt(12f - hw, y).y)
                    topSandPath.lineTo(pt(12f + hw, y).x, pt(12f + hw, y).y)
                    topSandPath.lineTo(pt(12f, 12f).x, pt(12f, 12f).y)
                    topSandPath.close()
                    drawPath(topSandPath, tint)
                }

                fun drawBottomSand(h: Float) {
                    if (h <= 0.01f) return
                    val y = 20f - h
                    val hw = 6f * (y - 12f) / 8f
                    bottomSandPath.reset()
                    bottomSandPath.moveTo(pt(12f - hw, y).x, pt(12f - hw, y).y)
                    bottomSandPath.lineTo(pt(12f + hw, y).x, pt(12f + hw, y).y)
                    bottomSandPath.lineTo(pt(18f, 20f).x, pt(18f, 20f).y)
                    bottomSandPath.lineTo(pt(6f, 20f).x, pt(6f, 20f).y)
                    bottomSandPath.close()
                    drawPath(bottomSandPath, tint)
                }

                val frac = pos - floor(pos)
                if (frac < 0.5f) {
                    val t = frac / 0.5f
                    val delta = 180f * t * t * (3f - 2f * t)
                    rotate(degrees = delta, pivot = pivot) {
                        drawGlass()
                        drawBottomSand(8f)
                    }
                } else {
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

/**
 * Material 3 Expressive floating pill navigation dock.
 * Both labels stay visible for zero layout shift; the selected tab lights up in secondaryContainer.
 */
@Composable
fun ExpressiveNavBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.28f)),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(6.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) colorScheme.secondaryContainer else Color.Transparent,
                        animationSpec = ExpressiveMotion.fastEffects(),
                        label = "tabContainer"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
                        animationSpec = ExpressiveMotion.fastEffects(),
                        label = "tabContent"
                    )
                    val tabTitle = stringResource(tab.titleRes)

                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = containerColor,
                        onClick = {
                            if (!isSelected) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            onTabSelected(tab)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            NavTabIcon(
                                tab = tab,
                                selected = isSelected,
                                tint = contentColor,
                                description = tabTitle
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tabTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Same-window dialog with expressive spring entrance and Android 14+ predictive back animation.
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
                        dampingRatio = 0.65f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                throw e
            }
        }

        val entrance = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            entrance.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
            )
        }
        val scrimClick = remember { MutableInteractionSource() }
        val cardClick = remember { MutableInteractionSource() }
        val cardShape = remember { RoundedCornerShape(32.dp) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val raw = progress.value
                    val emphasized = 1f - (1f - raw) * (1f - raw)
                    val scrimAlpha = 0.64f * (1f - emphasized * 1.4f).coerceIn(0f, 1f) * entrance.value.coerceIn(0f, 1f)
                    drawRect(Color.Black.copy(alpha = scrimAlpha))
                }
                .clickable(interactionSource = scrimClick, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .graphicsLayer {
                        val raw = progress.value
                        val emphasized = 1f - (1f - raw) * (1f - raw)
                        val pop = entrance.value
                        val scale = (0.88f + 0.12f * pop) - 0.32f * emphasized
                        scaleX = scale
                        scaleY = scale
                        translationX = (if (fromLeft) 110.dp else (-110).dp).toPx() * emphasized
                        translationY = 64.dp.toPx() * emphasized + 44.dp.toPx() * (1f - pop)
                        alpha = (pop.coerceIn(0f, 1f)) * (1f - 0.18f * emphasized)
                        shape = cardShape
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
    var isKeyboardMode by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    PredictiveBackLayer(onDismiss = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 22.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.select_time),
                        style = MaterialTheme.typography.titleLarge,
                        color = colorScheme.onSurface
                    )
                    FilledTonalIconButton(
                        onClick = { isKeyboardMode = !isKeyboardMode },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isKeyboardMode) AppIcons.Schedule else AppIcons.Keyboard,
                            contentDescription = stringResource(R.string.toggle_mode),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (isKeyboardMode) {
                    TimeInput(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            timeSelectorSelectedContainerColor = colorScheme.primaryContainer,
                            timeSelectorSelectedContentColor = colorScheme.onPrimaryContainer,
                            timeSelectorUnselectedContainerColor = colorScheme.surfaceContainerHighest,
                            timeSelectorUnselectedContentColor = colorScheme.onSurfaceVariant
                        )
                    )
                } else {
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = colorScheme.surfaceContainer,
                            selectorColor = colorScheme.primary,
                            containerColor = colorScheme.surfaceContainerHigh,
                            clockDialSelectedContentColor = colorScheme.onPrimary,
                            clockDialUnselectedContentColor = colorScheme.onSurface,
                            timeSelectorSelectedContainerColor = colorScheme.primaryContainer,
                            timeSelectorSelectedContentColor = colorScheme.onPrimaryContainer,
                            timeSelectorUnselectedContainerColor = colorScheme.surfaceContainerHighest,
                            timeSelectorUnselectedContentColor = colorScheme.onSurfaceVariant
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(timePickerState.hour, timePickerState.minute) },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
            shape = RoundedCornerShape(32.dp),
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(stringResource(R.string.save))
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
    val endMillis = state.selectedEndDateMillis ?: startMillis

    PredictiveBackLayer(onDismiss = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                Text(
                    text = stringResource(R.string.pause_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp)
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
                        .height(450.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onClear != null) {
                        TextButton(
                            onClick = {
                                onClear()
                                onDismiss()
                            }
                        ) {
                            Text(stringResource(R.string.pause_resume))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = startMillis != null,
                        onClick = {
                            if (startMillis != null && endMillis != null) {
                                onConfirm(startMillis.utcDate(), endMillis.utcDate())
                            }
                            onDismiss()
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
