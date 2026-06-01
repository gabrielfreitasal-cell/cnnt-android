package com.cnnt.app.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class BrightnessSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var hue = 0f
    private var saturation = 1f
    var brightness = 1f
        private set

    var onBrightnessChanged: ((Float) -> Unit)? = null

    fun setHueSat(h: Float, s: Float) {
        hue = h
        saturation = s
        invalidate()
    }

    fun setBrightness(value: Float) {
        brightness = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barWidth = width.toFloat()
        val barHeight = height.toFloat()
        val cornerRadius = barWidth / 2f

        // Gradient from full color (top) to black (bottom)
        val topColor = Color.HSVToColor(floatArrayOf(hue, saturation, 1f))
        val bottomColor = Color.HSVToColor(floatArrayOf(hue, saturation, 0f))

        val shader = LinearGradient(
            0f, 0f, 0f, barHeight,
            topColor, bottomColor,
            Shader.TileMode.CLAMP
        )
        gradientPaint.shader = shader

        val rect = RectF(0f, 0f, barWidth, barHeight)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, gradientPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        // Draw indicator triangle on the right side
        val indicatorY = (1f - brightness) * barHeight
        val triSize = 10f
        val path = Path().apply {
            moveTo(barWidth + 4f, indicatorY)
            lineTo(barWidth + 4f + triSize, indicatorY - triSize / 2f)
            lineTo(barWidth + 4f + triSize, indicatorY + triSize / 2f)
            close()
        }
        val triFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4488FF.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, triFill)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                brightness = (1f - (event.y / height)).coerceIn(0f, 1f)
                invalidate()
                onBrightnessChanged?.invoke(brightness)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
