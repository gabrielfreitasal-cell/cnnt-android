package com.cnnt.app.data.model

import java.util.UUID

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: MutableList<StrokePoint> = mutableListOf(),
    val brushId: String = BrushPreset.DEFAULT_PEN_ID,
    val color: Int = 0xFFFFFFFF.toInt(),
    val size: Float = 4f,
    val opacity: Float = 1.0f,
    val layerId: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun addPoint(point: StrokePoint) {
        points.add(point)
    }

    fun isEmpty(): Boolean = points.isEmpty()

    fun getBounds(): FloatArray {
        if (points.isEmpty()) return floatArrayOf(0f, 0f, 0f, 0f)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }
}
