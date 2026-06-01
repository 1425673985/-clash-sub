package com.ddz.game.engine

import com.ddz.game.model.*

object HandEvaluator {

    fun evaluate(cards: List<Card>): HandResult {
        if (cards.isEmpty()) return HandResult.INVALID
        val sorted = cards.sortedByDescending { it.rank.weight }
        return when (cards.size) {
            1    -> evalSingle(sorted)
            2    -> evalTwo(sorted)
            3    -> evalThree(sorted)
            4    -> evalFour(sorted)
            5    -> evalFive(sorted)
            6    -> evalSix(sorted)
            else -> evalComplex(sorted)
        }
    }

    private fun evalSingle(cards: List<Card>) =
        HandResult(HandType.SINGLE, cards[0].rank.weight, 1, cards)

    private fun evalTwo(cards: List<Card>): HandResult {
        val (a, b) = cards
        if (a.rank == Rank.BIG_JOKER && b.rank == Rank.SMALL_JOKER)
            return HandResult(HandType.ROCKET, 100, 2, cards)
        if (a.rank == b.rank)
            return HandResult(HandType.PAIR, a.rank.weight, 2, cards)
        return HandResult.INVALID
    }

    private fun evalThree(cards: List<Card>): HandResult {
        if (cards.map { it.rank }.toSet().size == 1)
            return HandResult(HandType.TRIPLE, cards[0].rank.weight, 3, cards)
        return HandResult.INVALID
    }

    private fun evalFour(cards: List<Card>): HandResult {
        val groups = cards.groupBy { it.rank }
        if (groups.size == 1)
            return HandResult(HandType.BOMB, cards[0].rank.weight, 4, cards)
        val triple = groups.entries.firstOrNull { it.value.size == 3 }
            ?: return HandResult.INVALID
        return HandResult(HandType.TRIPLE_WITH_ONE, triple.key.weight, 4, cards)
    }

    private fun evalFive(cards: List<Card>): HandResult {
        val groups = cards.groupBy { it.rank }
        if (groups.size == 2) {
            val triple = groups.entries.firstOrNull { it.value.size == 3 }
            if (triple != null)
                return HandResult(HandType.TRIPLE_WITH_PAIR, triple.key.weight, 5, cards)
        }
        return evalStraight(cards)
    }

    private fun evalSix(cards: List<Card>): HandResult {
        val groups = cards.groupBy { it.rank }
        // 连对：3对连续
        if (groups.size == 3 && groups.values.all { it.size == 2 }) {
            val r = evalConsecutivePairs(cards)
            if (r.type != HandType.INVALID) return r
        }
        // 飞机不带：2组连续三张
        if (groups.size == 2 && groups.values.all { it.size == 3 }) {
            val r = evalAirplanePure(cards)
            if (r.type != HandType.INVALID) return r
        }
        // 四带两单
        val quad = groups.entries.firstOrNull { it.value.size == 4 }
        if (quad != null) {
            val rest = cards.filter { it.rank != quad.key }
            if (rest.size == 2)
                return HandResult(HandType.FOUR_WITH_TWO, quad.key.weight, 6, cards)
        }
        return HandResult.INVALID
    }

    private fun evalComplex(cards: List<Card>): HandResult {
        val groups = cards.groupBy { it.rank }

        // 顺子（5-12张，全不同）
        if (cards.size in 5..12 && groups.values.all { it.size == 1 }) {
            val r = evalStraight(cards)
            if (r.type != HandType.INVALID) return r
        }

        // 连对（6,8,10...20张）
        if (cards.size % 2 == 0 && cards.size >= 6 && groups.values.all { it.size == 2 }) {
            val r = evalConsecutivePairs(cards)
            if (r.type != HandType.INVALID) return r
        }

        // 飞机系列：提取三张组
        val tripleEntries = groups.entries.filter { it.value.size == 3 }
        val tripleRanks = tripleEntries.map { it.key.weight }.sorted()

        if (tripleRanks.size >= 2 && isConsecutive(tripleRanks) &&
            tripleRanks.all { it < Rank.TWO.weight }) {
            val tripleCards = tripleEntries.flatMap { it.value }
            val wings = cards.filter { it !in tripleCards }
            val wingGroups = wings.groupBy { it.rank }
            return when {
                wings.isEmpty() ->
                    HandResult(HandType.AIRPLANE, tripleRanks[0], cards.size, cards)
                wings.size == tripleRanks.size ->
                    HandResult(HandType.AIRPLANE_WITH_SINGLES, tripleRanks[0], cards.size, cards)
                wings.size == tripleRanks.size * 2 && wingGroups.values.all { it.size == 2 } ->
                    HandResult(HandType.AIRPLANE_WITH_PAIRS, tripleRanks[0], cards.size, cards)
                else -> HandResult.INVALID
            }
        }

        // 四带两单/两对
        val quad = groups.entries.firstOrNull { it.value.size == 4 }
        if (quad != null) {
            val rest = cards.filter { it.rank != quad.key }
            val restGroups = rest.groupBy { it.rank }
            if (rest.size == 2 && restGroups.values.all { it.size == 1 })
                return HandResult(HandType.FOUR_WITH_TWO, quad.key.weight, cards.size, cards)
            if (rest.size == 4 && restGroups.values.all { it.size == 2 })
                return HandResult(HandType.FOUR_WITH_TWO_PAIRS, quad.key.weight, cards.size, cards)
        }

        return HandResult.INVALID
    }

