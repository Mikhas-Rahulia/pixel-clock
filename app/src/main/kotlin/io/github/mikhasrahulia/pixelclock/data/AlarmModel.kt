package io.github.mikhasrahulia.pixelclock.data

import android.content.Context
import io.github.mikhasrahulia.pixelclock.R
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

@Serializable
data class AlarmModel(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val isEnabled: Boolean = true,
    // Days of week: 1 = Monday, 2 = Tuesday, ..., 7 = Sunday
    val daysOfWeek: Set<Int> = emptySet(),
    // Specific calendar date in ISO-8601 format: "YYYY-MM-DD"
    val specificDate: String? = null,
    // Bi-weekly recurrence
    val isBiWeekly: Boolean = false,
    // Week parity: 0 or 1 based on (epochDay + 3) / 7 % 2
    val weekParity: Int = currentWeekParity(),
    val vibrate: Boolean = true,
    val soundTitle: String = "",
    val soundUri: String? = null,
    // Optional pause, inclusive ISO dates. The alarm stays on but does not ring on these days.
    // A single paused day has pauseFrom == pauseUntil.
    val pauseFrom: String? = null,
    val pauseUntil: String? = null
) {
    companion object {
        /** Upper bound for how far past a pause we look for the next ring (about ten years). */
        private const val MAX_PAUSE_DAYS = 3700

        fun currentWeekParity(now: LocalDate = LocalDate.now()): Int {
            val epochDay = now.toEpochDay()
            val epochWeek = (epochDay + 3) / 7
            val parity = (epochWeek % 2).toInt()
            return if (parity < 0) parity + 2 else parity
        }

        fun getWeekParity(date: LocalDate): Int {
            val epochDay = date.toEpochDay()
            val epochWeek = (epochDay + 3) / 7
            val parity = (epochWeek % 2).toInt()
            return if (parity < 0) parity + 2 else parity
        }

        /** Next full hour. 17:49 → 18:00 today, 23:20 → 00:00 tomorrow. */
        fun nextHourSlot(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
            val truncated = now.withMinute(0).withSecond(0).withNano(0)
            return if (truncated.isAfter(now)) truncated else truncated.plusHours(1)
        }

        /** Today if this clock time is still ahead, otherwise tomorrow. */
        fun oneTimeDate(hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now()): LocalDate {
            val todayAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute, 0))
            return if (todayAt.isAfter(now)) now.toLocalDate() else now.toLocalDate().plusDays(1)
        }
    }

    val isRepeating: Boolean
        get() = daysOfWeek.isNotEmpty() && specificDate == null

    fun shiftCycleOneWeek(): AlarmModel {
        val newParity = (weekParity + 1) % 2
        return copy(weekParity = newParity)
    }

    fun isCurrentWeekActive(now: LocalDate = LocalDate.now()): Boolean {
        if (!isBiWeekly) return true
        return currentWeekParity(now) == weekParity
    }

    /** The pause as (first day, last day), both inclusive, or null when none is set. */
    fun pauseRange(): Pair<LocalDate, LocalDate>? {
        val from = pauseFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val until = pauseUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: from
        return if (until.isBefore(from)) until to from else from to until
    }

    fun isPausedOn(date: LocalDate): Boolean {
        val (from, until) = pauseRange() ?: return false
        return !date.isBefore(from) && !date.isAfter(until)
    }

    /** True while a pause is set that has not fully passed yet (it ends today or later). */
    fun hasPauseAhead(today: LocalDate = LocalDate.now()): Boolean {
        val (_, until) = pauseRange() ?: return false
        return !until.isBefore(today)
    }

    /** Same as [getNextTriggerMillis] but returns null when the alarm will not ring (a dated alarm inside its pause). */
    fun nextTriggerMillisOrNull(now: LocalDateTime = LocalDateTime.now()): Long? {
        val zone = ZoneId.systemDefault()
        val alarmTime = LocalTime.of(hour, minute, 0)

        // 1. If specific date is set, schedule for that exact calendar date
        if (!specificDate.isNullOrBlank()) {
            try {
                val targetDate = LocalDate.parse(specificDate)
                if (isPausedOn(targetDate)) return null
                val targetDateTime = LocalDateTime.of(targetDate, alarmTime)
                return targetDateTime.atZone(zone).toInstant().toEpochMilli()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val today = now.toLocalDate()

        // 2. If no recurring days are set, schedule for today if in future, else tomorrow,
        //    then move past any paused days
        if (daysOfWeek.isEmpty()) {
            var targetDateTime = LocalDateTime.of(today, alarmTime)
            if (!targetDateTime.isAfter(now)) {
                targetDateTime = targetDateTime.plusDays(1)
            }
            var guard = 0
            while (isPausedOn(targetDateTime.toLocalDate()) && guard++ < MAX_PAUSE_DAYS) {
                targetDateTime = targetDateTime.plusDays(1)
            }
            return targetDateTime.atZone(zone).toInstant().toEpochMilli()
        }

        // 3. Repeating days with optional bi-weekly cadence, skipping paused days.
        //    The search window grows with the pause so a long pause still finds the next ring.
        val pauseDaysAhead = pauseRange()?.let { (_, until) ->
            if (until.isBefore(today)) 0 else ChronoUnit.DAYS.between(today, until).toInt() + 1
        } ?: 0
        for (dayOffset in 0..(28 + pauseDaysAhead.coerceAtMost(MAX_PAUSE_DAYS))) {
            val candidateDate = today.plusDays(dayOffset.toLong())
            val candidateDayOfWeek = candidateDate.dayOfWeek.value

            if (daysOfWeek.contains(candidateDayOfWeek)) {
                if (isBiWeekly) {
                    val candidateParity = getWeekParity(candidateDate)
                    if (candidateParity != weekParity) {
                        continue
                    }
                }
                if (isPausedOn(candidateDate)) continue

                val candidateDateTime = LocalDateTime.of(candidateDate, alarmTime)
                if (candidateDateTime.isAfter(now)) {
                    return candidateDateTime.atZone(zone).toInstant().toEpochMilli()
                }
            }
        }

        return null
    }

    fun getNextTriggerMillis(now: LocalDateTime = LocalDateTime.now()): Long {
        return nextTriggerMillisOrNull(now)
            ?: now.plusDays(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * Clean, concise un-bloated relative time string.
     */
    fun getFormattedNextOccurrence(
        context: Context,
        now: LocalDateTime = LocalDateTime.now()
    ): String {
        if (!isEnabled) return ""

        val nextTriggerMillis = nextTriggerMillisOrNull(now) ?: return ""
        val nextDateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(nextTriggerMillis),
            ZoneId.systemDefault()
        )

        val daysUntil = ChronoUnit.DAYS.between(now.toLocalDate(), nextDateTime.toLocalDate())
        val timeString = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

        return when {
            daysUntil == 0L -> timeString
            daysUntil == 1L -> {
                val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
                "${nextDateTime.format(dayFormatter)}, $timeString"
            }
            daysUntil in 2L..6L -> {
                val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
                "${nextDateTime.format(dayFormatter)}, $timeString"
            }
            else -> {
                val dayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
                "${nextDateTime.format(dayFormatter)}, $timeString"
            }
        }
    }

    fun getDaysSummary(context: Context): String {
        if (!specificDate.isNullOrBlank()) {
            try {
                val targetDate = LocalDate.parse(specificDate)
                val formatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
                return targetDate.format(formatter)
            } catch (e: Exception) {
                return specificDate
            }
        }

        if (daysOfWeek.isEmpty()) {
            val nextDate = LocalDate.now().let { today ->
                val atToday = LocalDateTime.of(today, LocalTime.of(hour, minute, 0))
                if (atToday.isAfter(LocalDateTime.now())) today else today.plusDays(1)
            }
            return if (nextDate == LocalDate.now()) context.getString(R.string.today)
            else context.getString(R.string.tomorrow)
        }
        if (daysOfWeek.size == 7) return context.getString(R.string.every_day)
        if (daysOfWeek == setOf(1, 2, 3, 4, 5)) return context.getString(R.string.weekdays)
        if (daysOfWeek == setOf(6, 7)) return context.getString(R.string.weekends)

        val dayNames = mapOf(
            1 to context.getString(R.string.day_mon),
            2 to context.getString(R.string.day_tue),
            3 to context.getString(R.string.day_wed),
            4 to context.getString(R.string.day_thu),
            5 to context.getString(R.string.day_fri),
            6 to context.getString(R.string.day_sat),
            7 to context.getString(R.string.day_sun)
        )
        return daysOfWeek.sorted().mapNotNull { dayNames[it] }.joinToString(", ")
    }
}
