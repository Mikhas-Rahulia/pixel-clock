package io.github.mikhasrahulia.pixelclock.ui.alarm

import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import io.github.mikhasrahulia.pixelclock.ui.components.ExpressiveSwitch
import io.github.mikhasrahulia.pixelclock.ui.components.ExpressiveTimePickerDialog
import io.github.mikhasrahulia.pixelclock.ui.components.ScallopedShape
import io.github.mikhasrahulia.pixelclock.ui.components.groupedCardShape
import io.github.mikhasrahulia.pixelclock.ui.theme.ExpressiveMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ScallopedBadgeShape = ScallopedShape(lobes = 10, depthFraction = 0.08f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(
    repository: AlarmRepository,
    scheduler: AlarmScheduler,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val storedAlarms by repository.alarms.collectAsStateWithLifecycle()
    val alarms = remember(storedAlarms) {
        storedAlarms.sortedBy { it.hour * 60 + it.minute }
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isCreatingAlarm by remember { mutableStateOf(false) }
    var editingAlarmId by remember { mutableStateOf<String?>(null) }
    var editingTimeAlarm by remember { mutableStateOf<AlarmModel?>(null) }
    var pickingDateAlarm by remember { mutableStateOf<AlarmModel?>(null) }
    var pausingAlarmId by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Periodic 30s tick so upcoming alarm banner & relative countdowns stay fresh
    var minuteTick by remember { mutableLongStateOf(System.currentTimeMillis() / 30_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            minuteTick = System.currentTimeMillis() / 30_000L
        }
    }

    // Zero-permission SAF JSON Export & Import launchers
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val payload = repository.exportAlarmsJson()
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(payload.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                        true
                    }.getOrDefault(false)
                }
                snackbarHostState.showSnackbar(
                    context.getString(if (ok) R.string.backup_exported else R.string.backup_failed)
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val added = withContext(Dispatchers.IO) {
                    runCatching {
                        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: return@runCatching -1
                        val count = repository.importAlarmsJson(raw)
                        if (count >= 0) scheduler.rescheduleAllAlarms()
                        count
                    }.getOrDefault(-1)
                }
                snackbarHostState.showSnackbar(
                    if (added >= 0) context.getString(R.string.backup_imported, added)
                    else context.getString(R.string.backup_failed)
                )
            }
        }
    }

    // Soonest upcoming enabled alarm summary for the header banner
    val nextAlarmBanner = remember(alarms, minuteTick) {
        val nowInstant = Instant.now()
        alarms
            .filter { it.isEnabled }
            .mapNotNull { alarm -> alarm.nextTriggerMillisOrNull()?.let { alarm to it } }
            .minByOrNull { it.second }
            ?.let { (alarm, triggerMillis) ->
                val formattedWhen = alarm.getFormattedNextOccurrence(context)
                val relative = formatRelativeDuration(nowInstant, Instant.ofEpochMilli(triggerMillis))
                if (formattedWhen.isNotBlank()) "$formattedWhen · $relative" else relative
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Expressive Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 6.dp, start = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tab_alarm),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = AppIcons.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.backup_export)) },
                            onClick = {
                                menuExpanded = false
                                exportLauncher.launch("alarms-backup.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.backup_import)) },
                            onClick = {
                                menuExpanded = false
                                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            }
                        )
                    }
                }
            }

            // Live Next Alarm Pill
            AnimatedVisibility(
                visible = nextAlarmBanner != null,
                enter = fadeIn(ExpressiveMotion.fastEffects()) +
                    expandVertically(ExpressiveMotion.defaultSpatial()),
                exit = fadeOut(ExpressiveMotion.fastEffects()) +
                    shrinkVertically(ExpressiveMotion.defaultSpatial())
            ) {
                nextAlarmBanner?.let { banner ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = banner,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(ScallopedBadgeShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Alarm,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.no_alarms),
                            style = MaterialTheme.typography.titleLarge,
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
                        val isSelected = editingAlarmId == alarm.id
                        ExpressiveAlarmCard(
                            alarm = alarm,
                            minuteTick = minuteTick,
                            isSelected = isSelected,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                editingAlarmId = alarm.id
                            },
                            onToggleEnabled = { enabled ->
                                val updated = repository.toggleAlarm(alarm.id, enabled)
                                if (updated != null) {
                                    if (enabled) scheduler.scheduleAlarm(updated)
                                    else scheduler.cancelAlarm(alarm.id)
                                }
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 88.dp)
        )

        // Material 3 Expressive Rounded-Square Large FAB
        FloatingActionButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                isCreatingAlarm = true
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
                contentDescription = stringResource(R.string.add_alarm),
                modifier = Modifier.size(30.dp)
            )
        }
    }

    // Material 3 Expressive Bottom Sheet Alarm Editor (zero card-blink, native predictive back)
    editingAlarmId?.let { activeId ->
        val activeAlarm = alarms.firstOrNull { it.id == activeId }
        if (activeAlarm == null) {
            editingAlarmId = null
        } else {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { editingAlarmId = null },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                dragHandle = {
                    BottomSheetDefaults.DragHandle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            ) {
                AlarmEditorSheetContent(
                    alarm = activeAlarm,
                    minuteTick = minuteTick,
                    onEditTime = { editingTimeAlarm = activeAlarm },
                    onUpdateAlarm = { updated ->
                        repository.updateAlarm(updated)
                        scheduler.scheduleAlarm(updated)
                    },
                    onUpdateLabelOnly = { updated ->
                        repository.updateAlarm(updated)
                    },
                    onShiftCycle = {
                        val updated = repository.shiftAlarmCycle(activeAlarm.id)
                        if (updated != null) {
                            scheduler.scheduleAlarm(updated)
                        }
                    },
                    onPickDate = { pickingDateAlarm = activeAlarm },
                    onPickPause = { pausingAlarmId = activeAlarm.id },
                    onDelete = {
                        editingAlarmId = null
                        scheduler.cancelAlarm(activeAlarm.id)
                        repository.deleteAlarm(activeAlarm.id)
                    },
                    onDone = {
                        coroutineScope.launch {
                            sheetState.hide()
                            editingAlarmId = null
                        }
                    }
                )
            }
        }
    }

    // Create new alarm dialog
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
                isCreatingAlarm = false
                editingAlarmId = newAlarm.id
                coroutineScope.launch {
                    val idx = alarms.indexOfFirst { it.id == newAlarm.id }.coerceAtLeast(0)
                    listState.animateScrollToItem(idx)
                }
            },
            onDismiss = { isCreatingAlarm = false }
        )
    }

    // Edit alarm time dialog
    editingTimeAlarm?.let { alarmToEdit ->
        ExpressiveTimePickerDialog(
            initialHour = alarmToEdit.hour,
            initialMinute = alarmToEdit.minute,
            onConfirm = { hour, minute ->
                val updated = alarmToEdit.copy(hour = hour, minute = minute, isEnabled = true)
                repository.updateAlarm(updated)
                scheduler.scheduleAlarm(updated)
                editingTimeAlarm = null
            },
            onDismiss = { editingTimeAlarm = null }
        )
    }

    // Pick specific one-time date dialog
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

    // Pick pause range dialog
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
                onClear = if (alarm.pauseRange() != null) {
                    {
                        val updated = alarm.copy(pauseFrom = null, pauseUntil = null)
                        repository.updateAlarm(updated)
                        scheduler.scheduleAlarm(updated)
                        pausingAlarmId = null
                    }
                } else null,
                onDismiss = { pausingAlarmId = null }
            )
        }
    }
}

