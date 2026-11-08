package io.github.mikhasrahulia.pixelclock.ui.alarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.mikhasrahulia.pixelclock.R
import io.github.mikhasrahulia.pixelclock.alarm.AlarmScheduler
import io.github.mikhasrahulia.pixelclock.data.AlarmModel
import io.github.mikhasrahulia.pixelclock.data.AlarmRepository
import io.github.mikhasrahulia.pixelclock.ui.components.ExpressiveDatePickerDialog
import io.github.mikhasrahulia.pixelclock.ui.components.ExpressiveDateRangePickerDialog
import io.github.mikhasrahulia.pixelclock.ui.components.ExpressiveTimePickerDialog
import io.github.mikhasrahulia.pixelclock.ui.components.KeyboardTimeFields
import io.github.mikhasrahulia.pixelclock.ui.components.PredictiveBackLayer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AlarmScreen(
    repository: AlarmRepository,
    scheduler: AlarmScheduler,
    modifier: Modifier = Modifier
) {
    val storedAlarms by repository.alarms.collectAsState()
    val alarms = remember(storedAlarms) {
        storedAlarms.sortedBy { it.hour * 60 + it.minute }
    }
    var isCreatingAlarm by remember { mutableStateOf(false) }
    var expandedAlarmId by remember { mutableStateOf<String?>(null) }
    var editingTimeAlarm by remember { mutableStateOf<AlarmModel?>(null) }
    var pickingDateAlarm by remember { mutableStateOf<AlarmModel?>(null) }
    var pausingAlarmId by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

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
            // Screen Title
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
                            imageVector = Icons.Rounded.Alarm,
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
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        val isExpanded = expandedAlarmId == alarm.id

                        ExpressiveAlarmCard(
                            alarm = alarm,
                            isExpanded = isExpanded,
                            onToggleExpand = {
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
                            onShiftCycle = {
                                val updated = repository.shiftAlarmCycle(alarm.id)
                                if (updated != null) {
                                    scheduler.scheduleAlarm(updated)
                                }
                            },
                            onEditTime = {
                                editingTimeAlarm = alarm
                            },
                            onPickDate = {
                                pickingDateAlarm = alarm
                            },
                            onPickPause = {
                                pausingAlarmId = alarm.id
                            },
                            onDelete = {
                                scheduler.cancelAlarm(alarm.id)
                                repository.deleteAlarm(alarm.id)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        // FAB + (Opens time picker dialog to choose time and create alarm)
        FloatingActionButton(
            onClick = { isCreatingAlarm = true },
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
                contentDescription = stringResource(R.string.add_alarm),
                modifier = Modifier.size(32.dp)
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

    pickingDateAlarm?.let { alarmToEdit ->
        ExpressiveDatePickerDialog(
            onDateSelected = { pickedDate ->
                val dateStr = pickedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val updated = alarmToEdit.copy(
                    specificDate = dateStr,
                    daysOfWeek = emptySet()
                )
                repository.updateAlarm(updated)
                scheduler.scheduleAlarm(updated)
                pickingDateAlarm = null
            },
            onDismiss = { pickingDateAlarm = null }
        )
    }

    pausingAlarmId?.let { id ->
        val current = repository.getAlarmById(id)
        if (current == null) {
            pausingAlarmId = null
        } else {
            val range = current.pauseRange()?.takeIf { current.hasPauseAhead() }
            ExpressiveDateRangePickerDialog(
                initialStart = range?.first,
                initialEnd = range?.second,
                onConfirm = { from, until ->
                    val updated = (repository.getAlarmById(id) ?: current).copy(
                        pauseFrom = from.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        pauseUntil = until.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    )
                    repository.updateAlarm(updated)
                    scheduler.scheduleAlarm(updated)
                },
                onClear = if (range != null) {
                    {
                        val updated = (repository.getAlarmById(id) ?: current)
                            .copy(pauseFrom = null, pauseUntil = null)
                        repository.updateAlarm(updated)
                        scheduler.scheduleAlarm(updated)
                    }
                } else null,
                onDismiss = { pausingAlarmId = null }
            )
        }
    }
}

@Composable
fun ExpressiveAlarmCard(
    alarm: AlarmModel,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateAlarm: (AlarmModel) -> Unit,
    onShiftCycle: () -> Unit,
    onEditTime: () -> Unit,
    onPickDate: () -> Unit,
    onPickPause: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
    val daysSummary = remember(alarm.daysOfWeek, alarm.specificDate) { alarm.getDaysSummary(context) }
    val nextOccurrence = remember(alarm) { alarm.getFormattedNextOccurrence(context) }

    val pauseRange = alarm.pauseRange()?.takeIf { alarm.hasPauseAhead() }
    val pauseDayFormat = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    val pauseMonthFormat = remember { DateTimeFormatter.ofPattern("MMM", Locale.getDefault()) }

    val pauseRangeFormatted = pauseRange?.let { (from, until) ->
        if (from == until) {
            from.format(pauseDayFormat)
        } else if (from.month == until.month && from.year == until.year) {
            "${from.dayOfMonth}–${until.dayOfMonth} ${from.format(pauseMonthFormat)}"
        } else {
            "${from.format(pauseDayFormat)} – ${until.format(pauseDayFormat)}"
        }
    }

    val pauseText: String? = pauseRange?.let { (from, until) ->
        val today = LocalDate.now()
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

    val cardShape = RoundedCornerShape(28.dp)
    val collapsedBg = if (alarm.isEnabled) Color(0xFFDEDEE6) else Color(0xFF1E1E24)
    val expandedBg = Color(0xFF1E1E24)

    var tapCenter by remember { mutableStateOf<Offset?>(null) }
    val revealProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "alarmReveal"
    )

    val contentProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "alarmContentProgress"
    )
    val collapsedContentColor = if (alarm.isEnabled) Color(0xFF191C20) else Color(0xFF8E8D96)
    val expandedContentColor = Color(0xFFE3E1E9)
    val animatedContentColor = androidx.compose.ui.graphics.lerp(collapsedContentColor, expandedContentColor, contentProgress)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .animateContentSize(spring(stiffness = Spring.StiffnessMedium))
            .drawBehind {
                if (!alarm.isEnabled) {
                    drawRect(expandedBg)
                } else {
                    drawRect(collapsedBg)
                    if (revealProgress > 0f) {
                        val center = tapCenter ?: Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = hypot(size.width, size.height)
                        drawCircle(
                            color = expandedBg,
                            radius = maxRadius * revealProgress,
                            center = center
                        )
                    }
                }
            },
        shape = cardShape,
        color = Color.Transparent
    ) {
        if (!isExpanded) {
            // Collapsed Card Layout (Google Clock M3E Screenshot 1)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            tapCenter = offset
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

                // Top: Schedule & Multiline Label
                Text(
                    text = scheduleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = animatedContentColor
                )
                if (hasLabel) {
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = animatedContentColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (alarm.isEnabled && pauseText != null) {
                    Text(
                        text = pauseText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) Color(0xFF561E10) else Color(0xFFF5C76B),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Bottom row: Big Time on left, Switch on right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = animatedContentColor
                    )
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFFFFFF),
                            checkedTrackColor = Color(0xFF191C20),
                            uncheckedThumbColor = Color(0xFF8E8D96),
                            uncheckedTrackColor = Color(0xFF33323B),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        } else {
            // Expanded Card Layout (Google Clock M3E Screenshot 4)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Drag handle at top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                tapCenter = offset
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
                            .background(Color(0xFF75747D))
                    )
                }

                // Time + Edytuj button + < and > buttons below Edytuj (Image 4)
                val cardRepeating = alarm.daysOfWeek.isNotEmpty() && alarm.specificDate == null
                val cardPreview = if (cardRepeating) {
                    null
                } else if (!alarm.specificDate.isNullOrBlank()) {
                    try {
                        LocalDate.parse(alarm.specificDate).dayOfWeek.value
                    } catch (_: Exception) {
                        AlarmModel.oneTimeDate(alarm.hour, alarm.minute).dayOfWeek.value
                    }
                } else {
                    AlarmModel.oneTimeDate(alarm.hour, alarm.minute).dayOfWeek.value
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFFE3E1E9),
                        modifier = Modifier.clickable { onEditTime() }
                    )
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF33323B),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onEditTime() }
                        ) {
                            Text(
                                text = stringResource(R.string.edit),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE3E1E9),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }

                        // Circular < and > buttons placed directly below Edytuj (Image 4)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2B2A32))
                                    .clickable {
                                        if (!cardRepeating) {
                                            val day = cardPreview ?: AlarmModel.oneTimeDate(alarm.hour, alarm.minute).dayOfWeek.value
                                            onUpdateAlarm(alarm.copy(daysOfWeek = setOf(day), specificDate = null, isBiWeekly = true))
                                        } else if (!alarm.isBiWeekly) {
                                            onUpdateAlarm(alarm.copy(isBiWeekly = true))
                                        } else {
                                            onShiftCycle()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = stringResource(R.string.shift_cycle),
                                    tint = if (alarm.isBiWeekly) Color(0xFFE3E1E9) else Color(0xFF6E6D77),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2B2A32))
                                    .clickable {
                                        if (!cardRepeating) {
                                            val day = cardPreview ?: AlarmModel.oneTimeDate(alarm.hour, alarm.minute).dayOfWeek.value
                                            onUpdateAlarm(alarm.copy(daysOfWeek = setOf(day), specificDate = null, isBiWeekly = true))
                                        } else if (!alarm.isBiWeekly) {
                                            onUpdateAlarm(alarm.copy(isBiWeekly = true))
                                        } else {
                                            onShiftCycle()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.shift_cycle),
                                    tint = if (alarm.isBiWeekly) Color(0xFFE3E1E9) else Color(0xFF6E6D77),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Dedicated bi-weekly toggle & shift 2 weeks row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: "Every 2 weeks" toggle pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (alarm.isBiWeekly) Color(0xFFFFFFFF) else Color(0xFF28272F),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                if (!cardRepeating) {
                                    val day = cardPreview ?: AlarmModel.oneTimeDate(alarm.hour, alarm.minute).dayOfWeek.value
                                    onUpdateAlarm(alarm.copy(daysOfWeek = setOf(day), specificDate = null, isBiWeekly = true))
                                } else {
                                    onUpdateAlarm(alarm.copy(isBiWeekly = !alarm.isBiWeekly))
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Alarm,
                                contentDescription = null,
                                tint = if (alarm.isBiWeekly) Color(0xFF191C20) else Color(0xFFC6C5D0),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.repeat_biweekly),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (alarm.isBiWeekly) Color(0xFF191C20) else Color(0xFFC6C5D0)
                            )
                        }
                    }

                    // Right: Shift 2 weeks status pill
                    if (alarm.isBiWeekly) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF28272F),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onShiftCycle() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = stringResource(R.string.shift_cycle),
                                    tint = Color(0xFFE3E1E9),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(if (alarm.isCurrentWeekActive()) R.string.active_this_week else R.string.active_next_week),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF5C76B)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.shift_cycle),
                                    tint = Color(0xFFE3E1E9),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Day chips (circular 42dp chips: P W Ś C P S N)
                DayOfWeekSelector(
                    selectedDays = if (cardRepeating) alarm.daysOfWeek else emptySet(),
                    previewDay = cardPreview,
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

                // Upcoming alarm info & Ustaw alarm button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.upcoming_alarm),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8E8D96)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = nextOccurrence.ifBlank { daysSummary },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE3E1E9)
                        )
                    }

                    val isDateMode = alarm.specificDate != null
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF28272F),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                if (isDateMode) {
                                    onUpdateAlarm(alarm.copy(specificDate = null))
                                } else {
                                    onPickDate()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isDateMode) Icons.Rounded.Clear else Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = Color(0xFFE3E1E9),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isDateMode) alarm.specificDate.orEmpty() else stringResource(R.string.set_alarm_date),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE3E1E9)
                            )
                        }
                    }
                }

                // Pause Card (Matching Screenshot 4)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF28272F),
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
                                imageVector = Icons.Rounded.PauseCircle,
                                contentDescription = null,
                                tint = Color(0xFFC6C5D0),
                                modifier = Modifier.size(24.dp)
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
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE3E1E9),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.edit),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF8E8D96),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onPickPause() }
                                        .padding(top = 2.dp)
                                )
                            }
                        }

                        if (pauseRange != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF3A3942),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onUpdateAlarm(alarm.copy(pauseFrom = null, pauseUntil = null)) }
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE3E1E9),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Alarm Name (Nazwa alarmu) container
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF28272F),
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
                                imageVector = Icons.AutoMirrored.Rounded.Label,
                                contentDescription = null,
                                tint = Color(0xFF8E8D96),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.alarm_name_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF8E8D96)
                            )
                        }
                        BasicTextField(
                            value = alarm.label,
                            onValueChange = { onUpdateAlarm(alarm.copy(label = it)) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = Color(0xFFE3E1E9),
                                fontSize = 16.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp),
                            minLines = 1,
                            maxLines = 4,
                            cursorBrush = SolidColor(Color(0xFFE3E1E9)),
                            decorationBox = { innerTextField ->
                                if (alarm.label.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.alarm_name_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF5A5862)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                // Vibration row
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF28272F),
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
                                imageVector = Icons.Rounded.Vibration,
                                contentDescription = null,
                                tint = Color(0xFF8E8D96),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.vibration),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFE3E1E9)
                            )
                        }
                        Switch(
                            checked = alarm.vibrate,
                            onCheckedChange = { onUpdateAlarm(alarm.copy(vibrate = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF191C20),
                                checkedTrackColor = Color(0xFFE2E2E9),
                                uncheckedThumbColor = Color(0xFF8E8D96),
                                uncheckedTrackColor = Color(0xFF33323B),
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }
                }

                // Bottom row: Usuń & Zapisz
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF2C2828),
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onDelete() }
                    ) {
                        Text(
                            text = stringResource(R.string.delete),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB4AB),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFFFFFFFF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onToggleExpand() }
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF191C20),
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
    previewDay: Int? = null
) {
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
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color(0xFFFFFFFF)
                        else Color(0xFF2B2A32)
                    )
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
                    color = if (isSelected) Color(0xFF191C20)
                    else Color(0xFFC6C5D0)
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CreateOrEditAlarmDialog(
    initialAlarm: AlarmModel? = null,
    onSave: (AlarmModel) -> Unit,
    onDismiss: () -> Unit
) {
    val createdSlot = remember(initialAlarm?.id) {
        if (initialAlarm == null) AlarmModel.nextHourSlot() else null
    }
    val timePickerState = rememberTimePickerState(
        initialHour = initialAlarm?.hour ?: createdSlot?.hour ?: 7,
        initialMinute = initialAlarm?.minute ?: createdSlot?.minute ?: 0,
        is24Hour = true
    )
    var isKeyboardMode by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val autoDate = AlarmModel.oneTimeDate(timePickerState.hour, timePickerState.minute)
    val shownDate = pickedDate ?: autoDate
    val onceLabel = shownDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))

    PredictiveBackLayer(onDismiss = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF1E1E24),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium, color = Color(0xFFE3E1E9))
                    }
                    IconButton(
                        onClick = { isKeyboardMode = !isKeyboardMode },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isKeyboardMode) Icons.Rounded.Schedule else Icons.Rounded.Keyboard,
                            contentDescription = stringResource(R.string.toggle_mode),
                            tint = Color(0xFFE3E1E9),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFFFFFFFF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable {
                                val date = pickedDate
                                    ?: AlarmModel.oneTimeDate(timePickerState.hour, timePickerState.minute)
                                onSave(
                                    AlarmModel(
                                        hour = timePickerState.hour,
                                        minute = timePickerState.minute,
                                        daysOfWeek = emptySet(),
                                        specificDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                        isEnabled = true
                                    )
                                )
                            }
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF191C20),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (isKeyboardMode) {
                    KeyboardTimeFields(timePickerState)
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

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF28272F),
                    modifier = Modifier
                        .clip(RoundedCornerShape(26.dp))
                        .clickable { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = Color(0xFFE3E1E9),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${stringResource(R.string.once)} · $onceLabel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE3E1E9)
                        )
                        if (pickedDate != null) {
                            IconButton(
                                onClick = { pickedDate = null },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = stringResource(R.string.clear_date),
                                    tint = Color(0xFFE3E1E9),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    if (showDatePicker) {
        ExpressiveDatePickerDialog(
            onDateSelected = { date ->
                pickedDate = date
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}
