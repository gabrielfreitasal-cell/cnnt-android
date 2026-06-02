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

class FlashcardReviewDialog(
    context: Context,
    private val viewModel: MainViewModel,
    private val reviewCards: List<Flashcard>
) : Dialog(context, R.style.Theme_CNNT_Dialog) {

    private var currentIndex = 0
    private var showingFront = true
    private val cards = reviewCards.toMutableList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_flashcard)

        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val cardText = findViewById<TextView>(R.id.cardText)
        val cardCount = findViewById<TextView>(R.id.cardCount)
        val btnFlip = findViewById<Button>(R.id.btnFlip)
        val btnAgain = findViewById<Button>(R.id.btnAgain)
        val btnHard = findViewById<Button>(R.id.btnHard)
        val btnGood = findViewById<Button>(R.id.btnGood)
        val btnEasy = findViewById<Button>(R.id.btnEasy)
        val btnCreate = findViewById<Button>(R.id.btnCreate)
        val reviewButtons = findViewById<View>(R.id.reviewButtons)
        val createSection = findViewById<View>(R.id.createSection)
        val inputFront = findViewById<EditText>(R.id.inputFront)
        val inputBack = findViewById<EditText>(R.id.inputBack)
        val inputTags = findViewById<EditText>(R.id.inputTags)

        btnCreate?.visibility = View.GONE
        createSection?.visibility = View.GONE
        inputFront?.visibility = View.GONE
        inputBack?.visibility = View.GONE
        inputTags?.visibility = View.GONE

        if (cards.isEmpty()) {
            cardText?.text = "Nenhum card disponível para revisão."
            cardCount?.text = "0 / 0"
            btnFlip?.isEnabled = false
            reviewButtons?.visibility = View.GONE
            return
        }

        showCard(cardText, cardCount)

        btnFlip?.setOnClickListener {
            showingFront = !showingFront
            showCard(cardText, cardCount)
            reviewButtons?.visibility = if (showingFront) View.GONE else View.VISIBLE
        }

        btnAgain?.setOnClickListener { reviewAndNext(ReviewResult.AGAIN, cardText, cardCount, reviewButtons) }
        btnHard?.setOnClickListener { reviewAndNext(ReviewResult.HARD, cardText, cardCount, reviewButtons) }
        btnGood?.setOnClickListener { reviewAndNext(ReviewResult.GOOD, cardText, cardCount, reviewButtons) }
        btnEasy?.setOnClickListener { reviewAndNext(ReviewResult.EASY, cardText, cardCount, reviewButtons) }
    }

    private fun showCard(cardText: TextView?, cardCount: TextView?) {
        if (cards.isEmpty()) return
        val card = cards[currentIndex]
        cardText?.text = if (showingFront) {
            viewModel.flashcardManager.renderFront(card)
        } else {
            viewModel.flashcardManager.renderBack(card)
        }
        cardCount?.text = "${currentIndex + 1} / ${cards.size}"
    }

    private fun reviewAndNext(
        result: ReviewResult,
        cardText: TextView?,
        cardCount: TextView?,
        reviewButtons: View?
    ) {
        if (cards.isEmpty()) return
        val currentCard = cards[currentIndex]
        val updated = viewModel.flashcardManager.reviewFlashcard(currentCard, result)
        viewModel.addFlashcard(updated)
        cards[currentIndex] = updated

        if (cards.size == 1) {
            showingFront = true
            showCard(cardText, cardCount)
            reviewButtons?.visibility = View.GONE
            Toast.makeText(context, "Rodada concluída.", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        currentIndex = (currentIndex + 1) % cards.size
        showingFront = true
        showCard(cardText, cardCount)
        reviewButtons?.visibility = View.GONE
    }
}