package com.ddz.game.model

enum class GamePhase {
    DEALING, BIDDING, PLAYING, ROUND_END
}

data class GameState(
    val players: List<Player>,
    val landlordIndex: Int = -1,
    val currentPlayerIndex: Int = 0,
    val phase: GamePhase = GamePhase.DEALING,
    val bottomCards: List<Card> = emptyList(),
    val lastPlayedCards: HandResult? = null,
    val lastPlayedByIndex: Int = -1,
    val passCount: Int = 0,
    val roundMultiplier: Int = 1,
    val totalScores: List<Int> = listOf(0, 0, 0),
    val winnerIndex: Int = -1,
    val isSpring: Boolean = false,
    val currentBidderIndex: Int = 0,
    val highestBid: Int = 0,
    val highestBidderIndex: Int = -1,
    val bidsDone: Int = 0
)
