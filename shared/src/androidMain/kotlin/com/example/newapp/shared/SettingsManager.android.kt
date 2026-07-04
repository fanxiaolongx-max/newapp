package com.example.newapp.shared

import android.content.Context
import android.content.SharedPreferences

object AppContext {
    lateinit var context: Context
}

actual class SettingsManager actual constructor() {
    private val prefs: SharedPreferences by lazy {
        AppContext.context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }

    actual fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun getString(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    actual fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }
}
