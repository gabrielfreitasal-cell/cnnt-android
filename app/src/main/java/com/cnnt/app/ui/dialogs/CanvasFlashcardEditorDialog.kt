package com.cnnt.app.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.cnnt.app.R
import com.cnnt.app.data.model.Flashcard
import com.cnnt.app.data.model.SpatialObject
import com.cnnt.app.ui.MainViewModel

class CanvasFlashcardEditorDialog(
    context: Context,
    private val viewModel: MainViewModel,
    private val block: SpatialObject
) : Dialog(context, R.style.Theme_CNNT_Dialog) {

    private enum class Mode { BASIC, CLOZE }

    private var currentMode = Mode.BASIC
    private var existingCard: Flashcard? = null

    private lateinit var subtitle: TextView
    private lateinit var inputFront: EditText
    private lateinit var inputBack: EditText
    private lateinit var inputTags: EditText
    private lateinit var btnModeBasic: Button
    private lateinit var btnModeCloze: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_canvas_flashcard_editor)

        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        subtitle = findViewById(R.id.editorSubtitle)
        inputFront = findViewById(R.id.inputFront)
        inputBack = findViewById(R.id.inputBack)
        inputTags = findViewById(R.id.inputTags)
        btnModeBasic = findViewById(R.id.btnModeBasic)
        btnModeCloze = findViewById(R.id.btnModeCloze)
        val btnDelete = findViewById<Button>(R.id.btnDelete)
        val btnSave = findViewById<Button>(R.id.btnSave)

        existingCard = viewModel.flashcards.value.firstOrNull { it.linkedRegionId == block.id }
        currentMode = if (existingCard?.let { viewModel.flashcardManager.isCloze(it) } == true) {
            Mode.CLOZE
        } else {
            Mode.BASIC
        }

        populateFields()
        applyModeUi()

        btnModeBasic.setOnClickListener {
            currentMode = Mode.BASIC
            applyModeUi()
        }

        btnModeCloze.setOnClickListener {
            currentMode = Mode.CLOZE
            applyModeUi()
        }

        btnDelete.setOnClickListener {
            existingCard?.let { viewModel.deleteFlashcard(it.id) }
            viewModel.removeCanvasLinkedFlashcardBlock(block.id)
            Toast.makeText(context, "Bloco removido.", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        btnSave.setOnClickListener {
            save()
        }
    }

    private fun populateFields() {
        val card = existingCard ?: return
        inputFront.setText(card.front)
        inputBack.setText(if (viewModel.flashcardManager.isCloze(card)) "" else card.back)
        inputTags.setText(
            card.tags.filterNot {
                it.startsWith("type:") || it.startsWith("origin:")
            }.joinToString(", ")
        )
    }

    private fun applyModeUi() {
        val isBasic = currentMode == Mode.BASIC
        btnModeBasic.alpha = if (isBasic) 1f else 0.6f
        btnModeCloze.alpha = if (isBasic) 0.6f else 1f
        inputFront.hint = if (isBasic) "Frente" else "Texto com {{c1::oclusão}}"
        inputBack.visibility = if (isBasic) View.VISIBLE else View.GONE
        subtitle.text = if (isBasic) {
            "Basic: frente e verso."
        } else {
            "Cloze: máximo de 2 oclusões por bloco."
        }
    }

    private fun save() {
        val front = inputFront.text?.toString()?.trim().orEmpty()
        val back = inputBack.text?.toString()?.trim().orEmpty()
        val tags = inputTags.text?.toString()
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        if (front.isBlank()) {
            Toast.makeText(context, "Preencha o conteúdo do card.", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentMode == Mode.BASIC && back.isBlank()) {
            Toast.makeText(context, "Preencha o verso do basic.", Toast.LENGTH_SHORT).show()
            return
        }

        val boardId = viewModel.currentBoard.value?.id
        val created = try {
            if (currentMode == Mode.BASIC) {
                viewModel.flashcardManager.createBasicFlashcard(
                    front = front,
                    back = back,
                    tags = tags,
                    linkedRegionId = block.id,
                    boardId = boardId,
                    origin = "canvas-block"
                )
            } else {
                viewModel.flashcardManager.createClozeFlashcard(
                    text = front,
                    tags = tags,
                    linkedRegionId = block.id,
                    boardId = boardId,
                    origin = "canvas-block"
                )
            }
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, e.message ?: "Não foi possível salvar.", Toast.LENGTH_SHORT).show()
            return
        }

        val toSave = existingCard?.let {
            created.copy(
                id = it.id,
                difficulty = it.difficulty,
                reviewHistory = it.reviewHistory,
                nextReview = it.nextReview,
                createdAt = it.createdAt
            )
        } ?: created

        viewModel.addFlashcard(toSave)
        viewModel.syncFlashcardBlockPreview(toSave)
        Toast.makeText(context, "Flashcard do canvas salvo.", Toast.LENGTH_SHORT).show()
        dismiss()
    }
}