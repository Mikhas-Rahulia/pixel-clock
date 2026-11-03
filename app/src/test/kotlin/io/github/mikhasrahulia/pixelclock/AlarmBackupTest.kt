package io.github.mikhasrahulia.pixelclock

import io.github.mikhasrahulia.pixelclock.data.AlarmBackup
import io.github.mikhasrahulia.pixelclock.data.AlarmModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmBackupTest {

    private val demo = listOf(
        AlarmModel(hour = 7, minute = 0, daysOfWeek = setOf(1, 2, 3, 4, 5)),
        AlarmModel(hour = 8, minute = 30, daysOfWeek = setOf(1, 3, 5), isBiWeekly = true)
    )

    private val real = AlarmModel(
        hour = 8,
        minute = 45,
        label = "Lecture A",
        daysOfWeek = setOf(1, 2, 4)
    )

    @Test
    fun demoSeedIsRecognised() {
        assertTrue(AlarmBackup.isDemoSeed(demo))
        assertFalse(AlarmBackup.isDemoSeed(listOf(real)))
        assertFalse(AlarmBackup.isDemoSeed(demo + real))
        assertTrue(demo.all { AlarmBackup.isDemoAlarm(it) })
    }

    @Test
    fun mergeReplacesDemoWithImportedAlarms() {
        val imported = listOf(real, AlarmModel(hour = 11, minute = 0, label = "Lab", daysOfWeek = setOf(5)))
        val merged = AlarmBackup.merge(demo, imported)
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.label == "Lab" })
        assertTrue(merged.none { it.hour == 7 && it.label.isBlank() })
    }

    @Test
    fun mergeKeepsExistingRealAlarmsAndSkipsDuplicates() {
        val existing = listOf(real)
        val incoming = listOf(
            real.copy(id = "other-id"),
            AlarmModel(hour = 12, minute = 45, label = "Gym", daysOfWeek = setOf(1), isBiWeekly = true)
        )
        val merged = AlarmBackup.merge(existing, incoming)
        assertEquals(2, merged.size)
        assertEquals("Lecture A", merged[0].label)
        assertEquals("Gym", merged[1].label)
    }

    @Test
    fun jsonRoundTripKeepsNameAndBiweekly() {
        val original = listOf(
            real,
            AlarmModel(
                hour = 12,
                minute = 45,
                label = "Gym",
                daysOfWeek = setOf(1),
                isBiWeekly = true,
                weekParity = 1
            )
        )
        val parsed = AlarmBackup.decode(AlarmBackup.encode(original))!!
        assertEquals(original[0].label, parsed[0].label)
        assertEquals(setOf(1, 2, 4), parsed[0].daysOfWeek)
        assertTrue(parsed[1].isBiWeekly)
        assertEquals(1, parsed[1].weekParity)
    }
}
