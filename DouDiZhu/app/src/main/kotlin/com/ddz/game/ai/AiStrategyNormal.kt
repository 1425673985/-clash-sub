package com.ddz.game.ai

import com.ddz.game.engine.HandEvaluator
import com.ddz.game.model.*

class AiStrategyNormal : AiStrategy {

    override fun decidePlay(hand: List<Card>, table: HandResult?, gameState: GameState, selfIndex: Int): List<Card>? {
        val isLeading = table == null || gameState.lastPlayedByIndex == selfIndex
        val isLandlord = gameState.players[selfIndex].role == PlayerRole.LANDLORD

        return if (isLeading) leadPlay(hand, gameState, selfIndex, isLandlord)
        else followPlay(hand, table!!, gameState, selfIndex, isLandlord)
    }

    private fun leadPlay(hand: List<Card>, gameState: GameState, selfIndex: Int, isLandlord: Boolean): List<Card> {
        val groups = hand.groupBy { it.rank }

        // 优先打出顺子（清手牌）
        val straight = findBestStraight(hand)
        if (straight != null) return straight

        // 优先打连对
        val consecPairs = findConsecPairs(hand)
        if (consecPairs != null) return consecPairs

        // 打飞机（不带）
        val airplane = findAirplane(hand)
        if (airplane != null) return airplane

        // 打三带一
        val triple = groups.entries.firstOrNull { it.value.size >= 3 }
        if (triple != null) {
            val kicker = hand.firstOrNull { it.rank != triple.key && it.rank.weight < Rank.TWO.weight }
            if (kicker != null) return triple.value.take(3) + kicker
            return triple.value.take(3)
        }

        // 打对子
        val pair = groups.entries.firstOrNull { it.value.size >= 2 }
        if (pair != null) return pair.value.take(2)

        // 打最小单张（不轻易出2）
        val single = hand.filter { it.rank.weight < Rank.TWO.weight }.minByOrNull { it.rank.weight }
            ?: hand.minByOrNull { it.rank.weight }!!
        return listOf(single)
    }

    private fun followPlay(hand: List<Card>, table: HandResult, gameState: GameState, selfIndex: Int, isLandlord: Boolean): List<Card>? {
        // 如果是农民，且队友控场，选择pass（除非对方快赢了）
        if (!isLandlord) {
            val teammate = gameState.players.firstOrNull { it.role == PlayerRole.FARMER && it.index != selfIndex }
            if (teammate != null && gameState.lastPlayedByIndex == teammate.index) {
                // 队友控场：pass（除非地主快出完了）
                val landlord = gameState.players[gameState.landlordIndex]
                if (landlord.cardCount > 3) return null
            }
        }

        // 找最小的能压过的牌
        val candidates = HandEvaluator.findAllBeatingHands(hand, table)
        if (candidates.isEmpty()) return null

        // 选最小的（不浪费大牌）
        return candidates.minByOrNull { cards ->
            HandEvaluator.evaluate(cards).primaryWeight
        }
    }

    private fun findBestStraight(hand: List<Card>): List<Card>? {
        val valid = hand.filter { it.rank.weight < Rank.TWO.weight }
        val byWeight = valid.groupBy { it.rank.weight }
        val weights = byWeight.keys.sorted()
        var best: List<Card>? = null
        for (start in weights) {
            val seq = (start until start + 5).map { byWeight[it]?.first() }
            if (seq.all { it != null }) {
                val cards = seq.filterNotNull()
                if (best == null || cards.size > best.size) best = cards
            }
        }
        return best
    }

    private fun findConsecPairs(hand: List<Card>): List<Card>? {
        val pairs = hand.groupBy { it.rank }
            .filter { it.value.size >= 2 && it.key.weight < Rank.TWO.weight }
        val weights = pairs.keys.map { it.weight }.sorted()
        for (start in weights) {
            val seq = (start until start + 3).map { w ->
                val rank = Rank.values().firstOrNull { it.weight == w } ?: return@map null
                pairs[rank]?.take(2)
            }
            if (seq.all { it != null }) return seq.flatten().filterNotNull()
        }
        return null
    }

    private fun findAirplane(hand: List<Card>): List<Card>? {
        val triples = hand.groupBy { it.rank }
            .filter { it.value.size >= 3 && it.key.weight < Rank.TWO.weight }
        val weights = triples.keys.map { it.weight }.sorted()
        for (start in weights) {
            val seq = (start until start + 2).map { w ->
                val rank = Rank.values().firstOrNull { it.weight == w } ?: return@map null
                triples[rank]?.take(3)
            }
            if (seq.all { it != null }) return seq.flatten().filterNotNull()
        }
        return null
    }
}
