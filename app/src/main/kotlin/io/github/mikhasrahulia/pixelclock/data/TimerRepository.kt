package io.github.mikhasrahulia.pixelclock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TimerRepository(context: Context) {
    private val prefs: SharedPreferences = context.clockPrefs(PREFS_NAME, KEY_TIMERS)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadTimers(): List<TimerModel> = decode(KEY_TIMERS)

    fun saveTimers(list: List<TimerModel>) {
        prefs.edit().putString(KEY_TIMERS, json.encodeToString(list)).apply()
    }

    fun loadRuns(): List<TimerRunRecord> = decode(KEY_RUNS)

    fun saveRuns(list: List<TimerRunRecord>) {
        prefs.edit().putString(KEY_RUNS, json.encodeToString(list)).apply()
    }

    private inline fun <reified T> decode(key: String): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<T>>(raw)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "pixel_clock_timers"
        private const val KEY_TIMERS = "key_timers_list"
        private const val KEY_RUNS = "key_timer_runs"

        @Volatile
        private var instance: TimerRepository? = null

        fun getInstance(context: Context): TimerRepository {
            return instance ?: synchronized(this) {
                instance ?: TimerRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
