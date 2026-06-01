package com.ddz.game.engine

import com.ddz.game.ai.*
import com.ddz.game.model.*
import kotlinx.coroutines.*

class GameEngine(
    private val difficulty: Difficulty,
    private val scope: CoroutineScope,
    private val onStateChanged: (GameState) -> Unit,
    private val onSoundEvent: (SoundEvent) -> Unit = {}
) {
    enum class SoundEvent { DEAL, PLAY, PASS, BID, BOMB, WIN, LOSE }

    private val aiStrategy: AiStrategy = when (difficulty) {
        Difficulty.BEGINNER -> AiStrategyBeginner()
        Difficulty.NORMAL   -> AiStrategyNormal()
        Difficulty.EXPERT   -> AiStrategyExpert()
    }

    private var state: GameState = GameState(players = emptyList())
    private var aiJob: Job? = null

    fun getState(): GameState = state

    fun startGame() {
        aiJob?.cancel()
        dealCards()
    }

    private fun dealCards() {
        val deck = Deck.createShuffled()
        val bottomCards = deck.takeLast(3)
        val hands = deck.dropLast(3).chunked(17)
        val players = listOf(
            Player(0, "你", PlayerType.HUMAN, hand = hands[0]),
            Player(1, "左家", PlayerType.AI, hand = hands[1]),
            Player(2, "右家", PlayerType.AI, hand = hands[2])
        )
        state = GameState(
            players = players,
            bottomCards = bottomCards,
            phase = GamePhase.BIDDING,
            currentPlayerIndex = 0,
            currentBidderIndex = 0
        )
        onStateChanged(state)
        // 玩家先叫，等待UI回调
    }

    fun onPlayerBid(score: Int) {
        if (state.phase != GamePhase.BIDDING) return
        if (state.currentBidderIndex != 0) return
        processBid(0, score)
    }

    private fun processBid(playerIndex: Int, score: Int) {
        onSoundEvent(SoundEvent.BID)
        val newHighest = if (score > state.highestBid) score else state.highestBid
        val newHighestBidder = if (score > state.highestBid) playerIndex else state.highestBidderIndex
        val newBidsDone = state.bidsDone + 1

        state = state.copy(
            players = state.players.map {
                if (it.index == playerIndex) it.copy(bidScore = score) else it
            },
            highestBid = newHighest,
            highestBidderIndex = newHighestBidder,
            bidsDone = newBidsDone
        )

        // 叫3分直接成为地主
        if (score == 3) {
            determineLandlord()
            return
        }

        // 所有人都叫完
        if (newBidsDone >= 3) {
            if (newHighest == 0) {
                // 全叫0，重新发牌
                startGame()
            } else {
                determineLandlord()
            }
            return
        }

        // 下一个叫牌
        val nextBidder = (playerIndex + 1) % 3
        state = state.copy(currentBidderIndex = nextBidder)
        onStateChanged(state)

        if (nextBidder != 0) {
            triggerAiBid(nextBidder)
        }
    }

    private fun triggerAiBid(playerIndex: Int) {
        aiJob = scope.launch {
            delay(600L)
            val hand = state.players[playerIndex].hand
            val score = BidEngine.autoScore(hand, difficulty)
            processBid(playerIndex, score)
        }
    }

    private fun determineLandlord() {
        val landlordIdx = if (state.highestBidderIndex == -1) 0 else state.highestBidderIndex
        val bottomCards = state.bottomCards
        val updatedPlayers = state.players.map { p ->
            val newHand = if (p.index == landlordIdx) p.hand + bottomCards else p.hand
            val role = if (p.index == landlordIdx) PlayerRole.LANDLORD else PlayerRole.FARMER
            p.copy(hand = HandEvaluator.sortHand(newHand), role = role)
        }
        state = state.copy(
            players = updatedPlayers,
            landlordIndex = landlordIdx,
            currentPlayerIndex = landlordIdx,
            phase = GamePhase.PLAYING,
            lastPlayedCards = null,
            lastPlayedByIndex = -1
        )
        onStateChanged(state)
        triggerAiIfNeeded()
    }

    fun onPlayerPlay(cards: List<Card>): Boolean {
        if (state.phase != GamePhase.PLAYING) return false
        if (state.currentPlayerIndex != 0) return false

        val result = HandEvaluator.evaluate(cards)
        if (result.type == HandType.INVALID) return false

        val tableLast = state.lastPlayedCards
        if (tableLast != null && state.lastPlayedByIndex != 0) {
            if (!result.canBeat(tableLast)) return false
        }

        applyPlay(0, cards, result)
        return true
    }

    fun onPlayerPass(): Boolean {
        if (state.phase != GamePhase.PLAYING) return false
        if (state.currentPlayerIndex != 0) return false
        // 不能在自己是本轮首出时pass
        if (state.lastPlayedCards == null || state.lastPlayedByIndex == 0) return false

        applyPass(0)
        return true
    }

    private fun applyPlay(playerIndex: Int, cards: List<Card>, result: HandResult) {
        val isBomb = result.type == HandType.BOMB || result.type == HandType.ROCKET
        val newMultiplier = if (isBomb) state.roundMultiplier * 2 else state.roundMultiplier

        if (isBomb) onSoundEvent(SoundEvent.BOMB) else onSoundEvent(SoundEvent.PLAY)

        val updatedPlayers = state.players.map { p ->
            if (p.index == playerIndex) {
                val newHand = p.hand.filter { it !in cards }
                p.copy(hand = HandEvaluator.sortHand(newHand))
            } else p
        }

        val newState = state.copy(
            players = updatedPlayers,
            lastPlayedCards = result,
            lastPlayedByIndex = playerIndex,
            passCount = 0,
            currentPlayerIndex = (playerIndex + 1) % 3,
            roundMultiplier = newMultiplier
        )
        state = newState

        // 检查胜利
        if (updatedPlayers[playerIndex].hand.isEmpty()) {
            endRound(playerIndex)
            return
        }

        onStateChanged(state)
        triggerAiIfNeeded()
    }

    private fun applyPass(playerIndex: Int) {
        onSoundEvent(SoundEvent.PASS)
        val newPassCount = state.passCount + 1
        if (newPassCount >= 2) {
            // 另外两人都pass，清空桌面
            state = state.copy(
                passCount = 0,
                lastPlayedCards = null,
                currentPlayerIndex = state.lastPlayedByIndex
            )
        } else {
            state = state.copy(
                passCount = newPassCount,
                currentPlayerIndex = (playerIndex + 1) % 3
            )
        }
        onStateChanged(state)
        triggerAiIfNeeded()
    }

    private fun triggerAiIfNeeded() {
        val cur = state.currentPlayerIndex
        if (state.players[cur].type != PlayerType.AI) return
        if (state.phase != GamePhase.PLAYING) return

        aiJob = scope.launch {
            val thinkTime = when (difficulty) {
                Difficulty.BEGINNER -> (400L..900L).random()
                Difficulty.NORMAL   -> (300L..700L).random()
                Difficulty.EXPERT   -> (500L..1200L).random()
            }
            delay(thinkTime)
            if (state.currentPlayerIndex != cur || state.phase != GamePhase.PLAYING) return@launch

            val player = state.players[cur]
            val tableLast = state.lastPlayedCards
            val isLeading = tableLast == null || state.lastPlayedByIndex == cur

            val cards = aiStrategy.decidePlay(player.hand, tableLast, state, cur)

            if (cards == null || (!isLeading && cards.isEmpty())) {
                if (!isLeading) applyPass(cur)
                else {
                    // AI 必须出牌时 fallback 出最小单张
                    val fallback = player.hand.minByOrNull { it.rank.weight }
                    if (fallback != null) {
                        val r = HandEvaluator.evaluate(listOf(fallback))
                        applyPlay(cur, listOf(fallback), r)
                    }
                }
            } else {
                val result = HandEvaluator.evaluate(cards)
                applyPlay(cur, cards, result)
            }
        }
    }

    private fun endRound(winnerIndex: Int) {
        aiJob?.cancel()
        val winnerRole = state.players[winnerIndex].role
        val isLandlordWin = winnerRole == PlayerRole.LANDLORD

        // 检查春天：获胜方对手完全没有出牌
        val spring = checkSpring(winnerIndex, isLandlordWin)
        val finalMultiplier = if (spring) state.roundMultiplier * 2 else state.roundMultiplier

        val deltaScores = IntArray(3)
        val landlordIdx = state.landlordIndex
        val base = finalMultiplier
        if (isLandlordWin) {
            deltaScores[landlordIdx] = base * 2
            for (i in 0..2) if (i != landlordIdx) deltaScores[i] = -base
        } else {
            deltaScores[landlordIdx] = -base * 2
            for (i in 0..2) if (i != landlordIdx) deltaScores[i] = base
        }

        val newTotals = state.totalScores.mapIndexed { i, s -> s + deltaScores[i] }

        val playerWon = winnerIndex == 0 ||
                (state.players[0].role == PlayerRole.FARMER && !isLandlordWin)
        onSoundEvent(if (playerWon) SoundEvent.WIN else SoundEvent.LOSE)

        state = state.copy(
            phase = GamePhase.ROUND_END,
            winnerIndex = winnerIndex,
            isSpring = spring,
            totalScores = newTotals,
            roundMultiplier = finalMultiplier
        )
        onStateChanged(state)
    }

    private fun checkSpring(winnerIndex: Int, isLandlordWin: Boolean): Boolean {
        return if (isLandlordWin) {
            // 地主赢：两个农民都没有出过牌（手牌数=17）
            state.players.filter { it.role == PlayerRole.FARMER }
                .all { it.cardCount == 17 }
        } else {
            // 农民赢：地主没出过牌（手牌=20）
            state.players[state.landlordIndex].cardCount == 20
        }
    }

    fun cancelAi() {
        aiJob?.cancel()
    }
}
