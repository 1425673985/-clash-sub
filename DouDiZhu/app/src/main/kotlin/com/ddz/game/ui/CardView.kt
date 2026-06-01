package com.ddz.game.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.ddz.game.model.Card
import com.ddz.game.model.Rank

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

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x38000000.toInt()
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CACACA")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    private val backDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x30FFFFFF.toInt()
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = w * 0.13f

        // Drop shadow offset below-right
        canvas.drawRoundRect(RectF(3f, 5f, w + 1f, h + 1f), radius, radius, shadowPaint)

        val rect = RectF(1f, 1f, w - 3f, h - 2f)

        if (!isFaceUp) {
            drawBack(canvas, rect, radius)
            return
        }

        // Warm ivory gradient card face
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h,
            0xFFFFFBEE.toInt(), 0xFFFFF0C0.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        bgPaint.shader = null

        val c = card ?: return
        val isRed = c.isRed || c.rank == Rank.BIG_JOKER
        val textColor = if (isRed) Color.parseColor("#C62828") else Color.parseColor("#1A1A1A")

        val rankSize  = h * 0.22f
        val suitSize  = h * 0.145f
        val centerSz  = h * 0.40f
        val leftPad   = w * 0.12f

        rankPaint.color = textColor; rankPaint.textSize = rankSize
        suitSmallPaint.color = textColor; suitSmallPaint.textSize = suitSize
        centerPaint.color = textColor

        // Top-left rank
        canvas.drawText(c.rank.display, leftPad, rankSize + 3f, rankPaint)
        // Top-left suit (not for jokers)
        if (!c.isJoker) canvas.drawText(c.suit.symbol, leftPad, rankSize + suitSize + 4f, suitSmallPaint)

        // Center symbol
        val cx = w / 2f
        val cy = h * 0.57f
        when {
            c.rank == Rank.BIG_JOKER -> {
                centerPaint.textSize = h * 0.27f
                centerPaint.color = Color.parseColor("#B71C1C")
                canvas.drawText("大", cx, cy - centerPaint.textSize * 0.52f, centerPaint)
                canvas.drawText("王", cx, cy + centerPaint.textSize * 0.62f, centerPaint)
            }
            c.rank == Rank.SMALL_JOKER -> {
                centerPaint.textSize = h * 0.27f
                centerPaint.color = Color.parseColor("#1B5E20")
                canvas.drawText("小", cx, cy - centerPaint.textSize * 0.52f, centerPaint)
                canvas.drawText("王", cx, cy + centerPaint.textSize * 0.62f, centerPaint)
            }
            else -> {
                centerPaint.textSize = centerSz
                canvas.drawText(c.suit.symbol, cx, cy, centerPaint)
            }
        }

        // Bottom-right corner (rotated 180°)
        canvas.save()
        canvas.rotate(180f, w / 2f, h / 2f)
        canvas.drawText(c.rank.display, leftPad, rankSize + 3f, rankPaint)
        if (!c.isJoker) canvas.drawText(c.suit.symbol, leftPad, rankSize + suitSize + 4f, suitSmallPaint)
        canvas.restore()

        // Border — gold glow when selected, subtle gray otherwise
        if (isCardSelected) {
            selectedBorderPaint.shader = LinearGradient(0f, 0f, w, h,
                Color.parseColor("#FFD740"), Color.parseColor("#FF8F00"), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, radius, radius, selectedBorderPaint)
            selectedBorderPaint.shader = null
        } else {
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }
    }

    private fun drawBack(canvas: Canvas, rect: RectF, radius: Float) {
        // Deep indigo gradient
        bgPaint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
            Color.parseColor("#1A237E"), Color.parseColor("#283593"), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        bgPaint.shader = null

        // Dot grid pattern
        val step = rect.width() / 5.5f
        val dotR = step * 0.13f
        var x = rect.left + step * 0.7f
        while (x < rect.right) {
            var y = rect.top + step * 0.7f
            while (y < rect.bottom) {
                canvas.drawCircle(x, y, dotR, backDotPaint)
                y += step
            }
            x += step
        }

        // Inner rounded border
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x50FFFFFF.toInt()
            style = Paint.Style.STROKE; strokeWidth = 2f
        }
        canvas.drawRoundRect(
            RectF(rect.left + 5f, rect.top + 5f, rect.right - 5f, rect.bottom - 5f),
            radius - 3f, radius - 3f, innerPaint
        )

        // Diamond ornament in center
        val cx = rect.centerX(); val cy = rect.centerY()
        val dw = rect.width() * 0.24f; val dh = rect.height() * 0.16f
        val path = Path().apply {
            moveTo(cx, cy - dh * 1.5f)
            lineTo(cx + dw, cy)
            lineTo(cx, cy + dh * 1.5f)
            lineTo(cx - dw, cy)
            close()
        }
        val dPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x40FFFFFF.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, dPaint)

        // Outer border
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x60FFFFFF.toInt()
            style = Paint.Style.STROKE; strokeWidth = 1.5f
        }
        canvas.drawRoundRect(rect, radius, radius, outerPaint)
    }
}
