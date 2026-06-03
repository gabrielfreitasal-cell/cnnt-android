package com.cnnt.app.ui.block

import androidx.annotation.DrawableRes
import com.cnnt.app.R
import com.cnnt.app.data.model.BlockType

data class SidebarBlockType(
    val type: BlockType,
    val label: String,
    val category: String,
    val description: String,
    @DrawableRes val iconRes: Int
)

object SidebarBlockCatalog {
    val items = listOf(
        SidebarBlockType(BlockType.Text, "Texto", "Anotações", "Nota rápida com salvamento automático", R.drawable.ic_ocr),
        SidebarBlockType(BlockType.Markdown, "Markdown", "Anotações", "Bloco rico com preview e listas", R.drawable.ic_select),
        SidebarBlockType(BlockType.Flashcard, "Flashcard", "Estudo", "Card Basic/Cloze no canvas", R.drawable.ic_flashcard),
        SidebarBlockType(BlockType.InteractiveText, "Questão interativa", "Estudo", "Múltipla escolha com feedback imediato", R.drawable.ic_focus),
        SidebarBlockType(BlockType.Image, "Imagem", "Mídia", "Importa imagem com ajuste automático", R.drawable.ic_export),
        SidebarBlockType(BlockType.Pdf, "PDF", "Mídia", "Documento paginado com navegação", R.drawable.ic_redo)
    )
}