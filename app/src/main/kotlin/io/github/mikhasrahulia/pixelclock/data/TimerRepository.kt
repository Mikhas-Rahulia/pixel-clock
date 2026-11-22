package io.github.mikhasrahulia.pixelclock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.io.File

class TimerRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.clockPrefs(PREFS_NAME, KEY_TIMERS)
    private val json = AlarmBackup.json
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val timersChannel = Channel<List<TimerModel>>(Channel.CONFLATED)
    private val runsChannel = Channel<List<TimerRunRecord>>(Channel.CONFLATED)

    private val timersFile: File
        get() = File(context.deviceProtectedContext().filesDir, TIMERS_FILE)
    private val runsFile: File
        get() = File(context.deviceProtectedContext().filesDir, RUNS_FILE)

    init {
        ioScope.launch {
            for (snapshot in timersChannel) {
                runCatching { writeAtomicText(timersFile, json.encodeToString(snapshot)) }
            }
        }
        ioScope.launch {
            for (snapshot in runsChannel) {
                runCatching { writeAtomicText(runsFile, json.encodeToString(snapshot)) }
            }
        }
    }

    fun loadTimers(): List<TimerModel> = decodeFileOrPrefs(timersFile, KEY_TIMERS)

    fun saveTimers(list: List<TimerModel>) {
        timersChannel.trySend(list)
    }

    fun loadRuns(): List<TimerRunRecord> = decodeFileOrPrefs(runsFile, KEY_RUNS)

    fun saveRuns(list: List<TimerRunRecord>) {
        runsChannel.trySend(list)
    }

    private inline fun <reified T> decodeFileOrPrefs(file: File, prefsKey: String): List<T> {
        val raw = readAtomicText(file) ?: prefs.getString(prefsKey, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<T>>(raw)
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "pixel_clock_timers"
        private const val KEY_TIMERS = "key_timers_list"
        private const val KEY_RUNS = "key_timer_runs"
        private const val TIMERS_FILE = "timers-store.json"
        private const val RUNS_FILE = "timer-runs-store.json"

        @Volatile
        private var instance: TimerRepository? = null

        fun getInstance(context: Context): TimerRepository {
            return instance ?: synchronized(this) {
                instance ?: TimerRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
