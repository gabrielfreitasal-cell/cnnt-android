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
import com.cnnt.app.data.model.ReviewResult
import com.cnnt.app.ui.MainViewModel

class FlashcardDialog(
    context: Context,
    private val viewModel: MainViewModel
) : Dialog(context, R.style.Theme_CNNT_Dialog) {

    private var currentIndex = 0
    private var showingFront = true
    private var currentCards: List<Flashcard> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_flashcard)

        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        currentCards = viewModel.flashcards.value

        val cardText = findViewById<TextView>(R.id.cardText)
        val cardCount = findViewById<TextView>(R.id.cardCount)
        val btnFlip = findViewById<Button>(R.id.btnFlip)
        val btnAgain = findViewById<Button>(R.id.btnAgain)
        val btnHard = findViewById<Button>(R.id.btnHard)
        val btnGood = findViewById<Button>(R.id.btnGood)
        val btnEasy = findViewById<Button>(R.id.btnEasy)
        val btnCreate = findViewById<Button>(R.id.btnCreate)
        val reviewButtons = findViewById<View>(R.id.reviewButtons)

        val inputFront = findViewById<EditText>(R.id.inputFront)
        val inputBack = findViewById<EditText>(R.id.inputBack)
        val inputTags = findViewById<EditText>(R.id.inputTags)
        val btnSave = findViewById<Button>(R.id.btnSaveCard)
        val createSection = findViewById<View>(R.id.createSection)

        // Review mode
        if (currentCards.isNotEmpty()) {
            showCard(cardText, cardCount)
        } else {
            cardText?.text = "Nenhum flashcard ainda.\nCrie um novo!"
            cardCount?.text = "0 vencidos • 0 no total"
            reviewButtons?.visibility = View.GONE
        }

        btnFlip?.setOnClickListener {
            showingFront = !showingFront
            showCard(cardText, cardCount)
            reviewButtons?.visibility = if (showingFront) View.GONE else View.VISIBLE
        }

        btnAgain?.setOnClickListener { reviewAndNext(ReviewResult.AGAIN, cardText, cardCount, reviewButtons) }
        btnHard?.setOnClickListener { reviewAndNext(ReviewResult.HARD, cardText, cardCount, reviewButtons) }
        btnGood?.setOnClickListener { reviewAndNext(ReviewResult.GOOD, cardText, cardCount, reviewButtons) }
        btnEasy?.setOnClickListener { reviewAndNext(ReviewResult.EASY, cardText, cardCount, reviewButtons) }

        btnCreate?.setOnClickListener {
            createSection?.visibility = View.VISIBLE
        }

        btnSave?.setOnClickListener {
            val front = inputFront?.text?.toString() ?: ""
            val back = inputBack?.text?.toString() ?: ""
            val tags = inputTags?.text?.toString()?.split(",")?.map { it.trim() } ?: emptyList()
            if (front.isNotEmpty() && back.isNotEmpty()) {
                val flashcard = viewModel.flashcardManager.createFlashcard(front, back, tags)
                viewModel.addFlashcard(flashcard)
                currentCards = viewModel.flashcards.value
                currentIndex = currentCards.lastIndex.coerceAtLeast(0)
                showingFront = true
                showCard(cardText, cardCount)
                Toast.makeText(context, "Flashcard salvo!", Toast.LENGTH_SHORT).show()
                inputFront?.text?.clear()
                inputBack?.text?.clear()
                inputTags?.text?.clear()
                createSection?.visibility = View.GONE
            }
        }
    }

    private fun showCard(cardText: TextView?, cardCount: TextView?) {
        if (currentCards.isEmpty()) {
            cardText?.text = "Nenhum flashcard ainda.\nCrie um novo!"
            cardCount?.text = "0 vencidos • 0 no total"
            return
        }
        val card = currentCards[currentIndex]
        cardText?.text = if (showingFront) card.front else card.back
        val dueCount = viewModel.flashcardManager.getDueCount(currentCards)
        cardCount?.text = "${currentIndex + 1} / ${currentCards.size} • $dueCount vencidos"
    }

    private fun reviewAndNext(result: ReviewResult, cardText: TextView?, cardCount: TextView?, reviewButtons: View?) {
        if (currentCards.isEmpty()) return
        val card = currentCards[currentIndex]
        val updated = viewModel.flashcardManager.reviewFlashcard(card, result)
        viewModel.addFlashcard(updated)
        currentCards = currentCards.toMutableList().apply { set(currentIndex, updated) }
        currentIndex = (currentIndex + 1) % currentCards.size
        showingFront = true
        showCard(cardText, cardCount)
        reviewButtons?.visibility = View.GONE
    }
}
