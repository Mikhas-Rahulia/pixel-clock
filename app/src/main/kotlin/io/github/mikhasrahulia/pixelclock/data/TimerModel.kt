package io.github.mikhasrahulia.pixelclock.data

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.UUID

@Immutable
@Serializable
data class TimerModel(
    val id: String = UUID.randomUUID().toString(),
    val initialDurationMillis: Long,
    val remainingMillis: Long,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val label: String = "",
    /** Wall-clock target end (for reboot recovery & notification display). */
    val targetEndTimeMillis: Long = 0L,
    /** Monotonic elapsedRealtime target end (immune to NTP/timezone jumps). */
    val targetElapsedRealtimeMillis: Long = 0L,
    /** Duration the user actually started. Stays put when time is added. */
    val originDurationMillis: Long = initialDurationMillis,
    val startedAtMillis: Long = 0L,
    val hasFired: Boolean = false,
    /** Running time banked before the current segment (pauses). */
    val bankedRunMillis: Long = 0L,
    /** Wall-clock start of the current running segment. 0 when not running. */
    val runAnchorMillis: Long = 0L,
    /** Monotonic start of the current running segment. 0 when not running. */
    val runAnchorElapsedMillis: Long = 0L
) {
    fun calculateCurrentRemaining(
        currentTimeMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = safeElapsedRealtime()
    ): Long {
        if (!isRunning || isPaused) return remainingMillis
        if (targetElapsedRealtimeMillis > 0L && elapsedRealtimeMillis > 0L) {
            return (targetElapsedRealtimeMillis - elapsedRealtimeMillis).coerceAtLeast(0L)
        }
        if (targetEndTimeMillis > 0L) {
            return (targetEndTimeMillis - currentTimeMillis).coerceAtLeast(0L)
        }
        return remainingMillis
    }

    fun progress(
        currentTimeMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = safeElapsedRealtime()
    ): Float {
        val rem = calculateCurrentRemaining(currentTimeMillis, elapsedRealtimeMillis)
        return if (initialDurationMillis > 0) {
            (rem.toFloat() / initialDurationMillis.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    fun formattedTime(
        currentTimeMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = safeElapsedRealtime()
    ): String {
        return formatDuration(calculateCurrentRemaining(currentTimeMillis, elapsedRealtimeMillis))
    }

    fun runningMillis(
        now: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = safeElapsedRealtime()
    ): Long {
        val live = if (isRunning && !isPaused) {
            when {
                runAnchorElapsedMillis > 0L && elapsedRealtimeMillis > 0L ->
                    (elapsedRealtimeMillis - runAnchorElapsedMillis).coerceAtLeast(0L)
                runAnchorMillis > 0L ->
                    (now - runAnchorMillis).coerceAtLeast(0L)
                else -> 0L
            }
        } else 0L
        return bankedRunMillis + live
    }

    /** Whole minutes that have actually elapsed. A partial minute does not count. */
    fun completedFullMinutes(
        now: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = safeElapsedRealtime()
    ): Int {
        return (runningMillis(now, elapsedRealtimeMillis) / 60_000L).toInt()
    }

    fun addMinutes(
        minutes: Int = 5,
        currentTimeMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = safeElapsedRealtime()
    ): TimerModel {
        val addMs = minutes * 60_000L
        val newInitial = initialDurationMillis + addMs
        return if (isRunning && !isPaused) {
            val newRem = calculateCurrentRemaining(currentTimeMillis, elapsedRealtimeMillis) + addMs
            copy(
                initialDurationMillis = newInitial,
                remainingMillis = newRem,
                targetEndTimeMillis = currentTimeMillis + newRem,
                targetElapsedRealtimeMillis = if (elapsedRealtimeMillis > 0L) elapsedRealtimeMillis + newRem else 0L
            )
        } else {
            copy(
                initialDurationMillis = newInitial,
                remainingMillis = remainingMillis + addMs
            )
        }
    }

    fun addOneMinute(
        currentTimeMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = safeElapsedRealtime()
    ): TimerModel = addMinutes(1, currentTimeMillis, elapsedRealtimeMillis)

    /** Ringing timer: keep counting, add 5 minutes from now. */
    fun snoozeFive(
        now: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = safeElapsedRealtime()
    ): TimerModel {
        val addMs = 5 * 60_000L
        val banked = runningMillis(now, elapsedRealtimeMillis)
        return copy(
            isRunning = true,
            isPaused = false,
            hasFired = false,
            initialDurationMillis = initialDurationMillis + addMs,
            remainingMillis = addMs,
            targetEndTimeMillis = now + addMs,
            targetElapsedRealtimeMillis = if (elapsedRealtimeMillis > 0L) elapsedRealtimeMillis + addMs else 0L,
            bankedRunMillis = banked,
            runAnchorMillis = now,
            runAnchorElapsedMillis = elapsedRealtimeMillis
        )
    }

    fun start(
        currentTimeMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = safeElapsedRealtime()
    ): TimerModel {
        val currentRem = if (remainingMillis > 0) remainingMillis else originDurationMillis
        return copy(
            isRunning = true,
            isPaused = false,
            hasFired = false,
            remainingMillis = currentRem,
            targetEndTimeMillis = currentTimeMillis + currentRem,
            targetElapsedRealtimeMillis = if (elapsedRealtimeMillis > 0L) elapsedRealtimeMillis + currentRem else 0L,
            runAnchorMillis = currentTimeMillis,
            runAnchorElapsedMillis = elapsedRealtimeMillis,
            startedAtMillis = if (startedAtMillis > 0L) startedAtMillis else currentTimeMillis
        )
    }

    fun pause(
        currentTimeMillis: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = safeElapsedRealtime()
    ): TimerModel {
        return copy(
            isRunning = false,
            isPaused = true,
            remainingMillis = calculateCurrentRemaining(currentTimeMillis, elapsedRealtimeMillis),
            targetEndTimeMillis = 0L,
            targetElapsedRealtimeMillis = 0L,
            bankedRunMillis = runningMillis(currentTimeMillis, elapsedRealtimeMillis),
            runAnchorMillis = 0L,
            runAnchorElapsedMillis = 0L
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
            targetElapsedRealtimeMillis = 0L,
            bankedRunMillis = 0L,
            runAnchorMillis = 0L,
            runAnchorElapsedMillis = 0L,
            startedAtMillis = 0L
        )
    }

    companion object {
        fun safeElapsedRealtime(): Long = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)

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

@Immutable
@Serializable
data class TimerRunRecord(
    val timerId: String,
    val durationMillis: Long,
    val startedAtMillis: Long,
    val completedMinutes: Int = 0
)

@Immutable
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
