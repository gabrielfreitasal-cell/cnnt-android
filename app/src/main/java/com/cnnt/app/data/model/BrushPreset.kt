package com.cnnt.app.data.model

import java.util.UUID

data class BrushPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: BrushCategory,
    val baseSize: Float = 4f,
    val minSize: Float = 1f,
    val maxSize: Float = 20f,
    val opacity: Float = 1.0f,
    val pressureSensitivity: Float = 1.0f,
    val tiltSensitivity: Float = 0.5f,
    val smoothing: Float = 0.5f,
    val textureId: String? = null,
    val tipShape: TipShape = TipShape.ROUND,
    val startBehavior: StrokeBehavior = StrokeBehavior.TAPER,
    val endBehavior: StrokeBehavior = StrokeBehavior.TAPER,
    val velocitySensitivity: Float = 0.3f,
    val directionSensitivity: Float = 0f,
    val jitter: Float = 0f,
    val spacing: Float = 0.1f,
    val grain: Float = 0f
) {
    companion object {
        const val DEFAULT_PEN_ID = "brush_gel_pen"
        const val CHISEL_ID = "brush_chisel"
        const val CNNT_SPECIAL_ID = "brush_cnnt_special"

        fun defaultBrushes(): List<BrushPreset> = listOf(
            gelPen(), softPencil(), marker(), highlighter(),
            chiselCalligraphy(), cnntSpecial(), wetInk(),
            technicalPen(), grainBrush(), neonLaser(),
            sprayAirbrush(), experimentalWild()
        )

        fun gelPen() = BrushPreset(
            id = DEFAULT_PEN_ID, name = "Caneta Gel",
            category = BrushCategory.PEN, baseSize = 8f,
            pressureSensitivity = 0.8f, smoothing = 0.4f,
            tipShape = TipShape.ROUND,
            startBehavior = StrokeBehavior.SMOOTH_TAPER,
            endBehavior = StrokeBehavior.SMOOTH_TAPER
        )

        fun softPencil() = BrushPreset(
            id = "brush_soft_pencil", name = "Lápis Macio",
            category = BrushCategory.PENCIL, baseSize = 7f,
            pressureSensitivity = 1.2f, smoothing = 0.2f,
            tipShape = TipShape.ROUND, grain = 0.4f, opacity = 0.85f,
            startBehavior = StrokeBehavior.NONE,
            endBehavior = StrokeBehavior.FADE
        )

        fun marker() = BrushPreset(
            id = "brush_marker", name = "Marcador",
            category = BrushCategory.MARKER, baseSize = 14f,
            pressureSensitivity = 0.3f, smoothing = 0.6f,
            tipShape = TipShape.FLAT, opacity = 0.9f,
            directionSensitivity = 0.6f,
            startBehavior = StrokeBehavior.NONE,
            endBehavior = StrokeBehavior.NONE
        )

        fun highlighter() = BrushPreset(
            id = "brush_highlighter", name = "Marca-texto",
            category = BrushCategory.HIGHLIGHTER, baseSize = 28f,
            pressureSensitivity = 0.1f, smoothing = 0.8f,
            tipShape = TipShape.FLAT, opacity = 0.30f,
            directionSensitivity = 0.4f,
            startBehavior = StrokeBehavior.NONE,
            endBehavior = StrokeBehavior.NONE
        )

        fun chiselCalligraphy() = BrushPreset(
            id = CHISEL_ID, name = "Caligrafia",
            category = BrushCategory.CALLIGRAPHY, baseSize = 12f,
            pressureSensitivity = 1.0f, smoothing = 0.3f,
            tipShape = TipShape.CHISEL, directionSensitivity = 1.0f,
            tiltSensitivity = 0.8f,
            startBehavior = StrokeBehavior.TAPER,
            endBehavior = StrokeBehavior.TAPER
        )

        fun cnntSpecial() = BrushPreset(
            id = CNNT_SPECIAL_ID, name = "Pincel CNNT",
            category = BrushCategory.SPECIAL, baseSize = 12f,
            pressureSensitivity = 0.8f, smoothing = 0.3f,
            tipShape = TipShape.CHISEL, directionSensitivity = 0.85f,
            tiltSensitivity = 0.6f, velocitySensitivity = 0.3f,
            startBehavior = StrokeBehavior.SMOOTH_TAPER,
            endBehavior = StrokeBehavior.SMOOTH_TAPER
        )

        fun wetInk() = BrushPreset(
            id = "brush_wet_ink", name = "Tinta Úmida",
            category = BrushCategory.INK, baseSize = 10f,
            pressureSensitivity = 1.3f, smoothing = 0.6f,
            tipShape = TipShape.ROUND, velocitySensitivity = 0.7f,
            jitter = 0.1f,
            startBehavior = StrokeBehavior.BLOB,
            endBehavior = StrokeBehavior.FADE
        )

        fun technicalPen() = BrushPreset(
            id = "brush_technical_pen", name = "Caneta Técnica",
            category = BrushCategory.PEN, baseSize = 5f,
            pressureSensitivity = 0.0f, smoothing = 0.7f,
            tipShape = TipShape.ROUND,
            startBehavior = StrokeBehavior.NONE,
            endBehavior = StrokeBehavior.NONE
        )

        fun grainBrush() = BrushPreset(
            id = "brush_grain", name = "Granulado",
            category = BrushCategory.TEXTURE, baseSize = 16f,
            pressureSensitivity = 0.9f, smoothing = 0.3f,
            tipShape = TipShape.ROUND, grain = 0.8f, spacing = 0.05f,
            startBehavior = StrokeBehavior.NONE,
            endBehavior = StrokeBehavior.FADE
        )

        fun neonLaser() = BrushPreset(
            id = "brush_neon", name = "Neon",
            category = BrushCategory.EFFECT, baseSize = 8f,
            pressureSensitivity = 0.5f, smoothing = 0.5f,
            tipShape = TipShape.ROUND, opacity = 0.9f,
            startBehavior = StrokeBehavior.NONE,
            endBehavior = StrokeBehavior.NONE
        )

        fun sprayAirbrush() = BrushPreset(
            id = "brush_spray", name = "Spray",
            category = BrushCategory.SPRAY, baseSize = 25f,
            pressureSensitivity = 1.0f, smoothing = 0.1f,
            tipShape = TipShape.ROUND, jitter = 0.8f, spacing = 0.02f,
            opacity = 0.3f,
            startBehavior = StrokeBehavior.NONE,
            endBehavior = StrokeBehavior.NONE
        )

        fun experimentalWild() = BrushPreset(
            id = "brush_experimental", name = "Experimental",
            category = BrushCategory.EXPERIMENTAL, baseSize = 12f,
            pressureSensitivity = 1.5f, smoothing = 0.2f,
            tipShape = TipShape.ROUND, jitter = 0.3f,
            velocitySensitivity = 1.0f, directionSensitivity = 0.5f,
            grain = 0.3f,
            startBehavior = StrokeBehavior.BLOB,
            endBehavior = StrokeBehavior.SCATTER
        )
    }
}

enum class BrushCategory {
    PEN, PENCIL, MARKER, HIGHLIGHTER, CALLIGRAPHY,
    SPECIAL, INK, TEXTURE, EFFECT, SPRAY, EXPERIMENTAL
}

enum class TipShape {
    ROUND, FLAT, CHISEL, CUSTOM
}

enum class StrokeBehavior {
    NONE, TAPER, SMOOTH_TAPER, FADE, BLOB, SCATTER
}
