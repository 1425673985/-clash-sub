package com.ddz.game.model

data class HandResult(
    val type: HandType,
    val primaryWeight: Int,
    val cardCount: Int,
    val cards: List<Card>
) {
    companion object {
        val INVALID = HandResult(HandType.INVALID, 0, 0, emptyList())
    }

    fun canBeat(other: HandResult): Boolean {
        if (this.type == HandType.ROCKET) return true
        if (other.type == HandType.ROCKET) return false

        if (this.type == HandType.BOMB && other.type != HandType.BOMB) return true
        if (this.type != HandType.BOMB && other.type == HandType.BOMB) return false

        if (this.type == HandType.BOMB && other.type == HandType.BOMB) {
            return this.primaryWeight > other.primaryWeight
        }

        if (this.type != other.type) return false
        if (this.cardCount != other.cardCount) return false

        return this.primaryWeight > other.primaryWeight
    }
}
