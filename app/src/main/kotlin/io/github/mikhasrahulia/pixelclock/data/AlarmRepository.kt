package io.github.mikhasrahulia.pixelclock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.clockPrefs(AlarmBackup.PREFS_NAME, KEY_ALARMS)

    private val _alarms = MutableStateFlow<List<AlarmModel>>(emptyList())
    val alarms: StateFlow<List<AlarmModel>> = _alarms.asStateFlow()

    init {
        loadAlarms()
    }

    private fun loadAlarms() {
        val fromPrefs = prefs.getString(KEY_ALARMS, null)?.let { AlarmBackup.decode(it) }
        val fromImport = AlarmBackup.consumeImportFile(context)
        val fromLegacy = AlarmBackup.readLegacyPackage(context)
        val fromBackup = AlarmBackup.readBackup(context)

        var list = fromPrefs ?: emptyList()
        // A JSON file dropped next to the app (or adb-pushed) wins as a one-shot import.
        if (fromImport != null) {
            list = AlarmBackup.merge(list, fromImport)
        } else if (fromLegacy != null && (list.isEmpty() || AlarmBackup.isDemoSeed(list))) {
            list = AlarmBackup.merge(list, fromLegacy)
        } else if (list.isEmpty() && fromBackup != null) {
            list = fromBackup
        }

        val cleaned = deduplicateAlarms(list)
        _alarms.value = cleaned
        saveAlarms(cleaned)
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

    private fun saveAlarms(list: List<AlarmModel>) {
        try {
            val jsonStr = AlarmBackup.encode(list)
            prefs.edit().putString(KEY_ALARMS, jsonStr).apply()
            AlarmBackup.writeBackup(context, list)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun replaceAll(list: List<AlarmModel>) {
        val cleaned = deduplicateAlarms(list)
        _alarms.value = cleaned
        saveAlarms(cleaned)
    }

    fun addAlarm(alarm: AlarmModel) {
        val updated = _alarms.value + alarm
        _alarms.value = updated
        saveAlarms(updated)
    }

    fun updateAlarm(alarm: AlarmModel) {
        val updated = _alarms.value.map { if (it.id == alarm.id) alarm else it }
        _alarms.value = updated
        saveAlarms(updated)
    }

    fun deleteAlarm(alarmId: String) {
        val updated = _alarms.value.filterNot { it.id == alarmId }
        _alarms.value = updated
        saveAlarms(updated)
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
        saveAlarms(updated)
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
        saveAlarms(updated)
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
