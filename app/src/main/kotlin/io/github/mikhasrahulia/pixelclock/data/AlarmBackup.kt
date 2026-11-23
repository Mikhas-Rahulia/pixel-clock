package io.github.mikhasrahulia.pixelclock.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File

/**
 * Resilient, multi-layer atomic JSON persistence for alarms inside Device-Encrypted (DE)
 * internal storage, with CE & legacy fallbacks.
 *
 * Guarantees that user alarms survive every app update, schema evolution, process death,
 * and Pixel overnight OTA Direct Boot reboot — strictly local on-device.
 */
internal object AlarmBackup {
    const val LEGACY_PACKAGE = "com.example.pixelclock"
    const val PREFS_NAME = "pixel_clock_alarms"
    const val KEY_ALARMS = "key_alarms_list"
    const val KEY_SCHEMA = "key_alarms_schema"
    const val KEY_USER_CLEARED = "key_alarms_user_cleared"
    const val KEY_MIGRATED_CE = "key_alarms_migrated_ce"
    const val SCHEMA_VERSION = 4
    const val STORAGE_NAME = "alarms-store.json"
    const val BACKUP_NAME = "alarms-backup.json"
    const val PREV_SNAPSHOT_NAME = "alarms-prev.json"
    const val IMPORT_NAME = "alarms-import.json"

    private const val TAG = "AlarmBackup"

    private fun logWarn(msg: String, t: Throwable? = null) {
        runCatching {
            if (t != null) Log.w(TAG, msg, t) else Log.w(TAG, msg)
        }
    }

    private fun logError(msg: String, t: Throwable? = null) {
        runCatching {
            if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val prettyJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    /**
     * Resiliently decodes an alarm list from raw JSON.
     * - Supports both a plain JSON array `[...]` and a wrapped object `{"alarms":[...]}`.
     * - Decodes element-by-element so one corrupt/incompatible entry never drops other alarms.
     * - Returns null if the payload is syntactically invalid or non-empty with zero valid entries.
     */
    fun decode(raw: String): List<AlarmModel>? = runCatching {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val root = json.parseToJsonElement(trimmed)
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["alarms"] as? JsonArray ?: return null
            else -> return null
        }
        if (array.isEmpty()) return emptyList()
        val decoded = mutableListOf<AlarmModel>()
        for (element in array) {
            runCatching {
                json.decodeFromJsonElement<AlarmModel>(element)
            }.onSuccess { alarm ->
                if (alarm.hour in 0..23 && alarm.minute in 0..59) {
                    decoded += alarm
                }
            }.onFailure { err ->
                logWarn("Skipped malformed alarm entry during decode", err)
            }
        }
        if (decoded.isEmpty()) null else decoded
    }.onFailure { logWarn("Could not parse alarms JSON", it) }.getOrNull()

    fun encode(list: List<AlarmModel>): String = json.encodeToString(list)

    fun encodePretty(list: List<AlarmModel>): String = prettyJson.encodeToString(list)

    fun storeFile(context: Context): File = File(context.deviceProtectedContext().filesDir, STORAGE_NAME)

    fun backupFile(context: Context): File = File(context.deviceProtectedContext().filesDir, BACKUP_NAME)

    fun prevSnapshotFile(context: Context): File = File(context.deviceProtectedContext().filesDir, PREV_SNAPSHOT_NAME)

    fun ceBackupFile(context: Context): File? =
        if (context.isUserUnlockedSafe()) {
            runCatching { File(context.applicationContext.filesDir, BACKUP_NAME) }.getOrNull()
        } else null

    fun writeStore(context: Context, encodedJson: String, isNonEmpty: Boolean) {
        val primary = storeFile(context)
        // Before overwriting a valid non-empty store, preserve it as the rolling previous snapshot.
        if (isNonEmpty) {
            runCatching {
                val existing = readAtomicText(primary)
                if (existing != null && existing != encodedJson && !decode(existing).isNullOrEmpty()) {
                    writeAtomicText(prevSnapshotFile(context), existing)
                }
            }.onFailure { logWarn("Failed updating rolling alarm snapshot", it) }
        }

        runCatching { writeAtomicText(primary, encodedJson) }
            .onFailure { logError("Failed writing atomic alarm store", it) }
        runCatching { writeAtomicText(backupFile(context), encodedJson) }
            .onFailure { logWarn("Failed writing atomic alarm backup", it) }

        // Keep an internal CE mirror when unlocked as an extra local safeguard across updates.
        ceBackupFile(context)?.let { ceFile ->
            runCatching { writeAtomicText(ceFile, encodedJson) }
                .onFailure { logWarn("Failed writing CE alarm mirror", it) }
        }
    }

    /**
     * Reads alarms from DE internal storage first (primary -> backup -> rolling snapshot).
     * When [allowEmpty] is false, an unexpected empty file does not short-circuit recovery
     * from non-empty backup copies.
     */
    fun readStore(context: Context, allowEmpty: Boolean = true): List<AlarmModel>? {
        val candidates = listOf(
            storeFile(context),
            backupFile(context),
            prevSnapshotFile(context)
        )
        var foundEmpty = false
        for (file in candidates) {
            val raw = readAtomicText(file) ?: continue
            val parsed = decode(raw)
            if (parsed != null) {
                if (parsed.isNotEmpty() || allowEmpty) return parsed
                foundEmpty = true
            } else {
                quarantineCorruptFile(file)
            }
        }
        return readCeAndLegacyBackups(context, allowEmpty) ?: if (foundEmpty) emptyList() else null
    }

    fun readBackup(context: Context): List<AlarmModel>? = readCeAndLegacyBackups(context, allowEmpty = true)

    /**
     * Checks Credential-Encrypted (CE) storage and legacy pre-beta.14 backup locations
     * when the user is unlocked, guaranteeing zero data loss when upgrading from any beta.
     */
    fun readCeAndLegacyBackups(context: Context, allowEmpty: Boolean = true): List<AlarmModel>? {
        if (!context.isUserUnlockedSafe()) return null
        val app = context.applicationContext

        // 1. CE SharedPreferences (beta.1–beta.13 primary store)
        val cePrefsRaw = runCatching {
            app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ALARMS, null)
        }.getOrNull()
        if (cePrefsRaw != null) {
            decode(cePrefsRaw)?.let { list ->
                if (list.isNotEmpty() || allowEmpty) return list
            }
        }

        // 2. CE internal backup file & legacy external files backup (written by beta.3–beta.13)
        val files = buildList {
            runCatching { File(app.filesDir, BACKUP_NAME) }.getOrNull()?.let(::add)
            runCatching { app.getExternalFilesDir(null)?.let { File(it, BACKUP_NAME) } }
                .getOrNull()?.let(::add)
        }
        for (file in files) {
            val raw = readAtomicText(file) ?: continue
            val parsed = decode(raw)
            if (parsed != null) {
                if (parsed.isNotEmpty() || allowEmpty) return parsed
            } else {
                quarantineCorruptFile(file)
            }
        }

        // 3. Legacy package (com.example.pixelclock) if still installed
        val legacyRaw = runCatching {
            app.createPackageContext(LEGACY_PACKAGE, 0)
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ALARMS, null)
        }.getOrNull()
        if (legacyRaw != null) {
            decode(legacyRaw)?.takeIf { it.isNotEmpty() }?.let { return it }
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
            if (list != null) {
                file.delete()
            }
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
