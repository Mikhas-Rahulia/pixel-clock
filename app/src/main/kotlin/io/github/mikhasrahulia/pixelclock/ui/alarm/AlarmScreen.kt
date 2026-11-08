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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
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
                    state = listState,
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
            try {
                LocalDate.parse(dateStr).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
        ExpressiveDatePickerDialog(
            initialDateMillis = currentSelected,
            onDateSelected = { pickedDate ->
                val updated = alarmToPick.copy(
                    specificDate = pickedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    daysOfWeek = emptySet()
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
            val initialStart = alarm.pauseFrom?.let {
                try {
                    LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
            val initialEnd = alarm.pauseUntil?.let {
                try {
                    LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
            val parseStart = alarm.pauseFrom?.let {
                try { LocalDate.parse(it) } catch (_: Exception) { null }
            }
            val parseEnd = alarm.pauseUntil?.let {
                try { LocalDate.parse(it) } catch (_: Exception) { null }
            }
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
    val daysSummary = remember(alarm.daysOfWeek, alarm.specificDate) {
        alarm.getDaysSummary(context)
    }
    val nextOccurrence = remember(alarm) {
        val nextMillis = alarm.nextTriggerMillisOrNull()
        if (nextMillis != null) {
            val diff = java.time.Duration.between(
                java.time.Instant.now(),
                java.time.Instant.ofEpochMilli(nextMillis)
            )
            val days = diff.toDays()
            val hours = diff.toHours() % 24
            val minutes = diff.toMinutes() % 60
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
            try {
                LocalDate.parse(alarm.pauseFrom) to LocalDate.parse(alarm.pauseUntil)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    val pauseDayFormat = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    val pauseRangeFormatted: String? = pauseRange?.let { (from, until) ->
        if (from == until) {
            from.format(pauseDayFormat)
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

    // Cohesive color styling: Enabled alarms are bright/light (#DEDEE6), Disabled alarms are dark grey (#1E1E24).
    // Tapping to expand/collapse preserves this color family so there is ZERO radical color flip!
    val isLightCard = alarm.isEnabled
    val targetContainerColor = if (isLightCard) Color(0xFFDEDEE6) else Color(0xFF1E1E24)
    val animatedContainerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 180),
        label = "alarmCardContainerColor"
    )

    // Enabled alarms are light with dark high-contrast text (#191C20); Disabled alarms are muted dark grey with soft grey text (#75747D) - NEVER bright white!
    val primaryTextColor = if (isLightCard) Color(0xFF191C20) else Color(0xFF75747D)
    val secondaryTextColor = if (isLightCard) Color(0xFF49454F) else Color(0xFF55545D)
    val chipBg = if (isLightCard) Color(0xFFCECED8) else Color(0xFF28272F)
    val pillDarkBg = if (isLightCard) Color(0xFF2E2D35) else Color(0xFF33323B)
    val pillDarkText = if (isLightCard) Color(0xFFFFFFFF) else Color(0xFFC6C5D0)

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
            .clip(cardShape)
            .animateContentSize(spring(stiffness = Spring.StiffnessMedium))
            .drawBehind {
                drawRect(animatedContainerColor)
                if (rippleScale > 0f && rippleScale < 1f) {
                    val center = tapCenter ?: Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = hypot(size.width, size.height)
                    val rippleColor = if (isLightCard) Color(0x14000000) else Color(0x1FFFFFFF)
                    drawCircle(
                        color = rippleColor,
                        radius = maxRadius * rippleScale,
                        center = center
                    )
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

                // Top: Schedule & Multiline Label
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
                        color = if (alarm.isEnabled) Color(0xFFBA1A1A) else Color(0xFFF5C76B),
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
                        color = primaryTextColor
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
                // Drag handle at top (tap to collapse)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
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
                            .background(if (isLightCard) Color(0xFF9E9DA8) else Color(0xFF75747D))
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
                        color = primaryTextColor,
                        modifier = Modifier.clickable { onEditTime() }
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = pillDarkBg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onEditTime() }
                    ) {
                        Text(
                            text = stringResource(R.string.edit),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = pillDarkText,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }

                // Dedicated Bi-Weekly Recurrence & Shift 2 Weeks Controls
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = chipBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
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
                                    imageVector = Icons.Rounded.Alarm,
                                    contentDescription = null,
                                    tint = if (alarm.isBiWeekly) (if (isLightCard) Color(0xFF191C20) else Color(0xFFFFFFFF)) else secondaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.repeat_biweekly),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
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
                                    checkedThumbColor = if (isLightCard) Color(0xFFFFFFFF) else Color(0xFF191C20),
                                    checkedTrackColor = if (isLightCard) Color(0xFF191C20) else Color(0xFFE2E2E9),
                                    uncheckedThumbColor = Color(0xFF8E8D96),
                                    uncheckedTrackColor = Color(0xFF33323B),
                                    uncheckedBorderColor = Color.Transparent
                                )
                            )
                        }

                        if (alarm.isBiWeekly) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.shift_cycle),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryTextColor
                                )
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = pillDarkBg,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onShiftCycle() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                    }
                }

                // Day chips (circular 38dp chips: P W Ś C P S N)
                DayOfWeekSelector(
                    selectedDays = if (cardRepeating) alarm.daysOfWeek else emptySet(),
                    previewDay = cardPreview,
                    isLightCard = isLightCard,
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
                            color = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = nextOccurrence.ifBlank { daysSummary },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor
                        )
                    }

                    val isDateMode = alarm.specificDate != null
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = chipBg,
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

                // Pause Card (Matching Screenshot 4)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = chipBg,
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
                                tint = secondaryTextColor,
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
                                    color = primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.edit),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryTextColor,
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
                                color = pillDarkBg,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onUpdateAlarm(alarm.copy(pauseFrom = null, pauseUntil = null)) }
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = pillDarkText,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Alarm Name (Nazwa alarmu) container
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = chipBg,
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
                                tint = secondaryTextColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.alarm_name_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor
                            )
                        }
                        BasicTextField(
                            value = alarm.label,
                            onValueChange = { onUpdateAlarm(alarm.copy(label = it)) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = primaryTextColor,
                                fontSize = 16.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp),
                            minLines = 1,
                            maxLines = 4,
                            cursorBrush = SolidColor(primaryTextColor),
                            decorationBox = { innerTextField ->
                                if (alarm.label.isEmpty()) {
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

                // Vibration row
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = chipBg,
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
                                tint = secondaryTextColor,
                                modifier = Modifier.size(22.dp)
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
                                checkedThumbColor = if (isLightCard) Color(0xFFFFFFFF) else Color(0xFF191C20),
                                checkedTrackColor = if (isLightCard) Color(0xFF191C20) else Color(0xFFE2E2E9),
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
                        color = if (isLightCard) Color(0xFFFBEBEB) else Color(0xFF2C2828),
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onDelete() }
                    ) {
                        Text(
                            text = stringResource(R.string.delete),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFBA1A1A),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = pillDarkBg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onToggleExpand() }
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = pillDarkText,
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
    isLightCard: Boolean = false
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
                        if (isSelected) {
                            if (isLightCard) Color(0xFF191C20) else Color(0xFFFFFFFF)
                        } else {
                            if (isLightCard) Color(0xFFCECED8) else Color(0xFF2B2A32)
                        }
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
                    color = if (isSelected) {
                        if (isLightCard) Color(0xFFFFFFFF) else Color(0xFF191C20)
                    } else {
                        if (isLightCard) Color(0xFF49454F) else Color(0xFFC6C5D0)
                    }
                )
            }
        }
    }
}
