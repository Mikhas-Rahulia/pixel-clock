package io.github.mikhasrahulia.pixelclock.ui.alarm

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mikhasrahulia.pixelclock.R
import io.github.mikhasrahulia.pixelclock.alarm.AlarmScheduler
import io.github.mikhasrahulia.pixelclock.data.AlarmModel
import io.github.mikhasrahulia.pixelclock.data.AlarmRepository
import io.github.mikhasrahulia.pixelclock.ui.components.AppIcons
import io.github.mikhasrahulia.pixelclock.ui.components.ExpressiveDatePickerDialog
import io.github.mikhasrahulia.pixelclock.ui.components.ExpressiveDateRangePickerDialog
import io.github.mikhasrahulia.pixelclock.ui.components.ExpressiveTimePickerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.hypot

private val CardShape = RoundedCornerShape(28.dp)
private val InnerCardShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(20.dp)
private val ActionButtonShape = RoundedCornerShape(24.dp)

@Composable
fun AlarmScreen(
    repository: AlarmRepository,
    scheduler: AlarmScheduler,
    modifier: Modifier = Modifier
) {
    val storedAlarms by repository.alarms.collectAsStateWithLifecycle()
    val alarms = remember(storedAlarms) {
        storedAlarms.sortedBy { it.hour * 60 + it.minute }
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var isCreatingAlarm by remember { mutableStateOf(false) }
    var expandedAlarmId by remember { mutableStateOf<String?>(null) }
    var editingTimeAlarm by remember { mutableStateOf<AlarmModel?>(null) }
    var pickingDateAlarm by remember { mutableStateOf<AlarmModel?>(null) }
    var pausingAlarmId by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    // Periodic 30s minute tick so "upcoming alarm" and Today/Tomorrow never go stale
    var minuteTick by remember { mutableLongStateOf(System.currentTimeMillis() / 30_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            minuteTick = System.currentTimeMillis() / 30_000L
        }
    }

    BackHandler(enabled = expandedAlarmId != null) {
        focusManager.clearFocus()
        expandedAlarmId = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.tab_alarm),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp, start = 4.dp)
            )

            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = AppIcons.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.no_alarms),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 112.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        val isExpanded = expandedAlarmId == alarm.id

                        ExpressiveAlarmCard(
                            alarm = alarm,
                            minuteTick = minuteTick,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                focusManager.clearFocus()
                                expandedAlarmId = if (isExpanded) null else alarm.id
                            },
                            onToggleEnabled = { enabled ->
                                val updated = repository.toggleAlarm(alarm.id, enabled)
                                if (updated != null) {
                                    if (enabled) scheduler.scheduleAlarm(updated)
                                    else scheduler.cancelAlarm(alarm.id)
                                }
                            },
                            onUpdateAlarm = { updated ->
                                repository.updateAlarm(updated)
                                scheduler.scheduleAlarm(updated)
                            },
                            onUpdateLabelOnly = { updated ->
                                repository.updateAlarm(updated)
                            },
                            onShiftCycle = {
                                val updated = repository.shiftAlarmCycle(alarm.id)
                                if (updated != null) {
                                    scheduler.scheduleAlarm(updated)
                                }
                            },
                            onEditTime = { editingTimeAlarm = alarm },
                            onPickDate = { pickingDateAlarm = alarm },
                            onPickPause = { pausingAlarmId = alarm.id },
                            onDelete = {
                                if (expandedAlarmId == alarm.id) expandedAlarmId = null
                                scheduler.cancelAlarm(alarm.id)
                                repository.deleteAlarm(alarm.id)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { isCreatingAlarm = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .size(64.dp),
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = AppIcons.Add,
                contentDescription = stringResource(R.string.add_alarm),
                modifier = Modifier.size(30.dp)
            )
        }
    }

    if (isCreatingAlarm) {
        val nextSlot = remember { AlarmModel.nextHourSlot() }
        ExpressiveTimePickerDialog(
            initialHour = nextSlot.hour,
            initialMinute = nextSlot.minute,
            onConfirm = { hour, minute ->
                val date = AlarmModel.oneTimeDate(hour, minute)
                val newAlarm = AlarmModel(
                    hour = hour,
                    minute = minute,
                    daysOfWeek = emptySet(),
                    specificDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    isEnabled = true
                )
                repository.addAlarm(newAlarm)
                scheduler.scheduleAlarm(newAlarm)
                expandedAlarmId = newAlarm.id
                isCreatingAlarm = false
                coroutineScope.launch {
                    val idx = alarms.indexOfFirst { it.id == newAlarm.id }.coerceAtLeast(0)
                    listState.animateScrollToItem(idx)
                }
            },
            onDismiss = { isCreatingAlarm = false }
        )
    }

    editingTimeAlarm?.let { alarmToEdit ->
        ExpressiveTimePickerDialog(
            initialHour = alarmToEdit.hour,
            initialMinute = alarmToEdit.minute,
            onConfirm = { hour, minute ->
                val updated = alarmToEdit.copy(hour = hour, minute = minute)
                repository.updateAlarm(updated)
                scheduler.scheduleAlarm(updated)
                editingTimeAlarm = null
            },
            onDismiss = { editingTimeAlarm = null }
        )
    }

    pickingDateAlarm?.let { alarmToPick ->
        val currentSelected = alarmToPick.specificDate?.let { dateStr ->
            runCatching {
                LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
        }
        ExpressiveDatePickerDialog(
            initialDateMillis = currentSelected,
            onDateSelected = { pickedDate ->
                val updated = alarmToPick.copy(
                    specificDate = pickedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    daysOfWeek = emptySet(),
                    isBiWeekly = false
                )
                repository.updateAlarm(updated)
                scheduler.scheduleAlarm(updated)
                pickingDateAlarm = null
            },
            onDismiss = { pickingDateAlarm = null }
        )
    }

    pausingAlarmId?.let { alarmId ->
        val alarm = alarms.firstOrNull { it.id == alarmId }
        if (alarm != null) {
            val parseStart = alarm.pauseFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val parseEnd = alarm.pauseUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ExpressiveDateRangePickerDialog(
                initialStart = parseStart,
                initialEnd = parseEnd,
                onConfirm = { start, end ->
                    val updated = alarm.copy(
                        pauseFrom = start.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        pauseUntil = end.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    )
                    repository.updateAlarm(updated)
                    scheduler.scheduleAlarm(updated)
                    pausingAlarmId = null
                },
                onClear = {
                    val updated = alarm.copy(pauseFrom = null, pauseUntil = null)
                    repository.updateAlarm(updated)
                    scheduler.scheduleAlarm(updated)
                    pausingAlarmId = null
                },
                onDismiss = { pausingAlarmId = null }
            )
        }
    }
}

