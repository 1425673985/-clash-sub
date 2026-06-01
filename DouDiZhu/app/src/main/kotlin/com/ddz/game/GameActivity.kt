package com.ddz.game

import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ddz.game.audio.SoundManager
import com.ddz.game.databinding.ActivityGameBinding
import com.ddz.game.engine.HandEvaluator
import com.ddz.game.model.*
import com.ddz.game.util.AnimationHelper

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DIFFICULTY = "difficulty"
    }

    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameViewModel by viewModels()
    private lateinit var difficulty: Difficulty
    private var lastPlayerHandIds = emptyList<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SoundManager.init(this)

        difficulty = Difficulty.valueOf(
            intent.getStringExtra(EXTRA_DIFFICULTY) ?: Difficulty.NORMAL.name
        )

        setupButtons()
        observeState()

        if (savedInstanceState == null) {
            viewModel.init(difficulty)
        }
    }

    private fun setupButtons() {
        binding.btnPlay.setOnClickListener {
            val selected = binding.playerHand.getSelected()
            viewModel.onPlay(selected)
            binding.playerHand.clearSelection()
            updatePlayButtonState()
        }

        binding.btnPass.setOnClickListener {
            viewModel.onPass()
        }

        binding.btnSort.setOnClickListener {
            // 重新整理：清除选中
            binding.playerHand.clearSelection()
        }

        binding.playerHand.onSelectionChanged = { updatePlayButtonState() }
    }

    private fun updatePlayButtonState() {
        val state = viewModel.gameState.value ?: return
        val isMyTurn = state.currentPlayerIndex == 0 && state.phase == GamePhase.PLAYING
        val selected = binding.playerHand.getSelected()
        val isLeading = state.lastPlayedCards == null || state.lastPlayedByIndex == 0

        binding.btnPass.isEnabled = isMyTurn && !isLeading
        binding.btnPlay.isEnabled = false

        if (isMyTurn && selected.isNotEmpty()) {
            val result = HandEvaluator.evaluate(selected)
            if (result.type != HandType.INVALID) {
                val tableLast = state.lastPlayedCards
                binding.btnPlay.isEnabled = tableLast == null ||
                        state.lastPlayedByIndex == 0 ||
                        result.canBeat(tableLast)
            }
        }
    }

    private fun observeState() {
        viewModel.gameState.observe(this) { state -> renderState(state) }
        viewModel.toastMsg.observe(this) { msg ->
            if (!msg.isNullOrEmpty()) {
                AnimationHelper.animateShake(binding.btnPlay)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderState(state: GameState) {
        when (state.phase) {
            GamePhase.BIDDING  -> renderBidding(state)
            GamePhase.PLAYING  -> renderPlaying(state)
            GamePhase.ROUND_END -> showRoundEnd(state)
            else -> {}
        }
    }

    private fun renderBidding(state: GameState) {
        lastPlayerHandIds = emptyList()
        binding.bidPanel.visibility = View.VISIBLE
        binding.gamePanel.visibility = View.GONE

        // 显示玩家手牌（让玩家看牌决定叫分）
        val myHand = state.players[0].hand
        binding.playerHandBid.bindCards(HandEvaluator.sortHand(myHand))

        // 显示底牌提示（背面）
        binding.bottomCardsAreaBid.showCards(state.bottomCards, faceUp = false)

        val isMyTurn = state.currentBidderIndex == 0
        binding.btnBid0.isEnabled = isMyTurn
        binding.btnBid1.isEnabled = isMyTurn
        binding.btnBid2.isEnabled = isMyTurn
        binding.btnBid3.isEnabled = isMyTurn

        // 显示叫牌状态
        val bidStatus = state.players.joinToString("  ") { p ->
            val name = if (p.index == 0) "你" else p.name
            if (p.bidScore > 0 || state.bidsDone > p.index) "$name:${p.bidScore}分" else "$name:?"
        }
        binding.tvBidStatus.text = bidStatus
        binding.tvBidTip.text = if (isMyTurn) "轮到你叫牌" else "${state.players[state.currentBidderIndex].name}思考中..."

        binding.btnBid0.setOnClickListener { viewModel.onBid(0) }
        binding.btnBid1.setOnClickListener { viewModel.onBid(1) }
        binding.btnBid2.setOnClickListener { viewModel.onBid(2) }
        binding.btnBid3.setOnClickListener { viewModel.onBid(3) }
    }

    private fun renderPlaying(state: GameState) {
        binding.bidPanel.visibility = View.GONE
        binding.gamePanel.visibility = View.VISIBLE

        val myPlayer = state.players[0]
        val leftAi = state.players[1]
        val rightAi = state.players[2]

        // 玩家手牌：仅在手牌实际变化时重绑，保留选中状态
        val newHandIds = myPlayer.hand.map { it.id }
        if (newHandIds != lastPlayerHandIds) {
            lastPlayerHandIds = newHandIds
            binding.playerHand.bindCards(HandEvaluator.sortHand(myPlayer.hand))
        }

        // 左AI信息
        binding.tvLeftAiName.text = buildPlayerLabel(leftAi, state.landlordIndex)
        binding.tvLeftAiCount.text = "${leftAi.cardCount}张"
        binding.leftAiCards.showCards(List(leftAi.cardCount) {
            com.ddz.game.model.Card(Rank.THREE, Suit.SPADE, -it - 100)
        }, faceUp = false)

        // 右AI信息
        binding.tvRightAiName.text = buildPlayerLabel(rightAi, state.landlordIndex)
        binding.tvRightAiCount.text = "${rightAi.cardCount}张"
        binding.rightAiCards.showCards(List(rightAi.cardCount) {
            com.ddz.game.model.Card(Rank.THREE, Suit.SPADE, -it - 200)
        }, faceUp = false)

        // 底牌
        binding.bottomCardsArea.showCards(state.bottomCards, faceUp = true)
        binding.tvLandlordBadge.text = "地主：${state.players[state.landlordIndex].name}"
        binding.tvMultiplier.text = "x${state.roundMultiplier}"

        // 桌面出牌
        updateTableCards(state)

        // 玩家标签
        binding.tvPlayerName.text = buildPlayerLabel(myPlayer, state.landlordIndex)
        binding.tvPlayerCount.text = "${myPlayer.cardCount}张"

        // 按钮状态
        val isMyTurn = state.currentPlayerIndex == 0
        val isLeading = state.lastPlayedCards == null || state.lastPlayedByIndex == 0
        binding.btnPass.isEnabled = isMyTurn && !isLeading
        binding.btnPlay.isEnabled = false
        binding.btnSort.isEnabled = true

        // 轮到谁的提示
        binding.tvTurnHint.text = when (state.currentPlayerIndex) {
            0 -> if (isLeading) "轮到你出牌" else "轮到你（跟牌或不出）"
            1 -> "${leftAi.name}出牌中..."
            2 -> "${rightAi.name}出牌中..."
            else -> ""
        }

        // 总分显示
        binding.tvScores.text = "你:${state.totalScores[0]}  左:${state.totalScores[1]}  右:${state.totalScores[2]}"

        updatePlayButtonState()
    }

    private fun buildPlayerLabel(player: Player, landlordIndex: Int): String {
        val role = if (player.index == landlordIndex) "👑" else "🌾"
        return "$role ${player.name}"
    }

    private fun updateTableCards(state: GameState) {
        // 清空所有桌面显示
        if (state.lastPlayedCards == null) {
            binding.tableCardsLeft.clear()
            binding.tableCardsCenter.clear()
            binding.tableCardsRight.clear()
            return
        }

        val cards = state.lastPlayedCards.cards
        when (state.lastPlayedByIndex) {
            0 -> {
                binding.tableCardsLeft.clear()
                binding.tableCardsRight.clear()
                binding.tableCardsCenter.showCards(cards)
            }
            1 -> {
                binding.tableCardsCenter.clear()
                binding.tableCardsRight.clear()
                binding.tableCardsLeft.showCards(cards)
            }
            2 -> {
                binding.tableCardsCenter.clear()
                binding.tableCardsLeft.clear()
                binding.tableCardsRight.showCards(cards)
            }
        }
    }

    private fun showRoundEnd(state: GameState) {
        val winnerIndex = state.winnerIndex
        val winnerName = when (winnerIndex) {
            0 -> "你"
            else -> state.players[winnerIndex].name
        }
        val isLandlordWin = state.players[winnerIndex].role == PlayerRole.LANDLORD
        val isPlayerWin = (winnerIndex == 0) ||
                (state.players[0].role == PlayerRole.FARMER && !isLandlordWin)

        val base = state.roundMultiplier
        val landlordIdx = state.landlordIndex
        val myDelta = when {
            isLandlordWin && landlordIdx == 0  ->  base * 2
            isLandlordWin && landlordIdx != 0  -> -base
            !isLandlordWin && landlordIdx != 0 ->  base
            else                               -> -base * 2
        }

        val springText = if (state.isSpring) "✨ 春天！×2\n" else ""
        val roleText = if (state.players[0].role == PlayerRole.LANDLORD) "（地主）" else "（农民）"

        val resultMsg = buildString {
            appendLine(if (isPlayerWin) "🎉 你赢了！" else "😢 你输了")
            append(springText)
            appendLine("胜者：$winnerName")
            appendLine("倍率：×$base")
            appendLine()
            appendLine("你$roleText  ${formatScore(myDelta)} 分")
            appendLine("累计：你 ${state.totalScores[0]}  ${state.players[1].name} ${state.totalScores[1]}  ${state.players[2].name} ${state.totalScores[2]}")
        }

        AlertDialog.Builder(this)
            .setTitle("本局结束")
            .setMessage(resultMsg)
            .setCancelable(false)
            .setPositiveButton("再来一局") { _, _ ->
                viewModel.init(difficulty)
            }
            .setNegativeButton("返回主菜单") { _, _ ->
                finish()
            }
            .show()
    }

    private fun formatScore(score: Int) = if (score >= 0) "+$score" else "$score"

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}
