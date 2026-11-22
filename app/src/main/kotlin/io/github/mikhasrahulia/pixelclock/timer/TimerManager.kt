package io.github.mikhasrahulia.pixelclock.timer

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import io.github.mikhasrahulia.pixelclock.data.TimerHourStat
import io.github.mikhasrahulia.pixelclock.data.TimerModel
import io.github.mikhasrahulia.pixelclock.data.TimerRepository
import io.github.mikhasrahulia.pixelclock.data.TimerRunRecord
import io.github.mikhasrahulia.pixelclock.data.summarizeTimerHour
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimerManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var nextDueJob: Job? = null
    private val repository = TimerRepository.getInstance(context)
    private val scheduler = TimerScheduler.getInstance(context)

    private val _timers = MutableStateFlow(repository.loadTimers())
    val timers: StateFlow<List<TimerModel>> = _timers.asStateFlow()

    private val _runs = MutableStateFlow(repository.loadRuns())
    val runs: StateFlow<List<TimerRunRecord>> = _runs.asStateFlow()

    fun hourStat(durationMillis: Long, now: Long = System.currentTimeMillis()): TimerHourStat {
        return summarizeTimerHour(_runs.value, _timers.value, durationMillis, now)
    }

    fun reconcile(acknowledgeStale: Boolean = false, fromBoot: Boolean = false) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val fired = mutableListOf<TimerModel>()
        val next = _timers.value.map { timer ->
            if (timer.hasFired && acknowledgeStale) {
                scheduler.cancel(timer.id)
                timer.reset()
            } else if (timer.isRunning && !timer.isPaused && !timer.hasFired) {
                // On boot or legacy migration, elapsedRealtime resets to 0; reconstruct from wall-clock targetEndTimeMillis
                val rem = if (fromBoot || timer.targetElapsedRealtimeMillis <= 0L) {
                    (timer.targetEndTimeMillis - now).coerceAtLeast(0L)
                } else {
                    timer.calculateCurrentRemaining(now, elapsed)
                }
                if (rem <= 0L) {
                    val done = finishTimer(timer, now, elapsed)
                    fired += done
                    done
                } else {
                    val refreshed = timer.copy(
                        remainingMillis = rem,
                        targetEndTimeMillis = now + rem,
                        targetElapsedRealtimeMillis = elapsed + rem,
                        runAnchorElapsedMillis = if (fromBoot || timer.runAnchorElapsedMillis <= 0L) elapsed else timer.runAnchorElapsedMillis
                    )
                    scheduler.schedule(refreshed)
                    refreshed
                }
            } else {
                if (!timer.isRunning) scheduler.cancel(timer.id)
                timer
            }
        }
        commit(next)
        scheduleNextLocalDue()
        if (!fromBoot) {
            syncWithService()
            fired.forEach { deliverDue(it.id) }
        }
    }

    fun createAndStartTimer(durationMillis: Long, label: String = ""): TimerModel {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val timer = TimerModel(
            initialDurationMillis = durationMillis,
            originDurationMillis = durationMillis,
            remainingMillis = durationMillis,
            isRunning = true,
            isPaused = false,
            label = label,
            targetEndTimeMillis = now + durationMillis,
            targetElapsedRealtimeMillis = elapsed + durationMillis,
            startedAtMillis = now,
            runAnchorMillis = now,
            runAnchorElapsedMillis = elapsed
        )
        recordStart(timer)
        commit(_timers.value + timer)
        scheduler.schedule(timer)
        scheduleNextLocalDue()
        syncWithService()
        return timer
    }

    fun startTimer(timerId: String) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        var started: TimerModel? = null
        val next = _timers.value.map { timer ->
            if (timer.id != timerId) timer
            else {
                val fresh = timer.hasFired || timer.startedAtMillis == 0L
                val base = if (fresh) {
                    timer.copy(
                        bankedRunMillis = 0L,
                        runAnchorMillis = 0L,
                        runAnchorElapsedMillis = 0L,
                        startedAtMillis = 0L,
                        hasFired = false,
                        isPaused = false,
                        isRunning = false,
                        remainingMillis = timer.originDurationMillis,
                        initialDurationMillis = timer.originDurationMillis
                    )
                } else timer
                val running = base.start(now, elapsed)
                if (fresh) recordStart(running)
                started = running
                running
            }
        }
        commit(next)
        started?.let { scheduler.schedule(it) }
        scheduleNextLocalDue()
        syncWithService()
    }

    fun pauseTimer(timerId: String) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        var paused: TimerModel? = null
        val next = _timers.value.map { timer ->
            if (timer.id != timerId) timer
            else timer.pause(now, elapsed).also { paused = it }
        }
        paused?.let { updateRun(it.id, it.startedAtMillis, it.completedFullMinutes(now, elapsed)) }
        commit(next)
        scheduler.cancel(timerId)
        scheduleNextLocalDue()
        syncWithService()
    }

    fun resetTimer(timerId: String) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val current = _timers.value.find { it.id == timerId }
        if (current != null && current.startedAtMillis > 0L) {
            updateRun(current.id, current.startedAtMillis, current.completedFullMinutes(now, elapsed))
        }
        commit(_timers.value.map { if (it.id == timerId) it.reset() else it })
        scheduler.cancel(timerId)
        scheduleNextLocalDue()
        syncWithService()
    }

    fun deleteTimer(timerId: String) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val current = _timers.value.find { it.id == timerId }
        if (current != null && current.startedAtMillis > 0L && !current.hasFired) {
            updateRun(current.id, current.startedAtMillis, current.completedFullMinutes(now, elapsed))
        }
        commit(_timers.value.filterNot { it.id == timerId })
        scheduler.cancel(timerId)
        scheduleNextLocalDue()
        syncWithService()
    }

    fun addFiveMinutes(timerId: String) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        var updated: TimerModel? = null
        val next = _timers.value.map { timer ->
            if (timer.id != timerId) timer
            else {
                val changed = if (timer.hasFired || (!timer.isRunning && timer.remainingMillis <= 0L)) {
                    timer.snoozeFive(now, elapsed)
                } else {
                    timer.addMinutes(5, now, elapsed)
                }
                updated = changed
                changed
            }
        }
        commit(next)
        updated?.let { timer ->
            if (timer.isRunning && !timer.isPaused) scheduler.schedule(timer) else scheduler.cancel(timer.id)
        }
        scheduleNextLocalDue()
        syncWithService()
    }

    fun addOneMinute(timerId: String) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        var updated: TimerModel? = null
        val next = _timers.value.map { timer ->
            if (timer.id != timerId) timer
            else timer.addMinutes(1, now, elapsed).also { updated = it }
        }
        commit(next)
        updated?.let { if (it.isRunning && !it.isPaused) scheduler.schedule(it) }
        scheduleNextLocalDue()
        syncWithService()
    }

    fun updateLabel(timerId: String, newLabel: String) {
        commit(_timers.value.map { if (it.id == timerId) it.copy(label = newLabel) else it })
        syncWithService()
    }

    /**
     * Called when AlarmManager fires, including after the process was killed or Dozing.
     */
    fun onSystemAlarm(timerId: String) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val current = _timers.value.find { it.id == timerId } ?: return
        if (current.hasFired || !current.isRunning || current.isPaused) return
        if (current.calculateCurrentRemaining(now, elapsed) > 1500L) return
        val done = finishTimer(current, now, elapsed)
        commit(_timers.value.map { if (it.id == timerId) done else it })
        scheduler.cancel(timerId)
        scheduleNextLocalDue()
        deliverDue(timerId)
    }

    private fun finishTimer(timer: TimerModel, now: Long, elapsed: Long): TimerModel {
        val minutes = timer.completedFullMinutes(now, elapsed).coerceAtLeast(timer.originFullMinutes())
        updateRun(timer.id, timer.startedAtMillis, minutes)
        return timer.copy(
            isRunning = false,
            isPaused = false,
            hasFired = true,
            remainingMillis = 0L,
            targetEndTimeMillis = 0L,
            targetElapsedRealtimeMillis = 0L,
            bankedRunMillis = minutes * 60_000L,
            runAnchorMillis = 0L,
            runAnchorElapsedMillis = 0L
        )
    }

    private fun TimerModel.originFullMinutes(): Int = (originDurationMillis / 60_000L).toInt()

    private fun deliverDue(timerId: String) {
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_TIMER_DUE
            putExtra(TimerService.EXTRA_TIMER_ID, timerId)
        }
        runCatching {
            context.startForegroundService(intent)
        }.onFailure { Log.e(TAG, "Unable to start timer alert", it) }
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

    /**
     * Zero-polling local wakeup: suspends for the exact remaining duration of the soonest timer
     * (no 500ms CPU wake loop). AlarmManager guarantees wakeup if the process is frozen/Dozing.
     */
    private fun scheduleNextLocalDue() {
        nextDueJob?.cancel()
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val soonest = _timers.value
            .filter { it.isRunning && !it.isPaused && !it.hasFired }
            .minByOrNull { it.calculateCurrentRemaining(now, elapsed) }
            ?: return

        val waitMs = soonest.calculateCurrentRemaining(now, elapsed)
        nextDueJob = scope.launch {
            if (waitMs > 0L) delay(waitMs + 50L)
            onSystemAlarm(soonest.id)
        }
    }

    private fun syncWithService() {
        // Only run FGS while at least one timer is actively counting down (or ringing).
        // Paused timers do not hold a foreground service.
        val hasRunning = _timers.value.any { it.isRunning && !it.isPaused && !it.hasFired }
        val hasRinging = _timers.value.any { it.hasFired }
        val intent = Intent(context, TimerService::class.java)
        if (hasRunning) {
            runCatching {
                context.startForegroundService(intent)
            }.onFailure { Log.e(TAG, "Foreground service start blocked", it) }
        } else if (!hasRinging) {
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
                    it.reconcile(acknowledgeStale = true, fromBoot = true)
                }
            }
        }
    }
}
