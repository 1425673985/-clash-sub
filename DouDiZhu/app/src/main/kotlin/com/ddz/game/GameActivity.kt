package com.ddz.game

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
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

        private val HAND_TYPE_NAMES = mapOf(
            HandType.SINGLE             to "单张",
            HandType.PAIR               to "对子",
            HandType.TRIPLE             to "三条",
            HandType.TRIPLE_WITH_ONE    to "三带一",
            HandType.TRIPLE_WITH_PAIR   to "三带对",
            HandType.STRAIGHT           to "顺子",
            HandType.CONSECUTIVE_PAIRS  to "连对",
            HandType.AIRPLANE           to "飞机",
            HandType.AIRPLANE_WITH_SINGLES to "飞机带翅",
            HandType.AIRPLANE_WITH_PAIRS   to "飞机带对",
            HandType.FOUR_WITH_TWO      to "四带两单",
            HandType.FOUR_WITH_TWO_PAIRS to "四带两对",
            HandType.BOMB               to "炸弹 !!!",
            HandType.ROCKET             to "火箭 !!!"
        )
    }

    private lateinit var difficulty: Difficulty
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var engine: GameEngine? = null

    // ── 叫牌面板 ──
    private lateinit var bidPanel: LinearLayout
    private lateinit var tvBidTip: TextView
    private lateinit var tvBidStatus: TextView
    private lateinit var playerHandBid: PlayerHandView
    private lateinit var bottomCardsAreaBid: CardGroupView
    private lateinit var btnBid0: Button
    private lateinit var btnBid1: Button
    private lateinit var btnBid2: Button
    private lateinit var btnBid3: Button

    // ── 游戏面板 ──
    private lateinit var gamePanel: LinearLayout
    private lateinit var tvLeftAiName: TextView
    private lateinit var tvLeftAiCount: TextView
    private lateinit var leftAiCards: CardGroupView
    private lateinit var tvLeftAiThinking: TextView
    private lateinit var tvLeftPass: TextView
    private lateinit var tvRightAiName: TextView
    private lateinit var tvRightAiCount: TextView
    private lateinit var rightAiCards: CardGroupView
    private lateinit var tvRightAiThinking: TextView
    private lateinit var tvRightPass: TextView
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
    private lateinit var tvComboHint: TextView
    private lateinit var playerHand: PlayerHandView
    private lateinit var btnHint: Button
    private lateinit var btnSort: Button
    private lateinit var btnPass: Button
    private lateinit var btnPlay: Button

    // ── 结算浮层 ──
    private lateinit var roundEndOverlay: FrameLayout
    private lateinit var tvRoundEndTitle: TextView
    private lateinit var tvRoundEndSpring: TextView
    private lateinit var tvRoundEndDetail: TextView
    private lateinit var btnRoundEndRestart: Button
    private lateinit var btnRoundEndBack: Button

    // pass / thinking tracking
    private var prevPassCount = -1
    private var prevCurrentPlayerIndex = -1
    private var lastPlayerHandIds = emptyList<Int>()

    private val mainHandler = Handler(Looper.getMainLooper())

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
        bidPanel           = findViewById(R.id.bidPanel) as LinearLayout
        tvBidTip           = findViewById(R.id.tvBidTip) as TextView
        tvBidStatus        = findViewById(R.id.tvBidStatus) as TextView
        playerHandBid      = findViewById(R.id.playerHandBid) as PlayerHandView
        bottomCardsAreaBid = findViewById(R.id.bottomCardsAreaBid) as CardGroupView
        btnBid0            = findViewById(R.id.btnBid0) as Button
        btnBid1            = findViewById(R.id.btnBid1) as Button
        btnBid2            = findViewById(R.id.btnBid2) as Button
        btnBid3            = findViewById(R.id.btnBid3) as Button

        gamePanel          = findViewById(R.id.gamePanel) as LinearLayout
        tvLeftAiName       = findViewById(R.id.tvLeftAiName) as TextView
        tvLeftAiCount      = findViewById(R.id.tvLeftAiCount) as TextView
        leftAiCards        = findViewById(R.id.leftAiCards) as CardGroupView
        tvLeftAiThinking   = findViewById(R.id.tvLeftAiThinking) as TextView
        tvLeftPass         = findViewById(R.id.tvLeftPass) as TextView
        tvRightAiName      = findViewById(R.id.tvRightAiName) as TextView
        tvRightAiCount     = findViewById(R.id.tvRightAiCount) as TextView
        rightAiCards       = findViewById(R.id.rightAiCards) as CardGroupView
        tvRightAiThinking  = findViewById(R.id.tvRightAiThinking) as TextView
        tvRightPass        = findViewById(R.id.tvRightPass) as TextView
        bottomCardsArea    = findViewById(R.id.bottomCardsArea) as CardGroupView
        tvLandlordBadge    = findViewById(R.id.tvLandlordBadge) as TextView
        tvMultiplier       = findViewById(R.id.tvMultiplier) as TextView
        tableCardsLeft     = findViewById(R.id.tableCardsLeft) as CardGroupView
        tableCardsCenter   = findViewById(R.id.tableCardsCenter) as CardGroupView
        tableCardsRight    = findViewById(R.id.tableCardsRight) as CardGroupView
        tvTurnHint         = findViewById(R.id.tvTurnHint) as TextView
        tvPlayerName       = findViewById(R.id.tvPlayerName) as TextView
        tvPlayerCount      = findViewById(R.id.tvPlayerCount) as TextView
        tvScores           = findViewById(R.id.tvScores) as TextView
        tvComboHint        = findViewById(R.id.tvComboHint) as TextView
        playerHand         = findViewById(R.id.playerHand) as PlayerHandView
        btnHint            = findViewById(R.id.btnHint) as Button
        btnSort            = findViewById(R.id.btnSort) as Button
        btnPass            = findViewById(R.id.btnPass) as Button
        btnPlay            = findViewById(R.id.btnPlay) as Button

        roundEndOverlay    = findViewById(R.id.roundEndOverlay) as FrameLayout
        tvRoundEndTitle    = findViewById(R.id.tvRoundEndTitle) as TextView
        tvRoundEndSpring   = findViewById(R.id.tvRoundEndSpring) as TextView
        tvRoundEndDetail   = findViewById(R.id.tvRoundEndDetail) as TextView
        btnRoundEndRestart = findViewById(R.id.btnRoundEndRestart) as Button
        btnRoundEndBack    = findViewById(R.id.btnRoundEndBack) as Button
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
                tvComboHint.visibility = View.GONE
            } else {
                AnimationHelper.animateShake(btnPlay)
                Toast.makeText(this, "出牌不合法", Toast.LENGTH_SHORT).show()
            }
        }
        btnPass.setOnClickListener {
            val ok = engine?.onPlayerPass() ?: false
            if (!ok) Toast.makeText(this, "本轮你先出，不能不出", Toast.LENGTH_SHORT).show()
        }
        btnSort.setOnClickListener {
            playerHand.clearSelection()
            tvComboHint.visibility = View.GONE
        }
        btnHint.setOnClickListener {
            val state = engine?.getState() ?: return@setOnClickListener
            if (state.currentPlayerIndex != 0 || state.phase != GamePhase.PLAYING) return@setOnClickListener
            val hand = state.players[0].hand
            val table = state.lastPlayedCards

            val hintCards: List<Card>? = if (table == null || state.lastPlayedByIndex == 0) {
                // Free lead — suggest the smallest single card
                hand.minByOrNull { it.rank.weight }?.let { listOf(it) }
            } else {
                // Must beat the table — find minimum valid beat
                HandEvaluator.findAllBeatingHands(hand, table)
                    .minByOrNull { cards ->
                        val r = HandEvaluator.evaluate(cards)
                        r.primaryWeight * 100 + cards.size
                    }
            }

            if (hintCards.isNullOrEmpty()) {
                Toast.makeText(this, "没有可以出的牌", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            playerHand.selectCards(hintCards)
            updatePlayButtonState()
        }
        playerHand.onSelectionChanged = {
            updatePlayButtonState()
            updateComboHint()
        }
        btnRoundEndRestart.setOnClickListener { startNewGame() }
        btnRoundEndBack.setOnClickListener { finish() }
    }

    private fun startNewGame() {
        engine?.cancelAi()
        lastPlayerHandIds = emptyList()
        prevPassCount = -1
        prevCurrentPlayerIndex = -1
        roundEndOverlay.visibility = View.GONE
        engine = GameEngine(
            difficulty = difficulty,
            scope = scope,
            onStateChanged = { state ->
                mainHandler.post { renderState(state) }
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
        prevPassCount = -1
        prevCurrentPlayerIndex = -1
        bidPanel.visibility = View.VISIBLE
        gamePanel.visibility = View.GONE
        roundEndOverlay.visibility = View.GONE

        playerHandBid.bindCards(HandEvaluator.sortHand(state.players[0].hand))
        bottomCardsAreaBid.showCards(state.bottomCards, faceUp = false)

        val isMyTurn = state.currentBidderIndex == 0
        btnBid0.isEnabled = isMyTurn
        btnBid1.isEnabled = isMyTurn
        btnBid2.isEnabled = isMyTurn
        btnBid3.isEnabled = isMyTurn

        // Show each player's bid status
        val parts = state.players.map { p ->
            val name = if (p.index == 0) "你" else p.name
            if (state.bidsDone > p.index) {
                if (p.bidScore == 0) "$name:不叫" else "$name:${p.bidScore}分"
            } else "$name:?"
        }
        tvBidStatus.text = parts.joinToString("   ")
        tvBidTip.text = if (isMyTurn) "轮到你叫牌"
                        else "${state.players[state.currentBidderIndex].name} 思考中..."

        btnBid0.setOnClickListener { engine?.onPlayerBid(0) }
        btnBid1.setOnClickListener { engine?.onPlayerBid(1) }
        btnBid2.setOnClickListener { engine?.onPlayerBid(2) }
        btnBid3.setOnClickListener { engine?.onPlayerBid(3) }
    }

    private fun renderPlaying(state: GameState) {
        bidPanel.visibility = View.GONE
        gamePanel.visibility = View.VISIBLE

        val myPlayer = state.players[0]
        val leftAi   = state.players[1]
        val rightAi  = state.players[2]

        // ── Detect pass ──
        if (prevPassCount >= 0 && state.passCount > prevPassCount) {
            when (prevCurrentPlayerIndex) {
                1 -> showPassBriefly(tvLeftPass)
                2 -> showPassBriefly(tvRightPass)
            }
        }
        prevPassCount = state.passCount
        prevCurrentPlayerIndex = state.currentPlayerIndex

        // ── Hand cards (preserve selection) ──
        val newHandIds = myPlayer.hand.map { it.id }
        if (newHandIds != lastPlayerHandIds) {
            lastPlayerHandIds = newHandIds
            playerHand.bindCards(HandEvaluator.sortHand(myPlayer.hand))
            tvComboHint.visibility = View.GONE
        }

        // ── Left AI ──
        tvLeftAiName.text  = buildLabel(leftAi, state.landlordIndex)
        tvLeftAiCount.text = "${leftAi.cardCount}张"
        leftAiCards.showBackCards(leftAi.cardCount)
        tvLeftAiThinking.visibility = if (state.currentPlayerIndex == 1) View.VISIBLE else View.GONE

        // ── Right AI ──
        tvRightAiName.text  = buildLabel(rightAi, state.landlordIndex)
        tvRightAiCount.text = "${rightAi.cardCount}张"
        rightAiCards.showBackCards(rightAi.cardCount)
        tvRightAiThinking.visibility = if (state.currentPlayerIndex == 2) View.VISIBLE else View.GONE

        // ── Bottom cards + landlord info ──
        bottomCardsArea.showCards(state.bottomCards, faceUp = true)
        val landlordName = state.players[state.landlordIndex].let {
            if (it.index == 0) "你" else it.name
        }
        tvLandlordBadge.text = "地主: $landlordName"
        tvMultiplier.text = "×${state.roundMultiplier}"

        // ── Table cards ──
        updateTableCards(state)

        // ── Player info ──
        tvPlayerName.text  = buildLabel(myPlayer, state.landlordIndex)
        tvPlayerCount.text = "${myPlayer.cardCount}张"
        tvScores.text = buildString {
            append("你:${state.totalScores[0]}")
            append("  ${leftAi.name}:${state.totalScores[1]}")
            append("  ${rightAi.name}:${state.totalScores[2]}")
        }

        // ── Turn hint ──
        val isMyTurn = state.currentPlayerIndex == 0
        val isLeading = state.lastPlayedCards == null || state.lastPlayedByIndex == 0
        tvTurnHint.text = when {
            state.currentPlayerIndex == 0 && isLeading  -> "出牌"
            state.currentPlayerIndex == 0 && !isLeading -> "跟牌"
            state.currentPlayerIndex == 1               -> "${leftAi.name}出牌中"
            else                                         -> "${rightAi.name}出牌中"
        }

        btnPass.isEnabled = isMyTurn && !isLeading
        btnPlay.isEnabled = false
        btnHint.isEnabled = isMyTurn
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

    private fun updateComboHint() {
        val selected = playerHand.getSelected()
        if (selected.isEmpty()) {
            tvComboHint.visibility = View.GONE
            return
        }
        val result = HandEvaluator.evaluate(selected)
        val name = HAND_TYPE_NAMES[result.type]
        if (name != null && result.type != HandType.INVALID) {
            tvComboHint.text = name
            tvComboHint.visibility = View.VISIBLE
        } else {
            tvComboHint.text = "无效组合"
            tvComboHint.visibility = View.VISIBLE
        }
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

    private fun showPassBriefly(view: View) {
        view.visibility = View.VISIBLE
        mainHandler.postDelayed({ view.visibility = View.GONE }, 1400)
    }

    private fun showRoundEnd(state: GameState) {
        val winnerIdx = state.winnerIndex
        val isLandlordWin = state.players[winnerIdx].role == PlayerRole.LANDLORD
        val isPlayerWin   = winnerIdx == 0 || (state.players[0].role == PlayerRole.FARMER && !isLandlordWin)

        val base = state.roundMultiplier
        val li   = state.landlordIndex
        val myDelta = when {
            isLandlordWin && li == 0  ->  base * 2
            isLandlordWin && li != 0  -> -base
            !isLandlordWin && li != 0 ->  base
            else                      -> -base * 2
        }

        tvRoundEndTitle.text = if (isPlayerWin) "你  赢  了 !" else "你  输  了..."
        tvRoundEndTitle.setTextColor(
            if (isPlayerWin) android.graphics.Color.parseColor("#F9A825")
            else android.graphics.Color.parseColor("#EF5350")
        )

        if (state.isSpring) {
            tvRoundEndSpring.visibility = View.VISIBLE
        } else {
            tvRoundEndSpring.visibility = View.GONE
        }

        val roleText   = if (state.players[0].role == PlayerRole.LANDLORD) "地主" else "农民"
        val deltaText  = if (myDelta >= 0) "+$myDelta" else "$myDelta"
        val winnerName = if (winnerIdx == 0) "你" else state.players[winnerIdx].name

        tvRoundEndDetail.text = buildString {
            appendLine("胜者: $winnerName   倍率: ×$base")
            appendLine("你($roleText)  $deltaText 分")
            appendLine()
            append("总分  你:${state.totalScores[0]}")
            append("  ${state.players[1].name}:${state.totalScores[1]}")
            append("  ${state.players[2].name}:${state.totalScores[2]}")
        }

        roundEndOverlay.visibility = View.VISIBLE
        AnimationHelper.animatePopIn(roundEndOverlay.getChildAt(0))
    }

    override fun onBackPressed() {
        if (roundEndOverlay.visibility == View.VISIBLE) {
            finish()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.cancelAi()
        scope.cancel()
        SoundManager.release()
    }
}
