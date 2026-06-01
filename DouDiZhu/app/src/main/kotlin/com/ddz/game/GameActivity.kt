package com.ddz.game

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ddz.game.audio.SoundManager
import com.ddz.game.engine.GameEngine
import com.ddz.game.engine.HandEvaluator
import com.ddz.game.model.*
import com.ddz.game.ui.CardGroupView
import com.ddz.game.ui.PlayerHandView
import com.ddz.game.util.AnimationHelper
import kotlinx.coroutines.*

class GameActivity : Activity() {

    companion object {
        const val EXTRA_DIFFICULTY = "difficulty"
    }

    private lateinit var difficulty: Difficulty
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var engine: GameEngine? = null

    // 叫牌面板
    private lateinit var bidPanel: LinearLayout
    private lateinit var tvBidTip: TextView
    private lateinit var tvBidStatus: TextView
    private lateinit var playerHandBid: PlayerHandView
    private lateinit var bottomCardsAreaBid: CardGroupView
    private lateinit var btnBid0: Button
    private lateinit var btnBid1: Button
    private lateinit var btnBid2: Button
    private lateinit var btnBid3: Button

    // 游戏面板
    private lateinit var gamePanel: LinearLayout
    private lateinit var tvLeftAiName: TextView
    private lateinit var tvLeftAiCount: TextView
    private lateinit var leftAiCards: CardGroupView
    private lateinit var tvRightAiName: TextView
    private lateinit var tvRightAiCount: TextView
    private lateinit var rightAiCards: CardGroupView
    private lateinit var bottomCardsArea: CardGroupView
    private lateinit var tvLandlordBadge: TextView
    private lateinit var tvMultiplier: TextView
    private lateinit var tableCardsLeft: CardGroupView
    private lateinit var tableCardsCenter: CardGroupView
    private lateinit var tableCardsRight: CardGroupView
    private lateinit var tvTurnHint: TextView
    private lateinit var tvPlayerName: TextView
    private lateinit var tvPlayerCount: TextView
    private lateinit var tvScores: TextView
    private lateinit var playerHand: PlayerHandView
    private lateinit var btnSort: Button
    private lateinit var btnPass: Button
    private lateinit var btnPlay: Button