    private fun evalStraight(cards: List<Card>): HandResult {
        if (cards.size < 5) return HandResult.INVALID
        val sorted = cards.sortedBy { it.rank.weight }
        if (sorted.any { it.rank.weight >= Rank.TWO.weight }) return HandResult.INVALID
        if (sorted.map { it.rank }.toSet().size != sorted.size) return HandResult.INVALID
        for (i in 1 until sorted.size) {
            if (sorted[i].rank.weight - sorted[i - 1].rank.weight != 1) return HandResult.INVALID
        }
        return HandResult(HandType.STRAIGHT, sorted[0].rank.weight, cards.size, cards)
    }

    private fun evalConsecutivePairs(cards: List<Card>): HandResult {
        val groups = cards.groupBy { it.rank }
        if (groups.values.any { it.size != 2 }) return HandResult.INVALID
        val ranks = groups.keys.map { it.weight }.sorted()
        if (ranks.size < 3) return HandResult.INVALID
        if (ranks.any { it >= Rank.TWO.weight }) return HandResult.INVALID
        if (!isConsecutive(ranks)) return HandResult.INVALID
        return HandResult(HandType.CONSECUTIVE_PAIRS, ranks[0], cards.size, cards)
    }

    private fun evalAirplanePure(cards: List<Card>): HandResult {
        val groups = cards.groupBy { it.rank }
        if (groups.values.any { it.size != 3 }) return HandResult.INVALID
        val ranks = groups.keys.map { it.weight }.sorted()
        if (ranks.size < 2) return HandResult.INVALID
        if (ranks.any { it >= Rank.TWO.weight }) return HandResult.INVALID
        if (!isConsecutive(ranks)) return HandResult.INVALID
        return HandResult(HandType.AIRPLANE, ranks[0], cards.size, cards)
    }

    private fun isConsecutive(weights: List<Int>): Boolean {
        for (i in 1 until weights.size) {
            if (weights[i] - weights[i - 1] != 1) return false
        }
        return true
    }

    // 生成所有能压过 table 的合法出牌，从 hand 中选
    fun findAllBeatingHands(hand: List<Card>, table: HandResult): List<List<Card>> {
        val results = mutableListOf<List<Card>>()
        val n = hand.size

        // 始终检查炸弹和火箭（除非桌面是火箭）
        if (table.type != HandType.ROCKET) {
            // 找火箭
            val sj = hand.firstOrNull { it.rank == Rank.SMALL_JOKER }
            val bj = hand.firstOrNull { it.rank == Rank.BIG_JOKER }
            if (sj != null && bj != null) results.add(listOf(sj, bj))

            // 找炸弹
            hand.groupBy { it.rank }.filter { it.value.size == 4 }.forEach { (rank, cards) ->
                val bomb = HandResult(HandType.BOMB, rank.weight, 4, cards)
                if (bomb.canBeat(table)) results.add(cards)
            }
        }

        when (table.type) {
            HandType.SINGLE -> {
                hand.filter { it.rank.weight > table.primaryWeight }
                    .forEach { results.add(listOf(it)) }
            }
            HandType.PAIR -> {
                hand.groupBy { it.rank }
                    .filter { it.value.size >= 2 && it.key.weight > table.primaryWeight }
                    .forEach { results.add(it.value.take(2)) }
            }
            HandType.TRIPLE -> {
                hand.groupBy { it.rank }
                    .filter { it.value.size >= 3 && it.key.weight > table.primaryWeight }
                    .forEach { results.add(it.value.take(3)) }
            }
            HandType.TRIPLE_WITH_ONE -> findTripleWithKickers(hand, table, 1, results)
            HandType.TRIPLE_WITH_PAIR -> findTripleWithKickers(hand, table, 2, results)
            HandType.STRAIGHT -> findStraights(hand, table.cardCount, table.primaryWeight, results)
            HandType.CONSECUTIVE_PAIRS -> findConsecPairs(hand, table.cardCount / 2, table.primaryWeight, results)
            HandType.AIRPLANE -> findAirplanes(hand, table.cardCount / 3, table.primaryWeight, results)
            HandType.AIRPLANE_WITH_SINGLES -> findAirplanesWithWings(hand, table.cardCount, false, table.primaryWeight, results)
            HandType.AIRPLANE_WITH_PAIRS -> findAirplanesWithWings(hand, table.cardCount, true, table.primaryWeight, results)
            HandType.FOUR_WITH_TWO -> findFourWithKickers(hand, table, false, results)
            HandType.FOUR_WITH_TWO_PAIRS -> findFourWithKickers(hand, table, true, results)
            HandType.BOMB -> {
                hand.groupBy { it.rank }
                    .filter { it.value.size == 4 && it.key.weight > table.primaryWeight }
                    .forEach { results.add(it.value) }
            }
            else -> {}
        }
        return results.distinctBy { it.map { c -> c.id }.sorted() }
    }

