package com.cnnt.app.data.model

import java.util.UUID

data class Notebook(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Notebook",
    val coverColor: Int = 0xFF2196F3.toInt(),
    val boards: MutableList<Board> = mutableListOf(Board()),
    val activeBoardIndex: Int = 0,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val activeBoard: Board get() = boards[activeBoardIndex]
}
