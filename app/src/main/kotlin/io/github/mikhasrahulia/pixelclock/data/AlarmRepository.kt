package io.github.mikhasrahulia.pixelclock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlarmRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.clockPrefs(AlarmBackup.PREFS_NAME, KEY_ALARMS)

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeQueue = Channel<List<AlarmModel>>(Channel.CONFLATED)

    private val _alarms = MutableStateFlow<List<AlarmModel>>(emptyList())
    val alarms: StateFlow<List<AlarmModel>> = _alarms.asStateFlow()

    init {
        loadAlarms()
        ioScope.launch {
            for (snapshot in writeQueue) {
                persistSync(snapshot)
            }
        }
    }

    private fun loadAlarms() {
        var needsPersist = false
        var list: List<AlarmModel>? = AlarmBackup.readStore(context)

        // Seamless one-time migration from SharedPreferences (preserves all existing user alarms)
        if (list == null) {
            val rawPrefs = prefs.getString(KEY_ALARMS, null)
            if (rawPrefs != null) {
                list = AlarmBackup.decode(rawPrefs)
                if (list != null) needsPersist = true
            }
        }

        val resolved = list ?: emptyList()
        val imported = AlarmBackup.consumeImportFile(context)
        val merged = if (imported != null) {
            needsPersist = true
            AlarmBackup.merge(resolved, imported)
        } else {
            resolved
        }

        val cleaned = deduplicateAlarms(merged)
        if (cleaned.size != merged.size) needsPersist = true

        _alarms.value = cleaned
        if (needsPersist) {
            scheduleSave(cleaned)
        }
        if (prefs.getInt(AlarmBackup.KEY_SCHEMA, 0) < AlarmBackup.SCHEMA_VERSION) {
            prefs.edit().putInt(AlarmBackup.KEY_SCHEMA, AlarmBackup.SCHEMA_VERSION).apply()
        }
    }

    private fun deduplicateAlarms(list: List<AlarmModel>): List<AlarmModel> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<AlarmModel>()
        for (alarm in list) {
            if (!alarm.isRepeating && alarm.label.isBlank()) {
                val key = "${alarm.hour}:${alarm.minute}:${alarm.specificDate}"
                if (seen.add(key)) {
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
        runCatching {
            val encoded = AlarmBackup.encode(list)
            AlarmBackup.writeStore(context, encoded)
            // Keep DE SharedPreferences mirrored for backwards compatibility across updates
            prefs.edit().putString(KEY_ALARMS, encoded).apply()
        }
    }

    fun replaceAll(list: List<AlarmModel>) {
        val cleaned = deduplicateAlarms(list)
        _alarms.value = cleaned
        scheduleSave(cleaned)
    }

    fun addAlarm(alarm: AlarmModel) {
        val updated = _alarms.value + alarm
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
