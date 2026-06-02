package com.cnnt.app.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cnnt.app.R
import com.cnnt.app.data.model.Flashcard
import com.cnnt.app.ui.MainViewModel

class FlashcardDialog(
    context: Context,
    private val viewModel: MainViewModel
) : Dialog(context, R.style.Theme_CNNT_Dialog) {

    private enum class EditorMode { BASIC, CLOZE }

    private lateinit var adapter: FlashcardDashboardAdapter
    private var currentCards: MutableList<Flashcard> = mutableListOf()
    private var editingCardId: String? = null
    private var editorMode: EditorMode = EditorMode.BASIC

    private lateinit var statsView: TextView
    private lateinit var editorTitle: TextView
    private lateinit var editorHint: TextView
    private lateinit var editorSection: View
    private lateinit var inputFront: EditText
    private lateinit var inputBack: EditText
    private lateinit var inputTags: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_flashcard_dashboard)

        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.88).toInt()
        )

        statsView = findViewById(R.id.dashboardStats)
        editorTitle = findViewById(R.id.editorTitle)
        editorHint = findViewById(R.id.editorHint)
        editorSection = findViewById(R.id.editorSection)
        inputFront = findViewById(R.id.inputFront)
        inputBack = findViewById(R.id.inputBack)
        inputTags = findViewById(R.id.inputTags)

        val dueOnly = findViewById<CheckBox>(R.id.checkDueOnly)
        val btnAddBasic = findViewById<Button>(R.id.btnAddBasic)
        val btnAddCloze = findViewById<Button>(R.id.btnAddCloze)
        val btnStartReview = findViewById<Button>(R.id.btnStartReview)
        val btnCancelEdit = findViewById<Button>(R.id.btnCancelEdit)
        val btnSaveCard = findViewById<Button>(R.id.btnSaveCard)
        val recycler = findViewById<RecyclerView>(R.id.flashcardRecycler)

        adapter = FlashcardDashboardAdapter(
            flashcardManager = viewModel.flashcardManager,
            onEdit = { card -> startEditing(card) },
            onDelete = { card -> deleteCard(card) }
        )

        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter

        refreshCards()

        btnAddBasic.setOnClickListener {
            startCreate(EditorMode.BASIC)
        }

        btnAddCloze.setOnClickListener {
            startCreate(EditorMode.CLOZE)
        }

        btnStartReview.setOnClickListener {
            val cards = if (dueOnly.isChecked) {
                currentCards.filter { it.nextReview <= System.currentTimeMillis() }
            } else {
                currentCards
            }
            if (cards.isEmpty()) {
                Toast.makeText(context, "Nenhum flashcard para revisar.", Toast.LENGTH_SHORT).show()
            } else {
                FlashcardReviewDialog(context, viewModel, cards).apply {
                    setOnDismissListener { refreshCards() }
                }.show()
            }
        }

        btnCancelEdit.setOnClickListener {
            resetEditor()
        }

        btnSaveCard.setOnClickListener {
            saveCard()
        }
    }

    private fun refreshCards() {
        currentCards = viewModel.flashcards.value.sortedBy { it.nextReview }.toMutableList()
        adapter.submit(currentCards)
        val dueCount = currentCards.count { it.nextReview <= System.currentTimeMillis() }
        statsView.text = "${currentCards.size} cards • $dueCount vencidos"
    }

    private fun startCreate(mode: EditorMode) {
        editingCardId = null
        editorMode = mode
        editorSection.visibility = View.VISIBLE
        editorTitle.text = if (mode == EditorMode.BASIC) "Novo card basic" else "Novo card cloze"
        editorHint.text = if (mode == EditorMode.BASIC) {
            "Basic: preencha frente e verso."
        } else {
            "Cloze: use {{c1::texto}} e opcionalmente {{c2::texto}}."
        }
        inputFront.hint = if (mode == EditorMode.BASIC) "Frente" else "Texto com cloze"
        inputBack.visibility = if (mode == EditorMode.BASIC) View.VISIBLE else View.GONE
        inputBack.setText("")
        inputFront.setText("")
        inputTags.setText("")
    }

    private fun startEditing(card: Flashcard) {
        editingCardId = card.id
        editorMode = if (viewModel.flashcardManager.isCloze(card)) EditorMode.CLOZE else EditorMode.BASIC
        editorSection.visibility = View.VISIBLE
        editorTitle.text = "Editar flashcard"
        editorHint.text = if (editorMode == EditorMode.BASIC) {
            "Basic: frente/verso."
        } else {
            "Cloze: até 2 oclusões."
        }
        inputFront.hint = if (editorMode == EditorMode.BASIC) "Frente" else "Texto com cloze"
        inputBack.visibility = if (editorMode == EditorMode.BASIC) View.VISIBLE else View.GONE
        inputFront.setText(card.front)
        inputBack.setText(if (editorMode == EditorMode.BASIC) card.back else "")
        inputTags.setText(card.tags.filterNot { it.startsWith("type:") || it.startsWith("origin:") }.joinToString(", "))
    }

    private fun saveCard() {
        val front = inputFront.text?.toString()?.trim().orEmpty()
        val back = inputBack.text?.toString()?.trim().orEmpty()
        val userTags = inputTags.text?.toString()
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        if (front.isBlank()) {
            Toast.makeText(context, "Preencha o conteúdo principal.", Toast.LENGTH_SHORT).show()
            return
        }

        if (editorMode == EditorMode.BASIC && back.isBlank()) {
            Toast.makeText(context, "Preencha o verso do card basic.", Toast.LENGTH_SHORT).show()
            return
        }

        val existing = editingCardId?.let { id -> currentCards.firstOrNull { it.id == id } }

        val created = try {
            if (editorMode == EditorMode.BASIC) {
                viewModel.flashcardManager.createBasicFlashcard(
                    front = front,
                    back = back,
                    tags = mergePersistentTags(existing, userTags),
                    linkedRegionId = existing?.linkedRegionId,
                    boardId = existing?.boardId,
                    origin = existing?.let { detectOrigin(it) }
                )
            } else {
                viewModel.flashcardManager.createClozeFlashcard(
                    text = front,
                    tags = mergePersistentTags(existing, userTags),
                    linkedRegionId = existing?.linkedRegionId,
                    boardId = existing?.boardId,
                    origin = existing?.let { detectOrigin(it) }
                )
            }
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, e.message ?: "Não foi possível salvar.", Toast.LENGTH_SHORT).show()
            return
        }

        val toSave = if (existing != null) {
            created.copy(
                id = existing.id,
                reviewHistory = existing.reviewHistory,
                difficulty = existing.difficulty,
                nextReview = existing.nextReview,
                createdAt = existing.createdAt
            )
        } else {
            created
        }

        viewModel.addFlashcard(toSave)
        viewModel.syncFlashcardBlockPreview(toSave)

        currentCards.removeAll { it.id == toSave.id }
        currentCards.add(toSave)
        currentCards.sortBy { it.nextReview }
        adapter.submit(currentCards)
        val dueCount = currentCards.count { it.nextReview <= System.currentTimeMillis() }
        statsView.text = "${currentCards.size} cards • $dueCount vencidos"
        resetEditor()
        Toast.makeText(context, "Flashcard salvo.", Toast.LENGTH_SHORT).show()
    }

    private fun deleteCard(card: Flashcard) {
        viewModel.deleteFlashcard(card.id)
        if (card.linkedRegionId != null && detectOrigin(card) == "canvas-block") {
            viewModel.removeCanvasLinkedFlashcardBlock(card.linkedRegionId)
        }
        currentCards.removeAll { it.id == card.id }
        adapter.submit(currentCards)
        val dueCount = currentCards.count { it.nextReview <= System.currentTimeMillis() }
        statsView.text = "${currentCards.size} cards • $dueCount vencidos"
        if (editingCardId == card.id) {
            resetEditor()
        }
        Toast.makeText(context, "Flashcard excluído.", Toast.LENGTH_SHORT).show()
    }

    private fun resetEditor() {
        editingCardId = null
        editorSection.visibility = View.GONE
        inputFront.setText("")
        inputBack.setText("")
        inputTags.setText("")
    }

    private fun mergePersistentTags(existing: Flashcard?, userTags: List<String>): List<String> {
        val persistent = existing?.tags?.filter {
            it.startsWith("origin:") || it.startsWith("type:")
        }.orEmpty()
        return (userTags + persistent).distinct()
    }

    private fun detectOrigin(card: Flashcard): String? {
        return card.tags.firstOrNull { it.startsWith("origin:") }?.substringAfter("origin:")
    }
}