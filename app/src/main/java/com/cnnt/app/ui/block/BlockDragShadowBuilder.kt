package com.cnnt.app.ui.block

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class BlockDragShadowBuilder(
    view: View,
    private val label: String
) : View.DragShadowBuilder(view) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC1F2430")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88CCFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        isFakeBoldText = true
    }

    override fun onProvideShadowMetrics(outShadowSize: android.graphics.Point, outShadowTouchPoint: android.graphics.Point) {
        outShadowSize.set(280, 140)
        outShadowTouchPoint.set(140, 70)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val rect = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        canvas.drawRoundRect(rect, 24f, 24f, backgroundPaint)
        canvas.drawRoundRect(rect, 24f, 24f, borderPaint)
        canvas.drawText(label, 28f, canvas.height / 2f + 10f, textPaint)
    }
}