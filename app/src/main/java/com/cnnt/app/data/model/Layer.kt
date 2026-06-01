package com.cnnt.app.data.model

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Layer 1",
    val boardId: String = "",
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1.0f,
    val order: Int = 0,
    val strokes: MutableList<Stroke> = CopyOnWriteArrayList(),
    val objects: MutableList<SpatialObject> = CopyOnWriteArrayList()
)
