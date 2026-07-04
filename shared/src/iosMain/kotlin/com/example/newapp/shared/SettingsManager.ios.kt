package com.example.newapp.shared

import platform.Foundation.NSUserDefaults

actual class SettingsManager actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun saveString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    actual fun getString(key: String, defaultValue: String): String {
        return defaults.stringForKey(key) ?: defaultValue
    }

    actual fun saveBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return defaults.boolForKey(key)
    }
}
