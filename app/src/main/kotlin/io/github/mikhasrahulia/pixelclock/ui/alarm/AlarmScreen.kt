package io.github.mikhasrahulia.pixelclock.ui.alarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.PauseCircle
import io.github.mikhasrahulia.pixelclock.ui.components.ExpressiveDateRangePickerDialog
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mikhasrahulia.pixelclock.R
import io.github.mikhasrahulia.pixelclock.alarm.AlarmScheduler
import io.github.mikhasrahulia.pixelclock.data.AlarmModel
import io.github.mikhasrahulia.pixelclock.data.AlarmRepository
import io.github.mikhasrahulia.pixelclock.ui.components.ExpressiveDatePickerDialog
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
    // Sorted by time of day only (hour, then minute). The date is ignored on purpose.
    val alarms = remember(storedAlarms) {
        storedAlarms.sortedBy { it.hour * 60 + it.minute }
    }
    var isCreatingAlarm by remember { mutableStateOf(false) }
    var expandedAlarmId by remember { mutableStateOf<String?>(null) }
    var editingTimeAlarm by remember { mutableStateOf<AlarmModel?>(null) }
    var pickingDateAlarm by remember { mutableStateOf<AlarmModel?>(null) }
    var pausingAlarmId by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    // Tapping empty space drops focus, which also closes the keyboard. Child elements
    // (cards, buttons, the name field) consume their own taps, so this only fires on background.
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
            // Snappy compact header
            Text(
                text = stringResource(R.string.tab_alarm),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 4.dp)
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
                    contentPadding = PaddingValues(bottom = 88.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

        // Floating Action Button cleanly positioned above bottom nav
        FloatingActionButton(
            onClick = { isCreatingAlarm = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
                .size(64.dp),
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.add_alarm),
                modifier = Modifier.size(30.dp)
            )
        }
    }

    // New Alarm Creation Dialog / Sheet
    if (isCreatingAlarm) {
        CreateOrEditAlarmDialog(
            initialAlarm = null,
            onSave = { newAlarm ->
                repository.addAlarm(newAlarm)
                scheduler.scheduleAlarm(newAlarm)
                isCreatingAlarm = false
            },
            onDismiss = { isCreatingAlarm = false }
        )
    }

    // Time edit dialog for existing alarm
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

    // Date picker for existing alarm
    pickingDateAlarm?.let { alarmToEdit ->
        ExpressiveDatePickerDialog(
            onDateSelected = { pickedDate ->
                val dateStr = pickedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val updated = alarmToEdit.copy(
                    specificDate = dateStr,
                    daysOfWeek = emptySet() // Specific date supersedes repeating days
                )
                repository.updateAlarm(updated)
                scheduler.scheduleAlarm(updated)
                pickingDateAlarm = null
            },
            onDismiss = { pickingDateAlarm = null }
        )
    }

    // Pause between dates (one day or a range). Always works on the latest saved alarm.
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

