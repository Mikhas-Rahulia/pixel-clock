package io.github.mikhasrahulia.pixelclock

import io.github.mikhasrahulia.pixelclock.data.AlarmModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmPauseTest {
    // Tuesday, 3 Nov 2026, 10:00
    private val now = LocalDateTime.of(2026, 11, 3, 10, 0)

    private fun dateOf(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun weekdays(pauseFrom: String? = null, pauseUntil: String? = null) = AlarmModel(
        hour = 7,
        minute = 0,
        daysOfWeek = setOf(1, 2, 3, 4, 5),
        pauseFrom = pauseFrom,
        pauseUntil = pauseUntil
    )

    @Test
    fun withoutPauseNextRingIsTomorrow() {
        val next = weekdays().nextTriggerMillisOrNull(now)!!
        assertEquals(LocalDate.of(2026, 11, 4), dateOf(next))
    }

    @Test
    fun oneDayPauseSkipsOnlyThatDay() {
        val alarm = weekdays("2026-11-04", "2026-11-04")
        assertTrue(alarm.isPausedOn(LocalDate.of(2026, 11, 4)))
        assertFalse(alarm.isPausedOn(LocalDate.of(2026, 11, 5)))
        val next = alarm.nextTriggerMillisOrNull(now)!!
        assertEquals(LocalDate.of(2026, 11, 5), dateOf(next))
    }

    @Test
    fun rangePauseResumesAfterLastDay() {
        // Wed 4 Nov .. Tue 10 Nov. Next ring must be Wed 11 Nov.
        val alarm = weekdays("2026-11-04", "2026-11-10")
        val next = alarm.nextTriggerMillisOrNull(now)!!
        assertEquals(LocalDate.of(2026, 11, 11), dateOf(next))
    }

    @Test
    fun pauseCoveringTodaySkipsTodaysRing() {
        // Alarm at 18:00, pause covers today: today's ring is skipped.
        val alarm = AlarmModel(
            hour = 18,
            minute = 0,
            daysOfWeek = setOf(1, 2, 3, 4, 5),
            pauseFrom = "2026-11-03",
            pauseUntil = "2026-11-03"
        )
        val next = alarm.nextTriggerMillisOrNull(now)!!
        assertEquals(LocalDate.of(2026, 11, 4), dateOf(next))
    }

    @Test
    fun longPauseStillFindsNextRing() {
        // About three months of pause must not hit the 28 day search window.
        val alarm = weekdays("2026-11-04", "2027-02-01")
        val next = alarm.nextTriggerMillisOrNull(now)!!
        // 2 Feb 2027 is a Tuesday.
        assertEquals(LocalDate.of(2027, 2, 2), dateOf(next))
    }

    @Test
    fun oneTimeAlarmMovesPastPause() {
        val alarm = AlarmModel(hour = 7, minute = 0, pauseFrom = "2026-11-04", pauseUntil = "2026-11-05")
        val next = alarm.nextTriggerMillisOrNull(now)!!
        assertEquals(LocalDate.of(2026, 11, 6), dateOf(next))
    }

    @Test
    fun datedAlarmInsidePauseDoesNotRing() {
        val alarm = AlarmModel(
            hour = 7,
            minute = 0,
            specificDate = "2026-11-10",
            pauseFrom = "2026-11-09",
            pauseUntil = "2026-11-12"
        )
        assertNull(alarm.nextTriggerMillisOrNull(now))
    }

    @Test
    fun datedAlarmOutsidePauseStillRings() {
        val alarm = AlarmModel(
            hour = 7,
            minute = 0,
            specificDate = "2026-11-20",
            pauseFrom = "2026-11-09",
            pauseUntil = "2026-11-12"
        )
        assertEquals(LocalDate.of(2026, 11, 20), dateOf(alarm.nextTriggerMillisOrNull(now)!!))
    }

    @Test
    fun biWeeklyPauseSkipsTheActiveWeek() {
        // Monday biweekly, active on the week of 16 Nov. Pause that Monday: next active Monday is 30 Nov.
        val alarm = AlarmModel(
            hour = 8,
            minute = 0,
            daysOfWeek = setOf(1),
            isBiWeekly = true,
            weekParity = AlarmModel.getWeekParity(LocalDate.of(2026, 11, 16)),
            pauseFrom = "2026-11-16",
            pauseUntil = "2026-11-16"
        )
        val next = alarm.nextTriggerMillisOrNull(now)!!
        assertEquals(LocalDate.of(2026, 11, 30), dateOf(next))
    }

    @Test
    fun finishedPauseIsIgnored() {
        val alarm = weekdays("2026-10-20", "2026-10-25")
        assertFalse(alarm.hasPauseAhead(now.toLocalDate()))
        val next = alarm.nextTriggerMillisOrNull(now)!!
        assertEquals(LocalDate.of(2026, 11, 4), dateOf(next))
    }

    @Test
    fun reversedPauseDatesAreNormalised() {
        val alarm = weekdays("2026-11-10", "2026-11-04")
        assertTrue(alarm.isPausedOn(LocalDate.of(2026, 11, 7)))
        assertEquals(LocalDate.of(2026, 11, 4), alarm.pauseRange()!!.first)
        assertEquals(LocalDate.of(2026, 11, 10), alarm.pauseRange()!!.second)
    }

    @Test
    fun alarmsWithoutPauseHaveNoRange() {
        assertNull(weekdays().pauseRange())
        assertFalse(weekdays().hasPauseAhead(now.toLocalDate()))
    }
}
