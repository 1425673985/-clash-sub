package com.ddz.game.model

enum class Suit(val symbol: String) {
    SPADE("♠"), HEART("♥"), DIAMOND("♦"), CLUB("♣"), JOKER("")
}

enum class Rank(val display: String, val weight: Int) {
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 9),
    TEN("10", 10),
    JACK("J", 11),
    QUEEN("Q", 12),
    KING("K", 13),
    ACE("A", 14),
    TWO("2", 15),
    SMALL_JOKER("小王", 16),
    BIG_JOKER("大王", 17)
}

data class Card(
    val rank: Rank,
    val suit: Suit,
    val id: Int
) : Comparable<Card> {
    override fun compareTo(other: Card): Int = this.rank.weight - other.rank.weight

    val isJoker: Boolean get() = rank == Rank.SMALL_JOKER || rank == Rank.BIG_JOKER
    val isRed: Boolean get() = suit == Suit.HEART || suit == Suit.DIAMOND
}

object Deck {
    fun createShuffled(): List<Card> {
        val cards = mutableListOf<Card>()
        var id = 0
        val suits = listOf(Suit.SPADE, Suit.HEART, Suit.DIAMOND, Suit.CLUB)
        val ranks = Rank.values().filter { it != Rank.SMALL_JOKER && it != Rank.BIG_JOKER }
        for (suit in suits) {
            for (rank in ranks) {
                cards.add(Card(rank, suit, id++))
            }
        }
        cards.add(Card(Rank.SMALL_JOKER, Suit.JOKER, id++))
        cards.add(Card(Rank.BIG_JOKER, Suit.JOKER, id++))
        return cards.shuffled()
    }
}
