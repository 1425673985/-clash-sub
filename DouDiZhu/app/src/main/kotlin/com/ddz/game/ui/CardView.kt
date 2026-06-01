package com.ddz.game.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.ddz.game.model.Card
import com.ddz.game.model.Rank
import com.ddz.game.model.Suit

class CardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var card: Card? = null
        set(value) { field = value; invalidate() }

    var isFaceUp: Boolean = true
        set(value) { field = value; invalidate() }

    var isCardSelected: Boolean = false
        set(value) { field = value; invalidate() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFDE7")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }
    private val suitSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        alpha = 80
        strokeWidth = 6f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = w * 0.12f
        val rect = RectF(3f, 3f, w - 3f, h - 3f)

        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        if (!isFaceUp) {
            drawBack(canvas, rect, radius)
            return
        }

        val c = card ?: return
        val isRed = c.isRed || c.rank == Rank.BIG_JOKER

        val textColor = if (isRed) Color.parseColor("#D32F2F") else Color.parseColor("#212121")
        rankPaint.color = textColor
        suitSmallPaint.color = textColor
        centerPaint.color = textColor

        val rankSize = h * 0.20f
        val suitSize = h * 0.14f
        val centerSize = h * 0.32f

        rankPaint.textSize = rankSize
        suitSmallPaint.textSize = suitSize
        centerPaint.textSize = if (c.isJoker) h * 0.22f else centerSize

        // 左上角 rank
        canvas.drawText(c.rank.display, 8f, rankSize + 4f, rankPaint)

        // 左上角花色（王牌不显示）
        if (!c.isJoker) {
            canvas.drawText(c.suit.symbol, 8f, rankSize + suitSize + 4f, suitSmallPaint)
        }

        // 中央
        val cx = w / 2f
        val cy = h / 2f + centerSize * 0.35f
        when {
            c.rank == Rank.BIG_JOKER -> {
                centerPaint.color = Color.parseColor("#D32F2F")
                canvas.drawText("大", cx, cy - centerPaint.textSize * 0.6f, centerPaint)
                canvas.drawText("王", cx, cy + centerPaint.textSize * 0.5f, centerPaint)
            }
            c.rank == Rank.SMALL_JOKER -> {
                centerPaint.color = Color.parseColor("#1B5E20")
                canvas.drawText("小", cx, cy - centerPaint.textSize * 0.6f, centerPaint)
                canvas.drawText("王", cx, cy + centerPaint.textSize * 0.5f, centerPaint)
            }
            else -> canvas.drawText(c.suit.symbol, cx, cy, centerPaint)
        }

        // 右下角（倒置的 rank + suit）
        canvas.save()
        canvas.rotate(180f, w / 2f, h / 2f)
        canvas.drawText(c.rank.display, 8f, rankSize + 4f, rankPaint)
        if (!c.isJoker) canvas.drawText(c.suit.symbol, 8f, rankSize + suitSize + 4f, suitSmallPaint)
        canvas.restore()

        // 选中高亮
        if (isCardSelected) canvas.drawRoundRect(rect, radius, radius, selectedBorderPaint)
        else canvas.drawRoundRect(rect, radius, radius, borderPaint)
    }

    private fun drawBack(canvas: Canvas, rect: RectF, radius: Float) {
        backPaint.color = Color.parseColor("#1565C0")
        canvas.drawRoundRect(rect, radius, radius, backPaint)
        // 斜线纹理
        val step = 18f
        var x = rect.left
        while (x < rect.right + rect.height()) {
            canvas.drawLine(x, rect.top, x - rect.height(), rect.bottom, backLinePaint)
            x += step
        }
        // 内边框
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; alpha = 60; style = Paint.Style.STROKE; strokeWidth = 3f
        }
        val innerRect = RectF(rect.left + 6f, rect.top + 6f, rect.right - 6f, rect.bottom - 6f)
        canvas.drawRoundRect(innerRect, radius - 3f, radius - 3f, innerPaint)
    }
}
