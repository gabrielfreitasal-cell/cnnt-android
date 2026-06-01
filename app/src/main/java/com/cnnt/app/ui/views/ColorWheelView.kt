package com.cnnt.app.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class ColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val selectorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x44000000.toInt()
    }

    private var wheelBitmap: Bitmap? = null
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    private var selectedHue = 0f
    private var selectedSat = 1f

    var brightness = 1f
        set(value) {
            field = value
            rebuildWheel()
            invalidate()
            notifyColorChanged()
        }

    var onColorChanged: ((Int) -> Unit)? = null

    private fun notifyColorChanged() {
        val color = Color.HSVToColor(floatArrayOf(selectedHue, selectedSat, brightness))
        onColorChanged?.invoke(color)
    }

    fun getSelectedColor(): Int {
        return Color.HSVToColor(floatArrayOf(selectedHue, selectedSat, brightness))
    }

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        selectedHue = hsv[0]
        selectedSat = hsv[1]
        brightness = hsv[2]
        rebuildWheel()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h)
        centerX = w / 2f
        centerY = h / 2f
        radius = size / 2f - 8f
        rebuildWheel()
    }

    private fun rebuildWheel() {
        val size = (radius * 2).toInt()
        if (size <= 0) return

        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cx = size / 2f
        val cy = size / 2f
        val r = size / 2f

        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - cx
                val dy = y - cy
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= r) {
                    val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
                    val sat = dist / r
                    val color = Color.HSVToColor(floatArrayOf(angle.toFloat(), sat.toFloat(), brightness))
                    bmp.setPixel(x, y, color)
                }
            }
        }
        wheelBitmap = bmp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw wheel border (subtle)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(centerX, centerY, radius + 2f, borderPaint)

        wheelBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, centerX - radius, centerY - radius, wheelPaint)
        }

        // Draw selector circle at selected position
        val selectorAngle = Math.toRadians(selectedHue.toDouble())
        val selectorDist = selectedSat * radius
        val sx = centerX + (cos(selectorAngle) * selectorDist).toFloat()
        val sy = centerY + (sin(selectorAngle) * selectorDist).toFloat()

        canvas.drawCircle(sx, sy, 12f, selectorFillPaint)
        canvas.drawCircle(sx, sy, 12f, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val dist = sqrt(dx * dx + dy * dy)

                if (dist <= radius) {
                    selectedHue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360).toFloat()
                    selectedSat = (dist / radius).coerceIn(0f, 1f)
                    invalidate()
                    notifyColorChanged()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
