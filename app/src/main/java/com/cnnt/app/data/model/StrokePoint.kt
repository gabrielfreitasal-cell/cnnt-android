package com.cnnt.app.data.model

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val orientation: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val toolType: Int = TOOL_TYPE_STYLUS
) {
    companion object {
        const val TOOL_TYPE_FINGER = 1
        const val TOOL_TYPE_STYLUS = 2
        const val TOOL_TYPE_ERASER = 4
    }
}
