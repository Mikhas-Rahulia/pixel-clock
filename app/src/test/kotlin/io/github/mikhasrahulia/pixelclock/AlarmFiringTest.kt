package io.github.mikhasrahulia.pixelclock

import io.github.mikhasrahulia.pixelclock.data.AlarmModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The alarm must fire at the exact minute on exactly the chosen days. No drift, no early or late rings. */
class AlarmFiringTest {
    private val zone = ZoneId.systemDefault()

    private fun fire(alarm: AlarmModel, now: LocalDateTime): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(alarm.getNextTriggerMillis(now)), zone)

    // 2 Nov 2026 is a Monday.
    private val mondayMorning = LocalDateTime.of(2026, 11, 2, 6, 0, 0)

    @Test
    fun weeklyMondayFiresEveryMondayAtExactTime() {
        val alarm = AlarmModel(hour = 7, minute = 30, daysOfWeek = setOf(1))
        var now = mondayMorning
        repeat(6) { week ->
            val next = fire(alarm, now)
            assertEquals(LocalDate.of(2026, 11, 2).plusWeeks(week.toLong()), next.toLocalDate())
            assertEquals(7, next.hour)
            assertEquals(30, next.minute)
            assertEquals(0, next.second)
            // Right after it rang the next ring must be exactly a week later, not the same day again.
            now = next.plusSeconds(1)
        }
    }

    @Test
    fun afterRingingTheSameDayIsNotRepeated() {
        val alarm = AlarmModel(hour = 7, minute = 30, daysOfWeek = setOf(1, 3))
        val rangAt = LocalDateTime.of(2026, 11, 2, 7, 30, 0, 500_000_000)
        val next = fire(alarm, rangAt)
        assertEquals(LocalDate.of(2026, 11, 4), next.toLocalDate()) // Wednesday
    }

    @Test
    fun weekdaysSkipTheWeekend() {
        val alarm = AlarmModel(hour = 8, minute = 0, daysOfWeek = setOf(1, 2, 3, 4, 5))
        val fridayAfter = LocalDateTime.of(2026, 11, 6, 8, 0, 1)
        assertEquals(LocalDate.of(2026, 11, 9), fire(alarm, fridayAfter).toLocalDate())
    }

    @Test
    fun biWeeklyJumpsTwoWeeks() {
        val parity = AlarmModel.getWeekParity(LocalDate.of(2026, 11, 2))
        val alarm = AlarmModel(hour = 7, minute = 0, daysOfWeek = setOf(1), isBiWeekly = true, weekParity = parity)
        val first = fire(alarm, mondayMorning.minusHours(2))
        assertEquals(LocalDate.of(2026, 11, 2), first.toLocalDate())
        val second = fire(alarm, first.plusSeconds(1))
        assertEquals(LocalDate.of(2026, 11, 16), second.toLocalDate())
        val third = fire(alarm, second.plusSeconds(1))
        assertEquals(LocalDate.of(2026, 11, 30), third.toLocalDate())
    }

    @Test
    fun oneTimeDateFiresOnThatDateOnly() {
        val alarm = AlarmModel(hour = 19, minute = 0, specificDate = "2026-11-08")
        val next = fire(alarm, LocalDateTime.of(2026, 11, 2, 12, 0))
        assertEquals(LocalDate.of(2026, 11, 8), next.toLocalDate())
        assertEquals(19, next.hour)
        assertEquals(0, next.minute)
    }

    @Test
    fun newAlarmDefaultDayFollowsNextFullHour() {
        // 17:49 -> 18:00 today
        val a = AlarmModel.nextHourSlot(LocalDateTime.of(2026, 11, 1, 17, 49))
        assertEquals(LocalDateTime.of(2026, 11, 1, 18, 0), a)
        assertEquals(LocalDate.of(2026, 11, 1), AlarmModel.oneTimeDate(a.hour, a.minute, LocalDateTime.of(2026, 11, 1, 17, 49)))
        // 23:20 -> 00:00 tomorrow
        val b = AlarmModel.nextHourSlot(LocalDateTime.of(2026, 11, 1, 23, 20))
        assertEquals(LocalDateTime.of(2026, 11, 2, 0, 0), b)
        assertEquals(LocalDate.of(2026, 11, 2), AlarmModel.oneTimeDate(b.hour, b.minute, LocalDateTime.of(2026, 11, 1, 23, 20)))
    }
}
