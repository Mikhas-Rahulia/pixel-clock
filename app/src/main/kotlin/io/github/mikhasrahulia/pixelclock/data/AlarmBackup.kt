package io.github.mikhasrahulia.pixelclock.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Atomic JSON persistence for alarms inside Device-Encrypted (DE) internal storage.
 * Survives app updates and Pixel overnight OTA reboots without exposing plaintext data
 * to external storage or cloud backup.
 */
internal object AlarmBackup {
    const val LEGACY_PACKAGE = "com.example.pixelclock"
    const val PREFS_NAME = "pixel_clock_alarms"
    const val KEY_ALARMS = "key_alarms_list"
    const val KEY_SCHEMA = "key_alarms_schema"
    const val SCHEMA_VERSION = 3
    const val STORAGE_NAME = "alarms-store.json"
    const val BACKUP_NAME = "alarms-backup.json"
    const val IMPORT_NAME = "alarms-import.json"

    private const val TAG = "AlarmBackup"

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    fun decode(raw: String): List<AlarmModel>? = runCatching {
        json.decodeFromString<List<AlarmModel>>(raw)
    }.onFailure { Log.w(TAG, "Could not parse alarms JSON", it) }.getOrNull()

    fun encode(list: List<AlarmModel>): String = json.encodeToString(list)

    fun storeFile(context: Context): File = File(context.deviceProtectedContext().filesDir, STORAGE_NAME)

    fun backupFile(context: Context): File = File(context.deviceProtectedContext().filesDir, BACKUP_NAME)

    fun writeStore(context: Context, encodedJson: String) {
        runCatching { writeAtomicText(storeFile(context), encodedJson) }
            .onFailure { Log.e(TAG, "Failed writing atomic alarm store", it) }
        runCatching { writeAtomicText(backupFile(context), encodedJson) }
            .onFailure { Log.w(TAG, "Failed writing atomic alarm backup", it) }
    }

    fun readStore(context: Context): List<AlarmModel>? {
        val primary = storeFile(context)
        readAtomicText(primary)?.let { raw ->
            decode(raw)?.let { return it }
            quarantineCorruptFile(primary)
        }
        return readBackup(context)
    }

    fun readBackup(context: Context): List<AlarmModel>? {
        val candidates = mutableListOf(backupFile(context))
        if (context.isUserUnlockedSafe()) {
            runCatching { File(context.applicationContext.filesDir, BACKUP_NAME) }
                .getOrNull()?.let { candidates += it }
        }
        for (file in candidates) {
            val raw = readAtomicText(file) ?: continue
            decode(raw)?.let { return it }
            quarantineCorruptFile(file)
        }
        return null
    }

    /** Optional one-shot import when unlocked. Never touches CE/external storage during Direct Boot. */
    fun consumeImportFile(context: Context): List<AlarmModel>? {
        if (!context.isUserUnlockedSafe()) return null
        return runCatching {
            val dir = context.applicationContext.getExternalFilesDir(null) ?: return null
            val file = File(dir, IMPORT_NAME)
            if (!file.isFile) return null
            val list = decode(file.readText())
            file.delete()
            list
        }.getOrNull()
    }

    private fun quarantineCorruptFile(file: File) {
        runCatching {
            if (file.isFile) {
                file.renameTo(File(file.parentFile, "${file.name}.corrupt"))
            }
        }
    }

    fun isDemoAlarm(alarm: AlarmModel): Boolean {
        if (alarm.label.isNotBlank()) return false
        val weekdays = alarm.hour == 7 && alarm.minute == 0 &&
            alarm.daysOfWeek == setOf(1, 2, 3, 4, 5) && !alarm.isBiWeekly
        val biweekly = alarm.hour == 8 && alarm.minute == 30 &&
            alarm.daysOfWeek == setOf(1, 3, 5) && alarm.isBiWeekly
        return weekdays || biweekly
    }

    fun isDemoSeed(list: List<AlarmModel>): Boolean {
        if (list.size != 2) return false
        return list.all(::isDemoAlarm)
    }

    fun sameAlarm(a: AlarmModel, b: AlarmModel): Boolean =
        a.hour == b.hour &&
            a.minute == b.minute &&
            a.label.trim() == b.label.trim() &&
            a.daysOfWeek == b.daysOfWeek &&
            a.isBiWeekly == b.isBiWeekly &&
            a.specificDate == b.specificDate

    /** Keep existing alarms, append imported ones that are not already there. */
    fun merge(existing: List<AlarmModel>, incoming: List<AlarmModel>): List<AlarmModel> {
        val base = existing.filterNot(::isDemoAlarm)
        val extra = incoming.filter { add -> base.none { sameAlarm(it, add) } }
        return base + extra
    }
}