@Composable
fun ExpressiveAlarmCard(
    alarm: AlarmModel,
    minuteTick: Long,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateAlarm: (AlarmModel) -> Unit,
    onUpdateLabelOnly: (AlarmModel) -> Unit,
    onShiftCycle: () -> Unit,
    onEditTime: () -> Unit,
    onPickDate: () -> Unit,
    onPickPause: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    val daysSummary = remember(alarm.daysOfWeek, alarm.specificDate, alarm.hour, alarm.minute, minuteTick) {
        alarm.getDaysSummary(context)
    }
    val nextOccurrence = remember(alarm, minuteTick) {
        val nextMillis = alarm.nextTriggerMillisOrNull()
        if (nextMillis != null) {
            val diff = Duration.between(Instant.now(), Instant.ofEpochMilli(nextMillis))
            val totalMins = diff.toMinutes().coerceAtLeast(1L)
            val days = totalMins / (24 * 60)
            val hours = (totalMins % (24 * 60)) / 60
            val minutes = totalMins % 60
            when {
                days > 0 -> "${days}d ${hours}h"
                hours > 0 -> "${hours}h ${minutes}m"
                else -> "${minutes}m"
            }
        } else ""
    }
    val formattedTime = remember(alarm.hour, alarm.minute) {
        String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
    }

    val pauseRange = remember(alarm.pauseFrom, alarm.pauseUntil) {
        if (!alarm.pauseFrom.isNullOrBlank() && !alarm.pauseUntil.isNullOrBlank()) {
            runCatching {
                LocalDate.parse(alarm.pauseFrom) to LocalDate.parse(alarm.pauseUntil)
            }.getOrNull()
        } else null
    }

    val pauseDayFormat = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    val pauseRangeFormatted: String? = pauseRange?.let { (from, until) ->
        if (from == until) from.format(pauseDayFormat)
        else "${from.format(pauseDayFormat)} – ${until.format(pauseDayFormat)}"
    }

    val today = remember(minuteTick) { LocalDate.now() }
    val pauseText: String? = pauseRange?.let { (from, until) ->
        when {
            !from.isAfter(today) && until == today -> stringResource(R.string.pause_today)
            !from.isAfter(today) -> stringResource(R.string.pause_until, until.format(pauseDayFormat))
            from == until -> stringResource(R.string.pause_single, from.format(pauseDayFormat))
            else -> stringResource(
                R.string.pause_range,
                from.format(pauseDayFormat),
                until.format(pauseDayFormat)
            )
        }
    }

    // Material You Dynamic Color styling:
    // Enabled alarms use vibrant primaryContainer; Disabled alarms are muted dark grey with dimmed grey digits (NEVER bright!).
    // Expanding/collapsing keeps the exact same color family with zero jarring flip.
    val isEnabled = alarm.isEnabled
    val targetContainerColor = if (isEnabled) colorScheme.primaryContainer else colorScheme.surfaceContainerLow
    val animatedContainerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 180),
        label = "alarmCardContainerColor"
    )

    val primaryTextColor = if (isEnabled) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
    val secondaryTextColor = if (isEnabled) colorScheme.onPrimaryContainer.copy(alpha = 0.78f) else colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
    val innerSurfaceColor = if (isEnabled) colorScheme.surface.copy(alpha = 0.22f) else colorScheme.surfaceContainer
    val pillBg = if (isEnabled) colorScheme.primary else colorScheme.surfaceContainerHigh
    val pillText = if (isEnabled) colorScheme.onPrimary else colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    // Buffered label editing to avoid disk I/O + recomposition on every keystroke
    var labelDraft by remember(alarm.id) { mutableStateOf(alarm.label) }
    LaunchedEffect(alarm.label) {
        if (alarm.label != labelDraft) labelDraft = alarm.label
    }
    fun commitLabelIfChanged() {
        if (labelDraft != alarm.label) {
            onUpdateLabelOnly(alarm.copy(label = labelDraft))
        }
    }
    DisposableEffect(alarm.id, isExpanded) {
        onDispose {
            if (isExpanded) commitLabelIfChanged()
        }
    }

    var tapCenter by remember { mutableStateOf<Offset?>(null) }
    var touchAnim by remember { mutableStateOf(false) }
    val rippleScale by animateFloatAsState(
        targetValue = if (touchAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "alarmRipple",
        finishedListener = { touchAnim = false }
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMedium))
            .drawBehind {
                drawRect(animatedContainerColor)
                if (rippleScale > 0f && rippleScale < 1f) {
                    val center = tapCenter ?: Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = hypot(size.width, size.height)
                    drawCircle(
                        color = primaryTextColor.copy(alpha = 0.08f),
                        radius = maxRadius * rippleScale,
                        center = center
                    )
                }
            },
        shape = CardShape,
        color = Color.Transparent
    ) {
        if (!isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            tapCenter = offset
                            touchAnim = true
                            onToggleExpand()
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                val scheduleText = if (alarm.isBiWeekly) {
                    val weekStatus = stringResource(if (alarm.isCurrentWeekActive()) R.string.active_this_week else R.string.active_next_week)
                    "${stringResource(R.string.repeat_biweekly)} ($daysSummary · $weekStatus)"
                } else {
                    daysSummary
                }
                val hasLabel = alarm.label.isNotBlank() && !alarm.label.equals(daysSummary, ignoreCase = true)

                Text(
                    text = scheduleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor
                )
                if (hasLabel) {
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (alarm.isEnabled && pauseText != null) {
                    Text(
                        text = pauseText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.tertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        ),
                        color = primaryTextColor
                    )
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.onPrimary,
                            checkedTrackColor = colorScheme.primary,
                            uncheckedThumbColor = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            uncheckedTrackColor = colorScheme.surfaceContainerHighest,
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Drag handle (tap to collapse)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                commitLabelIfChanged()
                                tapCenter = offset
                                touchAnim = true
                                onToggleExpand()
                            }
                        }
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(secondaryTextColor.copy(alpha = 0.5f))
                    )
                }

                val cardRepeating = alarm.daysOfWeek.isNotEmpty() && alarm.specificDate == null
                val cardPreview = if (cardRepeating) {
                    null
                } else if (!alarm.specificDate.isNullOrBlank()) {
                    runCatching { LocalDate.parse(alarm.specificDate).dayOfWeek.value }
                        .getOrElse { AlarmModel.oneTimeDate(alarm.hour, alarm.minute).dayOfWeek.value }
                } else {
                    AlarmModel.oneTimeDate(alarm.hour, alarm.minute).dayOfWeek.value
                }

                // Time & Edit button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        ),
                        color = primaryTextColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEditTime() }
                            .padding(horizontal = 4.dp)
                    )
                    Surface(
                        shape = PillShape,
                        color = pillBg,
                        onClick = onEditTime
                    ) {
                        Text(
                            text = stringResource(R.string.edit),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = pillText,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Day of week selector
                DayOfWeekSelector(
                    selectedDays = if (cardRepeating) alarm.daysOfWeek else emptySet(),
                    previewDay = cardPreview,
                    isEnabled = isEnabled,
                    onDaysChanged = { newDays ->
                        val weekly = newDays.isNotEmpty() && (cardPreview == null || newDays != setOf(cardPreview))
                        if (weekly) {
                            onUpdateAlarm(
                                alarm.copy(
                                    daysOfWeek = newDays,
                                    specificDate = null,
                                    isBiWeekly = alarm.isBiWeekly && newDays.isNotEmpty()
                                )
                            )
                        } else {
                            val onceDate = alarm.specificDate
                                ?: AlarmModel.oneTimeDate(alarm.hour, alarm.minute)
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                            onUpdateAlarm(
                                alarm.copy(
                                    daysOfWeek = emptySet(),
                                    specificDate = onceDate,
                                    isBiWeekly = false
                                )
                            )
                        }
                    }
                )

                // Dedicated Bi-Weekly Recurrence & Shift 2 Weeks Controls
                Surface(
                    shape = InnerCardShape,
                    color = innerSurfaceColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (!cardRepeating) {
                                            val day = cardPreview ?: AlarmModel.oneTimeDate(alarm.hour, alarm.minute).dayOfWeek.value
                                            onUpdateAlarm(alarm.copy(daysOfWeek = setOf(day), specificDate = null, isBiWeekly = !alarm.isBiWeekly))
                                        } else {
                                            onUpdateAlarm(alarm.copy(isBiWeekly = !alarm.isBiWeekly))
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = AppIcons.Alarm,
                                    contentDescription = null,
                                    tint = if (alarm.isBiWeekly) primaryTextColor else secondaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.repeat_biweekly),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryTextColor
                                    )
                                    Text(
                                        text = if (alarm.isBiWeekly) stringResource(R.string.repeat_biweekly) else stringResource(R.string.repeat_weekly),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryTextColor
                                    )
                                }
                            }
                            Switch(
                                checked = alarm.isBiWeekly,
                                onCheckedChange = { checked ->
                                    if (!cardRepeating && checked) {
                                        val day = cardPreview ?: AlarmModel.oneTimeDate(alarm.hour, alarm.minute).dayOfWeek.value
                                        onUpdateAlarm(alarm.copy(daysOfWeek = setOf(day), specificDate = null, isBiWeekly = true))
                                    } else {
                                        onUpdateAlarm(alarm.copy(isBiWeekly = checked))
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colorScheme.onPrimary,
                                    checkedTrackColor = colorScheme.primary,
                                    uncheckedThumbColor = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    uncheckedTrackColor = colorScheme.surfaceContainerHighest,
                                    uncheckedBorderColor = Color.Transparent
                                )
                            )
                        }

                        if (alarm.isBiWeekly) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.shift_cycle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryTextColor
                                )
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = pillBg,
                                    onClick = onShiftCycle
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.ChevronLeft,
                                            contentDescription = stringResource(R.string.shift_cycle),
                                            tint = pillText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(if (alarm.isCurrentWeekActive()) R.string.active_this_week else R.string.active_next_week),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = pillText
                                        )
                                        Icon(
                                            imageVector = AppIcons.ChevronRight,
                                            contentDescription = stringResource(R.string.shift_cycle),
                                            tint = pillText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Upcoming alarm & specific date button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = stringResource(R.string.upcoming_alarm),
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = nextOccurrence.ifBlank { daysSummary },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor
                        )
                    }

                    val isDateMode = alarm.specificDate != null
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = innerSurfaceColor,
                        onClick = {
                            if (isDateMode) onUpdateAlarm(alarm.copy(specificDate = null))
                            else onPickDate()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isDateMode) AppIcons.Close else AppIcons.CalendarMonth,
                                contentDescription = null,
                                tint = primaryTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isDateMode) alarm.specificDate.orEmpty() else stringResource(R.string.set_alarm_date),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryTextColor
                            )
                        }
                    }
                }

                // Pause between dates
                Surface(
                    shape = InnerCardShape,
                    color = innerSurfaceColor,
                    onClick = onPickPause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.PauseCircle,
                                contentDescription = null,
                                tint = secondaryTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (pauseRangeFormatted != null) {
                                        "${stringResource(R.string.pause_title)} ($pauseRangeFormatted)"
                                    } else {
                                        stringResource(R.string.pause_button)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.edit),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                            }
                        }

                        if (pauseRange != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = pillBg,
                                onClick = { onUpdateAlarm(alarm.copy(pauseFrom = null, pauseUntil = null)) }
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = pillText,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Alarm Name (buffered input)
                Surface(
                    shape = InnerCardShape,
                    color = innerSurfaceColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Label,
                                contentDescription = null,
                                tint = secondaryTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.alarm_name_title),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = secondaryTextColor
                            )
                        }
                        BasicTextField(
                            value = labelDraft,
                            onValueChange = { labelDraft = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = primaryTextColor,
                                fontSize = 16.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 26.dp)
                                .onFocusChanged { if (!it.isFocused) commitLabelIfChanged() },
                            minLines = 1,
                            maxLines = 4,
                            cursorBrush = SolidColor(primaryTextColor),
                            decorationBox = { innerTextField ->
                                if (labelDraft.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.alarm_name_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryTextColor.copy(alpha = 0.7f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                // Vibration toggle
                Surface(
                    shape = InnerCardShape,
                    color = innerSurfaceColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = AppIcons.Vibration,
                                contentDescription = null,
                                tint = secondaryTextColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.vibration),
                                style = MaterialTheme.typography.bodyLarge,
                                color = primaryTextColor
                            )
                        }
                        Switch(
                            checked = alarm.vibrate,
                            onCheckedChange = { onUpdateAlarm(alarm.copy(vibrate = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colorScheme.onPrimary,
                                checkedTrackColor = colorScheme.primary,
                                uncheckedThumbColor = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                uncheckedTrackColor = colorScheme.surfaceContainerHighest,
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }
                }

                // Bottom row: Delete & Save
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = ActionButtonShape,
                        color = colorScheme.errorContainer,
                        onClick = onDelete
                    ) {
                        Text(
                            text = stringResource(R.string.delete),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }

                    Surface(
                        shape = ActionButtonShape,
                        color = pillBg,
                        onClick = {
                            commitLabelIfChanged()
                            onToggleExpand()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = pillText,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DayOfWeekSelector(
    selectedDays: Set<Int>,
    onDaysChanged: (Set<Int>) -> Unit,
    previewDay: Int? = null,
    isEnabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val days = listOf(
        1 to stringResource(R.string.day_mon_initial),
        2 to stringResource(R.string.day_tue_initial),
        3 to stringResource(R.string.day_wed_initial),
        4 to stringResource(R.string.day_thu_initial),
        5 to stringResource(R.string.day_fri_initial),
        6 to stringResource(R.string.day_sat_initial),
        7 to stringResource(R.string.day_sun_initial)
    )
    val oncePreview = selectedDays.isEmpty() && previewDay != null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        days.forEach { (dayIndex, dayLabel) ->
            val base = if (oncePreview && previewDay != null) setOf(previewDay) else selectedDays
            val isSelected = base.contains(dayIndex)
            val bg = when {
                isSelected && isEnabled -> colorScheme.primary
                isSelected -> colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                isEnabled -> colorScheme.surface.copy(alpha = 0.24f)
                else -> colorScheme.surfaceContainer
            }
            val fg = when {
                isSelected && isEnabled -> colorScheme.onPrimary
                isSelected -> colorScheme.surface
                isEnabled -> colorScheme.onPrimaryContainer
                else -> colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable {
                        val newSet = if (base.contains(dayIndex)) base - dayIndex else base + dayIndex
                        onDaysChanged(newSet)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
            }
        }
    }
}