    private fun findTripleWithKickers(hand: List<Card>, table: HandResult, kickerCount: Int, results: MutableList<List<Card>>) {
        val groups = hand.groupBy { it.rank }
        groups.filter { it.value.size >= 3 && it.key.weight > table.primaryWeight }.forEach { (rank, tripleCards) ->
            val triple = tripleCards.take(3)
            val rest = hand.filter { it.rank != rank }
            if (kickerCount == 1) {
                rest.forEach { kicker -> results.add(triple + kicker) }
            } else {
                groups.filter { it.key != rank && it.value.size >= 2 }
                    .forEach { (_, pairCards) -> results.add(triple + pairCards.take(2)) }
            }
        }
    }

    private fun findStraights(hand: List<Card>, length: Int, minStart: Int, results: MutableList<List<Card>>) {
        val validCards = hand.filter { it.rank.weight < Rank.TWO.weight }
        val byRank = validCards.groupBy { it.rank.weight }
        val available = byRank.keys.sorted()
        for (start in available) {
            if (start <= minStart) continue
            val seq = (start until start + length).map { w -> byRank[w]?.first() }
            if (seq.all { it != null }) results.add(seq.filterNotNull())
        }
    }

    private fun findConsecPairs(hand: List<Card>, pairCount: Int, minStart: Int, results: MutableList<List<Card>>) {
        val pairs = hand.groupBy { it.rank }.filter { it.value.size >= 2 && it.key.weight < Rank.TWO.weight }
        val available = pairs.keys.map { it.weight }.sorted()
        for (start in available) {
            if (start <= minStart) continue
            val seq = (start until start + pairCount).map { w ->
                val rank = Rank.values().firstOrNull { it.weight == w } ?: return@map null
                pairs[rank]?.take(2)
            }
            if (seq.all { it != null }) results.add(seq.filterNotNull().flatten())
        }
    }

    private fun findAirplanes(hand: List<Card>, groupCount: Int, minStart: Int, results: MutableList<List<Card>>) {
        val triples = hand.groupBy { it.rank }.filter { it.value.size >= 3 && it.key.weight < Rank.TWO.weight }
        val available = triples.keys.map { it.weight }.sorted()
        for (start in available) {
            if (start <= minStart) continue
            val seq = (start until start + groupCount).map { w ->
                val rank = Rank.values().firstOrNull { it.weight == w } ?: return@map null
                triples[rank]?.take(3)
            }
            if (seq.all { it != null }) results.add(seq.filterNotNull().flatten())
        }
    }

    private fun findAirplanesWithWings(hand: List<Card>, totalCards: Int, pairWings: Boolean, minStart: Int, results: MutableList<List<Card>>) {
        val wingSize = if (pairWings) 2 else 1
        val groupCount = totalCards / (3 + wingSize)
        val triples = hand.groupBy { it.rank }.filter { it.value.size >= 3 && it.key.weight < Rank.TWO.weight }
        val available = triples.keys.map { it.weight }.sorted()
        for (start in available) {
            if (start <= minStart) continue
            val tripleRanks = (start until start + groupCount).map { w ->
                Rank.values().firstOrNull { it.weight == w }
            }
            if (tripleRanks.any { it == null || triples[it] == null }) continue
            val planeCards = tripleRanks.flatMap { triples[it!!]!!.take(3) }
            val planeRankSet = tripleRanks.toSet()
            val rest = hand.filter { it.rank !in planeRankSet }
            if (pairWings) {
                val pairCandidates = rest.groupBy { it.rank }.filter { it.value.size >= 2 }
                if (pairCandidates.size >= groupCount) {
                    val wings = pairCandidates.values.take(groupCount).flatMap { it.take(2) }
                    results.add(planeCards + wings)
                }
            } else {
                if (rest.size >= groupCount) {
                    results.add(planeCards + rest.take(groupCount))
                }
            }
        }
    }

    private fun findFourWithKickers(hand: List<Card>, table: HandResult, pairKickers: Boolean, results: MutableList<List<Card>>) {
        val quads = hand.groupBy { it.rank }.filter { it.value.size == 4 && it.key.weight > table.primaryWeight }
        quads.forEach { (rank, quadCards) ->
            val rest = hand.filter { it.rank != rank }
            if (pairKickers) {
                val pairs = rest.groupBy { it.rank }.filter { it.value.size >= 2 }
                if (pairs.size >= 2) {
                    val wings = pairs.values.take(2).flatMap { it.take(2) }
                    results.add(quadCards + wings)
                }
            } else {
                if (rest.size >= 2) results.add(quadCards + rest.take(2))
            }
        }
    }

    // 整理手牌排序（按权重降序，同权重按花色）
    fun sortHand(hand: List<Card>): List<Card> =
        hand.sortedWith(compareByDescending<Card> { it.rank.weight }.thenBy { it.suit.ordinal })
}
