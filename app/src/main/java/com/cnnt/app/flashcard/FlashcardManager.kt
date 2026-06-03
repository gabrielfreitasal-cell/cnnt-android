package com.cnnt.app.flashcard

import com.cnnt.app.data.model.Difficulty
import com.cnnt.app.data.model.Flashcard
import com.cnnt.app.data.model.ReviewEntry
import com.cnnt.app.data.model.ReviewResult

class FlashcardManager {
    private val clozeRegex = Regex("\\{\\{c([12])::(.+?)\\}\\}")

    fun createFlashcard(front: String, back: String, tags: List<String> = emptyList()): Flashcard {
        return createBasicFlashcard(front, back, tags)
    }

    fun createBasicFlashcard(
        front: String,
        back: String,
        tags: List<String> = emptyList(),
        linkedRegionId: String? = null,
        boardId: String? = null,
        origin: String? = null
    ): Flashcard {
        return Flashcard(
            front = front.trim(),
            back = back.trim(),
            tags = normalizeTags(tags, "type:basic", origin?.let { "origin:$it" }),
            linkedRegionId = linkedRegionId,
            boardId = boardId
        )
    }

    fun createClozeFlashcard(
        text: String,
        tags: List<String> = emptyList(),
        linkedRegionId: String? = null,
        boardId: String? = null,
        origin: String? = null
    ): Flashcard {
        val normalizedText = text.trim()
        val matches = clozeRegex.findAll(normalizedText).toList()
        require(matches.isNotEmpty()) { "Informe ao menos uma oclusão no formato {{c1::texto}}" }
        require(matches.size <= 2) { "Máximo de 2 oclusões por card cloze" }
        val answers = matches.joinToString(separator = "\n") { it.groupValues[2] }
        return Flashcard(
            front = normalizedText,
            back = answers,
            tags = normalizeTags(tags, "type:cloze", origin?.let { "origin:$it" }),
            linkedRegionId = linkedRegionId,
            boardId = boardId
        )
    }

    fun createFromSelection(selectedText: String, tags: List<String> = emptyList()): Flashcard {
        // Split at first line break or ":" to create front/back
        val parts = selectedText.split(Regex("[:\\n]"), 2)
        val front = parts[0].trim()
        val back = if (parts.size > 1) parts[1].trim() else ""
        return createBasicFlashcard(front, back, tags)
    }

    fun reviewFlashcard(flashcard: Flashcard, result: ReviewResult, responseTime: Long = 0): Flashcard {
        val entry = ReviewEntry(result = result, responseTime = responseTime)
        flashcard.reviewHistory.add(entry)

        val interval = calculateNextInterval(flashcard, result)
        val nextReview = System.currentTimeMillis() + interval

        return flashcard.copy(
            nextReview = nextReview,
            difficulty = updateDifficulty(flashcard.difficulty, result)
        )
    }

    private fun calculateNextInterval(flashcard: Flashcard, result: ReviewResult): Long {
        val baseInterval = when (result) {
            ReviewResult.AGAIN -> 60_000L // 1 minute
            ReviewResult.HARD -> 600_000L // 10 minutes
            ReviewResult.GOOD -> {
                val reviews = flashcard.reviewHistory.size
                when {
                    reviews < 3 -> 86_400_000L // 1 day
                    reviews < 7 -> 259_200_000L // 3 days
                    reviews < 14 -> 604_800_000L // 1 week
                    else -> 2_592_000_000L // 30 days
                }
            }
            ReviewResult.EASY -> {
                val reviews = flashcard.reviewHistory.size
                when {
                    reviews < 3 -> 259_200_000L // 3 days
                    reviews < 7 -> 604_800_000L // 1 week
                    else -> 5_184_000_000L // 60 days
                }
            }
        }

        val difficultyMultiplier = when (flashcard.difficulty) {
            Difficulty.EASY -> 1.5f
            Difficulty.MEDIUM -> 1.0f
            Difficulty.HARD -> 0.7f
        }

        return (baseInterval * difficultyMultiplier).toLong()
    }

    private fun updateDifficulty(current: Difficulty, result: ReviewResult): Difficulty {
        return when (result) {
            ReviewResult.AGAIN -> Difficulty.HARD
            ReviewResult.HARD -> if (current == Difficulty.EASY) Difficulty.MEDIUM else Difficulty.HARD
            ReviewResult.GOOD -> current
            ReviewResult.EASY -> if (current == Difficulty.HARD) Difficulty.MEDIUM else Difficulty.EASY
        }
    }

    fun getDueCount(flashcards: List<Flashcard>): Int {
        val now = System.currentTimeMillis()
        return flashcards.count { it.nextReview <= now }
    }

    fun getStatistics(flashcards: List<Flashcard>): FlashcardStats {
        val total = flashcards.size
        val due = getDueCount(flashcards)
        val totalReviews = flashcards.sumOf { it.reviewHistory.size }
        val avgAccuracy = if (totalReviews > 0) {
            flashcards.flatMap { it.reviewHistory }
                .count { it.result == ReviewResult.GOOD || it.result == ReviewResult.EASY }
                .toFloat() / totalReviews
        } else 0f

        return FlashcardStats(total, due, totalReviews, avgAccuracy)
    }

    fun isCloze(flashcard: Flashcard): Boolean {
        return flashcard.tags.any { it.equals("type:cloze", ignoreCase = true) } ||
            clozeRegex.containsMatchIn(flashcard.front)
    }

    fun renderFront(flashcard: Flashcard): String {
        return if (isCloze(flashcard)) {
            flashcard.front.replace(clozeRegex, "[____]")
        } else {
            flashcard.front
        }
    }

    fun renderBack(flashcard: Flashcard): String {
        return if (isCloze(flashcard)) {
            flashcard.back.ifBlank { flashcard.front }
        } else {
            flashcard.back
        }
    }

    private fun normalizeTags(tags: List<String>, vararg extraTags: String?): List<String> {
        return (tags + extraTags.filterNotNull())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}

data class FlashcardStats(
    val totalCards: Int,
    val dueCards: Int,
    val totalReviews: Int,
    val accuracy: Float
)
