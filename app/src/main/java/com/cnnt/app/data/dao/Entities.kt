package com.cnnt.app.data.dao

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverColor: Int,
    val tags: String = "", // JSON array
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "boards",
    foreignKeys = [ForeignKey(
        entity = NotebookEntity::class,
        parentColumns = ["id"],
        childColumns = ["notebookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("notebookId")]
)
data class BoardEntity(
    @PrimaryKey val id: String,
    val notebookId: String,
    val name: String,
    val order: Int,
    val backgroundColor: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "layers",
    foreignKeys = [ForeignKey(
        entity = BoardEntity::class,
        parentColumns = ["id"],
        childColumns = ["boardId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("boardId")]
)
data class LayerEntity(
    @PrimaryKey val id: String,
    val boardId: String,
    val name: String,
    val visible: Boolean,
    val locked: Boolean,
    val opacity: Float,
    val order: Int
)

@Entity(
    tableName = "strokes",
    foreignKeys = [ForeignKey(
        entity = LayerEntity::class,
        parentColumns = ["id"],
        childColumns = ["layerId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("layerId")]
)
data class StrokeEntity(
    @PrimaryKey val id: String,
    val layerId: String,
    val brushId: String,
    val color: Int,
    val size: Float,
    val opacity: Float,
    val pointsData: ByteArray, // Serialized stroke points
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StrokeEntity) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}

@Entity(
    tableName = "spatial_objects",
    foreignKeys = [ForeignKey(
        entity = LayerEntity::class,
        parentColumns = ["id"],
        childColumns = ["layerId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("layerId")]
)
data class SpatialObjectEntity(
    @PrimaryKey val id: String,
    val layerId: String,
    val type: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float,
    val zIndex: Int,
    val locked: Boolean,
    val groupId: String?,
    val contentJson: String,
    val styleJson: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val front: String,
    val back: String,
    val tags: String, // JSON array
    val difficulty: String,
    val linkedRegionId: String?,
    val boardId: String?,
    val reviewHistoryJson: String,
    val nextReview: Long,
    val createdAt: Long
)

@Entity(tableName = "palettes")
data class PaletteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorsJson: String,
    val isDefault: Boolean
)

@Entity(tableName = "brush_presets")
data class BrushPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val configJson: String
)
