package com.ddz.game.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

object SoundManager {
    private var toneGen: ToneGenerator? = null
    private var enabled = true

    fun init(context: Context) {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (e: Exception) {
            // 部分设备可能不支持，静默失败
        }
    }

    fun setEnabled(on: Boolean) { enabled = on }

    enum class Event { DEAL, PLAY, PASS, BID, BOMB, WIN, LOSE }

    fun play(event: Event) {
        if (!enabled) return
        val gen = toneGen ?: return
        try {
            when (event) {
                Event.DEAL -> gen.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                Event.PLAY -> gen.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
                Event.PASS -> gen.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 150)
                Event.BID  -> gen.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                Event.BOMB -> gen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
                Event.WIN  -> gen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 500)
                Event.LOSE -> gen.startTone(ToneGenerator.TONE_CDMA_LOW_L, 500)
            }
        } catch (e: Exception) { /* 忽略音效错误 */ }
    }

    fun release() {
        toneGen?.release()
        toneGen = null
    }
}
