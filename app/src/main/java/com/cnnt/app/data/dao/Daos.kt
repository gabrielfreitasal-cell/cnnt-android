package com.cnnt.app.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    fun getAllNotebooks(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebookById(id: String): NotebookEntity?

    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getMostRecentNotebook(): NotebookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notebook: NotebookEntity)

    @Update
    suspend fun update(notebook: NotebookEntity)

    @Delete
    suspend fun delete(notebook: NotebookEntity)
}

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards WHERE notebookId = :notebookId ORDER BY `order`")
    fun getBoardsByNotebook(notebookId: String): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards WHERE notebookId = :notebookId ORDER BY `order`")
    suspend fun getBoardsForNotebookSync(notebookId: String): List<BoardEntity>

    @Query("SELECT * FROM boards WHERE id = :id")
    suspend fun getBoardById(id: String): BoardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(board: BoardEntity)

    @Update
    suspend fun update(board: BoardEntity)

    @Delete
    suspend fun delete(board: BoardEntity)

    @Query("SELECT * FROM layers WHERE boardId = :boardId ORDER BY `order`")
    suspend fun getLayersForBoard(boardId: String): List<LayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayer(layer: LayerEntity)

    @Update
    suspend fun updateLayer(layer: LayerEntity)

    @Delete
    suspend fun deleteLayer(layer: LayerEntity)
}

@Dao
interface StrokeDao {
    @Query("SELECT * FROM strokes WHERE layerId = :layerId")
    suspend fun getStrokesForLayer(layerId: String): List<StrokeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stroke: StrokeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(strokes: List<StrokeEntity>)

    @Query("DELETE FROM strokes WHERE id = :strokeId")
    suspend fun deleteById(strokeId: String)

    @Query("DELETE FROM strokes WHERE layerId = :layerId")
    suspend fun deleteAllForLayer(layerId: String)
}

@Dao
interface SpatialObjectDao {
    @Query("SELECT * FROM spatial_objects WHERE layerId = :layerId ORDER BY zIndex")
    suspend fun getObjectsForLayer(layerId: String): List<SpatialObjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(obj: SpatialObjectEntity)

    @Update
    suspend fun update(obj: SpatialObjectEntity)

    @Query("DELETE FROM spatial_objects WHERE id = :objId")
    suspend fun deleteById(objId: String)

    @Query("DELETE FROM spatial_objects WHERE layerId = :layerId")
    suspend fun deleteAllForLayer(layerId: String)
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards ORDER BY nextReview ASC")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE nextReview <= :now ORDER BY nextReview ASC")
    fun getDueFlashcards(now: Long = System.currentTimeMillis()): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getFlashcardById(id: String): FlashcardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flashcard: FlashcardEntity)

    @Update
    suspend fun update(flashcard: FlashcardEntity)

    @Delete
    suspend fun delete(flashcard: FlashcardEntity)

    @Query("SELECT * FROM flashcards WHERE tags LIKE '%' || :tag || '%'")
    fun getFlashcardsByTag(tag: String): Flow<List<FlashcardEntity>>
}
