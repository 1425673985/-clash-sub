package com.ddz.game

import androidx.lifecycle.*
import com.ddz.game.audio.SoundManager
import com.ddz.game.engine.GameEngine
import com.ddz.game.model.*
import kotlinx.coroutines.cancel

class GameViewModel : ViewModel() {

    val gameState = MutableLiveData<GameState>()
    val toastMsg = MutableLiveData<String>()

    private var engine: GameEngine? = null

    fun init(difficulty: Difficulty) {
        engine?.cancelAi()
        engine = GameEngine(
            difficulty = difficulty,
            scope = viewModelScope,
            onStateChanged = { state -> gameState.postValue(state) },
            onSoundEvent = { event ->
                val se = when (event) {
                    GameEngine.SoundEvent.DEAL -> SoundManager.Event.DEAL
                    GameEngine.SoundEvent.PLAY -> SoundManager.Event.PLAY
                    GameEngine.SoundEvent.PASS -> SoundManager.Event.PASS
                    GameEngine.SoundEvent.BID  -> SoundManager.Event.BID
                    GameEngine.SoundEvent.BOMB -> SoundManager.Event.BOMB
                    GameEngine.SoundEvent.WIN  -> SoundManager.Event.WIN
                    GameEngine.SoundEvent.LOSE -> SoundManager.Event.LOSE
                }
                SoundManager.play(se)
            }
        )
        engine!!.startGame()
    }

    fun onPlay(cards: List<Card>) {
        if (cards.isEmpty()) {
            toastMsg.value = "请选择要出的牌"
            return
        }
        val ok = engine?.onPlayerPlay(cards) ?: false
        if (!ok) toastMsg.value = "出牌不合法"
    }

    fun onPass() {
        val ok = engine?.onPlayerPass() ?: false
        if (!ok) toastMsg.value = "本轮你先出，不能不出"
    }

    fun onBid(score: Int) {
        engine?.onPlayerBid(score)
    }

    fun restartGame() {
        engine?.startGame()
    }

    override fun onCleared() {
        super.onCleared()
        engine?.cancelAi()
    }
}
