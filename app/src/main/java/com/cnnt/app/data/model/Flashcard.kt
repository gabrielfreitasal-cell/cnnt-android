package com.cnnt.app.data.model

import java.util.UUID

data class Flashcard(
    val id: String = UUID.randomUUID().toString(),
    val front: String,
    val back: String,
    val tags: List<String> = emptyList(),
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val linkedRegionId: String? = null,
    val boardId: String? = null,
    val reviewHistory: MutableList<ReviewEntry> = mutableListOf(),
    val nextReview: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class Difficulty { EASY, MEDIUM, HARD }

data class ReviewEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val result: ReviewResult,
    val responseTime: Long = 0
)

enum class ReviewResult { AGAIN, HARD, GOOD, EASY }
