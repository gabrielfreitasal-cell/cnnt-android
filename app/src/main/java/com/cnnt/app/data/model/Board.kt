package com.cnnt.app.data.model

import java.util.UUID

data class Board(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Page 1",
    val notebookId: String = "",
    val order: Int = 0,
    val backgroundColor: Int = 0xFF1E1E1E.toInt(),
    val layers: MutableList<Layer> = mutableListOf(Layer()),
    val activeLayerIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val activeLayer: Layer get() = layers.getOrElse(activeLayerIndex) { layers.firstOrNull() ?: Layer() }
}
