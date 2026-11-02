package com.example.pixelclock.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferences that stay available across reboot, including before the first unlock.
 * Existing installs are copied across from credential storage once.
 */
internal fun Context.clockPrefs(name: String, legacyKey: String): SharedPreferences {
    val app = applicationContext
    val storage = if (app.isDeviceProtectedStorage) app else app.createDeviceProtectedStorageContext()
    val prefs = storage.getSharedPreferences(name, Context.MODE_PRIVATE)
    if (!prefs.contains(legacyKey) && !app.isDeviceProtectedStorage) {
        val legacy = app.getSharedPreferences(name, Context.MODE_PRIVATE).getString(legacyKey, null)
        if (legacy != null) {
            prefs.edit().putString(legacyKey, legacy).apply()
        }
    }
    return prefs
}
