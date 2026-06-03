package com.cnnt.app.ui



import android.app.Application

import androidx.lifecycle.AndroidViewModel

import androidx.lifecycle.ViewModel

import androidx.lifecycle.ViewModelProvider

import androidx.lifecycle.viewModelScope

import com.cnnt.app.CnntApplication

import com.cnnt.app.data.NotebookSnapshot
import com.cnnt.app.data.SessionStore
import com.cnnt.app.debug.DebugDiagnostics
import kotlinx.coroutines.runBlocking

import com.cnnt.app.data.model.*

import com.cnnt.app.data.repository.CnntRepository

import com.cnnt.app.flashcard.FlashcardManager

import com.cnnt.app.ocr.OcrEngine

import com.cnnt.app.export.ExportManager

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext
import java.util.Locale



class MainViewModel(application: Application) : AndroidViewModel(application) {



    private val repository: CnntRepository = (application as CnntApplication).repository

    private val sessionStore = SessionStore(application)

    val flashcardManager = FlashcardManager()

    val ocrEngine = OcrEngine()

    val exportManager = ExportManager(application)



    private val _currentNotebook = MutableStateFlow<Notebook?>(null)

    val currentNotebook: StateFlow<Notebook?> = _currentNotebook



    private val _currentBoard = MutableStateFlow<Board?>(null)

    val currentBoard: StateFlow<Board?> = _currentBoard



    private val _currentBrush = MutableStateFlow(BrushPreset.gelPen())

    val currentBrush: StateFlow<BrushPreset> = _currentBrush



    private val _currentColor = MutableStateFlow(0xFFFFFFFF.toInt())

    val currentColor: StateFlow<Int> = _currentColor



    private val _brushes = MutableStateFlow(BrushPreset.defaultBrushes())

    val brushes: StateFlow<List<BrushPreset>> = _brushes



    private val _palettes = MutableStateFlow(listOf(

        ColorPalette.defaultPalette(),

        ColorPalette.studyPalette(),

        ColorPalette.darkPalette()

    ))

    val palettes: StateFlow<List<ColorPalette>> = _palettes



    private val _flashcards = MutableStateFlow<List<Flashcard>>(emptyList())

    val flashcards: StateFlow<List<Flashcard>> = _flashcards



    private var autoSaveJob: kotlinx.coroutines.Job? = null

    @Volatile

    private var dirty = false

    private val app = application as CnntApplication

    init {
        NotebookSnapshot.register {
            runBlocking(Dispatchers.IO) {
                try {
                    val notebook = _currentNotebook.value ?: return@runBlocking
                    val board = _currentBoard.value ?: return@runBlocking
                    repository.saveNotebookMetadata(notebook)
                    repository.saveBoard(board, notebook.id)
                    sessionStore.saveSession(notebook.id, board.id, activeBoardIndex())
                } catch (e: Exception) {
                    android.util.Log.e("CNNT", "Emergency snapshot failed: ${e.message}", e)
                }
            }
        }
    }

    fun loadOrCreateDefaultNotebook() {

        viewModelScope.launch {

            val loaded = withContext(Dispatchers.IO) {

                val lastId = sessionStore.getLastNotebookId()

                when {

                    lastId != null -> repository.loadNotebookById(lastId)

                        ?: repository.loadMostRecentNotebook()

                    else -> repository.loadMostRecentNotebook()

                }

            }



            if (loaded != null) {

                val boardIndex = resolveBoardIndex(loaded)

                _currentNotebook.value = loaded

                _currentBoard.value = loaded.boards.getOrElse(boardIndex) { loaded.activeBoard }

                persistSession()

            } else {

                val notebook = Notebook(name = "Meu Caderno")

                val board = notebook.activeBoard.copy(notebookId = notebook.id)

                notebook.boards[0] = board

                _currentNotebook.value = notebook

                _currentBoard.value = board

                withContext(Dispatchers.IO) {

                    repository.saveNotebook(notebook)

                }

                persistSession()

            }

            startAutoSave()
            observeFlashcards()

        }

    }

    private fun observeFlashcards() {
        viewModelScope.launch {
            repository.getAllFlashcards().collectLatest { cards ->
                _flashcards.value = cards
            }
        }
    }



    private fun resolveBoardIndex(notebook: Notebook): Int {

        val byId = sessionStore.getLastBoardId()?.let { id ->

            notebook.boards.indexOfFirst { it.id == id }.takeIf { it >= 0 }

        }

        if (byId != null) return byId

        return sessionStore.getLastBoardIndex().coerceIn(0, (notebook.boards.size - 1).coerceAtLeast(0))

    }



    fun setCurrentBrush(brush: BrushPreset) {

        _currentBrush.value = brush

    }



    fun setCurrentColor(color: Int) {

        _currentColor.value = color

    }



