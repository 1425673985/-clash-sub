package com.ddz.game.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.ddz.game.model.Card
import kotlin.math.roundToInt

class PlayerHandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    val selectedCards = mutableSetOf<Card>()
    private val cardViews = mutableMapOf<Int, CardView>()
    var onSelectionChanged: (() -> Unit)? = null

    init {
        addView(container)
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(Color.TRANSPARENT)
        overScrollMode = OVER_SCROLL_NEVER
    }

    fun bindCards(cards: List<Card>) {
        container.removeAllViews()
        cardViews.clear()
        selectedCards.clear()

        val density = resources.displayMetrics.density
        val cardW   = (52 * density).roundToInt()
        val cardH   = (80 * density).roundToInt()
        val overlap = (34 * density).roundToInt()
        val liftDp  = (16 * density)

        cards.forEachIndexed { index, card ->
            val cv = CardView(context).apply {
                this.card = card
                isFaceUp = true
                isCardSelected = false
                val lp = LinearLayout.LayoutParams(cardW, cardH)
                if (index > 0) lp.marginStart = -overlap
                layoutParams = lp
                elevation = index.toFloat()
                setOnClickListener {
                    if (selectedCards.contains(card)) {
                        selectedCards.remove(card)
                        translationY = 0f
                        isCardSelected = false
                    } else {
                        selectedCards.add(card)
                        translationY = -liftDp
                        isCardSelected = true
                    }
                    onSelectionChanged?.invoke()
                }
            }
            cardViews[card.id] = cv
            container.addView(cv)
        }
    }

    fun selectCards(cards: List<Card>) {
        clearSelection()
        val density = resources.displayMetrics.density
        val liftDp = (16 * density)
        cards.forEach { card ->
            cardViews[card.id]?.let { cv ->
                selectedCards.add(card)
                cv.translationY = -liftDp
                cv.isCardSelected = true
            }
        }
        onSelectionChanged?.invoke()
    }

    fun clearSelection() {
        selectedCards.forEach { card ->
            cardViews[card.id]?.let {
                it.translationY = 0f
                it.isCardSelected = false
            }
        }
        selectedCards.clear()
        onSelectionChanged?.invoke()
    }

    fun getSelected(): List<Card> = selectedCards.toList()
}
