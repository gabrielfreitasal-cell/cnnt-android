package com.cnnt.app.data.repository

import com.cnnt.app.data.dao.*
import com.cnnt.app.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.google.gson.reflect.TypeToken
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CnntRepository(private val database: CnntDatabase) {
    private val gson = Gson()

    private val notebookDao = database.notebookDao()
    private val boardDao = database.boardDao()
    private val strokeDao = database.strokeDao()
    private val spatialObjectDao = database.spatialObjectDao()
    private val flashcardDao = database.flashcardDao()
    private val contentBlockDao = database.contentBlockDao()
    private val linkEdgeDao = database.linkEdgeDao()

    // --- Notebooks ---

    fun getAllNotebooks(): Flow<List<NotebookEntity>> = notebookDao.getAllNotebooks()

    suspend fun getAllNotebookEntities(): List<NotebookEntity> = notebookDao.getAllNotebooksSync()

    suspend fun getAllBoardEntities(): List<BoardEntity> = boardDao.getAllBoardsSync()

    suspend fun getAllLayerEntities(): List<LayerEntity> = boardDao.getAllLayersSync()

    suspend fun getAllStrokeEntities(): List<StrokeEntity> = strokeDao.getAllStrokesSync()

    suspend fun getAllSpatialObjectEntities(): List<SpatialObjectEntity> = spatialObjectDao.getAllObjectsSync()

    suspend fun getAllContentBlockEntities(): List<ContentBlockEntity> {
        val notebooks = notebookDao.getAllNotebooksSync()
        return notebooks.flatMap { contentBlockDao.getBlocksForNotebookSync(it.id) }
    }

    suspend fun getAllLinkEdgeEntities(): List<LinkEdgeEntity> {
        val notebooks = notebookDao.getAllNotebooksSync()
        return notebooks.flatMap { notebook ->
            val layers = boardDao.getBoardsForNotebookSync(notebook.id).flatMap { board ->
                boardDao.getLayersForBoard(board.id)
            }
            layers.flatMap { layer -> linkEdgeDao.getLinksForLayerSync(layer.id) }
        }.distinctBy { it.id }
    }

    suspend fun saveNotebook(notebook: Notebook) {
        saveNotebookMetadata(notebook)
        for (board in notebook.boards) {
            saveBoard(board, notebook.id)
        }
    }

    suspend fun saveNotebookMetadata(notebook: Notebook) {
        notebookDao.insert(NotebookEntity(
            id = notebook.id,
            name = notebook.name,
            coverColor = notebook.coverColor,
            tags = gson.toJson(notebook.tags),
            createdAt = notebook.createdAt,
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun loadMostRecentNotebook(): Notebook? {
        val entity = notebookDao.getMostRecentNotebook() ?: return null
        return loadNotebookById(entity.id)
    }

    suspend fun loadNotebookById(notebookId: String): Notebook? {
        val entity = notebookDao.getNotebookById(notebookId) ?: return null
        val boardEntities = boardDao.getBoardsForNotebookSync(notebookId)
        val boards = boardEntities.mapNotNull { loadBoard(it.id) }.toMutableList()
        if (boards.isEmpty()) {
            boards.add(Board(notebookId = notebookId, name = "Página 1"))
        }
        return Notebook(
            id = entity.id,
            name = entity.name,
            coverColor = entity.coverColor,
            boards = boards,
            tags = try {
                gson.fromJson(entity.tags, Array<String>::class.java)?.toList() ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    suspend fun saveBoard(board: Board, notebookId: String) {
        boardDao.insert(BoardEntity(
            id = board.id,
            notebookId = notebookId,
            name = board.name,
            order = board.order,
            backgroundColor = board.backgroundColor,
            createdAt = board.createdAt,
            updatedAt = System.currentTimeMillis()
        ))
        for (layer in board.layers) {
            saveLayer(layer, board.id)
        }
    }

    suspend fun saveLayer(layer: Layer, boardId: String) {
        boardDao.insertLayer(LayerEntity(
            id = layer.id,
            boardId = boardId,
            name = layer.name,
            visible = layer.visible,
            locked = layer.locked,
            opacity = layer.opacity,
            order = layer.order
        ))
        syncLayerStrokes(layer)
        syncLayerObjects(layer)
        syncLayerBlocks(layer, boardId)
        syncLayerLinks(layer)
    }

    /** Keeps Room strokes identical to in-memory layer (fixes erases / undo drift). */
    private suspend fun syncLayerStrokes(layer: Layer) {
        strokeDao.deleteAllForLayer(layer.id)
        if (layer.strokes.isEmpty()) return
        val strokeEntities = layer.strokes.map { stroke ->
            val layerId = stroke.layerId.ifEmpty { layer.id }
            StrokeEntity(
                id = stroke.id,
                layerId = layerId,
                brushId = stroke.brushId,
                color = stroke.color,
                size = stroke.size,
                opacity = stroke.opacity,
                pointsData = serializePoints(ArrayList(stroke.points)),
                createdAt = stroke.createdAt
            )
        }
        strokeDao.insertAll(strokeEntities)
    }

    private suspend fun syncLayerObjects(layer: Layer) {
        spatialObjectDao.deleteAllForLayer(layer.id)
        for (obj in layer.objects) {
            saveSpatialObject(
                if (obj.layerId.isEmpty()) obj.copy(layerId = layer.id) else obj,
                layer.id
            )
        }
    }

    private suspend fun syncLayerBlocks(layer: Layer, boardId: String) {
        val notebookId = boardDao.getBoardById(boardId)?.notebookId.orEmpty()
        contentBlockDao.getBlocksForLayerSync(layer.id).forEach { existing ->
            contentBlockDao.deleteById(existing.id)
        }
        if (layer.contentBlocks.isEmpty()) return
        contentBlockDao.insertAll(layer.contentBlocks.map { block ->
            block.toEntity(
                notebookId = if (block.notebookId.isNotBlank()) block.notebookId else notebookId,
                layerId = if (block.layerId.isNotBlank()) block.layerId else layer.id
            )
        })
    }

    private suspend fun syncLayerLinks(layer: Layer) {
        linkEdgeDao.getLinksForLayerSync(layer.id).forEach { existing ->
            linkEdgeDao.deleteById(existing.id)
        }
        if (layer.linkEdges.isEmpty()) return
        linkEdgeDao.insertAll(layer.linkEdges.map(::linkToEntity))
    }

    suspend fun saveStroke(stroke: Stroke, layerId: String) {
        val resolvedLayerId = stroke.layerId.ifEmpty { layerId }
        strokeDao.insert(StrokeEntity(
            id = stroke.id,
            layerId = resolvedLayerId,
            brushId = stroke.brushId,
            color = stroke.color,
            size = stroke.size,
            opacity = stroke.opacity,
            pointsData = serializePoints(stroke.points),
            createdAt = stroke.createdAt
        ))
    }

    suspend fun deleteStroke(strokeId: String) {
        strokeDao.deleteById(strokeId)
    }

    suspend fun loadBoard(boardId: String): Board? {
        val boardEntity = boardDao.getBoardById(boardId) ?: return null
        val layerEntities = boardDao.getLayersForBoard(boardId)
        val layers = layerEntities.map { layerEntity ->
            val strokes = strokeDao.getStrokesForLayer(layerEntity.id).map { strokeEntity ->
                Stroke(
                    id = strokeEntity.id,
                    points = deserializePoints(strokeEntity.pointsData),
                    brushId = strokeEntity.brushId,
                    color = strokeEntity.color,
                    size = strokeEntity.size,
                    opacity = strokeEntity.opacity,
                    layerId = strokeEntity.layerId,
                    createdAt = strokeEntity.createdAt
                )
            }
            val objects = spatialObjectDao.getObjectsForLayer(layerEntity.id).map { objEntity ->
                deserializeSpatialObject(objEntity)
            }
            val blocks = contentBlockDao.getBlocksForLayerSync(layerEntity.id).map(::deserializeContentBlock)
            val links = linkEdgeDao.getLinksForLayerSync(layerEntity.id).map(::deserializeLinkEdge)
            Layer(
                id = layerEntity.id,
                name = layerEntity.name,
                boardId = boardId,
                visible = layerEntity.visible,
                locked = layerEntity.locked,
                opacity = layerEntity.opacity,
                order = layerEntity.order,
                strokes = strokes.toMutableList(),
                objects = objects.toMutableList(),
                contentBlocks = blocks.toMutableList(),
                linkEdges = links.toMutableList()
            )
        }
        val normalizedLayers = layers.map { layer ->
            layer.copy(boardId = boardId)
        }.toMutableList().ifEmpty {
            mutableListOf(Layer(boardId = boardId))
        }
        return Board(
            id = boardEntity.id,
            name = boardEntity.name,
            notebookId = boardEntity.notebookId,
            order = boardEntity.order,
            backgroundColor = boardEntity.backgroundColor,
            layers = normalizedLayers,
            createdAt = boardEntity.createdAt,
            updatedAt = boardEntity.updatedAt
        )
    }

    // --- Spatial Objects ---

    suspend fun saveSpatialObject(obj: SpatialObject, layerId: String) {
        spatialObjectDao.insert(SpatialObjectEntity(
            id = obj.id,
            layerId = layerId,
            type = obj.type.name,
            x = obj.x,
            y = obj.y,
            width = obj.width,
            height = obj.height,
            rotation = obj.rotation,
            zIndex = obj.zIndex,
            locked = obj.locked,
            groupId = obj.groupId,
            contentJson = gson.toJson(obj.content),
            styleJson = gson.toJson(obj.style),
            createdAt = obj.createdAt,
            updatedAt = obj.updatedAt
        ))
    }

    suspend fun deleteSpatialObject(objId: String) {
        spatialObjectDao.deleteById(objId)
    }

    fun observeContentBlocks(layerId: String): Flow<List<ContentBlock>> =
        contentBlockDao.observeBlocksForLayer(layerId).map { list -> list.map(::deserializeContentBlock) }

    fun observeLinkEdges(layerId: String): Flow<List<LinkEdge>> =
        linkEdgeDao.observeLinksForLayer(layerId).map { list -> list.map(::deserializeLinkEdge) }

    suspend fun saveContentBlock(block: ContentBlock) {
        contentBlockDao.insert(block.toEntity(block.notebookId, block.layerId))
    }

    suspend fun deleteContentBlock(blockId: String) {
        contentBlockDao.deleteById(blockId)
    }

    suspend fun saveLinkEdge(edge: LinkEdge) {
        linkEdgeDao.insert(linkToEntity(edge))
    }

    suspend fun deleteLinkEdge(linkId: String) {
        linkEdgeDao.deleteById(linkId)
    }

    // --- Flashcards ---

    fun getAllFlashcards(): Flow<List<Flashcard>> = flashcardDao.getAllFlashcards().map { list ->
        list.map { entity -> entity.toModel() }
    }

    suspend fun getAllFlashcardEntities(): List<FlashcardEntity> = flashcardDao.getAllFlashcardsSync()

    fun getDueFlashcards(): Flow<List<Flashcard>> = flashcardDao.getDueFlashcards().map { list ->
        list.map { entity -> entity.toModel() }
    }

    suspend fun saveFlashcard(flashcard: Flashcard) {
        flashcardDao.insert(FlashcardEntity(
            id = flashcard.id,
            front = flashcard.front,
            back = flashcard.back,
            tags = gson.toJson(flashcard.tags),
            difficulty = flashcard.difficulty.name,
            linkedRegionId = flashcard.linkedRegionId,
            boardId = flashcard.boardId,
            reviewHistoryJson = gson.toJson(flashcard.reviewHistory),
            nextReview = flashcard.nextReview,
            createdAt = flashcard.createdAt
        ))
    }

    suspend fun deleteFlashcard(flashcardId: String) {
        val entity = flashcardDao.getFlashcardById(flashcardId) ?: return
        flashcardDao.delete(entity)
    }

    private fun FlashcardEntity.toModel(): Flashcard {
        val tagsType = object : TypeToken<List<String>>() {}.type
        val reviewHistoryType = object : TypeToken<MutableList<ReviewEntry>>() {}.type
        return Flashcard(
            id = id,
            front = front,
            back = back,
            tags = runCatching { gson.fromJson<List<String>>(tags, tagsType) ?: emptyList() }.getOrDefault(emptyList()),
            difficulty = runCatching { Difficulty.valueOf(difficulty) }.getOrDefault(Difficulty.MEDIUM),
            linkedRegionId = linkedRegionId,
            boardId = boardId,
            reviewHistory = runCatching {
                gson.fromJson<MutableList<ReviewEntry>>(reviewHistoryJson, reviewHistoryType) ?: mutableListOf()
            }.getOrDefault(mutableListOf()),
            nextReview = nextReview,
            createdAt = createdAt
        )
    }

    // --- Serialization helpers ---

    private fun serializePoints(points: List<StrokePoint>): ByteArray {
        // Each point: x(4) + y(4) + pressure(4) + tiltX(4) + tiltY(4) + orientation(4) + timestamp(8) = 32 bytes
        val buffer = ByteBuffer.allocate(points.size * 32).order(ByteOrder.LITTLE_ENDIAN)
        for (point in points) {
            buffer.putFloat(point.x)
            buffer.putFloat(point.y)
            buffer.putFloat(point.pressure)
            buffer.putFloat(point.tiltX)
            buffer.putFloat(point.tiltY)
            buffer.putFloat(point.orientation)
            buffer.putLong(point.timestamp)
        }
        return buffer.array()
    }

    private fun deserializePoints(data: ByteArray): MutableList<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        while (buffer.remaining() >= 32) {
            points.add(StrokePoint(
                x = buffer.getFloat(),
                y = buffer.getFloat(),
                pressure = buffer.getFloat(),
                tiltX = buffer.getFloat(),
                tiltY = buffer.getFloat(),
                orientation = buffer.getFloat(),
                timestamp = buffer.getLong()
            ))
        }
        return points
    }

    private fun deserializeSpatialObject(entity: SpatialObjectEntity): SpatialObject {
        return SpatialObject(
            id = entity.id,
            type = SpatialObjectType.valueOf(entity.type),
            x = entity.x,
            y = entity.y,
            width = entity.width,
            height = entity.height,
            rotation = entity.rotation,
            zIndex = entity.zIndex,
            locked = entity.locked,
            groupId = entity.groupId,
            content = deserializeContent(entity.contentJson, entity.type),
            style = gson.fromJson(entity.styleJson, ObjectStyle::class.java) ?: ObjectStyle(),
            layerId = entity.layerId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun deserializeContent(json: String, type: String): ObjectContent {
        return try {
            when (SpatialObjectType.valueOf(type)) {
                SpatialObjectType.TEXT -> gson.fromJson(json, ObjectContent.Text::class.java)
                SpatialObjectType.CHECKLIST -> gson.fromJson(json, ObjectContent.Checklist::class.java)
                SpatialObjectType.IMAGE -> gson.fromJson(json, ObjectContent.Image::class.java)
                SpatialObjectType.PDF -> gson.fromJson(json, ObjectContent.Pdf::class.java)
                SpatialObjectType.LINK -> gson.fromJson(json, ObjectContent.Link::class.java)
                SpatialObjectType.FILE -> gson.fromJson(json, ObjectContent.File::class.java)
                SpatialObjectType.FLASHCARD -> gson.fromJson(json, ObjectContent.FlashcardContent::class.java)
                SpatialObjectType.GROUP -> gson.fromJson(json, ObjectContent.Group::class.java)
                else -> ObjectContent.Empty
            }
        } catch (e: Exception) {
            ObjectContent.Empty
        }
    }

    private fun deserializeContentBlock(entity: ContentBlockEntity): ContentBlock {
        val content = try {
            when (BlockType.fromKey(entity.type)) {
                BlockType.Image -> gson.fromJson(entity.contentJson, BlockContent.Image::class.java)
                BlockType.Markdown -> gson.fromJson(entity.contentJson, BlockContent.Markdown::class.java)
                BlockType.Flashcard -> gson.fromJson(entity.contentJson, BlockContent.Flashcard::class.java)
                BlockType.InteractiveText -> gson.fromJson(entity.contentJson, BlockContent.InteractiveText::class.java)
                BlockType.Pdf -> gson.fromJson(entity.contentJson, BlockContent.Pdf::class.java)
                BlockType.Text -> gson.fromJson(entity.contentJson, BlockContent.TextNote::class.java)
            }
        } catch (_: Exception) {
            BlockContent.TextNote()
        }
        return ContentBlock(
            id = entity.id,
            type = BlockType.fromKey(entity.type),
            posX = entity.posX,
            posY = entity.posY,
            width = entity.width,
            height = entity.height,
            rotation = entity.rotation,
            zIndex = entity.zIndex,
            contentJson = entity.contentJson,
            notebookId = entity.notebookId,
            layerId = entity.layerId,
            content = content,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun deserializeLinkEdge(entity: LinkEdgeEntity): LinkEdge {
        val style = try {
            gson.fromJson(entity.style, LinkStyle::class.java) ?: LinkStyle()
        } catch (_: Exception) {
            LinkStyle()
        }
        return LinkEdge(
            id = entity.id,
            sourceBlockId = entity.sourceBlockId,
            targetBlockId = entity.targetBlockId,
            label = entity.label,
            style = style,
            createdAt = entity.createdAt
        )
    }

    private fun ContentBlock.toEntity(notebookId: String, layerId: String): ContentBlockEntity {
        val serializedContent = when (content) {
            is BlockContent.Image -> gson.toJson(content)
            is BlockContent.Markdown -> gson.toJson(content)
            is BlockContent.Flashcard -> gson.toJson(content)
            is BlockContent.InteractiveText -> gson.toJson(content)
            is BlockContent.Pdf -> gson.toJson(content)
            is BlockContent.TextNote -> gson.toJson(content)
        }
        return ContentBlockEntity(
            id = id,
            type = type.key,
            posX = posX,
            posY = posY,
            width = width,
            height = height,
            rotation = rotation,
            zIndex = zIndex,
            contentJson = serializedContent,
            notebookId = notebookId,
            layerId = layerId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun linkToEntity(edge: LinkEdge): LinkEdgeEntity {
        return LinkEdgeEntity(
            id = edge.id,
            sourceBlockId = edge.sourceBlockId,
            targetBlockId = edge.targetBlockId,
            label = edge.label,
            style = gson.toJson(edge.style),
            createdAt = edge.createdAt
        )
    }
}