    fun activeBoardIndex(): Int {

        val notebook = _currentNotebook.value ?: return 0

        val board = _currentBoard.value ?: return 0

        val idx = notebook.boards.indexOfFirst { it.id == board.id }

        return if (idx >= 0) idx else 0

    }



    fun switchBoard(index: Int) {

        val notebook = _currentNotebook.value ?: return

        if (index in notebook.boards.indices) {

            flushSaveSync()

            _currentBoard.value = notebook.boards[index]

            persistSession()

        }

    }



    fun addNewBoard() {

        val notebook = _currentNotebook.value ?: return

        val newBoard = Board(

            name = "Página ${notebook.boards.size + 1}",

            notebookId = notebook.id,

            order = notebook.boards.size

        )

        notebook.boards.add(newBoard)

        _currentBoard.value = newBoard

        _currentNotebook.value = notebook

        markDirty()

        persistSession()

        viewModelScope.launch(Dispatchers.IO) {

            repository.saveBoard(newBoard, notebook.id)

        }

    }



    fun saveStroke(stroke: Stroke) {

        val board = _currentBoard.value ?: return

        val layer = board.activeLayer

        val layerId = layer.id

        val toSave = if (stroke.layerId.isEmpty()) stroke.copy(layerId = layerId) else stroke

        markDirty()

        app.applicationScope.launch {

            try {

                repository.saveStroke(toSave, layerId)

            } catch (e: Exception) {

                android.util.Log.e("CNNT", "Save stroke error: ${e.message}", e)

            }

        }

    }



    fun deleteStroke(strokeId: String) {

        markDirty()

        viewModelScope.launch(Dispatchers.IO) {

            try {

                repository.deleteStroke(strokeId)

            } catch (e: Exception) {

                android.util.Log.e("CNNT", "Delete stroke error: ${e.message}", e)

            }

        }

    }



    fun addSpatialObject(obj: SpatialObject) {

        val board = _currentBoard.value ?: return

        val layer = board.activeLayer

        val updatedObj = obj.copy(layerId = layer.id)

        layer.objects.add(updatedObj)

        markDirty()

        viewModelScope.launch(Dispatchers.IO) {

            repository.saveSpatialObject(updatedObj, layer.id)

        }

        _currentBoard.value = board

    }



    fun updateSpatialObject(obj: SpatialObject) {

        val notebook = _currentNotebook.value
        notebook?.boards?.forEach { board ->
            board.layers.forEach { layer ->
                val idx = layer.objects.indexOfFirst { it.id == obj.id }
                if (idx >= 0) {
                    layer.objects[idx] = obj
                }
            }
        }

        _currentBoard.value = _currentBoard.value

        markDirty()

        viewModelScope.launch(Dispatchers.IO) {

            repository.saveSpatialObject(obj, obj.layerId)

        }

    }

