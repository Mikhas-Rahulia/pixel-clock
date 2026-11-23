package io.github.mikhasrahulia.pixelclock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.clockPrefs(AlarmBackup.PREFS_NAME, KEY_ALARMS)

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeQueue = Channel<List<AlarmModel>>(Channel.CONFLATED)
    private val writeLock = Any()

    private val _alarms = MutableStateFlow<List<AlarmModel>>(emptyList())
    val alarms: StateFlow<List<AlarmModel>> = _alarms.asStateFlow()

    @Volatile
    private var loadedAfterUnlock = false

    init {
        loadAlarms()
        ioScope.launch {
            for (snapshot in writeQueue) {
                withContext(NonCancellable) {
                    persistSync(snapshot)
                }
            }
        }
    }

    /**
     * Re-checks CE & import sources once the user is unlocked (e.g., if Direct Boot
     * initialized this singleton before first unlock, or after an app update / manual import).
     */
    fun ensureLoadedAndMigrated() {
        synchronized(writeLock) {
            val unlocked = context.isUserUnlockedSafe()
            val needsCeCheck = unlocked && (!loadedAfterUnlock || !prefs.getBoolean(AlarmBackup.KEY_MIGRATED_CE, false))
            val imported = if (unlocked) AlarmBackup.consumeImportFile(context) else null

            if (!needsCeCheck && imported == null) return

            var current = _alarms.value
            var changed = false

            if (needsCeCheck) {
                val userCleared = prefs.getBoolean(AlarmBackup.KEY_USER_CLEARED, false)
                if (current.isEmpty() && !userCleared) {
                    val recovered = AlarmBackup.readCeAndLegacyBackups(context, allowEmpty = false)
                        ?: prefs.getString(KEY_ALARMS, null)?.let(AlarmBackup::decode)
                    if (!recovered.isNullOrEmpty()) {
                        current = recovered
                        changed = true
                    }
                }
                loadedAfterUnlock = true
                prefs.edit().putBoolean(AlarmBackup.KEY_MIGRATED_CE, true).apply()
            }

            if (imported != null && imported.isNotEmpty()) {
                current = AlarmBackup.merge(current, imported)
                changed = true
                prefs.edit().putBoolean(AlarmBackup.KEY_USER_CLEARED, false).apply()
            }

            if (changed) {
                val cleaned = deduplicateAlarms(current)
                _alarms.value = cleaned
                persistSync(cleaned)
            }
        }
    }

    private fun loadAlarms() {
        synchronized(writeLock) {
            val unlocked = context.isUserUnlockedSafe()
            val userCleared = prefs.getBoolean(AlarmBackup.KEY_USER_CLEARED, false)
            var needsPersist = false

            var list: List<AlarmModel>? = AlarmBackup.readStore(context, allowEmpty = userCleared)

            // Fallback to DE SharedPreferences (preserves alarms across every update path)
            if (list == null || (list.isEmpty() && !userCleared)) {
                val rawPrefs = prefs.getString(KEY_ALARMS, null)
                if (rawPrefs != null) {
                    val fromPrefs = AlarmBackup.decode(rawPrefs)
                    if (fromPrefs != null && (fromPrefs.isNotEmpty() || list == null)) {
                        list = fromPrefs
                        if (fromPrefs.isNotEmpty()) needsPersist = true
                    }
                }
            }

            // During Direct Boot before unlock, never persist an empty store over unmigrated CE data.
            if (list == null && !unlocked) {
                _alarms.value = emptyList()
                return
            }

            val resolved = list ?: emptyList()
            val imported = if (unlocked) AlarmBackup.consumeImportFile(context) else null
            val merged = if (imported != null && imported.isNotEmpty()) {
                needsPersist = true
                prefs.edit().putBoolean(AlarmBackup.KEY_USER_CLEARED, false).apply()
                AlarmBackup.merge(resolved, imported)
            } else {
                resolved
            }

            val cleaned = deduplicateAlarms(merged)
            if (cleaned.size != merged.size) needsPersist = true

            _alarms.value = cleaned
            if (unlocked) {
                loadedAfterUnlock = true
                if (!prefs.getBoolean(AlarmBackup.KEY_MIGRATED_CE, false)) {
                    prefs.edit().putBoolean(AlarmBackup.KEY_MIGRATED_CE, true).apply()
                }
            }

            if (needsPersist && (cleaned.isNotEmpty() || userCleared)) {
                persistSync(cleaned)
            }
            if (prefs.getInt(AlarmBackup.KEY_SCHEMA, 0) < AlarmBackup.SCHEMA_VERSION) {
                prefs.edit().putInt(AlarmBackup.KEY_SCHEMA, AlarmBackup.SCHEMA_VERSION).apply()
                if (cleaned.isNotEmpty()) {
                    // Rewrite in latest self-contained schema with encodeDefaults = true
                    scheduleSave(cleaned)
                }
            }
        }
    }

    private fun deduplicateAlarms(list: List<AlarmModel>): List<AlarmModel> {
        val seenIds = mutableSetOf<String>()
        val seenKeys = mutableSetOf<String>()
        val result = mutableListOf<AlarmModel>()
        for (alarm in list) {
            if (!seenIds.add(alarm.id)) continue
            if (!alarm.isRepeating && alarm.label.isBlank()) {
                val key = "${alarm.hour}:${alarm.minute}:${alarm.specificDate}"
                if (seenKeys.add(key)) {
                    result.add(alarm)
                }
            } else {
                result.add(alarm)
            }
        }
        return result
    }

    private fun scheduleSave(list: List<AlarmModel>) {
        writeQueue.trySend(list)
    }

    private fun persistSync(list: List<AlarmModel>) {
        synchronized(writeLock) {
            runCatching {
                val encoded = AlarmBackup.encode(list)
                AlarmBackup.writeStore(context, encoded, isNonEmpty = list.isNotEmpty())
                // Keep DE SharedPreferences mirrored synchronously for backwards compatibility across updates
                prefs.edit()
                    .putString(KEY_ALARMS, encoded)
                    .putInt(AlarmBackup.KEY_SCHEMA, AlarmBackup.SCHEMA_VERSION)
                    .apply()
            }
        }
    }

    /** Guarantees the latest in-memory alarms are flushed atomically to disk right away. */
    fun flushBlocking() {
        persistSync(_alarms.value)
    }

    fun exportAlarmsJson(): String = AlarmBackup.encodePretty(_alarms.value)

    /**
     * Merges alarms decoded from a JSON backup string. Returns the number of newly added alarms,
     * or -1 if the JSON payload could not be parsed.
     */
    fun importAlarmsJson(rawJson: String): Int {
        val incoming = AlarmBackup.decode(rawJson) ?: return -1
        val before = _alarms.value
        val merged = deduplicateAlarms(AlarmBackup.merge(before, incoming))
        val addedCount = (merged.size - before.filterNot(AlarmBackup::isDemoAlarm).size).coerceAtLeast(0)
        if (merged.isNotEmpty()) {
            prefs.edit().putBoolean(AlarmBackup.KEY_USER_CLEARED, false).apply()
        }
        _alarms.value = merged
        persistSync(merged)
        return addedCount
    }

    fun replaceAll(list: List<AlarmModel>) {
        val cleaned = deduplicateAlarms(list)
        prefs.edit().putBoolean(AlarmBackup.KEY_USER_CLEARED, cleaned.isEmpty()).apply()
        _alarms.value = cleaned
        scheduleSave(cleaned)
    }

    fun addAlarm(alarm: AlarmModel) {
        val updated = _alarms.value + alarm
        prefs.edit().putBoolean(AlarmBackup.KEY_USER_CLEARED, false).apply()
        _alarms.value = updated
        scheduleSave(updated)
    }

    fun updateAlarm(alarm: AlarmModel) {
        val updated = _alarms.value.map { if (it.id == alarm.id) alarm else it }
        _alarms.value = updated
        scheduleSave(updated)
    }

    fun deleteAlarm(alarmId: String) {
        val updated = _alarms.value.filterNot { it.id == alarmId }
        if (updated.isEmpty()) {
            prefs.edit().putBoolean(AlarmBackup.KEY_USER_CLEARED, true).apply()
        }
        _alarms.value = updated
        scheduleSave(updated)
    }

    fun toggleAlarm(alarmId: String, enabled: Boolean): AlarmModel? {
        var updatedAlarm: AlarmModel? = null
        val updated = _alarms.value.map {
            if (it.id == alarmId) {
                val copy = it.copy(isEnabled = enabled)
                updatedAlarm = copy
                copy
            } else it
        }
        _alarms.value = updated
        scheduleSave(updated)
        return updatedAlarm
    }

    fun shiftAlarmCycle(alarmId: String): AlarmModel? {
        var updatedAlarm: AlarmModel? = null
        val updated = _alarms.value.map {
            if (it.id == alarmId && it.isBiWeekly) {
                val copy = it.shiftCycleOneWeek()
                updatedAlarm = copy
                copy
            } else it
        }
        _alarms.value = updated
        scheduleSave(updated)
        return updatedAlarm
    }

    fun getAlarmById(alarmId: String): AlarmModel? {
        return _alarms.value.find { it.id == alarmId }
    }

    companion object {
        private const val KEY_ALARMS = AlarmBackup.KEY_ALARMS

        @Volatile
        private var instance: AlarmRepository? = null

        fun getInstance(context: Context): AlarmRepository {
            return instance ?: synchronized(this) {
                instance ?: AlarmRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
