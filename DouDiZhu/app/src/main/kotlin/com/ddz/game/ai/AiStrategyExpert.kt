package com.ddz.game.ai

import com.ddz.game.engine.HandEvaluator
import com.ddz.game.model.*

class AiStrategyExpert : AiStrategy {

    override fun decidePlay(hand: List<Card>, table: HandResult?, gameState: GameState, selfIndex: Int): List<Card>? {
        val isLeading = table == null || gameState.lastPlayedByIndex == selfIndex
        val isLandlord = gameState.players[selfIndex].role == PlayerRole.LANDLORD

        return if (isLeading) expertLead(hand, gameState, selfIndex, isLandlord)
        else expertFollow(hand, table!!, gameState, selfIndex, isLandlord)
    }

    private fun expertLead(hand: List<Card>, gameState: GameState, selfIndex: Int, isLandlord: Boolean): List<Card> {
        // 找最优的出牌方案：最大化出完手牌的效率
        val candidates = generateAllLeadMoves(hand)
        if (candidates.isEmpty()) return listOf(hand.minByOrNull { it.rank.weight }!!)

        return candidates.maxByOrNull { move ->
            scoreMove(move, hand, gameState, selfIndex, isLandlord)
        } ?: candidates.first()
    }

    private fun expertFollow(hand: List<Card>, table: HandResult, gameState: GameState, selfIndex: Int, isLandlord: Boolean): List<Card>? {
        val opponents = gameState.players.filter { it.index != selfIndex }

        // 如果是农民，评估是否需要压制
        if (!isLandlord) {
            val landlord = gameState.players[gameState.landlordIndex]
            val teammate = gameState.players.firstOrNull { it.role == PlayerRole.FARMER && it.index != selfIndex }

            // 地主快出完了：必须压制
            if (landlord.cardCount <= 2 && table.type != HandType.BOMB) {
                val beats = HandEvaluator.findAllBeatingHands(hand, table)
                    .filter { HandEvaluator.evaluate(it).type != HandType.BOMB }
                if (beats.isNotEmpty()) return beats.minByOrNull { HandEvaluator.evaluate(it).primaryWeight }
            }

            // 队友控场：优先pass（保留大牌）
            if (teammate != null && gameState.lastPlayedByIndex == teammate.index) {
                if (landlord.cardCount > 3) return null
            }
        }

        val candidates = HandEvaluator.findAllBeatingHands(hand, table)
        if (candidates.isEmpty()) return null

        // 选择最优压制：用最小代价压过去
        return candidates.maxByOrNull { move ->
            scoreFollowMove(move, hand, gameState, selfIndex, isLandlord)
        }
    }

    private fun generateAllLeadMoves(hand: List<Card>): List<List<Card>> {
        val moves = mutableListOf<List<Card>>()
        val groups = hand.groupBy { it.rank }

        // 单张
        hand.forEach { moves.add(listOf(it)) }

        // 对子
        groups.filter { it.value.size >= 2 }.forEach { moves.add(it.value.take(2)) }

        // 三张及三带一/三带二
        groups.filter { it.value.size >= 3 }.forEach { (rank, tripleCards) ->
            val triple = tripleCards.take(3)
            moves.add(triple)
            val rest = hand.filter { it.rank != rank }
            rest.take(1).let { if (it.isNotEmpty()) moves.add(triple + it) }
            rest.groupBy { it.rank }.filter { it.value.size >= 2 }
                .forEach { (_, p) -> moves.add(triple + p.take(2)) }
        }

        // 炸弹
        groups.filter { it.value.size == 4 }.forEach { moves.add(it.value) }

        // 火箭
        val sj = hand.firstOrNull { it.rank == Rank.SMALL_JOKER }
        val bj = hand.firstOrNull { it.rank == Rank.BIG_JOKER }
        if (sj != null && bj != null) moves.add(listOf(sj, bj))

        // 顺子（长度5-8，取最长）
        addStraights(hand, moves)
        addConsecPairs(hand, moves)
        addAirplanes(hand, moves)

        return moves.filter { HandEvaluator.evaluate(it).type != HandType.INVALID }
    }

