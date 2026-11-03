package io.github.mikhasrahulia.pixelclock.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Survives app updates (same applicationId). Also used to copy alarms from a
 * previous package, or from a JSON file pushed next to the app.
 *
 * Search order on load:
 *  1. Shared preferences (normal store)
 *  2. `alarms-import.json` in the app's external files dir (then deleted)
 *  3. The previous package [LEGACY_PACKAGE], if it is still installed
 *  4. Local [BACKUP_NAME] written on every save
 *
 * Demo 07:00 / 08:30 seeds are dropped when a real import is found.
 */
internal object AlarmBackup {
    const val LEGACY_PACKAGE = "com.example.pixelclock"
    const val PREFS_NAME = "pixel_clock_alarms"
    const val KEY_ALARMS = "key_alarms_list"
    const val KEY_SCHEMA = "key_alarms_schema"
    const val SCHEMA_VERSION = 2
    const val BACKUP_NAME = "alarms-backup.json"
    const val IMPORT_NAME = "alarms-import.json"

    private const val TAG = "AlarmBackup"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decode(raw: String): List<AlarmModel>? = runCatching {
        json.decodeFromString<List<AlarmModel>>(raw)
    }.onFailure { Log.w(TAG, "Could not parse alarms JSON", it) }.getOrNull()

    fun encode(list: List<AlarmModel>): String = json.encodeToString(list)

    fun backupFile(context: Context): File = File(context.filesDir, BACKUP_NAME)

    fun importFile(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, IMPORT_NAME)
    }

    fun writeBackup(context: Context, list: List<AlarmModel>) {
        val text = encode(list)
        runCatching { backupFile(context).writeText(text) }
        runCatching {
            val external = context.getExternalFilesDir(null) ?: return@runCatching
            File(external, BACKUP_NAME).writeText(text)
        }
    }

    fun readBackup(context: Context): List<AlarmModel>? {
        val files = listOf(
            backupFile(context),
            File(context.getExternalFilesDir(null) ?: return null, BACKUP_NAME)
        )
        for (file in files) {
            if (!file.isFile) continue
            decode(file.readText())?.let { return it }
        }
        return null
    }

    /** One-shot file the user (or an installer) can drop in. Consumed after a successful read. */
    fun consumeImportFile(context: Context): List<AlarmModel>? {
        val file = importFile(context)
        if (!file.isFile) return null
        val list = decode(file.readText())
        runCatching { file.delete() }
        return list
    }

    /**
     * Read SharedPreferences of [LEGACY_PACKAGE]. This only works if both apps share a UID
     * (they do not, after a package rename). Failures are ignored.
     */
    fun readLegacyPackage(context: Context): List<AlarmModel>? {
        val other = runCatching {
            context.createPackageContext(LEGACY_PACKAGE, 0)
        }.getOrNull() ?: return null
        val protected = runCatching { other.createDeviceProtectedStorageContext() }.getOrNull() ?: other
        val raw = protected.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALARMS, null)
            ?: other.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ALARMS, null)
            ?: return null
        return decode(raw)
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
