package com.ddz.game.util

import android.content.Context
import android.content.SharedPreferences
import com.ddz.game.model.Difficulty

object SettingsManager {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("ddz_settings", Context.MODE_PRIVATE)
    }

    var difficulty: Difficulty
        get() = try {
            Difficulty.valueOf(prefs.getString("difficulty", Difficulty.NORMAL.name)!!)
        } catch (e: Exception) { Difficulty.NORMAL }
        set(value) { prefs.edit().putString("difficulty", value.name).apply() }

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(value) { prefs.edit().putBoolean("sound_enabled", value).apply() }
}
