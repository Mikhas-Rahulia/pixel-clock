package io.github.mikhasrahulia.pixelclock.data

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import android.util.Log
import androidx.core.util.AtomicFile
import java.io.File

/**
 * Device-Encrypted (DE) storage helpers that stay 100% available across reboots
 * and Pixel overnight OTA updates before the user unlocks the device.
 */
internal fun Context.deviceProtectedContext(): Context {
    val app = applicationContext
    return if (app.isDeviceProtectedStorage) app else app.createDeviceProtectedStorageContext()
}

internal fun Context.isUserUnlockedSafe(): Boolean {
    return runCatching {
        getSystemService(UserManager::class.java)?.isUserUnlocked == true
    }.getOrDefault(false)
}

internal fun Context.clockPrefs(name: String, legacyKey: String): SharedPreferences {
    val app = applicationContext
    val storage = deviceProtectedContext()
    val prefs = storage.getSharedPreferences(name, Context.MODE_PRIVATE)
    if (!prefs.contains(legacyKey) && !app.isDeviceProtectedStorage && isUserUnlockedSafe()) {
        runCatching {
            if (!storage.moveSharedPreferencesFrom(app, name)) {
                val legacy = app.getSharedPreferences(name, Context.MODE_PRIVATE).getString(legacyKey, null)
                if (legacy != null) {
                    prefs.edit().putString(legacyKey, legacy).apply()
                }
            }
        }.onFailure { Log.w("ClockStorage", "Legacy prefs migration skipped", it) }
    }
    return prefs
}

internal fun writeAtomicText(file: File, content: String) {
    val atomic = AtomicFile(file)
    val stream = atomic.startWrite()
    try {
        stream.write(content.toByteArray(Charsets.UTF_8))
        stream.flush()
        atomic.finishWrite(stream)
    } catch (e: Exception) {
        atomic.failWrite(stream)
        throw e
    }
}

internal fun readAtomicText(file: File): String? {
    if (!file.isFile) return null
    return runCatching {
        AtomicFile(file).readFully().toString(Charsets.UTF_8)
    }.getOrNull()
}
