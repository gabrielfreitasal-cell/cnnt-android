package com.cnnt.app.ui.block

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.cnnt.app.data.model.ContentBlock
import com.cnnt.app.data.model.LinkEdge
import kotlin.math.abs
import kotlin.math.hypot

class LinksOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class ScreenBlockBounds(
        val block: ContentBlock,
        val rect: RectF
    )

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88CCFF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val selectedEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB74D")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val tempEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88CCFF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#332196F3")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88CCFF")
        style = Paint.Style.FILL
    }

    private var blocks: List<ScreenBlockBounds> = emptyList()
    private var links: List<LinkEdge> = emptyList()
    private var selectedLinkId: String? = null
    private var draggingFromBlockId: String? = null
    private var dragPoint: PointF? = null
    private var validTargets: Set<String> = emptySet()

    var onLinkTapped: ((LinkEdge) -> Unit)? = null
    var onLinkCreationFinished: ((String, String) -> Unit)? = null

    fun submit(
        blocks: List<ScreenBlockBounds>,
        links: List<LinkEdge>,
        selectedLinkId: String? = null
    ) {
        this.blocks = blocks
        this.links = links
        this.selectedLinkId = selectedLinkId
        invalidate()
    }

    fun startLinkPreview(sourceBlockId: String, point: PointF) {
        draggingFromBlockId = sourceBlockId
        dragPoint = point
        validTargets = blocks.map { it.block.id }.filterNot { it == sourceBlockId }.toSet()
        invalidate()
    }

    fun updateLinkPreview(point: PointF) {
        dragPoint = point
        invalidate()
    }

    fun finishLinkPreview(point: PointF) {
        val sourceId = draggingFromBlockId
        val targetId = findBlockAt(point)?.block?.id
        draggingFromBlockId = null
        dragPoint = null
        validTargets = emptySet()
        invalidate()
        if (sourceId != null && targetId != null && sourceId != targetId) {
            onLinkCreationFinished?.invoke(sourceId, targetId)
        }
    }

    fun cancelLinkPreview() {
        draggingFromBlockId = null
        dragPoint = null
        validTargets = emptySet()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val blocksById = blocks.associateBy { it.block.id }
        links.forEach { edge ->
            val source = blocksById[edge.sourceBlockId] ?: return@forEach
            val target = blocksById[edge.targetBlockId] ?: return@forEach
            drawLink(canvas, source.rect, target.rect, edge.label, edge.id == selectedLinkId)
        }

        validTargets.forEach { targetId ->
            val rect = blocksById[targetId]?.rect ?: return@forEach
            canvas.drawRoundRect(rect, 20f, 20f, glowPaint)
        }

        val sourceId = draggingFromBlockId
        val point = dragPoint
        if (sourceId != null && point != null) {
            val sourceRect = blocksById[sourceId]?.rect
            if (sourceRect != null) {
                drawTemporaryLink(canvas, sourceRect, point)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val tapped = findLinkNear(event.x, event.y)
            if (tapped != null) {
                onLinkTapped?.invoke(tapped)
                return true
            }
        }
        return false
    }

    private fun drawLink(
        canvas: Canvas,
        source: RectF,
        target: RectF,
        label: String,
        selected: Boolean
    ) {
        val path = bezierBetween(source, target)
        val paint = if (selected) selectedEdgePaint else edgePaint
        canvas.drawPath(path, paint)
        drawArrow(canvas, source, target, selected)
        if (label.isNotBlank()) {
            val midX = (source.centerX() + target.centerX()) / 2f
            val midY = (source.centerY() + target.centerY()) / 2f
            canvas.drawText(label, midX, midY, textPaint)
        }
    }

    private fun drawTemporaryLink(canvas: Canvas, source: RectF, point: PointF) {
        val start = PointF(source.right, source.centerY())
        val controlOffset = abs(point.x - start.x) * 0.45f
        val path = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(
                start.x + controlOffset,
                start.y,
                point.x - controlOffset,
                point.y,
                point.x,
                point.y
            )
        }
        canvas.drawPath(path, tempEdgePaint)
    }

    private fun drawArrow(canvas: Canvas, source: RectF, target: RectF, selected: Boolean) {
        val end = PointF(target.left, target.centerY())
        val arrowSize = if (selected) 18f else 14f
        val path = Path().apply {
            moveTo(end.x, end.y)
            lineTo(end.x - arrowSize, end.y - arrowSize / 2f)
            lineTo(end.x - arrowSize, end.y + arrowSize / 2f)
            close()
        }
        arrowPaint.color = if (selected) Color.parseColor("#FFB74D") else Color.parseColor("#88CCFF")
        canvas.drawPath(path, arrowPaint)
    }

    private fun bezierBetween(source: RectF, target: RectF): Path {
        val start = PointF(source.right, source.centerY())
        val end = PointF(target.left, target.centerY())
        val controlOffset = abs(end.x - start.x) * 0.45f
        return Path().apply {
            moveTo(start.x, start.y)
            cubicTo(
                start.x + controlOffset,
                start.y,
                end.x - controlOffset,
                end.y,
                end.x,
                end.y
            )
        }
    }

    private fun findBlockAt(point: PointF): ScreenBlockBounds? {
        return blocks.lastOrNull { it.rect.contains(point.x, point.y) }
    }

    private fun findLinkNear(x: Float, y: Float): LinkEdge? {
        val blocksById = blocks.associateBy { it.block.id }
        return links.firstOrNull { edge ->
            val source = blocksById[edge.sourceBlockId]?.rect ?: return@firstOrNull false
            val target = blocksById[edge.targetBlockId]?.rect ?: return@firstOrNull false
            val samples = 30
            var lastX = source.right
            var lastY = source.centerY()
            for (index in 1..samples) {
                val t = index / samples.toFloat()
                val point = cubicPoint(source, target, t)
                val distance = distancePointToSegment(x, y, lastX, lastY, point.x, point.y)
                if (distance < 28f) return@firstOrNull true
                lastX = point.x
                lastY = point.y
            }
            false
        }
    }

    private fun cubicPoint(source: RectF, target: RectF, t: Float): PointF {
        val startX = source.right
        val startY = source.centerY()
        val endX = target.left
        val endY = target.centerY()
        val offset = abs(endX - startX) * 0.45f
        val c1x = startX + offset
        val c1y = startY
        val c2x = endX - offset
        val c2y = endY
        val u = 1 - t
        val x = u * u * u * startX + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * endX
        val y = u * u * u * startY + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * endY
        return PointF(x, y)
    }

    private fun distancePointToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx) + ((py - y1) * dy)) / (dx * dx + dy * dy)
        val clamped = t.coerceIn(0f, 1f)
        val nearestX = x1 + clamped * dx
        val nearestY = y1 + clamped * dy
        return hypot(px - nearestX, py - nearestY)
    }
}