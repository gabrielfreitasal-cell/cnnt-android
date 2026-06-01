package com.cnnt.app.ink

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.cnnt.app.data.model.BrushPreset
import com.cnnt.app.data.model.Stroke
import com.cnnt.app.data.model.StrokePoint
import com.cnnt.app.data.model.TipShape
import com.cnnt.app.data.model.StrokeBehavior
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class InkEngine {

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    fun renderStroke(canvas: Canvas, stroke: Stroke, brush: BrushPreset, zoomScale: Float = 1f) {
        val pointsCopy = ArrayList(stroke.points)
        if (pointsCopy.size < 2) {
            if (pointsCopy.size == 1) {
                renderDot(canvas, pointsCopy[0], stroke, brush)
            }
            return
        }

        val z = zoomScale.coerceIn(1f, 16f)

        // Dispatch by brush ID for visually distinct rendering
        when (brush.id) {
            "brush_gel_pen" -> renderGelPen(canvas, stroke, brush, pointsCopy, z)
            "brush_soft_pencil" -> renderSoftPencil(canvas, stroke, brush, pointsCopy, z)
            "brush_marker" -> renderMarker(canvas, stroke, brush, pointsCopy, z)
            "brush_highlighter" -> renderHighlighter(canvas, stroke, brush, pointsCopy, z)
            "brush_chisel" -> renderChiselStroke(canvas, stroke, brush, pointsCopy, z)
            "brush_cnnt_special" -> renderCnntSpecial(canvas, stroke, brush, pointsCopy, z)
            "brush_wet_ink" -> renderWetInk(canvas, stroke, brush, pointsCopy, z)
            "brush_technical_pen" -> renderTechnicalPen(canvas, stroke, brush, pointsCopy, z)
            "brush_grain" -> renderGrainBrush(canvas, stroke, brush, pointsCopy, z)
            "brush_neon" -> renderNeonLaser(canvas, stroke, brush, pointsCopy, z)
            "brush_spray" -> renderSprayAirbrush(canvas, stroke, brush, pointsCopy, z)
            "brush_experimental" -> renderExperimental(canvas, stroke, brush, pointsCopy, z)
            else -> renderGelPen(canvas, stroke, brush, pointsCopy, z)
        }
    }

    private fun renderDot(canvas: Canvas, point: StrokePoint, stroke: Stroke, brush: BrushPreset) {
        fillPaint.color = stroke.color
        fillPaint.alpha = (stroke.opacity * 255 * brush.opacity).toInt()
        val radius = stroke.size * point.pressure * brush.pressureSensitivity * 0.5f
        canvas.drawCircle(point.x, point.y, max(radius, 0.5f), fillPaint)
    }

    // --- Gel Pen: smooth, pressure-responsive, tapered ends ---
    private fun renderGelPen(canvas: Canvas, stroke: Stroke, brush: BrushPreset, points: List<StrokePoint>, zoomScale: Float) {
        val extra = ((zoomScale - 1f) * 0.04f).coerceIn(0f, 0.35f)
        val smoothed = smoothPointsMultiPass(points, (brush.smoothing + extra).coerceIn(0.4f, 0.92f))

        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.maskFilter = null
        strokePaint.isAntiAlias = true
        strokePaint.color = stroke.color
        strokePaint.alpha = (stroke.opacity * 255).toInt()

        if (smoothed.size < 2) {
            if (smoothed.size == 1) renderDot(canvas, smoothed[0], stroke, brush)
            return
        }

        val path = Path()
        path.moveTo(smoothed[0].x, smoothed[0].y)
        for (i in 1 until smoothed.size) {
            val prev = smoothed[i - 1]
            val curr = smoothed[i]
            val midX = (prev.x + curr.x) * 0.5f
            val midY = (prev.y + curr.y) * 0.5f
            path.quadTo(prev.x, prev.y, midX, midY)
        }
        val last = smoothed.last()
        path.lineTo(last.x, last.y)

        val avgPressure = smoothed.map { it.pressure }.average().toFloat().coerceIn(0.15f, 1f)
        strokePaint.strokeWidth = max(stroke.size * avgPressure * 0.85f, 0.8f)
        canvas.drawPath(path, strokePaint)
    }

    // --- Soft Pencil: textured, grainy, variable opacity by pressure ---
    private fun renderSoftPencil(canvas: Canvas, stroke: Stroke, brush: BrushPreset, points: List<StrokePoint>, zoomScale: Float) {
        val smoothed = smoothPointsMultiPass(points, 0.35f)

        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.maskFilter = null

        for (i in 1 until smoothed.size) {
            val prev = smoothed[i - 1]
            val curr = smoothed[i]

            val pressure = curr.pressure.coerceIn(0.05f, 1f)
            val width = stroke.size * pressure * 1.2f
            // Pencil: opacity varies with pressure
            val alpha = (stroke.opacity * pressure * 0.7f * 255).toInt().coerceIn(20, 220)

            strokePaint.color = stroke.color
            strokePaint.alpha = alpha
            strokePaint.strokeWidth = max(width, 0.3f)
            canvas.drawLine(prev.x, prev.y, curr.x, curr.y, strokePaint)

            // Grain texture: scatter dots (every 3rd point for performance)
            if (i % 3 == 0) {
                val grainCount = (width * 0.4f).toInt().coerceIn(1, 3)
                fillPaint.color = stroke.color
                for (g in 0 until grainCount) {
                    val gx = curr.x + stableSigned(stroke.id, i, 3) * width * 1.2f
                    val gy = curr.y + stableSigned(stroke.id, i, 5) * width * 1.2f
                    fillPaint.alpha = (alpha * 0.4f * stable01(stroke.id, i, 9)).toInt().coerceIn(5, 100)
                    canvas.drawCircle(gx, gy, (0.3f + stable01(stroke.id, i, 13) * 0.5f), fillPaint)
                }
            }
        }
    }

    // --- Marker: flat tip, constant width, bold, square cap ---
    private fun renderMarker(canvas: Canvas, stroke: Stroke, brush: BrushPreset, points: List<StrokePoint>, zoomScale: Float) {
        val smoothed = smoothPointsMultiPass(points, 0.6f)

        strokePaint.strokeCap = Paint.Cap.SQUARE
        strokePaint.strokeJoin = Paint.Join.BEVEL
        strokePaint.maskFilter = null
        strokePaint.color = stroke.color
        strokePaint.alpha = (stroke.opacity * 0.9f * 255).toInt()
        // Marker: mostly constant width, slight direction variation
        val baseWidth = stroke.size * 2.0f

        for (i in 1 until smoothed.size) {
            val prev = smoothed[i - 1]
            val curr = smoothed[i]

            val direction = atan2((curr.y - prev.y).toDouble(), (curr.x - prev.x).toDouble()).toFloat()
            val dirFactor = 0.6f + abs(sin(direction)) * 0.4f
            strokePaint.strokeWidth = max(baseWidth * dirFactor, 1f)

            canvas.drawLine(prev.x, prev.y, curr.x, curr.y, strokePaint)
        }
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
    }

    // --- Highlighter: wide, transparent, flat ---
    private fun renderHighlighter(canvas: Canvas, stroke: Stroke, brush: BrushPreset, points: List<StrokePoint>, zoomScale: Float) {
        val smoothed = smoothPointsMultiPass(points, 0.75f)

        strokePaint.strokeCap = Paint.Cap.SQUARE
        strokePaint.strokeJoin = Paint.Join.BEVEL
        strokePaint.maskFilter = null
        strokePaint.color = stroke.color
        strokePaint.alpha = (stroke.opacity * 0.30f * 255).toInt()
        strokePaint.strokeWidth = stroke.size * 3f

        val path = Path()
        path.moveTo(smoothed[0].x, smoothed[0].y)
        for (i in 1 until smoothed.size) {
            path.lineTo(smoothed[i].x, smoothed[i].y)
        }
        canvas.drawPath(path, strokePaint)

        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
    }

    // --- Chisel/Calligraphy: direction-dependent width, elegant ---
    private fun renderChiselStroke(canvas: Canvas, stroke: Stroke, brush: BrushPreset, points: List<StrokePoint>, zoomScale: Float) {
        val smoothed = smoothPointsMultiPass(points, 0.45f)
        if (smoothed.size < 2) return

        val path = Path()
        val topPoints = mutableListOf<Pair<Float, Float>>()
        val bottomPoints = mutableListOf<Pair<Float, Float>>()

        val chiselAngle = Math.PI.toFloat() / 4f

        for (i in smoothed.indices) {
            val point = smoothed[i]
            val t = i.toFloat() / smoothed.size
            val taper = calculateTaper(t, StrokeBehavior.TAPER, StrokeBehavior.TAPER, smoothed.size)

            val direction = if (i > 0) {
                atan2(
                    (point.y - smoothed[i - 1].y).toDouble(),
                    (point.x - smoothed[i - 1].x).toDouble()
                ).toFloat()
            } else if (smoothed.size > 1) {
                atan2(
                    (smoothed[1].y - point.y).toDouble(),
                    (smoothed[1].x - point.x).toDouble()
                ).toFloat()
            } else 0f

            val dirFactor = abs(sin(direction + chiselAngle)) * 0.8f + 0.2f
            val width = stroke.size * point.pressure * dirFactor * taper * 1.5f

            val perpAngle = direction + Math.PI.toFloat() / 2f + chiselAngle * 0.8f
            val halfW = width * 0.5f

            topPoints.add(Pair(
                point.x + cos(perpAngle) * halfW,
                point.y + sin(perpAngle) * halfW
            ))
            bottomPoints.add(Pair(
                point.x - cos(perpAngle) * halfW,
                point.y - sin(perpAngle) * halfW
            ))
        }

        if (topPoints.isNotEmpty()) {
            path.moveTo(topPoints[0].first, topPoints[0].second)
            for (i in 1 until topPoints.size) {
                val prev = topPoints[i - 1]
                val curr = topPoints[i]
                val cx = (prev.first + curr.first) / 2f
                val cy = (prev.second + curr.second) / 2f
                path.quadTo(prev.first, prev.second, cx, cy)
            }
            path.lineTo(topPoints.last().first, topPoints.last().second)

            for (i in bottomPoints.indices.reversed()) {
                val point = bottomPoints[i]
                if (i == bottomPoints.size - 1) {
                    path.lineTo(point.first, point.second)
                } else {
                    val next = bottomPoints[i + 1]
                    val cx = (point.first + next.first) / 2f
                    val cy = (point.second + next.second) / 2f
                    path.quadTo(next.first, next.second, cx, cy)
                }
            }
            path.close()

            fillPaint.color = stroke.color
            fillPaint.alpha = (stroke.opacity * 255).toInt()
            fillPaint.maskFilter = null
            canvas.drawPath(path, fillPaint)
        }
    }

    // --- CNNT Special: Chisel/beveled flat pen for lettering ---
    // Simulates a flat-tip calligraphy pen held at ~45 degrees
    // DOWN strokes = THICK, UP strokes = THIN (hairline)
    // Uses brush.directionSensitivity and brush.pressureSensitivity for user tuning
    private fun renderCnntSpecial(canvas: Canvas, stroke: Stroke, brush: BrushPreset, points: List<StrokePoint>, zoomScale: Float) {
        val smoothed = smoothPointsMultiPass(points, brush.smoothing.coerceIn(0.3f, 0.8f))
        if (smoothed.size < 2) return

        val path = Path()
        val topEdge = mutableListOf<Pair<Float, Float>>()
        val bottomEdge = mutableListOf<Pair<Float, Float>>()

        // Chisel angle: 45 degrees (classic calligraphy)
        val chiselAngle = Math.PI.toFloat() / 4f
        // Direction sensitivity from brush config (0-1, higher = more dramatic thick/thin)
        val dirSens = brush.directionSensitivity.coerceIn(0f, 1.5f)
        // Pressure influence from brush config
        val pressSens = brush.pressureSensitivity.coerceIn(0f, 2f)

        var prevDirFactor = 0.5f

        for (i in smoothed.indices) {
            val point = smoothed[i]
            val t = i.toFloat() / smoothed.size
            val taper = calculateTaper(t, brush.startBehavior, brush.endBehavior, smoothed.size, stroke.id, i)
            val pressure = if (pressSens > 0.01f) {
                (0.3f + point.pressure.coerceIn(0.1f, 1f) * 0.7f * pressSens).coerceIn(0.2f, 1.2f)
            } else 1f

            val dy = if (i > 0) smoothed[i].y - smoothed[i - 1].y
                     else if (smoothed.size > 1) smoothed[1].y - smoothed[0].y else 0f
            val dx = if (i > 0) smoothed[i].x - smoothed[i - 1].x
                     else if (smoothed.size > 1) smoothed[1].x - smoothed[0].x else 0f

            val segLen = hypot(dx, dy)
            val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()

            // Chisel factor: perpendicular to chisel = max width
            val chiselFactor = abs(sin(angle + chiselAngle))

            // Down-stroke boost
            val verticalRatio = if (segLen > 0.1f) (dy / segLen) else 0f
            val downBoost = if (verticalRatio > 0.1f) 1f + verticalRatio * 0.4f else 1f

            // Min thickness: higher dirSens = thinner hairlines
            val minThickness = max(0.05f, 0.15f - dirSens * 0.1f)
            val rawDirFactor = (minThickness + chiselFactor * (1f - minThickness) * downBoost).coerceIn(minThickness, 1.2f)

            // Smooth transitions (lighter = more responsive, heavier = smoother)
            val smoothBlend = 0.4f - brush.smoothing * 0.3f
            val dirFactor = prevDirFactor * smoothBlend + rawDirFactor * (1f - smoothBlend)
            prevDirFactor = dirFactor

            // Width: use dirSens as multiplier for dramatic effect
            val widthMultiplier = 1.5f + dirSens * 1.5f
            val width = stroke.size * pressure * dirFactor * taper * widthMultiplier

            val perpAngle = angle + Math.PI.toFloat() / 2f
            val chiselPerpAngle = perpAngle + chiselAngle * 0.2f
            val halfW = max(width * 0.5f, 0.15f)

            topEdge.add(Pair(
                point.x + cos(chiselPerpAngle) * halfW,
                point.y + sin(chiselPerpAngle) * halfW
            ))
            bottomEdge.add(Pair(
                point.x - cos(chiselPerpAngle) * halfW,
                point.y - sin(chiselPerpAngle) * halfW
            ))
        }

        if (topEdge.isNotEmpty()) {
            path.moveTo(topEdge[0].first, topEdge[0].second)
            for (i in 1 until topEdge.size) {
                val prev = topEdge[i - 1]
                val curr = topEdge[i]
                val cx = (prev.first + curr.first) / 2f
                val cy = (prev.second + curr.second) / 2f
                path.quadTo(prev.first, prev.second, cx, cy)
            }
            path.lineTo(topEdge.last().first, topEdge.last().second)

            for (i in bottomEdge.indices.reversed()) {
                val pt = bottomEdge[i]
                if (i == bottomEdge.size - 1) {
                    path.lineTo(pt.first, pt.second)
                } else {
                    val next = bottomEdge[i + 1]
                    val cx = (pt.first + next.first) / 2f
                    val cy = (pt.second + next.second) / 2f
                    path.quadTo(next.first, next.second, cx, cy)
                }
            }
            path.close()

            fillPaint.color = stroke.color
            fillPaint.alpha = (stroke.opacity * 255).toInt()
            fillPaint.maskFilter = null
            canvas.drawPath(path, fillPaint)
        }
    }

    // --- Wet Ink: blob at start, flowing, slightly irregular ---
    private fun renderWetInk(canvas: Canvas, stroke: Stroke, brush: BrushPreset, points: List<StrokePoint>, zoomScale: Float) {
        val extra = ((zoomScale - 1f) * 0.05f).coerceIn(0f, 0.4f)
        val smoothed = smoothPointsMultiPass(points, (0.65f + extra).coerceIn(0.5f, 0.9f))

        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.maskFilter = null

        // Draw blob at start
        if (smoothed.isNotEmpty()) {
            fillPaint.color = stroke.color
            fillPaint.alpha = (stroke.opacity * 200).toInt()
            val blobSize = stroke.size * 1.8f * smoothed[0].pressure
            canvas.drawCircle(smoothed[0].x, smoothed[0].y, blobSize, fillPaint)
        }

        for (i in 1 until smoothed.size) {
            val prev = smoothed[i - 1]
            val curr = smoothed[i]

            val t = i.toFloat() / smoothed.size
            val pressure = curr.pressure.coerceIn(0.1f, 1f)
            val velocity = calculateVelocity(prev, curr)
            val velFactor = 1f - (velocity * 0.7f * 0.001f).coerceIn(0f, 0.6f)
            val fadeFactor = if (t > 0.7f) ((1f - t) / 0.3f) else 1f

            val width = stroke.size * pressure * 1.3f * velFactor * fadeFactor
            // Deterministic texture — avoids ink "moving" between frames
            val jitterX = stableSigned(stroke.id, i, 7) * 0.06f * stroke.size
            val jitterY = stableSigned(stroke.id, i, 11) * 0.06f * stroke.size

            strokePaint.color = stroke.color
            strokePaint.alpha = (stroke.opacity * 255 * fadeFactor).toInt().coerceIn(30, 255)
            strokePaint.strokeWidth = max(width, 0.5f)

            canvas.drawLine(prev.x + jitterX, prev.y + jitterY, curr.x + jitterX, curr.y + jitterY, strokePaint)
        }
    }

    // --- Technical Pen: uniform width, no pressure variation ---
    private fun renderTechnicalPen(canvas: Canvas, stroke: Stroke, brush: BrushPreset, points: List<StrokePoint>, zoomScale: Float) {
        val smoothed = smoothPoints(points, 0.7f)

        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.maskFilter = null
        strokePaint.color = stroke.color
        strokePaint.alpha = (stroke.opacity * 255).toInt()
        strokePaint.strokeWidth = stroke.size // Fixed width, no pressure

        val path = Path()
        path.moveTo(smoothed[0].x, smoothed[0].y)
        for (i in 1 until smoothed.size) {
            val mid = smoothed[i]
            path.lineTo(mid.x, mid.y)
        }
        canvas.drawPath(path, strokePaint)
    }

    // --- Grain Brush: heavy texture, scattered particles ---
    private fun renderGrainBrush(canvas: Canvas, stroke: Stroke, brush: BrushPreset, points: List<StrokePoint>, zoomScale: Float) {
        val smoothed = smoothPointsMultiPass(points, 0.45f)

        // Draw the base line thin
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.maskFilter = null
        strokePaint.color = stroke.color
        strokePaint.alpha = (stroke.opacity * 150).toInt()
        strokePaint.strokeWidth = stroke.size * 0.5f

        for (i in 1 until smoothed.size) {
            canvas.drawLine(smoothed[i-1].x, smoothed[i-1].y, smoothed[i].x, smoothed[i].y, strokePaint)
        }

        // Draw heavy grain/texture
        fillPaint.color = stroke.color
        val grainStep = max(1, smoothed.size / 60)
        var gi = 0
        while (gi < smoothed.size) {
            val point = smoothed[gi]
            val pressure = point.pressure.coerceIn(0.1f, 1f)
            val radius = stroke.size * pressure * 1.5f
            val particleCount = (radius * 1.5f).toInt().coerceIn(2, 10)

            for (p in 0 until particleCount) {
                val gx = point.x + stableSigned(stroke.id, gi, 2) * radius * 2f
                val gy = point.y + stableSigned(stroke.id, gi, 4) * radius * 2f
                fillPaint.alpha = (stroke.opacity * 80 * stable01(stroke.id, gi, 6)).toInt().coerceIn(10, 120)
                canvas.drawCircle(gx, gy, 0.3f + stable01(stroke.id, gi, 8) * 1.0f, fillPaint)
            }
            gi += grainStep
        }
    }

    // --- Neon/Laser: glow effect, bright core ---
    private fun renderNeonLaser(canvas: Canvas, stroke: Stroke, brush: BrushPreset, points: List<StrokePoint>, zoomScale: Float) {
        val smoothed = smoothPoints(points, 0.5f)

        // Outer glow (wide, faded)
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.color = stroke.color
        strokePaint.alpha = (stroke.opacity * 40).toInt()
        strokePaint.strokeWidth = stroke.size * 4f
        strokePaint.maskFilter = getCachedBlur(stroke.size * 2f)

        val glowPath = Path()
        glowPath.moveTo(smoothed[0].x, smoothed[0].y)
        for (i in 1 until smoothed.size) {
            glowPath.lineTo(smoothed[i].x, smoothed[i].y)
        }
        canvas.drawPath(glowPath, strokePaint)

        // Middle glow
        strokePaint.alpha = (stroke.opacity * 100).toInt()
        strokePaint.strokeWidth = stroke.size * 2f
        strokePaint.maskFilter = getCachedBlur(stroke.size)
        canvas.drawPath(glowPath, strokePaint)

        // Bright core (white-ish)
        strokePaint.maskFilter = null
        strokePaint.color = brightenColor(stroke.color)
        strokePaint.alpha = (stroke.opacity * 255).toInt()
        strokePaint.strokeWidth = stroke.size * 0.6f
        canvas.drawPath(glowPath, strokePaint)
    }

    // --- Spray/Airbrush: scattered dots, soft cloud (optimized) ---
    private fun renderSprayAirbrush(canvas: Canvas, stroke: Stroke, brush: BrushPreset, points: List<StrokePoint>, zoomScale: Float) {
        fillPaint.color = stroke.color
        fillPaint.maskFilter = null

        // Sample every Nth point to reduce rendering load
        val step = max(1, points.size / 80)
        var idx = 0
        while (idx < points.size) {
            val point = points[idx]
            val pressure = point.pressure.coerceIn(0.1f, 1f)
            val radius = stroke.size * pressure * 2f
            val count = (radius * 1.5f).toInt().coerceIn(3, 15)

            for (s in 0 until count) {
                val seed = idx * 31 + s
                val angle = stable01(stroke.id, seed, 1) * Math.PI * 2
                val dist = stable01(stroke.id, seed, 2) * radius
                val sx = point.x + (cos(angle) * dist).toFloat()
                val sy = point.y + (sin(angle) * dist).toFloat()

                val distFactor = 1f - (dist / radius)
                fillPaint.alpha = (stroke.opacity * 0.3f * distFactor * 255).toInt().coerceIn(5, 80)
                canvas.drawCircle(sx, sy, 0.6f + stable01(stroke.id, seed, 3) * 0.6f, fillPaint)
            }
            idx += step
        }
    }

    // --- Experimental: chaotic, all effects combined ---
    private fun renderExperimental(canvas: Canvas, stroke: Stroke, brush: BrushPreset, points: List<StrokePoint>, zoomScale: Float) {
        val smoothed = smoothPointsMultiPass(points, 0.35f)

        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.maskFilter = null

        for (i in 1 until smoothed.size) {
            val prev = smoothed[i - 1]
            val curr = smoothed[i]

            val t = i.toFloat() / smoothed.size
            val pressure = curr.pressure.coerceIn(0.1f, 1f)
            val velocity = calculateVelocity(prev, curr)
            val velFactor = 1f - (velocity * 1.0f * 0.001f).coerceIn(0f, 0.7f)
            val direction = atan2((curr.y - prev.y).toDouble(), (curr.x - prev.x).toDouble()).toFloat()
            val dirFactor = abs(sin(direction * 2f)) * 0.5f + 0.5f

            val width = stroke.size * pressure * 1.5f * velFactor * dirFactor

            val jitterX = stableSigned(stroke.id, i, 15) * 0.2f * stroke.size
            val jitterY = stableSigned(stroke.id, i, 17) * 0.2f * stroke.size

            strokePaint.color = stroke.color
            strokePaint.alpha = (stroke.opacity * (0.5f + pressure * 0.5f) * 255).toInt()
            strokePaint.strokeWidth = max(width, 0.5f)

            canvas.drawLine(prev.x + jitterX, prev.y + jitterY, curr.x + jitterX, curr.y + jitterY, strokePaint)

            if (stable01(stroke.id, i, 19) < 0.4f) {
                fillPaint.color = stroke.color
                fillPaint.alpha = (stroke.opacity * 100).toInt()
                val gx = curr.x + stableSigned(stroke.id, i, 21) * width * 3f
                val gy = curr.y + stableSigned(stroke.id, i, 23) * width * 3f
                canvas.drawCircle(gx, gy, stable01(stroke.id, i, 25) * 1.5f + 0.3f, fillPaint)
            }
        }
    }

    private val blurCache = HashMap<Int, BlurMaskFilter>()

    private fun getCachedBlur(radius: Float): BlurMaskFilter {
        val key = (radius * 10).toInt()
        return blurCache.getOrPut(key) {
            BlurMaskFilter(max(radius, 1f), BlurMaskFilter.Blur.NORMAL)
        }
    }

    private fun brightenColor(color: Int): Int {
        val r = min(255, Color.red(color) + 100)
        val g = min(255, Color.green(color) + 100)
        val b = min(255, Color.blue(color) + 100)
        return Color.argb(Color.alpha(color), r, g, b)
    }

    private fun calculateTaper(
        t: Float,
        startBehavior: StrokeBehavior,
        endBehavior: StrokeBehavior,
        totalPoints: Int,
        strokeId: String = "",
        pointIndex: Int = 0
    ): Float {
        var factor = 1f
        val taperLength = min(0.15f, 5f / totalPoints)

        when (startBehavior) {
            StrokeBehavior.TAPER -> {
                if (t < taperLength) factor *= (t / taperLength)
            }
            StrokeBehavior.SMOOTH_TAPER -> {
                if (t < taperLength) {
                    val st = t / taperLength
                    factor *= st * st * (3f - 2f * st)
                }
            }
            StrokeBehavior.FADE -> {
                if (t < taperLength) factor *= sqrt(t / taperLength)
            }
            StrokeBehavior.BLOB -> {
                if (t < taperLength * 0.5f) factor *= 1.3f
            }
            else -> {}
        }

        when (endBehavior) {
            StrokeBehavior.TAPER -> {
                if (t > 1f - taperLength) factor *= ((1f - t) / taperLength)
            }
            StrokeBehavior.SMOOTH_TAPER -> {
                if (t > 1f - taperLength) {
                    val st = (1f - t) / taperLength
                    factor *= st * st * (3f - 2f * st)
                }
            }
            StrokeBehavior.FADE -> {
                if (t > 1f - taperLength) factor *= sqrt((1f - t) / taperLength)
            }
            StrokeBehavior.SCATTER -> {
                if (t > 1f - taperLength) {
                    factor *= (1f - t) / taperLength
                    factor *= (0.5f + stable01(strokeId, pointIndex, 99) * 0.5f)
                }
            }
            else -> {}
        }

        return factor.coerceIn(0.05f, 2f)
    }

    private fun calculateVelocity(prev: StrokePoint, curr: StrokePoint): Float {
        val dx = curr.x - prev.x
        val dy = curr.y - prev.y
        val dt = max(1L, curr.timestamp - prev.timestamp)
        return hypot(dx, dy) / dt
    }

    private fun smoothPoints(points: List<StrokePoint>, smoothing: Float): List<StrokePoint> {
        if (points.size < 3) return points
        val result = mutableListOf<StrokePoint>()
        result.add(points[0])

        val weight = smoothing.coerceIn(0f, 0.9f)
        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]

            val smoothX = curr.x * (1f - weight) + (prev.x + next.x) * weight * 0.5f
            val smoothY = curr.y * (1f - weight) + (prev.y + next.y) * weight * 0.5f

            result.add(curr.copy(x = smoothX, y = smoothY))
        }
        result.add(points.last())
        return result
    }

    /** Two-pass smoothing for less jagged strokes on fast input. */
    private fun smoothPointsMultiPass(points: List<StrokePoint>, smoothing: Float): List<StrokePoint> {
        if (points.size < 3) return points
        val w = smoothing.coerceIn(0.2f, 0.9f)
        return smoothPoints(smoothPoints(points, w), w * 0.75f)
    }

    fun isPointNearStroke(stroke: Stroke, x: Float, y: Float, threshold: Float): Boolean {
        for (point in stroke.points) {
            val dist = hypot((point.x - x).toDouble(), (point.y - y).toDouble())
            if (dist < threshold + stroke.size) return true
        }
        return false
    }

    fun getStrokeBounds(stroke: Stroke): RectF {
        val bounds = stroke.getBounds()
        val padding = stroke.size
        return RectF(
            bounds[0] - padding, bounds[1] - padding,
            bounds[2] + padding, bounds[3] + padding
        )
    }

    /** Stable 0..1 — same output every frame for a given stroke + index. */
    private fun stable01(strokeId: String, index: Int, salt: Int = 0): Float {
        var h = strokeId.hashCode()
        h = 31 * h + index
        h = 31 * h + salt
        return ((h and 0x7fffffff) % 10000) / 10000f
    }

    private fun stableSigned(strokeId: String, index: Int, salt: Int = 0): Float =
        stable01(strokeId, index, salt) * 2f - 1f
}
