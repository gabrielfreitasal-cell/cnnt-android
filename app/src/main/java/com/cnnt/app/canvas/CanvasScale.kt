package com.cnnt.app.canvas

/**
 * Zoom, brush caliber (mm), and export sizing — aligned with Starnote-style workflow.
 *
 * - View zoom: 100% (1.0x) … 1600% (16.0x)
 * - Brush sizes are defined in mm; converted to canvas pixels at the current zoom
 * - Export raster target: 6500 × 4800 px
 */
object CanvasScale {

    const val MIN_ZOOM_PERCENT = 100f
    const val MAX_ZOOM_PERCENT = 1600f
    const val MIN_ZOOM_FACTOR = 1f
    const val MAX_ZOOM_FACTOR = 16f

    const val EXPORT_WIDTH_PX = 6500
    const val EXPORT_HEIGHT_PX = 4800

    /** Logical page width at 100% zoom (mm), used for mm ↔ pixel mapping. */
    const val LOGICAL_PAGE_WIDTH_MM = 210f

    val pixelsPerMmAtBaseZoom: Float =
        EXPORT_WIDTH_PX / LOGICAL_PAGE_WIDTH_MM

    fun zoomPercentToFactor(percent: Float): Float =
        (percent / 100f).coerceIn(MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR)

    fun zoomFactorToPercent(factor: Float): Float =
        (factor * 100f).coerceIn(MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT)

    fun mmToCanvasPixels(mm: Float): Float = mm * pixelsPerMmAtBaseZoom

    fun canvasPixelsToMm(pixels: Float): Float = pixels / pixelsPerMmAtBaseZoom

    /** Canvas-space stroke width so the line looks constant on screen as zoom changes. */
    fun brushSizeAtZoom(sizeMm: Float, zoomFactor: Float): Float {
        val z = zoomFactor.coerceIn(MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR)
        return mmToCanvasPixels(sizeMm) / z
    }

    /** Default gel pen ~0.5 mm */
    const val DEFAULT_BRUSH_MM = 0.5f
    const val MIN_BRUSH_MM = 0.2f
    const val MAX_BRUSH_MM = 8f
}