/**
 * Material 3 Expressive Alarm Card.
 * - Uses native clipped Surface ripple (no unclipped drawBehind square, no blinking).
 * - Smoothly shifts color and corner radius across Disabled -> Enabled -> Selected states.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpressiveAlarmCard(
    alarm: AlarmModel,
    minuteTick: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    val daysSummary = remember(alarm.daysOfWeek, alarm.specificDate, alarm.hour, alarm.minute, minuteTick) {
        alarm.getDaysSummary(context)
    }
    val formattedTime = remember(alarm.hour, alarm.minute) {
        String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
    }
    val relativeCountdown = remember(alarm, minuteTick) {
        if (!alarm.isEnabled) ""
        else alarm.nextTriggerMillisOrNull()?.let { millis ->
            formatRelativeDuration(Instant.now(), Instant.ofEpochMilli(millis))
        }.orEmpty()
    }

    val pauseRange = remember(alarm.pauseFrom, alarm.pauseUntil) { alarm.pauseRange() }
    val pauseDayFormat = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    val today = remember(minuteTick) { LocalDate.now() }
    val pauseText: String? = pauseRange?.takeIf { !it.second.isBefore(today) }?.let { (from, until) ->
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

    val isEnabled = alarm.isEnabled

    // Three unmistakable, smoothly animated color states (Disabled / Enabled / Selected)
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> colorScheme.tertiaryContainer
            isEnabled -> colorScheme.primaryContainer
            else -> colorScheme.surfaceContainerLow
        },
        animationSpec = ExpressiveMotion.fastEffects(),
        label = "alarmCardBg"
    )
    val primaryTextColor by animateColorAsState(
        targetValue = when {
            isSelected -> colorScheme.onTertiaryContainer
            isEnabled -> colorScheme.onPrimaryContainer
            else -> colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
        },
        animationSpec = ExpressiveMotion.fastEffects(),
        label = "alarmCardTitle"
    )
    val secondaryTextColor by animateColorAsState(
        targetValue = when {
            isSelected -> colorScheme.onTertiaryContainer.copy(alpha = 0.80f)
            isEnabled -> colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            else -> colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
        },
        animationSpec = ExpressiveMotion.fastEffects(),
        label = "alarmCardSub"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> colorScheme.tertiary
            isEnabled -> Color.Transparent
            else -> colorScheme.outlineVariant.copy(alpha = 0.18f)
        },
        animationSpec = ExpressiveMotion.fastEffects(),
        label = "alarmCardBorder"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 28.dp,
        animationSpec = ExpressiveMotion.fastSpatial(),
        label = "alarmCardCorner"
    )

    val hasLabel = alarm.label.isNotBlank() && !alarm.label.equals(daysSummary, ignoreCase = true)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = if (borderColor != Color.Transparent) BorderStroke(1.5.dp, borderColor) else null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            // Top schedule + optional status pills
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (alarm.isBiWeekly) {
                        "${stringResource(R.string.repeat_biweekly)} · $daysSummary"
                    } else {
                        daysSummary
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = secondaryTextColor
                )

                if (alarm.isBiWeekly) {
                    val weekStatus = stringResource(
                        if (alarm.isCurrentWeekActive()) R.string.active_this_week else R.string.active_next_week
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isEnabled || isSelected) {
                            primaryTextColor.copy(alpha = 0.14f)
                        } else {
                            colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = weekStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryTextColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                if (isEnabled && pauseText != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = pauseText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (hasLabel) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = primaryTextColor.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hero time + relative countdown + M3E Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 54.sp,
                            lineHeight = 58.sp
                        ),
                        color = primaryTextColor
                    )
                    if (relativeCountdown.isNotBlank()) {
                        Text(
                            text = relativeCountdown,
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }

                ExpressiveSwitch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    checkedTrackColor = if (isSelected) colorScheme.tertiary else colorScheme.primary,
                    checkedThumbColor = if (isSelected) colorScheme.onTertiary else colorScheme.onPrimary,
                    checkedIconColor = if (isSelected) colorScheme.tertiary else colorScheme.primary
                )
            }
        }
    }
}

/**
 * Material 3 Expressive Alarm Editor Bottom Sheet.
 * Replaces inline card expansion for zero layout jump, generous touch targets,
 * shape-morphing day pills, grouped M3E settings list, and 1-tap bi-weekly week switching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmEditorSheetContent(
    alarm: AlarmModel,
    minuteTick: Long,
    onEditTime: () -> Unit,
    onUpdateAlarm: (AlarmModel) -> Unit,
    onUpdateLabelOnly: (AlarmModel) -> Unit,
    onShiftCycle: () -> Unit,
    onPickDate: () -> Unit,
    onPickPause: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    val formattedTime = remember(alarm.hour, alarm.minute) {
        String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
    }
    val daysSummary = remember(alarm.daysOfWeek, alarm.specificDate, alarm.hour, alarm.minute, minuteTick) {
        alarm.getDaysSummary(context)
    }
    val nextOccurrence = remember(alarm, minuteTick) {
        alarm.nextTriggerMillisOrNull()?.let { millis ->
            formatRelativeDuration(Instant.now(), Instant.ofEpochMilli(millis))
        }.orEmpty()
    }

    val pauseRange = remember(alarm.pauseFrom, alarm.pauseUntil) { alarm.pauseRange() }
    val pauseDayFormat = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    val pauseRangeFormatted: String? = pauseRange?.let { (from, until) ->
        if (from == until) from.format(pauseDayFormat)
        else "${from.format(pauseDayFormat)} – ${until.format(pauseDayFormat)}"
    }

    var labelDraft by remember(alarm.id) { mutableStateOf(alarm.label) }
    LaunchedEffect(alarm.label) {
        if (alarm.label != labelDraft) labelDraft = alarm.label
    }
    fun commitLabelIfChanged() {
        if (labelDraft != alarm.label) {
            onUpdateLabelOnly(alarm.copy(label = labelDraft))
        }
    }
    DisposableEffect(alarm.id) {
        onDispose { commitLabelIfChanged() }
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
    ) {
        // 1. Monumental Clickable Time Display Card
        Surface(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onEditTime()
            },
            shape = RoundedCornerShape(28.dp),
            color = colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 62.sp,
                            lineHeight = 66.sp
                        ),
                        color = colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (nextOccurrence.isNotBlank()) {
                            "${stringResource(R.string.upcoming_alarm)} · $nextOccurrence"
                        } else {
                            daysSummary
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Edit,
                            contentDescription = null,
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.edit),
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Shape-Morphing Day of Week Selector
        ExpressiveDayOfWeekSelector(
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

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Material 3 Expressive Grouped Settings List (5 contiguous items with outer 24.dp / inner 8.dp corners)
        val groupItemCount = 5
        val itemContainer = colorScheme.surfaceContainerHigh

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Item 0: Every 2 Weeks + Active Week Segmented Switcher
            Surface(
                shape = groupedCardShape(0, groupItemCount),
                color = itemContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
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
                                .padding(end = 12.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Repeat,
                                contentDescription = null,
                                tint = if (alarm.isBiWeekly) colorScheme.primary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.repeat_biweekly),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = if (alarm.isBiWeekly) {
                                        stringResource(
                                            if (alarm.isCurrentWeekActive()) R.string.active_this_week
                                            else R.string.active_next_week
                                        )
                                    } else {
                                        stringResource(R.string.repeat_weekly)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (alarm.isBiWeekly) colorScheme.primary else colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        ExpressiveSwitch(
                            checked = alarm.isBiWeekly,
                            onCheckedChange = { checked ->
                                if (!cardRepeating && checked) {
                                    val day = cardPreview ?: AlarmModel.oneTimeDate(alarm.hour, alarm.minute).dayOfWeek.value
                                    onUpdateAlarm(alarm.copy(daysOfWeek = setOf(day), specificDate = null, isBiWeekly = true))
                                } else {
                                    onUpdateAlarm(alarm.copy(isBiWeekly = checked))
                                }
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = alarm.isBiWeekly,
                        enter = expandVertically(ExpressiveMotion.defaultSpatial()) + fadeIn(ExpressiveMotion.fastEffects()),
                        exit = shrinkVertically(ExpressiveMotion.defaultSpatial()) + fadeOut(ExpressiveMotion.fastEffects())
                    ) {
                        val isThisWeek = alarm.isCurrentWeekActive()
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            SegmentedButton(
                                selected = isThisWeek,
                                onClick = {
                                    if (!isThisWeek) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        onShiftCycle()
                                    }
                                },
                                icon = {},
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = colorScheme.secondaryContainer,
                                    activeContentColor = colorScheme.onSecondaryContainer,
                                    inactiveContainerColor = colorScheme.surfaceContainer,
                                    inactiveContentColor = colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.active_this_week),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            SegmentedButton(
                                selected = !isThisWeek,
                                onClick = {
                                    if (isThisWeek) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        onShiftCycle()
                                    }
                                },
                                icon = {},
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = colorScheme.secondaryContainer,
                                    activeContentColor = colorScheme.onSecondaryContainer,
                                    inactiveContainerColor = colorScheme.surfaceContainer,
                                    inactiveContentColor = colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.active_next_week),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // Item 1: Specific Date (One-time date picker)
            val isDateMode = alarm.specificDate != null
            Surface(
                onClick = onPickDate,
                shape = groupedCardShape(1, groupItemCount),
                color = itemContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = AppIcons.CalendarMonth,
                            contentDescription = null,
                            tint = if (isDateMode) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.set_alarm_date),
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = if (isDateMode) daysSummary else stringResource(R.string.select_date),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDateMode) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (isDateMode) {
                        IconButton(
                            onClick = { onUpdateAlarm(alarm.copy(specificDate = null)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = stringResource(R.string.clear_date),
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Item 2: Pause Between Dates
            Surface(
                onClick = onPickPause,
                shape = groupedCardShape(2, groupItemCount),
                color = itemContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
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
                            imageVector = AppIcons.PauseCircle,
                            contentDescription = null,
                            tint = if (pauseRange != null) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.pause_button),
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = pauseRangeFormatted ?: stringResource(R.string.pause_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pauseRange != null) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (pauseRange != null) {
                        Surface(
                            onClick = { onUpdateAlarm(alarm.copy(pauseFrom = null, pauseUntil = null)) },
                            shape = RoundedCornerShape(50),
                            color = colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.pause_resume),
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Item 3: Alarm Label
            Surface(
                shape = groupedCardShape(3, groupItemCount),
                color = itemContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppIcons.Label,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.alarm_name_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        BasicTextField(
                            value = labelDraft,
                            onValueChange = { labelDraft = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (!it.isFocused) commitLabelIfChanged() },
                            minLines = 1,
                            maxLines = 3,
                            cursorBrush = SolidColor(colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (labelDraft.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.alarm_name_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            // Item 4: Vibration Toggle
            Surface(
                shape = groupedCardShape(4, groupItemCount),
                color = itemContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AppIcons.Vibration,
                            contentDescription = null,
                            tint = if (alarm.vibrate) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.vibration),
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurface
                        )
                    }
                    ExpressiveSwitch(
                        checked = alarm.vibrate,
                        onCheckedChange = { onUpdateAlarm(alarm.copy(vibrate = it)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Footer Actions: Delete & Done
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    onDelete()
                },
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colorScheme.errorContainer,
                    contentColor = colorScheme.onErrorContainer
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = AppIcons.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.delete),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Button(
                onClick = {
                    commitLabelIfChanged()
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onDone()
                },
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                modifier = Modifier.weight(1.3f)
            ) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.save),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Material 3 Expressive shape-morphing Day-of-Week toggle pills:
 * unselected days are soft circles; selected days spring-morph into bold rounded squares in primary color.
 */