    private var lastPlayerHandIds = emptyList<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_game)

        SoundManager.init(this)

        difficulty = Difficulty.valueOf(
            intent.getStringExtra(EXTRA_DIFFICULTY) ?: Difficulty.NORMAL.name
        )

        bindViews()
        setupButtons()
        startNewGame()
    }

    private fun bindViews() {
        bidPanel = findViewById(R.id.bidPanel) as LinearLayout
        tvBidTip = findViewById(R.id.tvBidTip) as TextView
        tvBidStatus = findViewById(R.id.tvBidStatus) as TextView
        playerHandBid = findViewById(R.id.playerHandBid) as PlayerHandView
        bottomCardsAreaBid = findViewById(R.id.bottomCardsAreaBid) as CardGroupView
        btnBid0 = findViewById(R.id.btnBid0) as Button
        btnBid1 = findViewById(R.id.btnBid1) as Button
        btnBid2 = findViewById(R.id.btnBid2) as Button
        btnBid3 = findViewById(R.id.btnBid3) as Button

        gamePanel = findViewById(R.id.gamePanel) as LinearLayout
        tvLeftAiName = findViewById(R.id.tvLeftAiName) as TextView
        tvLeftAiCount = findViewById(R.id.tvLeftAiCount) as TextView
        leftAiCards = findViewById(R.id.leftAiCards) as CardGroupView
        tvRightAiName = findViewById(R.id.tvRightAiName) as TextView
        tvRightAiCount = findViewById(R.id.tvRightAiCount) as TextView
        rightAiCards = findViewById(R.id.rightAiCards) as CardGroupView
        bottomCardsArea = findViewById(R.id.bottomCardsArea) as CardGroupView
        tvLandlordBadge = findViewById(R.id.tvLandlordBadge) as TextView
        tvMultiplier = findViewById(R.id.tvMultiplier) as TextView
        tableCardsLeft = findViewById(R.id.tableCardsLeft) as CardGroupView
        tableCardsCenter = findViewById(R.id.tableCardsCenter) as CardGroupView
        tableCardsRight = findViewById(R.id.tableCardsRight) as CardGroupView
        tvTurnHint = findViewById(R.id.tvTurnHint) as TextView
        tvPlayerName = findViewById(R.id.tvPlayerName) as TextView
        tvPlayerCount = findViewById(R.id.tvPlayerCount) as TextView
        tvScores = findViewById(R.id.tvScores) as TextView
        playerHand = findViewById(R.id.playerHand) as PlayerHandView
        btnSort = findViewById(R.id.btnSort) as Button
        btnPass = findViewById(R.id.btnPass) as Button
        btnPlay = findViewById(R.id.btnPlay) as Button
    }

    private fun setupButtons() {
        btnPlay.setOnClickListener {
            val selected = playerHand.getSelected()
            if (selected.isEmpty()) {
                Toast.makeText(this, "请选择要出的牌", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ok = engine?.onPlayerPlay(selected) ?: false
            if (ok) {
                playerHand.clearSelection()
                lastPlayerHandIds = emptyList()
            } else {
                AnimationHelper.animateShake(btnPlay)
                Toast.makeText(this, "出牌不合法", Toast.LENGTH_SHORT).show()
            }
        }
        btnPass.setOnClickListener {
            val ok = engine?.onPlayerPass() ?: false
            if (!ok) Toast.makeText(this, "本轮你先出，不能不出", Toast.LENGTH_SHORT).show()
        }
        btnSort.setOnClickListener { playerHand.clearSelection() }
        playerHand.onSelectionChanged = { updatePlayButtonState() }
    }

    private fun startNewGame() {
        engine?.cancelAi()
        lastPlayerHandIds = emptyList()
        engine = GameEngine(
            difficulty = difficulty,
            scope = scope,
            onStateChanged = { state ->
                Handler(Looper.getMainLooper()).post { renderState(state) }
            },
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

    private fun renderState(state: GameState) {
        when (state.phase) {
            GamePhase.BIDDING   -> renderBidding(state)
            GamePhase.PLAYING   -> renderPlaying(state)
            GamePhase.ROUND_END -> showRoundEnd(state)
            else -> {}
        }
    }

    private fun renderBidding(state: GameState) {
        lastPlayerHandIds = emptyList()
        bidPanel.visibility = View.VISIBLE
        gamePanel.visibility = View.GONE

        playerHandBid.bindCards(HandEvaluator.sortHand(state.players[0].hand))
        bottomCardsAreaBid.showCards(state.bottomCards, faceUp = false)

        val isMyTurn = state.currentBidderIndex == 0
        btnBid0.isEnabled = isMyTurn
        btnBid1.isEnabled = isMyTurn
        btnBid2.isEnabled = isMyTurn
        btnBid3.isEnabled = isMyTurn

        val bidStatus = state.players.joinToString("  ") { p ->
            val name = if (p.index == 0) "你" else p.name
            if (p.bidScore > 0 || state.bidsDone > p.index) "$name:${p.bidScore}分" else "$name:?"
        }
        tvBidStatus.text = bidStatus
        tvBidTip.text = if (isMyTurn) "轮到你叫牌"
        else "${state.players[state.currentBidderIndex].name}思考中..."

        btnBid0.setOnClickListener { engine?.onPlayerBid(0) }
        btnBid1.setOnClickListener { engine?.onPlayerBid(1) }
        btnBid2.setOnClickListener { engine?.onPlayerBid(2) }
        btnBid3.setOnClickListener { engine?.onPlayerBid(3) }
    }

    private fun renderPlaying(state: GameState) {
        bidPanel.visibility = View.GONE
        gamePanel.visibility = View.VISIBLE

        val myPlayer = state.players[0]
        val leftAi  = state.players[1]
        val rightAi = state.players[2]

        val newHandIds = myPlayer.hand.map { it.id }
        if (newHandIds != lastPlayerHandIds) {
            lastPlayerHandIds = newHandIds
            playerHand.bindCards(HandEvaluator.sortHand(myPlayer.hand))
        }

        tvLeftAiName.text  = buildLabel(leftAi, state.landlordIndex)
        tvLeftAiCount.text = "${leftAi.cardCount}张"
        leftAiCards.showBackCards(leftAi.cardCount)

        tvRightAiName.text  = buildLabel(rightAi, state.landlordIndex)
        tvRightAiCount.text = "${rightAi.cardCount}张"
        rightAiCards.showBackCards(rightAi.cardCount)

        bottomCardsArea.showCards(state.bottomCards, faceUp = true)
        tvLandlordBadge.text = "地主:${state.players[state.landlordIndex].name}"
        tvMultiplier.text = "x${state.roundMultiplier}"

        updateTableCards(state)

        tvPlayerName.text  = buildLabel(myPlayer, state.landlordIndex)
        tvPlayerCount.text = "${myPlayer.cardCount}张"
        tvScores.text = "你:${state.totalScores[0]}  左:${state.totalScores[1]}  右:${state.totalScores[2]}"

        val isMyTurn = state.currentPlayerIndex == 0
        val isLeading = state.lastPlayedCards == null || state.lastPlayedByIndex == 0
        btnPass.isEnabled = isMyTurn && !isLeading
        btnPlay.isEnabled = false
        tvTurnHint.text = when (state.currentPlayerIndex) {
            0    -> if (isLeading) "轮到你出牌" else "轮到你跟牌"
            1    -> "${leftAi.name}思考中..."
            else -> "${rightAi.name}思考中..."
        }
        updatePlayButtonState()
    }

    private fun updatePlayButtonState() {
        val state = engine?.getState() ?: return
        val isMyTurn = state.currentPlayerIndex == 0 && state.phase == GamePhase.PLAYING
        if (!isMyTurn) { btnPlay.isEnabled = false; return }

        val selected = playerHand.getSelected()
        if (selected.isEmpty()) { btnPlay.isEnabled = false; return }

        val result = HandEvaluator.evaluate(selected)
        if (result.type == HandType.INVALID) { btnPlay.isEnabled = false; return }

        val table = state.lastPlayedCards
        btnPlay.isEnabled = table == null || state.lastPlayedByIndex == 0 || result.canBeat(table)
    }

    private fun updateTableCards(state: GameState) {
        if (state.lastPlayedCards == null) {
            tableCardsLeft.clear(); tableCardsCenter.clear(); tableCardsRight.clear()
            return
        }
        val cards = state.lastPlayedCards.cards
        when (state.lastPlayedByIndex) {
            0 -> { tableCardsLeft.clear(); tableCardsRight.clear(); tableCardsCenter.showCards(cards) }
            1 -> { tableCardsCenter.clear(); tableCardsRight.clear(); tableCardsLeft.showCards(cards) }
            2 -> { tableCardsCenter.clear(); tableCardsLeft.clear(); tableCardsRight.showCards(cards) }
        }
    }

    private fun buildLabel(player: Player, landlordIndex: Int): String {
        val role = if (player.index == landlordIndex) "[地]" else "[农]"
        return "$role ${player.name}"
    }

    private fun showRoundEnd(state: GameState) {
        val winnerIndex = state.winnerIndex
        val isLandlordWin = state.players[winnerIndex].role == PlayerRole.LANDLORD
        val isPlayerWin = winnerIndex == 0 || (state.players[0].role == PlayerRole.FARMER && !isLandlordWin)

        val base = state.roundMultiplier
        val li = state.landlordIndex
        val myDelta = when {
            isLandlordWin && li == 0  ->  base * 2
            isLandlordWin && li != 0  -> -base
            !isLandlordWin && li != 0 ->  base
            else                      -> -base * 2
        }
        val springText = if (state.isSpring) "春天! x2\n" else ""
        val roleText = if (state.players[0].role == PlayerRole.LANDLORD) "(地主)" else "(农民)"
        val msg = buildString {
            appendLine(if (isPlayerWin) "你赢了!" else "你输了...")
            append(springText)
            appendLine("胜者: ${if (winnerIndex == 0) "你" else state.players[winnerIndex].name}")
            appendLine("倍率: x$base")
            appendLine()
            appendLine("你$roleText  ${if (myDelta >= 0) "+$myDelta" else "$myDelta"} 分")
            append("累计: 你${state.totalScores[0]}  ${state.players[1].name}${state.totalScores[1]}  ${state.players[2].name}${state.totalScores[2]}")
        }

        AlertDialog.Builder(this)
            .setTitle("本局结束")
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton("再来一局") { _, _ -> startNewGame() }
            .setNegativeButton("返回") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.cancelAi()
        scope.cancel()
        SoundManager.release()
    }
}
