package com.ddz.game

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.ddz.game.audio.SoundManager
import com.ddz.game.model.Difficulty
import com.ddz.game.util.SettingsManager
import com.ddz.game.util.StatsManager

class MainActivity : Activity() {

    private lateinit var btnSoundToggleMain: Button
    private lateinit var rowBeginner: LinearLayout
    private lateinit var rowNormal: LinearLayout
    private lateinit var rowExpert: LinearLayout
    private lateinit var tvBeginnerStat: TextView
    private lateinit var tvNormalStat: TextView
    private lateinit var tvExpertStat: TextView
    private lateinit var tvBeginnerRate: TextView
    private lateinit var tvNormalRate: TextView
    private lateinit var tvExpertRate: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)

        SettingsManager.init(this)
        StatsManager.init(this)
        SoundManager.init(this)
        SoundManager.setEnabled(SettingsManager.soundEnabled)

        bindViews()
        setupListeners()
    }

    private fun bindViews() {
        btnSoundToggleMain = findViewById(R.id.btnSoundToggleMain) as Button
        rowBeginner        = findViewById(R.id.rowBeginner) as LinearLayout
        rowNormal          = findViewById(R.id.rowNormal) as LinearLayout
        rowExpert          = findViewById(R.id.rowExpert) as LinearLayout
        tvBeginnerStat     = findViewById(R.id.tvBeginnerStat) as TextView
        tvNormalStat       = findViewById(R.id.tvNormalStat) as TextView
        tvExpertStat       = findViewById(R.id.tvExpertStat) as TextView
        tvBeginnerRate     = findViewById(R.id.tvBeginnerRate) as TextView
        tvNormalRate       = findViewById(R.id.tvNormalRate) as TextView
        tvExpertRate       = findViewById(R.id.tvExpertRate) as TextView
    }

    private fun setupListeners() {
        rowBeginner.setOnClickListener { startGame(Difficulty.BEGINNER) }
        rowNormal.setOnClickListener   { startGame(Difficulty.NORMAL) }
        rowExpert.setOnClickListener   { startGame(Difficulty.EXPERT) }

        btnSoundToggleMain.setOnClickListener {
            val newEnabled = !SettingsManager.soundEnabled
            SettingsManager.soundEnabled = newEnabled
            SoundManager.setEnabled(newEnabled)
            updateSoundButton()
        }
    }

    private fun startGame(difficulty: Difficulty) {
        SoundManager.play(SoundManager.Event.BID)
        startActivity(Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty.name)
        })
    }

    private fun updateSoundButton() {
        btnSoundToggleMain.text = if (SettingsManager.soundEnabled) "♪ 音效" else "✕ 静音"
    }

    private fun updateStats() {
        fun winRateStr(d: Difficulty): String {
            val w = StatsManager.getWins(d)
            val t = StatsManager.getTotal(d)
            return if (t == 0) "" else "胜率 ${(w * 100f / t).toInt()}%"
        }
        tvBeginnerStat.text = StatsManager.statStr(Difficulty.BEGINNER)
        tvNormalStat.text   = StatsManager.statStr(Difficulty.NORMAL)
        tvExpertStat.text   = StatsManager.statStr(Difficulty.EXPERT)
        tvBeginnerRate.text = winRateStr(Difficulty.BEGINNER)
        tvNormalRate.text   = winRateStr(Difficulty.NORMAL)
        tvExpertRate.text   = winRateStr(Difficulty.EXPERT)
    }

    override fun onResume() {
        super.onResume()
        updateStats()
        updateSoundButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}
