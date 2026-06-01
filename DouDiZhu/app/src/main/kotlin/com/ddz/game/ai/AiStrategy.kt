package com.ddz.game.ai

import com.ddz.game.model.*

interface AiStrategy {
    // 返回要出的牌列表；null 或空列表表示 pass（仅跟牌时有效）
    fun decidePlay(
        hand: List<Card>,
        table: HandResult?,
        gameState: GameState,
        selfIndex: Int
    ): List<Card>?
}
