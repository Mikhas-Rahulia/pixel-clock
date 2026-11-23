package io.github.mikhasrahulia.pixelclock

import io.github.mikhasrahulia.pixelclock.data.AlarmBackup
import io.github.mikhasrahulia.pixelclock.data.AlarmModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
                weekParity = 1,
                pauseFrom = "2026-12-24",
                pauseUntil = "2026-12-26"
            )
        )
        val encoded = AlarmBackup.encode(original)
        // encodeDefaults = true ensures critical defaults are always preserved explicitly
        assertTrue(encoded.contains("\"isEnabled\":true"))
        assertTrue(encoded.contains("\"vibrate\":true"))

        val parsed = AlarmBackup.decode(encoded)!!
        assertEquals(original[0].label, parsed[0].label)
        assertEquals(setOf(1, 2, 4), parsed[0].daysOfWeek)
        assertTrue(parsed[1].isBiWeekly)
        assertEquals(1, parsed[1].weekParity)
        assertEquals("2026-12-24", parsed[1].pauseFrom)
        assertEquals("2026-12-26", parsed[1].pauseUntil)
    }

    @Test
    fun legacyMinimalSchemaPayloadPreservesAlarms() {
        // Simulates an old v1/v2 backup missing newer optional properties and carrying unknown fields
        val legacyJson = """
            [
              {
                "id": "legacy-1",
                "hour": 6,
                "minute": 30,
                "label": "Morning bus",
                "isEnabled": true,
                "daysOfWeek": [1, 2, 3, 4, 5],
                "removedFutureField": "ignored"
              },
              {
                "id": "legacy-2",
                "hour": 9,
                "minute": 15,
                "isEnabled": false,
                "isBiWeekly": true,
                "weekParity": 0,
                "daysOfWeek": [2, 4]
              }
            ]
        """.trimIndent()

        val decoded = AlarmBackup.decode(legacyJson)
        assertNotNull(decoded)
        assertEquals(2, decoded!!.size)
        assertEquals("Morning bus", decoded[0].label)
        assertTrue(decoded[0].isEnabled)
        assertTrue(decoded[0].vibrate)
        assertEquals(setOf(1, 2, 3, 4, 5), decoded[0].daysOfWeek)
        assertFalse(decoded[1].isEnabled)
        assertTrue(decoded[1].isBiWeekly)
        assertEquals(0, decoded[1].weekParity)
    }

    @Test
    fun corruptSingleEntryDoesNotDropOtherAlarms() {
        val mixedJson = """
            [
              {"id": "ok-1", "hour": 7, "minute": 15, "label": "Work"},
              {"id": "broken", "hour": "not-an-int"},
              {"id": "out-of-range", "hour": 99, "minute": 0},
              {"id": "ok-2", "hour": 22, "minute": 0, "label": "Sleep"}
            ]
        """.trimIndent()

        val decoded = AlarmBackup.decode(mixedJson)
        assertNotNull(decoded)
        assertEquals(2, decoded!!.size)
        assertEquals("Work", decoded[0].label)
        assertEquals("Sleep", decoded[1].label)
    }

    @Test
    fun wrappedObjectAndEmptyListAndCorruptJsonHandledSafely() {
        val wrapped = """{"schema":4,"alarms":[{"id":"w1","hour":8,"minute":0,"label":"Class"}]}"""
        val wrappedDecoded = AlarmBackup.decode(wrapped)
        assertNotNull(wrappedDecoded)
        assertEquals(1, wrappedDecoded!!.size)
        assertEquals("Class", wrappedDecoded[0].label)

        assertEquals(emptyList<AlarmModel>(), AlarmBackup.decode("[]"))
        assertNull(AlarmBackup.decode("{not valid json"))
        assertNull(AlarmBackup.decode(""))
    }

    @Test
    fun prettyExportRoundTripsCleanly() {
        val list = listOf(real)
        val pretty = AlarmBackup.encodePretty(list)
        val parsed = AlarmBackup.decode(pretty)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.size)
        assertEquals(real.hour, parsed[0].hour)
        assertEquals(real.minute, parsed[0].minute)
        assertEquals(real.label, parsed[0].label)
    }
}
