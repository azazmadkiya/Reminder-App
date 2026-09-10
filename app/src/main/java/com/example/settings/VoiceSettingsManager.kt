package com.example.settings

import android.content.Context
import android.content.SharedPreferences

class VoiceSettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("voice_settings_prefs", Context.MODE_PRIVATE)

    fun isInternetAllowedForVoice(): Boolean {
        // Default to false (100% Offline) based on user preference
        return prefs.getBoolean(KEY_VOICE_INTERNET_ALLOWED, false)
    }

    fun setInternetAllowedForVoice(allowed: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_INTERNET_ALLOWED, allowed).apply()
    }

    companion object {
        private const val KEY_VOICE_INTERNET_ALLOWED = "voice_internet_allowed"
    }
}
