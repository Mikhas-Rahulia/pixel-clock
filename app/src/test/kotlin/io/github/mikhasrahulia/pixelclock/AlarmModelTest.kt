package io.github.mikhasrahulia.pixelclock

import io.github.mikhasrahulia.pixelclock.data.AlarmModel
import io.github.mikhasrahulia.pixelclock.data.TimerModel
import io.github.mikhasrahulia.pixelclock.data.TimerRunRecord
import io.github.mikhasrahulia.pixelclock.data.summarizeTimerHour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AlarmModelTest {

    @Test
    fun testWeekParityAlternation() {
        // Reference Monday
        val monday1 = LocalDate.of(2026, 11, 2) // Monday
        val sunday1 = LocalDate.of(2026, 11, 8) // Sunday of same week
        val monday2 = LocalDate.of(2026, 11, 9) // Next Monday
        val sunday2 = LocalDate.of(2026, 11, 15) // Next Sunday

        val parityWeek1Mon = AlarmModel.getWeekParity(monday1)
        val parityWeek1Sun = AlarmModel.getWeekParity(sunday1)
        val parityWeek2Mon = AlarmModel.getWeekParity(monday2)
        val parityWeek2Sun = AlarmModel.getWeekParity(sunday2)

        // Same week must have exact same parity
        assertEquals(parityWeek1Mon, parityWeek1Sun)
        assertEquals(parityWeek2Mon, parityWeek2Sun)

        // Adjacent weeks must have alternating parity (0 -> 1 or 1 -> 0)
        assertEquals((parityWeek1Mon + 1) % 2, parityWeek2Mon)
    }

    @Test
    fun testBiWeeklyRecurrenceAndCycleShift() {
        // Suppose today is Tuesday, 2026-11-03 at 10:00
        val now = LocalDateTime.of(2026, 11, 3, 10, 0)
        val currentParity = AlarmModel.getWeekParity(now.toLocalDate())

        // Alarm for Monday (day 1) at 08:00
        val alarmThisCycle = AlarmModel(
            hour = 8,
            minute = 0,
            daysOfWeek = setOf(1), // Monday
            isBiWeekly = true,
            weekParity = currentParity // Active this week
        )

        // Today is Tuesday, so Monday of this week has passed.
        // But since this week was active, the NEXT active week will be in 2 weeks (2026-11-16)!
        val triggerMillis = alarmThisCycle.getNextTriggerMillis(now)
        val triggerDate = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(triggerMillis),
            ZoneId.systemDefault()
        ).toLocalDate()

        assertEquals(LocalDate.of(2026, 11, 16), triggerDate)

        // NOW: SHIFT CYCLE BY 1 WEEK!
        val shiftedAlarm = alarmThisCycle.shiftCycleOneWeek()
        assertEquals((currentParity + 1) % 2, shiftedAlarm.weekParity)

        // With the 1-week shift, the active week becomes NEXT week (2026-11-09)!
        val shiftedTriggerMillis = shiftedAlarm.getNextTriggerMillis(now)
        val shiftedTriggerDate = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(shiftedTriggerMillis),
            ZoneId.systemDefault()
        ).toLocalDate()

        assertEquals(LocalDate.of(2026, 11, 9), shiftedTriggerDate)
    }

    @Test
    fun testSpecificCalendarDateTrigger() {
        val now = LocalDateTime.of(2026, 11, 3, 10, 0)
        val alarmWithDate = AlarmModel(
            hour = 14,
            minute = 30,
            specificDate = "2026-12-25"
        )
        val triggerMillis = alarmWithDate.getNextTriggerMillis(now)
        val triggerDateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(triggerMillis),
            ZoneId.systemDefault()
        )

        assertEquals(LocalDate.of(2026, 12, 25), triggerDateTime.toLocalDate())
        assertEquals(14, triggerDateTime.hour)
        assertEquals(30, triggerDateTime.minute)
        assertFalse(alarmWithDate.isRepeating)
    }

    @Test
    fun testNextHourLandsTodayOrTomorrow() {
        val afternoon = LocalDateTime.of(2026, 11, 1, 17, 49)
        val nextHour = AlarmModel.nextHourSlot(afternoon)
        assertEquals(LocalDate.of(2026, 11, 1), nextHour.toLocalDate())
        assertEquals(18, nextHour.hour)
        assertEquals(0, nextHour.minute)
        assertEquals(LocalDate.of(2026, 11, 1), AlarmModel.oneTimeDate(18, 0, afternoon))

        val late = LocalDateTime.of(2026, 11, 1, 23, 20)
        val tomorrowHour = AlarmModel.nextHourSlot(late)
        assertEquals(LocalDate.of(2026, 11, 2), tomorrowHour.toLocalDate())
        assertEquals(0, tomorrowHour.hour)
        assertEquals(LocalDate.of(2026, 11, 2), AlarmModel.oneTimeDate(0, 0, late))
        assertEquals(LocalDate.of(2026, 11, 2), AlarmModel.oneTimeDate(8, 0, afternoon))
    }

    @Test
    fun testNewAlarmIsOneTime() {
        val alarm = AlarmModel(hour = 18, minute = 0, specificDate = "2026-11-01")
        assertFalse(alarm.isRepeating)
        assertTrue(alarm.daysOfWeek.isEmpty())
    }

    @Test
    fun testTimerHourCountsStartsAndFullMinutes() {
        val now = 3_600_000L
        val records = listOf(
            TimerRunRecord("a", 300_000L, now - 50 * 60_000L, completedMinutes = 5),
            TimerRunRecord("b", 300_000L, now - 20 * 60_000L, completedMinutes = 5),
            TimerRunRecord("c", 300_000L, now - 90 * 60_000L, completedMinutes = 5),
            TimerRunRecord("d", 600_000L, now - 10 * 60_000L, completedMinutes = 10)
        )
        val five = summarizeTimerHour(records, emptyList(), 300_000L, now)
        assertEquals(2, five.starts)
        assertEquals(10, five.completedMinutes)
        val ten = summarizeTimerHour(records, emptyList(), 600_000L, now)
        assertEquals(1, ten.starts)
        assertEquals(10, ten.completedMinutes)
    }

    @Test
    fun testTimerModelAddMinute() {
        val timer = TimerModel(
            initialDurationMillis = 60_000L,
            remainingMillis = 60_000L,
            isRunning = false
        )
        val updated = timer.addOneMinute()
        assertEquals(120_000L, updated.initialDurationMillis)
        assertEquals(120_000L, updated.remainingMillis)
        assertEquals("02:00", updated.formattedTime())
    }
}
