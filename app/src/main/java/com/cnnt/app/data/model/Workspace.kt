package com.cnnt.app.data.model

import java.util.UUID

data class Workspace(
    val id: String = UUID.randomUUID().toString(),
    val notebooks: MutableList<Notebook> = mutableListOf(),
    val brushPresets: List<BrushPreset> = BrushPreset.defaultBrushes(),
    val palettes: MutableList<ColorPalette> = mutableListOf(ColorPalette.defaultPalette()),
    val settings: AppSettings = AppSettings(),
    val createdAt: Long = System.currentTimeMillis()
)

data class ColorPalette(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colors: List<Int>,
    val isDefault: Boolean = false
) {
    companion object {
        fun defaultPalette() = ColorPalette(
            name = "Padrão",
            isDefault = true,
            colors = listOf(
                0xFFFFFFFF.toInt(), 0xFFE0E0E0.toInt(), 0xFF9E9E9E.toInt(),
                0xFF000000.toInt(), 0xFFFF1744.toInt(), 0xFFFF9100.toInt(),
                0xFFFFEA00.toInt(), 0xFF00E676.toInt(), 0xFF00B0FF.toInt(),
                0xFF651FFF.toInt(), 0xFFFF4081.toInt(), 0xFF76FF03.toInt(),
                0xFF18FFFF.toInt(), 0xFFFFAB40.toInt(), 0xFFFF6E40.toInt(),
                0xFF7C4DFF.toInt(), 0xFFB388FF.toInt(), 0xFF80D8FF.toInt(),
                0xFFA7FFEB.toInt(), 0xFFF4FF81.toInt(), 0xFFFFE57F.toInt(),
                0xFFFF8A80.toInt(), 0xFFEA80FC.toInt(), 0xFF8C9EFF.toInt()
            )
        )

        fun studyPalette() = ColorPalette(
            name = "Estudo",
            colors = listOf(
                0xFFFFEB3B.toInt(), 0xFF4CAF50.toInt(), 0xFF2196F3.toInt(),
                0xFFFF5722.toInt(), 0xFF9C27B0.toInt(), 0xFFE91E63.toInt(),
                0xFF00BCD4.toInt(), 0xFF8BC34A.toInt(), 0xFFFF9800.toInt(),
                0xFF673AB7.toInt(), 0xFF03A9F4.toInt(), 0xFFCDDC39.toInt()
            )
        )

        fun darkPalette() = ColorPalette(
            name = "Dark",
            colors = listOf(
                0xFFE8EAF6.toInt(), 0xFFC5CAE9.toInt(), 0xFF9FA8DA.toInt(),
                0xFF7986CB.toInt(), 0xFF5C6BC0.toInt(), 0xFF3F51B5.toInt(),
                0xFFB0BEC5.toInt(), 0xFF78909C.toInt(), 0xFF546E7A.toInt(),
                0xFF37474F.toInt(), 0xFF263238.toInt(), 0xFF1A237E.toInt()
            )
        )
    }
}

data class AppSettings(
    val darkMode: Boolean = true,
    val defaultBrushId: String = BrushPreset.DEFAULT_PEN_ID,
    val defaultColor: Int = 0xFFFFFFFF.toInt(),
    val palmRejection: Boolean = true,
    val fingerDrawing: Boolean = false,
    val autoSaveInterval: Long = 5000,
    val showMinimap: Boolean = false,
    val canvasBackgroundColor: Int = 0xFF1E1E1E.toInt()
)
