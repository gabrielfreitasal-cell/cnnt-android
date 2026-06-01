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

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext



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

        markDirty()

        viewModelScope.launch(Dispatchers.IO) {

            repository.saveSpatialObject(obj, obj.layerId)

        }

    }



    fun addFlashcard(flashcard: Flashcard) {

        viewModelScope.launch {

            repository.saveFlashcard(flashcard)

            _flashcards.value = _flashcards.value + flashcard

        }

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

