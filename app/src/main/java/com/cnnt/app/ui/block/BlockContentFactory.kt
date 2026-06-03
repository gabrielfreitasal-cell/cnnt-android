package com.cnnt.app.ui.block

import com.cnnt.app.data.model.BlockContent
import com.cnnt.app.data.model.BlockType
import com.cnnt.app.data.model.ContentBlock

object BlockContentFactory {
    fun create(
        type: BlockType,
        notebookId: String,
        layerId: String,
        x: Float,
        y: Float,
        zIndex: Int
    ): ContentBlock {
        val defaultSize = when (type) {
            BlockType.Image -> 280f to 220f
            BlockType.Markdown -> 320f to 240f
            BlockType.Flashcard -> 280f to 180f
            BlockType.InteractiveText -> 320f to 240f
            BlockType.Pdf -> 320f to 260f
            BlockType.Text -> 240f to 140f
        }
        val content = when (type) {
            BlockType.Image -> BlockContent.Image()
            BlockType.Markdown -> BlockContent.Markdown("# Novo bloco\n\n- toque duas vezes para editar")
            BlockType.Flashcard -> BlockContent.Flashcard(previewText = "Novo flashcard")
            BlockType.InteractiveText -> BlockContent.InteractiveText(
                question = "Nova questão",
                alternatives = listOf(
                    com.cnnt.app.data.model.InteractiveAlternative(text = "Alternativa A"),
                    com.cnnt.app.data.model.InteractiveAlternative(text = "Alternativa B", correct = true)
                ),
                explanation = "Explique a resposta aqui."
            )
            BlockType.Pdf -> BlockContent.Pdf()
            BlockType.Text -> BlockContent.TextNote()
        }
        return ContentBlock(
            type = type,
            posX = x,
            posY = y,
            width = defaultSize.first,
            height = defaultSize.second,
            zIndex = zIndex,
            notebookId = notebookId,
            layerId = layerId,
            content = content
        )
    }
}