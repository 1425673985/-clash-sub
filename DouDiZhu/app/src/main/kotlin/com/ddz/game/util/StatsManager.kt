package com.ddz.game.util

import android.content.Context
import android.content.SharedPreferences
import com.ddz.game.model.Difficulty

object StatsManager {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("ddz_stats", Context.MODE_PRIVATE)
    }

    fun recordWin(difficulty: Difficulty)  = inc("w_${difficulty.name}")
    fun recordLoss(difficulty: Difficulty) = inc("l_${difficulty.name}")

    fun getWins(d: Difficulty)   = prefs.getInt("w_${d.name}", 0)
    fun getLosses(d: Difficulty) = prefs.getInt("l_${d.name}", 0)
    fun getTotal(d: Difficulty)  = getWins(d) + getLosses(d)

    fun statStr(d: Difficulty): String {
        val w = getWins(d); val l = getLosses(d); val t = w + l
        val rate = if (t == 0) "--" else "${(w * 100f / t).toInt()}%"
        return if (t == 0) "暂无记录" else "${w}胜 ${l}负  $rate"
    }

    private fun inc(key: String) =
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
}
