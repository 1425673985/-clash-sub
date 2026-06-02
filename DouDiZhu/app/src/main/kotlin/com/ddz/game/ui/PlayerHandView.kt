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
        setGravity(android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.BOTTOM)
    }

    val selectedCards = mutableSetOf<Card>()
    private val cardViews = mutableMapOf<Int, CardView>()
    var onSelectionChanged: (() -> Unit)? = null

    init {
        addView(container)
        isFillViewport = true          // makes container fill full width → centering works
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(Color.TRANSPARENT)
        overScrollMode = OVER_SCROLL_NEVER
    }

    fun bindCards(cards: List<Card>) {
        container.removeAllViews()
        cardViews.clear()
        selectedCards.clear()

        val density = resources.displayMetrics.density
        val cardW   = (44 * density).roundToInt()   // was 52
        val cardH   = (66 * density).roundToInt()   // was 80; view height 80dp = cardH+liftDp
        val overlap = (28 * density).roundToInt()   // was 34
        val liftDp  = (14 * density)                // was 16

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
        val liftDp = (14 * density)
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
