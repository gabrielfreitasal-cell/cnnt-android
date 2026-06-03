package com.cnnt.app.ui.block

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.cnnt.app.data.model.ContentBlock

class MiniMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC101319")
    }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6699CCFF")
    }
    private val viewportPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private var blocks: List<ContentBlock> = emptyList()
    private var viewport: RectF = RectF()
    private var worldBounds: RectF = RectF(-500f, -500f, 500f, 500f)
    var onNavigate: ((Float, Float) -> Unit)? = null

    fun submit(blocks: List<ContentBlock>, viewportWorld: RectF) {
        this.blocks = blocks
        this.viewport = viewportWorld
        worldBounds = computeWorldBounds(blocks, viewportWorld)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 20f, 20f, backgroundPaint)
        if (worldBounds.width() <= 0f || worldBounds.height() <= 0f) return

        blocks.forEach { block ->
            val left = mapX(block.posX)
            val top = mapY(block.posY)
            val right = mapX(block.posX + block.width)
            val bottom = mapY(block.posY + block.height)
            canvas.drawRoundRect(RectF(left, top, right, bottom), 6f, 6f, blockPaint)
        }

        viewportPaint.color = Color.parseColor("#FFD8F1FF")
        val viewportRect = RectF(
            mapX(viewport.left),
            mapY(viewport.top),
            mapX(viewport.right),
            mapY(viewport.bottom)
        )
        canvas.drawRoundRect(viewportRect, 8f, 8f, viewportPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
            val worldX = unmapX(event.x)
            val worldY = unmapY(event.y)
            onNavigate?.invoke(worldX, worldY)
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun computeWorldBounds(blocks: List<ContentBlock>, viewportWorld: RectF): RectF {
        var minX = viewportWorld.left
        var minY = viewportWorld.top
        var maxX = viewportWorld.right
        var maxY = viewportWorld.bottom
        blocks.forEach { block ->
            minX = minOf(minX, block.posX)
            minY = minOf(minY, block.posY)
            maxX = maxOf(maxX, block.posX + block.width)
            maxY = maxOf(maxY, block.posY + block.height)
        }
        return RectF(minX - 120f, minY - 120f, maxX + 120f, maxY + 120f)
    }

    private fun mapX(worldX: Float): Float = ((worldX - worldBounds.left) / worldBounds.width()) * width
    private fun mapY(worldY: Float): Float = ((worldY - worldBounds.top) / worldBounds.height()) * height
    private fun unmapX(screenX: Float): Float = worldBounds.left + (screenX / width) * worldBounds.width()
    private fun unmapY(screenY: Float): Float = worldBounds.top + (screenY / height) * worldBounds.height()
}