/**
 * Streamlined Material 3 Expressive Dialog for creating or editing an alarm.
 * Features direct numeric TimeInput without dial aiming, single-line optional naming,
 * and zero bloat buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // A new alarm is always one-time. The date follows the chosen time (today if it is still
    // ahead, otherwise tomorrow) until the user picks a date by hand.
    val autoDate = AlarmModel.oneTimeDate(timePickerState.hour, timePickerState.minute)
    val shownDate = pickedDate ?: autoDate
    val onceLabel = shownDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))

    PredictiveBackLayer(onDismiss = onDismiss) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
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
                        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(
                        onClick = { isKeyboardMode = !isKeyboardMode },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isKeyboardMode) Icons.Rounded.Schedule else Icons.Rounded.Keyboard,
                            contentDescription = stringResource(R.string.toggle_mode),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Button(
                        onClick = {
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
                        },
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
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
                            clockDialColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectorColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                            clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                            timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                            timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tap the chip to choose another date. The cross returns to the automatic date.
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = if (pickedDate != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(26.dp))
                        .clickable { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val chipContent = if (pickedDate != null) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onTertiaryContainer
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = chipContent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${stringResource(R.string.once)} · $onceLabel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = chipContent
                        )
                        if (pickedDate != null) {
                            IconButton(
                                onClick = { pickedDate = null },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = stringResource(R.string.clear_date),
                                    tint = chipContent,
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
    val focusManager = LocalFocusManager.current
    var nameFocused by remember { mutableStateOf(false) }

    // Pause status, shown on the card and in the editor. Only while the pause has not fully passed.
    val pauseRange = alarm.pauseRange()?.takeIf { alarm.hasPauseAhead() }
    val pauseDayFormat = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    // Non-breaking space so "20 Nov" is never split across two lines
    fun pauseDay(date: LocalDate): String = date.format(pauseDayFormat).replace(' ', '\u00A0')
    val pauseText: String? = pauseRange?.let { (from, until) ->
        val today = LocalDate.now()
        when {
            !from.isAfter(today) && until == today -> stringResource(R.string.pause_today)
            !from.isAfter(today) -> stringResource(R.string.pause_until, pauseDay(until))
            from == until -> stringResource(R.string.pause_single, pauseDay(from))
            else -> stringResource(
                R.string.pause_range,
                pauseDay(from),
                pauseDay(until)
            )
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMedium))
            // Only a collapsed card expands on tap. Taps inside an open card must not collapse it.
            .clickable(enabled = !isExpanded) { onToggleExpand() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (alarm.isEnabled) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (alarm.isEnabled) 2.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            // Main Top Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isExpanded) { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onEditTime() }
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val scheduleText = if (alarm.isBiWeekly) {
                        "${stringResource(R.string.repeat_biweekly)} ($daysSummary)"
                    } else {
                        daysSummary
                    }

                    val hasLabel = alarm.label.isNotBlank() && !alarm.label.equals(daysSummary, ignoreCase = true)
                    val subtitleText = if (hasLabel) {
                        "${alarm.label}  ·  $scheduleText"
                    } else {
                        scheduleText
                    }

                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            !alarm.isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            alarm.isBiWeekly -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (alarm.isEnabled && alarm.isBiWeekly) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (alarm.isEnabled && pauseText != null) {
                        Text(
                            text = pauseText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(if (isExpanded) R.string.collapse else R.string.expand),
                            tint = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Expanded Details Section
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(spring(stiffness = Spring.StiffnessHigh)) + expandVertically(spring(stiffness = Spring.StiffnessMedium)),
                exit = fadeOut(spring(stiffness = Spring.StiffnessHigh)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    // Direct inline Label Field (Edit directly right on card!)
                    OutlinedTextField(
                        value = alarm.label,
                        onValueChange = { onUpdateAlarm(alarm.copy(label = it)) },
                        singleLine = false,
                        minLines = 1,
                        maxLines = 3,
                        placeholder = { Text(stringResource(R.string.alarm_name_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Label,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        // Small confirm button, only while typing. The name is already saved on each keystroke.
                        trailingIcon = {
                            if (nameFocused) {
                                IconButton(
                                    onClick = { focusManager.clearFocus() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .onFocusChanged { nameFocused = it.isFocused }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrightPill(
                            text = stringResource(R.string.repeat_biweekly),
                            selected = alarm.isBiWeekly && cardRepeating,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!cardRepeating) {
                                    val day = cardPreview ?: AlarmModel.oneTimeDate(alarm.hour, alarm.minute).dayOfWeek.value
                                    onUpdateAlarm(alarm.copy(daysOfWeek = setOf(day), specificDate = null, isBiWeekly = true))
                                } else {
                                    onUpdateAlarm(alarm.copy(isBiWeekly = !alarm.isBiWeekly))
                                }
                            }
                        )
                        val isDateMode = alarm.specificDate != null
                        val dateLabel = if (!alarm.specificDate.isNullOrBlank()) {
                            try {
                                LocalDate.parse(alarm.specificDate).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
                            } catch (_: Exception) {
                                stringResource(R.string.specific_date)
                            }
                        } else {
                            stringResource(R.string.specific_date)
                        }
                        BrightPill(
                            text = dateLabel,
                            selected = isDateMode,
                            icon = if (isDateMode) Icons.Rounded.Clear else Icons.Rounded.CalendarMonth,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (isDateMode) {
                                    onUpdateAlarm(alarm.copy(specificDate = null, daysOfWeek = emptySet(), isBiWeekly = false))
                                } else {
                                    onPickDate()
                                }
                            }
                        )
                    }

                    // Compact Bi-weekly shift strip (Zero bloat!)
                    if (alarm.isBiWeekly) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val isThisWeekActive = alarm.isCurrentWeekActive()

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(
                                        if (isThisWeekActive) R.string.active_this_week else R.string.active_next_week
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )

                                FilledTonalButton(
                                    onClick = onShiftCycle,
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SyncAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.shift_cycle),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // Pause between dates: one day or a range, then the alarm resumes by itself
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (pauseText != null) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onPickPause() }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .fillMaxWidth()
                                .heightIn(min = 40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PauseCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = pauseText ?: stringResource(R.string.pause_button),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (pauseText != null) {
                                FilledTonalButton(
                                    onClick = { onUpdateAlarm(alarm.copy(pauseFrom = null, pauseUntil = null)) },
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.pause_resume),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Vibration & Delete Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onUpdateAlarm(alarm.copy(vibrate = !alarm.vibrate)) }
                                .padding(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Vibration,
                                contentDescription = null,
                                tint = if (alarm.vibrate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.vibration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
        1 to stringResource(R.string.day_mon),
        2 to stringResource(R.string.day_tue),
        3 to stringResource(R.string.day_wed),
        4 to stringResource(R.string.day_thu),
        5 to stringResource(R.string.day_fri),
        6 to stringResource(R.string.day_sat),
        7 to stringResource(R.string.day_sun)
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
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer
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
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun BrightPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    // Highlighted ONLY while it is really switched on. Otherwise it is plain text on an outline.
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = background,
        border = if (selected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
