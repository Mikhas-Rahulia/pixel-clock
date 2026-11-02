package io.github.mikhasrahulia.pixelclock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

class AlarmRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.clockPrefs("pixel_clock_alarms", KEY_ALARMS)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _alarms = MutableStateFlow<List<AlarmModel>>(emptyList())
    val alarms: StateFlow<List<AlarmModel>> = _alarms.asStateFlow()

    init {
        loadAlarms()
    }

    private fun loadAlarms() {
        val jsonStr = prefs.getString(KEY_ALARMS, null)
        if (jsonStr != null) {
            try {
                val list = json.decodeFromString<List<AlarmModel>>(jsonStr)
                _alarms.value = list
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Default initial alarms for Pixel experience
        val currentParity = AlarmModel.currentWeekParity(LocalDate.now())
        val defaultList = listOf(
            AlarmModel(
                hour = 7,
                minute = 0,
                label = "",
                isEnabled = true,
                daysOfWeek = setOf(1, 2, 3, 4, 5),
                isBiWeekly = false
            ),
            AlarmModel(
                hour = 8,
                minute = 30,
                label = "",
                isEnabled = true,
                daysOfWeek = setOf(1, 3, 5), // Mon, Wed, Fri
                isBiWeekly = true,
                weekParity = currentParity // Active this week
            )
        )
        _alarms.value = defaultList
        saveAlarms(defaultList)
    }

    private fun saveAlarms(list: List<AlarmModel>) {
        try {
            val jsonStr = json.encodeToString(list)
            prefs.edit().putString(KEY_ALARMS, jsonStr).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        private const val KEY_ALARMS = "key_alarms_list"

        @Volatile
        private var instance: AlarmRepository? = null

        fun getInstance(context: Context): AlarmRepository {
            return instance ?: synchronized(this) {
                instance ?: AlarmRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