    fun deleteSpatialObject(objectId: String) {

        val notebook = _currentNotebook.value ?: return

        notebook.boards.forEach { board ->
            board.layers.forEach { layer ->
                layer.objects.removeAll { it.id == objectId }
            }
        }

        _currentBoard.value = _currentBoard.value
        markDirty()

        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSpatialObject(objectId)
        }
    }

    fun deleteSpatialObjects(objectIds: Collection<String>) {

        if (objectIds.isEmpty()) return

        val notebook = _currentNotebook.value ?: return

        notebook.boards.forEach { board ->
            board.layers.forEach { layer ->
                layer.objects.removeAll { objectIds.contains(it.id) }
            }
        }

        _currentBoard.value = _currentBoard.value
        markDirty()

        viewModelScope.launch(Dispatchers.IO) {
            objectIds.forEach { repository.deleteSpatialObject(it) }
        }
    }



    fun addFlashcard(flashcard: Flashcard) {

        viewModelScope.launch {

            repository.saveFlashcard(flashcard)

        }

    }

    fun deleteFlashcard(flashcardId: String) {

        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFlashcard(flashcardId)
        }

    }

    fun syncFlashcardBlockPreview(flashcard: Flashcard) {

        val blockId = flashcard.linkedRegionId ?: return
        val notebook = _currentNotebook.value ?: return
        val isCloze = flashcard.tags.any { it.equals("type:cloze", ignoreCase = true) } ||
            Regex("\\{\\{c[12]::.+?}}").containsMatchIn(flashcard.front)
        val preview = if (isCloze) {
            flashcard.front.replace(Regex("\\{\\{c[12]::(.*?)}}"), "[____]")
        } else {
            flashcard.front
        }.trim().ifBlank { "Novo flashcard" }

        notebook.boards.forEach { board ->
            board.layers.forEach { layer ->
                val index = layer.objects.indexOfFirst { it.id == blockId }
                if (index >= 0) {
                    val obj = layer.objects[index]
                    val content = obj.content as? ObjectContent.FlashcardContent ?: ObjectContent.FlashcardContent()
                    val noteType = if (isCloze) "cloze" else "basic"
                    layer.objects[index] = obj.copy(
                        content = content.copy(
                            flashcardId = flashcard.id,
                            flashcardIds = listOf(flashcard.id),
                            previewText = preview,
                            noteType = noteType
                        ),
                        updatedAt = System.currentTimeMillis()
                    )
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.saveSpatialObject(layer.objects[index], layer.id)
                    }
                }
                val contentBlockIndex = layer.contentBlocks.indexOfFirst { existing ->
                    existing.id == blockId ||
                        (existing.content as? BlockContent.Flashcard)?.flashcardId == flashcard.id
                }
                if (contentBlockIndex >= 0) {
                    val existing = layer.contentBlocks[contentBlockIndex]
                    val existingContent = existing.content as? BlockContent.Flashcard ?: BlockContent.Flashcard()
                    layer.contentBlocks[contentBlockIndex] = existing.copy(
                        content = existingContent.copy(
                            flashcardId = flashcard.id,
                            previewText = preview,
                            noteType = if (isCloze) "cloze" else "basic"
                        ),
                        updatedAt = System.currentTimeMillis()
                    )
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.saveContentBlock(layer.contentBlocks[contentBlockIndex])
                    }
                }
            }
        }

        _currentBoard.value = _currentBoard.value
    }

    fun removeCanvasLinkedFlashcardBlock(blockId: String) {
        deleteSpatialObject(blockId)
    }

    fun buildWorkspaceSnapshot(): Workspace {
        val notebook = _currentNotebook.value
        return Workspace(
            notebooks = notebook?.let { mutableListOf(it) } ?: mutableListOf(),
            brushPresets = _brushes.value,
            palettes = _palettes.value.toMutableList(),
            settings = AppSettings(
                defaultBrushId = _currentBrush.value.id,
                defaultColor = _currentColor.value
            )
        )
    }

    fun currentLayerBlocks(): List<ContentBlock> {
        return _currentBoard.value?.activeLayer?.contentBlocks?.sortedBy { it.zIndex } ?: emptyList()
    }

    fun currentLayerLinks(): List<LinkEdge> {
        return _currentBoard.value?.activeLayer?.linkEdges ?: emptyList()
    }

    fun addContentBlock(block: ContentBlock) {
        val board = _currentBoard.value ?: return
        board.activeLayer.contentBlocks.add(block)
        _currentBoard.value = board
        markDirty()
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveContentBlock(block)
        }
    }

    fun updateContentBlock(block: ContentBlock) {
        val notebook = _currentNotebook.value ?: return
        notebook.boards.forEach { board ->
            board.layers.forEach { layer ->
                val index = layer.contentBlocks.indexOfFirst { it.id == block.id }
                if (index >= 0) {
                    layer.contentBlocks[index] = block
                }
            }
        }
        _currentBoard.value = _currentBoard.value
        markDirty()
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveContentBlock(block)
        }
    }

    fun duplicateContentBlock(blockId: String) {
        val board = _currentBoard.value ?: return
        val layer = board.activeLayer
        val source = layer.contentBlocks.firstOrNull { it.id == blockId } ?: return
        val duplicate = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            posX = source.posX + 36f,
            posY = source.posY + 36f,
            zIndex = (layer.contentBlocks.maxOfOrNull { it.zIndex } ?: 0) + 1,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        addContentBlock(duplicate)
    }

    fun deleteContentBlock(blockId: String) {
        val notebook = _currentNotebook.value ?: return
        notebook.boards.forEach { board ->
            board.layers.forEach { layer ->
                layer.contentBlocks.removeAll { it.id == blockId }
                layer.linkEdges.removeAll { it.sourceBlockId == blockId || it.targetBlockId == blockId }
            }
        }
        _currentBoard.value = _currentBoard.value
        markDirty()
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteContentBlock(blockId)
        }
    }

    fun deleteContentBlocks(blockIds: Collection<String>) {
        if (blockIds.isEmpty()) return
        blockIds.forEach(::deleteContentBlock)
    }

    fun bringContentBlockToFront(blockId: String) {
        val board = _currentBoard.value ?: return
        val layer = board.activeLayer
        val index = layer.contentBlocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val nextZ = (layer.contentBlocks.maxOfOrNull { it.zIndex } ?: 0) + 1
        updateContentBlock(layer.contentBlocks[index].copy(zIndex = nextZ, updatedAt = System.currentTimeMillis()))
    }

    fun sendContentBlockToBack(blockId: String) {
        val board = _currentBoard.value ?: return
        val layer = board.activeLayer
        val index = layer.contentBlocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val prevZ = (layer.contentBlocks.minOfOrNull { it.zIndex } ?: 0) - 1
        updateContentBlock(layer.contentBlocks[index].copy(zIndex = prevZ, updatedAt = System.currentTimeMillis()))
    }

    fun addLinkEdge(edge: LinkEdge) {
        val board = _currentBoard.value ?: return
        board.activeLayer.linkEdges.removeAll { it.id == edge.id }
        board.activeLayer.linkEdges.add(edge)
        _currentBoard.value = board
        markDirty()
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveLinkEdge(edge)
        }
    }

    fun updateLinkEdge(edge: LinkEdge) {
        addLinkEdge(edge)
    }

    fun deleteLinkEdge(linkId: String) {
        val board = _currentBoard.value ?: return
        board.activeLayer.linkEdges.removeAll { it.id == linkId }
        _currentBoard.value = board
        markDirty()
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteLinkEdge(linkId)
        }
    }

    fun findRelatedBlocks(blockId: String): Pair<List<ContentBlock>, List<ContentBlock>> {
        val layer = _currentBoard.value?.activeLayer ?: return emptyList<ContentBlock>() to emptyList()
        val blocksById = layer.contentBlocks.associateBy { it.id }
        val incoming = layer.linkEdges.filter { it.targetBlockId == blockId }.mapNotNull { blocksById[it.sourceBlockId] }
        val outgoing = layer.linkEdges.filter { it.sourceBlockId == blockId }.mapNotNull { blocksById[it.targetBlockId] }
        return incoming to outgoing
    }

    fun searchBlocks(query: String): List<ContentBlock> {
        if (query.isBlank()) return emptyList()
        val normalized = query.lowercase(Locale.getDefault())
        return _currentNotebook.value?.boards
            ?.flatMap { it.layers }
            ?.flatMap { it.contentBlocks }
            ?.filter { block ->
                when (val content = block.content) {
                    is BlockContent.TextNote -> content.text.lowercase(Locale.getDefault()).contains(normalized)
                    is BlockContent.Markdown -> content.markdown.lowercase(Locale.getDefault()).contains(normalized)
                    is BlockContent.InteractiveText -> {
                        content.question.lowercase(Locale.getDefault()).contains(normalized) ||
                            content.alternatives.any { it.text.lowercase(Locale.getDefault()).contains(normalized) } ||
                            content.explanation.lowercase(Locale.getDefault()).contains(normalized)
                    }
                    is BlockContent.Flashcard -> content.previewText.lowercase(Locale.getDefault()).contains(normalized)
                    is BlockContent.Image -> content.displayName.lowercase(Locale.getDefault()).contains(normalized)
                    is BlockContent.Pdf -> content.displayName.lowercase(Locale.getDefault()).contains(normalized)
                }
            }
            ?.sortedByDescending { it.updatedAt }
            .orEmpty()
    }



    private fun markDirty() {

        dirty = true

    }



    private fun startAutoSave() {

        autoSaveJob?.cancel()

        autoSaveJob = viewModelScope.launch {

            while (true) {

                kotlinx.coroutines.delay(AUTO_SAVE_INTERVAL_MS)

                saveCurrentState(force = true)

            }

        }

    }



    /** Call when app goes to background — ensures nothing is lost. */

    fun flushSave() {

        viewModelScope.launch {

            saveCurrentState(force = true)

        }

    }



    private fun flushSaveSync() {

        if (!dirty) {

            persistSession()

            return

        }

        viewModelScope.launch {

            saveCurrentState()

        }

    }



    private suspend fun saveCurrentState(force: Boolean = false) {

        try {

            if (!force && !dirty) return

            val notebook = _currentNotebook.value ?: return

            val board = _currentBoard.value ?: return

            withContext(Dispatchers.IO) {

                repository.saveNotebookMetadata(notebook)

                repository.saveBoard(board, notebook.id)

            }

            dirty = false

            persistSession()

            DebugDiagnostics.onSaveCompleted()

        } catch (e: Exception) {

            android.util.Log.e("CNNT", "Auto-save error: ${e.message}", e)

        }

    }



    private fun persistSession() {

        val notebook = _currentNotebook.value ?: return

        val board = _currentBoard.value ?: return

        sessionStore.saveSession(notebook.id, board.id, activeBoardIndex())

    }



    override fun onCleared() {

        super.onCleared()

        autoSaveJob?.cancel()

        NotebookSnapshot.unregister()

        ocrEngine.close()

    }



    companion object {

        private const val AUTO_SAVE_INTERVAL_MS = 15_000L

    }

}



class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {

            @Suppress("UNCHECKED_CAST")

            return MainViewModel(application) as T

        }

        throw IllegalArgumentException("Unknown ViewModel class")

    }

}

