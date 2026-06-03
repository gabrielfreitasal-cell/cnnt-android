package com.cnnt.app.data.model

import java.util.UUID

data class LinkEdge(
    val id: String = UUID.randomUUID().toString(),
    val sourceBlockId: String,
    val targetBlockId: String,
    val label: String = "",
    val style: LinkStyle = LinkStyle(),
    val createdAt: Long = System.currentTimeMillis()
)

data class LinkStyle(
    val strokeColor: Int = 0xFF88CCFF.toInt(),
    val strokeWidth: Float = 3f,
    val dashed: Boolean = false
)