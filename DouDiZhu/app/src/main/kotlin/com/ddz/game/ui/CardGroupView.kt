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

    // 显示 n 张背面牌（用于 AI 手牌展示）
    fun showBackCards(count: Int) {
        removeAllViews()
        if (count == 0) return
        val density = resources.displayMetrics.density
        val cardW = (32 * density).roundToInt()
        val cardH = (50 * density).roundToInt()
        val overlap = (20 * density).roundToInt()
        val show = minOf(count, 8) // 最多显示8张避免溢出
        repeat(show) { index ->
            val cv = CardView(context).apply {
                isFaceUp = false
                val lp = LayoutParams(cardW, cardH)
                if (index > 0) lp.marginStart = -overlap
                layoutParams = lp
                elevation = index.toFloat()
            }
            addView(cv)
        }
    }

    fun clear() {
        removeAllViews()
    }
}
