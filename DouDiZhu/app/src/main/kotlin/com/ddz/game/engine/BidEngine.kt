package com.ddz.game.engine

import com.ddz.game.model.*

object BidEngine {

    fun autoScore(hand: List<Card>, difficulty: Difficulty): Int {
        val score = evaluateHand(hand)
        return when (difficulty) {
            Difficulty.BEGINNER -> if (score >= 80) 1 else 0
            Difficulty.NORMAL   -> normalBid(score)
            Difficulty.EXPERT   -> {
                val hasBomb = hand.groupBy { it.rank }.any { it.value.size == 4 }
                val hasRocket = hand.any { it.rank == Rank.SMALL_JOKER } &&
                        hand.any { it.rank == Rank.BIG_JOKER }
                val boost = (if (hasBomb) 10 else 0) + (if (hasRocket) 15 else 0)
                normalBid((score + boost).coerceAtMost(100))
            }
        }
    }

    fun evaluateHand(hand: List<Card>): Int {
        var score = 0
        val groups = hand.groupBy { it.rank }
        score += groups.values.count { it.size == 4 } * 25
        score += hand.count { it.rank == Rank.BIG_JOKER } * 20
        score += hand.count { it.rank == Rank.SMALL_JOKER } * 15
        score += groups.values.count { it.size == 3 } * 8
        score += groups.values.count { it.size == 2 } * 3
        score += hand.count { it.rank == Rank.ACE || it.rank == Rank.TWO } * 5
        score += straightBonus(hand)
        return score.coerceIn(0, 100)
    }

    private fun straightBonus(hand: List<Card>): Int {
        val weights = hand.filter { it.rank.weight < Rank.TWO.weight }
            .map { it.rank.weight }.distinct().sorted()
        var maxRun = 0; var cur = 1
        for (i in 1 until weights.size) {
            cur = if (weights[i] - weights[i - 1] == 1) cur + 1 else 1
            if (cur > maxRun) maxRun = cur
        }
        return if (maxRun >= 5) maxRun * 2 else 0
    }

    private fun normalBid(score: Int) = when {
        score >= 85 -> 3
        score >= 65 -> 2
        score >= 45 -> 1
        else        -> 0
    }
}
