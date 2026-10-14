package com.stickerforge.app.data

import android.content.Context
import android.content.SharedPreferences

class AppUpdatePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getIgnoredVersion(): String? {
        return prefs.getString(KEY_IGNORED_VERSION, null)
    }

    fun setIgnoredVersion(versionTag: String) {
        prefs.edit().putString(KEY_IGNORED_VERSION, versionTag).apply()
    }

    fun clearIgnoredVersion() {
        prefs.edit().remove(KEY_IGNORED_VERSION).apply()
    }

    companion object {
        private const val PREFS_NAME = "app_update_prefs"
        private const val KEY_IGNORED_VERSION = "ignored_version"
    }
}
