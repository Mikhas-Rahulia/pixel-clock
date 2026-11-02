package io.github.mikhasrahulia.pixelclock.timer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.mikhasrahulia.pixelclock.data.TimerHourStat
import io.github.mikhasrahulia.pixelclock.data.TimerModel
import io.github.mikhasrahulia.pixelclock.data.TimerRepository
import io.github.mikhasrahulia.pixelclock.data.TimerRunRecord
import io.github.mikhasrahulia.pixelclock.data.summarizeTimerHour
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var tickerJob: Job? = null
    private val repository = TimerRepository.getInstance(context)
    private val scheduler = TimerScheduler.getInstance(context)

    private val _timers = MutableStateFlow(repository.loadTimers())
    val timers: StateFlow<List<TimerModel>> = _timers.asStateFlow()

    private val _runs = MutableStateFlow(repository.loadRuns())
    val runs: StateFlow<List<TimerRunRecord>> = _runs.asStateFlow()

    fun hourStat(durationMillis: Long, now: Long = System.currentTimeMillis()): TimerHourStat {
        return summarizeTimerHour(_runs.value, _timers.value, durationMillis, now)
    }

    fun reconcile(acknowledgeStale: Boolean = false) {
        val now = System.currentTimeMillis()
        val fired = mutableListOf<TimerModel>()
        val next = _timers.value.map { timer ->
            if (timer.hasFired && acknowledgeStale) {
                // Already rang in a previous run of the app: acknowledge it, never ring again.
                scheduler.cancel(timer.id)
                timer.reset()
            } else if (timer.isRunning && !timer.isPaused && !timer.hasFired && timer.targetEndTimeMillis > 0L) {
                if (timer.targetEndTimeMillis <= now) {
                    val done = finishTimer(timer, now)
                    fired += done
                    done
                } else {
                    scheduler.schedule(timer)
                    timer.copy(remainingMillis = timer.targetEndTimeMillis - now)
                }
            } else {
                if (!timer.isRunning) scheduler.cancel(timer.id)
                timer
            }
        }
        commit(next)
        ensureTickerRunning()
        syncWithService()
        fired.forEach { deliverDue(it.id) }
    }

    fun createAndStartTimer(durationMillis: Long, label: String = ""): TimerModel {
        val now = System.currentTimeMillis()
        val timer = TimerModel(
            initialDurationMillis = durationMillis,
            originDurationMillis = durationMillis,
            remainingMillis = durationMillis,
            isRunning = true,
            isPaused = false,
            label = label,
            targetEndTimeMillis = now + durationMillis,
            startedAtMillis = now,
            runAnchorMillis = now
        )
        recordStart(timer)
        commit(_timers.value + timer)
        scheduler.schedule(timer)
        ensureTickerRunning()
        syncWithService()
        return timer
    }

    fun startTimer(timerId: String) {
        val now = System.currentTimeMillis()
        var started: TimerModel? = null
        val next = _timers.value.map { timer ->
            if (timer.id != timerId) timer
            else {
                val fresh = timer.hasFired || timer.startedAtMillis == 0L
                val base = if (fresh) {
                    timer.copy(
                        bankedRunMillis = 0L,
                        runAnchorMillis = 0L,
                        startedAtMillis = 0L,
                        hasFired = false,
                        isPaused = false,
                        isRunning = false,
                        remainingMillis = timer.originDurationMillis,
                        initialDurationMillis = timer.originDurationMillis
                    )
                } else timer
                val running = base.start(now)
                if (fresh) recordStart(running)
                started = running
                running
            }
        }
        commit(next)
        started?.let { scheduler.schedule(it) }
        ensureTickerRunning()
        syncWithService()
    }

    fun pauseTimer(timerId: String) {
        val now = System.currentTimeMillis()
        var paused: TimerModel? = null
        val next = _timers.value.map { timer ->
            if (timer.id != timerId) timer
            else timer.pause(now).also { paused = it }
        }
        paused?.let { updateRun(it.id, it.startedAtMillis, it.completedFullMinutes(now)) }
        commit(next)
        scheduler.cancel(timerId)
        checkStopTicker()
        syncWithService()
    }

    fun resetTimer(timerId: String) {
        val now = System.currentTimeMillis()
        val current = _timers.value.find { it.id == timerId }
        if (current != null && current.startedAtMillis > 0L) {
            updateRun(current.id, current.startedAtMillis, current.completedFullMinutes(now))
        }
        commit(_timers.value.map { if (it.id == timerId) it.reset() else it })
        scheduler.cancel(timerId)
        checkStopTicker()
        syncWithService()
    }

    fun deleteTimer(timerId: String) {
        val now = System.currentTimeMillis()
        val current = _timers.value.find { it.id == timerId }
        if (current != null && current.startedAtMillis > 0L && !current.hasFired) {
            updateRun(current.id, current.startedAtMillis, current.completedFullMinutes(now))
        }
        commit(_timers.value.filterNot { it.id == timerId })
        scheduler.cancel(timerId)
        checkStopTicker()
        syncWithService()
    }

    fun addFiveMinutes(timerId: String) {
        val now = System.currentTimeMillis()
        var updated: TimerModel? = null
        val next = _timers.value.map { timer ->
            if (timer.id != timerId) timer
            else {
                val changed = if (timer.hasFired || (!timer.isRunning && timer.remainingMillis <= 0L)) {
                    timer.snoozeFive(now)
                } else {
                    timer.addMinutes(5, now).let { added ->
                        if (!added.isRunning) added else added
                    }
                }
                updated = if (changed.isRunning) changed else {
                    // Adding time to a paused or idle timer should keep it countable.
                    if (timer.hasFired) changed else changed
                }
                changed
            }
        }
        commit(next)
        updated?.let { timer ->
            if (timer.isRunning && !timer.isPaused) scheduler.schedule(timer) else scheduler.cancel(timer.id)
        }
        ensureTickerRunning()
        syncWithService()
    }

    fun addOneMinute(timerId: String) {
        val now = System.currentTimeMillis()
        var updated: TimerModel? = null
        val next = _timers.value.map { timer ->
            if (timer.id != timerId) timer
            else timer.addMinutes(1, now).also { updated = it }
        }
        commit(next)
        updated?.let { if (it.isRunning && !it.isPaused) scheduler.schedule(it) }
        ensureTickerRunning()
        syncWithService()
    }

    fun updateLabel(timerId: String, newLabel: String) {
        commit(_timers.value.map { if (it.id == timerId) it.copy(label = newLabel) else it })
        syncWithService()
    }

    /**
     * Called when AlarmManager fires, including after the process was killed.
     */
    fun onSystemAlarm(timerId: String) {
        val now = System.currentTimeMillis()
        val current = _timers.value.find { it.id == timerId } ?: return
        if (current.hasFired || !current.isRunning || current.isPaused) return
        if (current.targetEndTimeMillis > now + 1500L) return
        val done = finishTimer(current, now.coerceAtLeast(current.targetEndTimeMillis))
        commit(_timers.value.map { if (it.id == timerId) done else it })
        scheduler.cancel(timerId)
        checkStopTicker()
        deliverDue(timerId)
    }

    private fun finishTimer(timer: TimerModel, now: Long): TimerModel {
        val minutes = timer.completedFullMinutes(now).coerceAtLeast(timer.originFullMinutes())
        updateRun(timer.id, timer.startedAtMillis, minutes)
        return timer.copy(
            isRunning = false,
            isPaused = false,
            hasFired = true,
            remainingMillis = 0L,
            targetEndTimeMillis = 0L,
            bankedRunMillis = minutes * 60_000L,
            runAnchorMillis = 0L
        )
    }

    private fun TimerModel.originFullMinutes(): Int {
        return (originDurationMillis / 60_000L).toInt()
    }

    private fun deliverDue(timerId: String) {
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_TIMER_DUE
            putExtra(TimerService.EXTRA_TIMER_ID, timerId)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start timer alert", e)
        }
    }

    private fun recordStart(timer: TimerModel) {
        pruneRuns(timer.startedAtMillis)
        val record = TimerRunRecord(
            timerId = timer.id,
            durationMillis = timer.originDurationMillis,
            startedAtMillis = timer.startedAtMillis,
            completedMinutes = 0
        )
        val next = _runs.value.filterNot { it.timerId == timer.id && it.startedAtMillis == timer.startedAtMillis } + record
        _runs.value = next
        repository.saveRuns(next)
    }

    private fun updateRun(timerId: String, startedAtMillis: Long, completedMinutes: Int) {
        if (startedAtMillis <= 0L) return
        val next = _runs.value.map { record ->
            if (record.timerId == timerId && record.startedAtMillis == startedAtMillis) {
                record.copy(completedMinutes = completedMinutes.coerceAtLeast(record.completedMinutes))
            } else record
        }
        _runs.value = next
        repository.saveRuns(next)
    }

    private fun pruneRuns(now: Long) {
        val cutoff = now - 60L * 60L * 1000L
        val pruned = _runs.value.filter { it.startedAtMillis >= cutoff }
        if (pruned.size != _runs.value.size) {
            _runs.value = pruned
            repository.saveRuns(pruned)
        }
    }

    private fun commit(list: List<TimerModel>) {
        _timers.value = list
        repository.saveTimers(list)
    }

    private fun ensureTickerRunning() {
        if (tickerJob?.isActive == true) return
        if (_timers.value.none { it.isRunning && !it.isPaused && !it.hasFired }) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(500)
                val now = System.currentTimeMillis()
                val due = _timers.value.filter {
                    it.isRunning && !it.isPaused && !it.hasFired &&
                        it.targetEndTimeMillis > 0L && it.targetEndTimeMillis <= now
                }
                if (due.isEmpty()) {
                    if (_timers.value.none { it.isRunning && !it.isPaused && !it.hasFired }) break
                    continue
                }
                var list = _timers.value
                due.forEach { timer ->
                    val done = finishTimer(timer, now)
                    list = list.map { if (it.id == timer.id) done else it }
                    scheduler.cancel(timer.id)
                }
                commit(list)
                due.forEach { deliverDue(it.id) }
                if (list.none { it.isRunning && !it.isPaused && !it.hasFired }) break
            }
            tickerJob = null
        }
    }

    private fun checkStopTicker() {
        if (_timers.value.none { it.isRunning && !it.isPaused && !it.hasFired }) {
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    private fun syncWithService() {
        val active = _timers.value.any { (it.isRunning || it.isPaused) && !it.hasFired }
        val intent = Intent(context, TimerService::class.java)
        if (active) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Foreground service start blocked", e)
            }
        } else if (_timers.value.none { it.hasFired }) {
            context.stopService(intent)
        }
    }

    companion object {
        private const val TAG = "TimerManager"

        @Volatile
        private var instance: TimerManager? = null

        fun getInstance(context: Context): TimerManager {
            return instance ?: synchronized(this) {
                instance ?: TimerManager(context.applicationContext).also {
                    instance = it
                    it.reconcile(acknowledgeStale = true)
                }
            }
        }
    }
}