    private fun addStraights(hand: List<Card>, moves: MutableList<List<Card>>) {
        val valid = hand.filter { it.rank.weight < Rank.TWO.weight }
        val byWeight = valid.groupBy { it.rank.weight }
        val weights = byWeight.keys.sorted()
        for (start in weights) {
            for (len in 5..minOf(12, weights.last() - start + 1)) {
                val seq = (start until start + len).map { byWeight[it]?.first() }
                if (seq.all { it != null }) moves.add(seq.filterNotNull())
            }
        }
    }

    private fun addConsecPairs(hand: List<Card>, moves: MutableList<List<Card>>) {
        val pairs = hand.groupBy { it.rank }.filter { it.value.size >= 2 && it.key.weight < Rank.TWO.weight }
        val weights = pairs.keys.map { it.weight }.sorted()
        for (start in weights) {
            for (len in 3..minOf(10, weights.last() - start + 1)) {
                val seq = (start until start + len).map { w ->
                    val r = Rank.values().firstOrNull { it.weight == w } ?: return@map null
                    pairs[r]?.take(2)
                }
                if (seq.all { it != null }) moves.add(seq.flatten().filterNotNull())
            }
        }
    }

    private fun addAirplanes(hand: List<Card>, moves: MutableList<List<Card>>) {
        val triples = hand.groupBy { it.rank }.filter { it.value.size >= 3 && it.key.weight < Rank.TWO.weight }
        val weights = triples.keys.map { it.weight }.sorted()
        for (start in weights) {
            for (len in 2..minOf(6, weights.last() - start + 1)) {
                val seq = (start until start + len).map { w ->
                    val r = Rank.values().firstOrNull { it.weight == w } ?: return@map null
                    triples[r]?.take(3)
                }
                if (seq.all { it != null }) moves.add(seq.flatten().filterNotNull())
            }
        }
    }

    private fun scoreMove(move: List<Card>, hand: List<Card>, gameState: GameState, selfIndex: Int, isLandlord: Boolean): Int {
        val remaining = hand.filter { it !in move }
        var score = 0

        val result = HandEvaluator.evaluate(move)
        // 出多张牌：减少手牌数量更快
        score += move.size * 3

        // 保留炸弹（价值很高）
        val bombsLeft = remaining.groupBy { it.rank }.count { it.value.size == 4 }
        score += bombsLeft * 15

        // 剩余手牌越少越好
        score += (20 - remaining.size) * 2

        // 不轻易出2和王（留着压制用）
        val highCardsUsed = move.count { it.rank.weight >= Rank.TWO.weight }
        score -= highCardsUsed * 8

        // 农民：不要轻易切断配合
        if (!isLandlord) {
            val teammate = gameState.players.firstOrNull { it.role == PlayerRole.FARMER && it.index != selfIndex }
            if (teammate != null && teammate.cardCount <= 3) score += 10
        }

        return score
    }

    private fun scoreFollowMove(move: List<Card>, hand: List<Card>, gameState: GameState, selfIndex: Int, isLandlord: Boolean): Int {
        val result = HandEvaluator.evaluate(move)
        var score = 0

        // 尽量用小牌压（节约大牌）
        score -= result.primaryWeight * 2

        // 避免浪费炸弹（炸非炸弹要扣分）
        val isBomb = result.type == HandType.BOMB || result.type == HandType.ROCKET
        val tableIsBomb = false // 已在 canBeat 筛选过
        if (isBomb && !tableIsBomb) score -= 30

        // 剩余牌少时更积极出
        val remaining = hand.filter { it !in move }
        if (remaining.size <= 2) score += 20

        return score
    }
}
