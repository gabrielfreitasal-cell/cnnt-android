package com.cnnt.app.data.model

import java.util.UUID

sealed class BlockType(val key: String) {
    data object Image : BlockType("IMAGE")
    data object Markdown : BlockType("MARKDOWN")
    data object Flashcard : BlockType("FLASHCARD")
    data object InteractiveText : BlockType("INTERACTIVE_TEXT")
    data object Pdf : BlockType("PDF")
    data object Text : BlockType("TEXT")

    companion object {
        fun fromKey(key: String): BlockType = when (key.uppercase()) {
            Image.key -> Image
            Markdown.key -> Markdown
            Flashcard.key -> Flashcard
            InteractiveText.key -> InteractiveText
            Pdf.key -> Pdf
            else -> Text
        }
    }
}

data class ContentBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: BlockType,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val width: Float = 240f,
    val height: Float = 160f,
    val rotation: Float = 0f,
    val zIndex: Int = 0,
    val notebookId: String = "",
    val layerId: String = "",
    val contentJson: String = "",
    val content: BlockContent = BlockContent.TextNote(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

sealed class BlockContent {
    data class Image(
        val uri: String = "",
        val displayName: String = "",
        val persistable: Boolean = true
    ) : BlockContent()

    data class Markdown(
        val markdown: String = "",
        val previewMode: Boolean = true
    ) : BlockContent()

    data class Flashcard(
        val flashcardId: String = "",
        val previewText: String = "",
        val noteType: String = "basic"
    ) : BlockContent()

    data class InteractiveText(
        val question: String = "",
        val alternatives: List<InteractiveAlternative> = emptyList(),
        val multiple: Boolean = false,
        val explanation: String = "",
        val selectedIds: List<String> = emptyList(),
        val submitted: Boolean = false
    ) : BlockContent()

    data class Pdf(
        val uri: String = "",
        val displayName: String = "",
        val currentPage: Int = 0,
        val pageCount: Int = 0
    ) : BlockContent()

    data class TextNote(
        val text: String = "",
        val hint: String = "Escrever..."
    ) : BlockContent()
}

data class InteractiveAlternative(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val correct: Boolean = false
)