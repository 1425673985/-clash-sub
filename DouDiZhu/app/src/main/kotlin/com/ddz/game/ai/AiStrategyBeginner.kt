package com.ddz.game.ai

import com.ddz.game.engine.HandEvaluator
import com.ddz.game.model.*

class AiStrategyBeginner : AiStrategy {

    override fun decidePlay(hand: List<Card>, table: HandResult?, gameState: GameState, selfIndex: Int): List<Card>? {
        if (table == null || gameState.lastPlayedByIndex == selfIndex) {
            // 自由出牌：出最小单张
            val min = hand.minByOrNull { it.rank.weight } ?: return null
            return listOf(min)
        }

        // 跟牌：找所有能压过的，只用同类型（不主动出炸弹，除非桌面是炸弹）
        val candidates = HandEvaluator.findAllBeatingHands(hand, table)
            .filter { cards ->
                val r = HandEvaluator.evaluate(cards)
                // 新手不主动使用炸弹/火箭（除非对方出了炸弹）
                if (table.type != HandType.BOMB && table.type != HandType.ROCKET) {
                    r.type != HandType.BOMB && r.type != HandType.ROCKET
                } else true
            }

        if (candidates.isEmpty()) return null
        // 随机选一手（新手特征：不总是选最优解）
        return candidates.random()
    }
}
