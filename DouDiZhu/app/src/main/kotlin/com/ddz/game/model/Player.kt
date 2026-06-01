package com.ddz.game.model

enum class PlayerRole { LANDLORD, FARMER }
enum class PlayerType { HUMAN, AI }
enum class Difficulty { BEGINNER, NORMAL, EXPERT }

data class Player(
    val index: Int,
    val name: String,
    val type: PlayerType,
    val role: PlayerRole? = null,
    val hand: List<Card> = emptyList(),
    val bidScore: Int = 0
) {
    val cardCount: Int get() = hand.size
    val hasCards: Boolean get() = hand.isNotEmpty()
}
