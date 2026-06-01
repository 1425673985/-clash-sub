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

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)

        SoundManager.init(this)

        (findViewById(R.id.btnBeginner) as Button).setOnClickListener { startGame(Difficulty.BEGINNER) }
        (findViewById(R.id.btnNormal) as Button).setOnClickListener { startGame(Difficulty.NORMAL) }
        (findViewById(R.id.btnExpert) as Button).setOnClickListener { startGame(Difficulty.EXPERT) }
    }

    private fun startGame(difficulty: Difficulty) {
        SoundManager.play(SoundManager.Event.BID)
        startActivity(Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty.name)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}
