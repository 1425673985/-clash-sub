package com.ddz.game.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.ddz.game.model.Card
import kotlin.math.roundToInt

class CardGroupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        orientation = HORIZONTAL
    }

    fun showCards(cards: List<Card>, faceUp: Boolean = true) {
        removeAllViews()
        val density = resources.displayMetrics.density
        val cardW = (40 * density).roundToInt()
        val cardH = (62 * density).roundToInt()
        val overlap = (22 * density).roundToInt()

        cards.forEachIndexed { index, card ->
            val cv = CardView(context).apply {
                this.card = card
                isFaceUp = faceUp
                val lp = LayoutParams(cardW, cardH)
                if (index > 0) lp.marginStart = -overlap
                layoutParams = lp
                elevation = index.toFloat()
            }
            addView(cv)
        }
    }

    fun showPassLabel() {
        removeAllViews()
    }

    fun clear() {
        removeAllViews()
    }
}
