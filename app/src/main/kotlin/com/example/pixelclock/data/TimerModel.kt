package com.example.pixelclock.data

import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.UUID

@Serializable
data class TimerModel(
    val id: String = UUID.randomUUID().toString(),
    val initialDurationMillis: Long,
    val remainingMillis: Long,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val label: String = "",
    val targetEndTimeMillis: Long = 0L,
    /** Duration the user actually started. Stays put when time is added. */
    val originDurationMillis: Long = initialDurationMillis,
    val startedAtMillis: Long = 0L,
    val hasFired: Boolean = false,
    /** Running time banked before the current segment (pauses). */
    val bankedRunMillis: Long = 0L,
    /** Wall-clock start of the current running segment. 0 when not running. */
    val runAnchorMillis: Long = 0L
) {
    fun calculateCurrentRemaining(currentTimeMillis: Long = System.currentTimeMillis()): Long {
        return if (isRunning && !isPaused && targetEndTimeMillis > 0) {
            (targetEndTimeMillis - currentTimeMillis).coerceAtLeast(0L)
        } else {
            remainingMillis
        }
    }

    fun progress(currentTimeMillis: Long = System.currentTimeMillis()): Float {
        val rem = calculateCurrentRemaining(currentTimeMillis)
        return if (initialDurationMillis > 0) {
            (rem.toFloat() / initialDurationMillis.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    fun formattedTime(currentTimeMillis: Long = System.currentTimeMillis()): String {
        return formatDuration(calculateCurrentRemaining(currentTimeMillis))
    }

    fun runningMillis(now: Long = System.currentTimeMillis()): Long {
        val live = if (isRunning && !isPaused && runAnchorMillis > 0) {
            (now - runAnchorMillis).coerceAtLeast(0L)
        } else 0L
        return bankedRunMillis + live
    }

    /** Whole minutes that have actually elapsed. A partial minute does not count. */
    fun completedFullMinutes(now: Long = System.currentTimeMillis()): Int {
        return (runningMillis(now) / 60_000L).toInt()
    }

    fun addMinutes(minutes: Int = 5, currentTimeMillis: Long = System.currentTimeMillis()): TimerModel {
        val addMs = minutes * 60_000L
        val newInitial = initialDurationMillis + addMs
        return if (isRunning && !isPaused && targetEndTimeMillis > 0) {
            val newTarget = targetEndTimeMillis + addMs
            copy(
                initialDurationMillis = newInitial,
                targetEndTimeMillis = newTarget,
                remainingMillis = (newTarget - currentTimeMillis).coerceAtLeast(0L)
            )
        } else {
            copy(
                initialDurationMillis = newInitial,
                remainingMillis = remainingMillis + addMs
            )
        }
    }

    fun addOneMinute(currentTimeMillis: Long = System.currentTimeMillis()): TimerModel {
        return addMinutes(1, currentTimeMillis)
    }

    /** Ringing timer: keep counting, add 5 minutes from now. */
    fun snoozeFive(now: Long = System.currentTimeMillis()): TimerModel {
        val addMs = 5 * 60_000L
        val banked = runningMillis(now)
        return copy(
            isRunning = true,
            isPaused = false,
            hasFired = false,
            initialDurationMillis = initialDurationMillis + addMs,
            remainingMillis = addMs,
            targetEndTimeMillis = now + addMs,
            bankedRunMillis = banked,
            runAnchorMillis = now
        )
    }

    fun start(currentTimeMillis: Long = System.currentTimeMillis()): TimerModel {
        val currentRem = if (remainingMillis > 0) remainingMillis else originDurationMillis
        return copy(
            isRunning = true,
            isPaused = false,
            hasFired = false,
            remainingMillis = currentRem,
            targetEndTimeMillis = currentTimeMillis + currentRem,
            runAnchorMillis = currentTimeMillis,
            startedAtMillis = if (startedAtMillis > 0L) startedAtMillis else currentTimeMillis
        )
    }

    fun pause(currentTimeMillis: Long = System.currentTimeMillis()): TimerModel {
        return copy(
            isRunning = false,
            isPaused = true,
            remainingMillis = calculateCurrentRemaining(currentTimeMillis),
            targetEndTimeMillis = 0L,
            bankedRunMillis = runningMillis(currentTimeMillis),
            runAnchorMillis = 0L
        )
    }

    fun reset(): TimerModel {
        return copy(
            isRunning = false,
            isPaused = false,
            hasFired = false,
            initialDurationMillis = originDurationMillis,
            remainingMillis = originDurationMillis,
            targetEndTimeMillis = 0L,
            bankedRunMillis = 0L,
            runAnchorMillis = 0L,
            startedAtMillis = 0L
        )
    }

    companion object {
        fun formatDuration(millis: Long): String {
            val totalSecs = (millis.coerceAtLeast(0L) + 999) / 1000
            val hours = totalSecs / 3600
            val minutes = (totalSecs % 3600) / 60
            val seconds = totalSecs % 60
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        }
    }
}

@Serializable
data class TimerRunRecord(
    val timerId: String,
    val durationMillis: Long,
    val startedAtMillis: Long,
    val completedMinutes: Int = 0
)

data class TimerHourStat(
    val starts: Int,
    val completedMinutes: Int
)

fun summarizeTimerHour(
    records: List<TimerRunRecord>,
    liveTimers: List<TimerModel>,
    durationMillis: Long,
    now: Long
): TimerHourStat {
    if (durationMillis <= 0L) return TimerHourStat(0, 0)
    val cutoff = now - 60L * 60L * 1000L
    val recent = records.filter { it.durationMillis == durationMillis && it.startedAtMillis >= cutoff }
    if (recent.isEmpty()) return TimerHourStat(0, 0)
    val minutes = recent.sumOf { record ->
        val live = liveTimers.find { it.id == record.timerId && it.startedAtMillis == record.startedAtMillis }
        if (live != null && !live.hasFired && (live.isRunning || live.isPaused)) {
            live.completedFullMinutes(now)
        } else {
            record.completedMinutes
        }
    }
    return TimerHourStat(recent.size, minutes)
}
