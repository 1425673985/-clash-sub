package com.ddz.game

import android.content.Intent
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.ddz.game.audio.SoundManager
import com.ddz.game.databinding.ActivityMainBinding
import com.ddz.game.model.Difficulty

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SoundManager.init(this)

        binding.btnBeginner.setOnClickListener { startGame(Difficulty.BEGINNER) }
        binding.btnNormal.setOnClickListener { startGame(Difficulty.NORMAL) }
        binding.btnExpert.setOnClickListener { startGame(Difficulty.EXPERT) }
    }

    private fun startGame(difficulty: Difficulty) {
        SoundManager.play(SoundManager.Event.BID)
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty.name)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}