@Composable
fun ExpressiveDayOfWeekSelector(
    selectedDays: Set<Int>,
    onDaysChanged: (Set<Int>) -> Unit,
    previewDay: Int? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current

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
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { (dayIndex, dayLabel) ->
            val base = if (oncePreview && previewDay != null) setOf(previewDay) else selectedDays
            val isSelected = selectedDays.contains(dayIndex)
            val isPreviewOnly = oncePreview && previewDay == dayIndex

            val corner by animateDpAsState(
                targetValue = if (isSelected) 14.dp else 22.dp,
                animationSpec = ExpressiveMotion.fastSpatial(),
                label = "dayCorner"
            )
            val bg by animateColorAsState(
                targetValue = when {
                    isSelected -> colorScheme.primary
                    isPreviewOnly -> colorScheme.secondaryContainer
                    else -> colorScheme.surfaceContainerHigh
                },
                animationSpec = ExpressiveMotion.fastEffects(),
                label = "dayBg"
            )
            val fg by animateColorAsState(
                targetValue = when {
                    isSelected -> colorScheme.onPrimary
                    isPreviewOnly -> colorScheme.onSecondaryContainer
                    else -> colorScheme.onSurfaceVariant
                },
                animationSpec = ExpressiveMotion.fastEffects(),
                label = "dayFg"
            )

            Surface(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val newSet = if (base.contains(dayIndex)) base - dayIndex else base + dayIndex
                    onDaysChanged(newSet)
                },
                shape = RoundedCornerShape(corner),
                color = bg,
                border = if (isPreviewOnly) BorderStroke(1.5.dp, colorScheme.primary.copy(alpha = 0.6f)) else null,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected || isPreviewOnly) FontWeight.Bold else FontWeight.Medium,
                        color = fg
                    )
                }
            }
        }
    }
}

private fun formatRelativeDuration(now: Instant, target: Instant): String {
    val diff = Duration.between(now, target)
    val totalMins = diff.toMinutes().coerceAtLeast(1L)
    val days = totalMins / (24 * 60)
    val hours = (totalMins % (24 * 60)) / 60
    val minutes = totalMins % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
