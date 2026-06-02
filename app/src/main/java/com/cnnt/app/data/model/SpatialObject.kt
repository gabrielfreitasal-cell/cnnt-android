package com.cnnt.app.data.model

import java.util.UUID

data class SpatialObject(
    val id: String = UUID.randomUUID().toString(),
    val type: SpatialObjectType,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 200f,
    val height: Float = 150f,
    val rotation: Float = 0f,
    val zIndex: Int = 0,
    val locked: Boolean = false,
    val groupId: String? = null,
    val content: ObjectContent = ObjectContent.Empty,
    val style: ObjectStyle = ObjectStyle(),
    val layerId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class SpatialObjectType {
    TEXT, CHECKLIST, IMAGE, PDF, LINK, FILE, DRAWING_REGION, FLASHCARD, GROUP, HANDWRITING
}

sealed class ObjectContent {
    object Empty : ObjectContent()
    data class Text(val text: String, val fontSize: Float = 14f, val fontColor: Int = 0xFFFFFFFF.toInt()) : ObjectContent()
    data class Checklist(val items: List<ChecklistItem>) : ObjectContent()
    data class Image(val filePath: String, val originalWidth: Int = 0, val originalHeight: Int = 0) : ObjectContent()
    data class Pdf(val filePath: String, val currentPage: Int = 0, val totalPages: Int = 0) : ObjectContent()
    data class Link(val url: String, val title: String = "", val thumbnail: String? = null) : ObjectContent()
    data class File(val filePath: String, val fileName: String, val mimeType: String = "") : ObjectContent()
    data class DrawingRegion(val strokes: List<Stroke> = emptyList()) : ObjectContent()
    data class FlashcardContent(
        val flashcardId: String = "",
        val flashcardIds: List<String> = emptyList(),
        val previewText: String = "",
        val noteType: String = "basic"
    ) : ObjectContent()
    data class Group(val childIds: List<String>) : ObjectContent()
    data class Handwriting(
        val recognizedText: String = "",
        val lines: List<String> = emptyList(),
        val fontSize: Float = 16f,
        val fontColor: Int = 0xFFFFFFFF.toInt(),
        val isRecognizing: Boolean = false
    ) : ObjectContent()
}

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val checked: Boolean = false
)

data class ObjectStyle(
    val backgroundColor: Int = 0x00000000,
    val borderColor: Int = 0x44FFFFFF,
    val borderWidth: Float = 1f,
    val cornerRadius: Float = 8f,
    val shadowEnabled: Boolean = false,
    val padding: Float = 8f
)
