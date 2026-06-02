package com.cnnt.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.cnnt.app.canvas.CanvasMode
import com.cnnt.app.canvas.InfiniteCanvasView
import com.cnnt.app.data.model.*
import com.cnnt.app.databinding.ActivityMainBinding
import com.cnnt.app.ui.toolbar.ToolbarManager
import com.cnnt.app.ui.sidebar.SidebarManager
import com.cnnt.app.ui.dialogs.BrushPickerDialog
import com.cnnt.app.ui.dialogs.ColorPickerDialog
import com.cnnt.app.ui.dialogs.ExportDialog
import com.cnnt.app.ui.dialogs.FlashcardDialog
import com.cnnt.app.ui.dialogs.OcrDialog
import com.cnnt.app.debug.DebugDiagnostics
import com.cnnt.app.ink.HandwritingRecognizer
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), InfiniteCanvasView.CanvasListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var toolbarManager: ToolbarManager
    private lateinit var sidebarManager: SidebarManager
    private lateinit var handwritingRecognizer: HandwritingRecognizer

    private var focusModeActive = false
    private var handwritingReady = false

    // Accumulate strokes per block for multi-stroke recognition
    private val pendingHandwritingStrokes = mutableMapOf<String, MutableList<List<HandwritingRecognizer.StrokePointData>>>()
    private val handwritingTimers = mutableMapOf<String, android.os.Handler>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, MainViewModelFactory(application))
            .get(MainViewModel::class.java)

        setupImmersiveMode()
        setupCanvas()
        setupToolbar()
        setupSidebar()
        setupObservers()
        setupQuickActions()
        setupDebugOverlay()

        // Load or create default notebook
        viewModel.loadOrCreateDefaultNotebook()

        // Initialize handwriting recognition
        handwritingRecognizer = HandwritingRecognizer(this)
        handwritingRecognizer.initialize("pt-BR") { ready ->
            handwritingReady = ready
            if (!ready) {
                // Try English as fallback
                handwritingRecognizer.initialize("en-US") { fallbackReady ->
                    handwritingReady = fallbackReady
                }
            }
        }
    }

    private fun setupImmersiveMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("CNNT", "Immersive mode error: ${e.message}")
        }
    }

    private fun setupCanvas() {
        binding.canvasView.listener = this
        binding.canvasView.setPalmRejection(true)
        binding.canvasView.setFingerDrawing(false)
    }

    private fun setupToolbar() {
        toolbarManager = ToolbarManager(binding, this)
        toolbarManager.onBrushSelected = { brush ->
            viewModel.setCurrentBrush(brush)
            binding.canvasView.setBrush(brush)
            binding.canvasView.setMode(CanvasMode.DRAW)
            toolbarManager.updateActiveMode(CanvasMode.DRAW)
        }
        toolbarManager.onColorSelected = { color ->
            viewModel.setCurrentColor(color)
            binding.canvasView.setDrawColor(color)
        }
        toolbarManager.onSizeChanged = { sizeMm ->
            binding.canvasView.setDrawSizeMm(sizeMm)
        }
        toolbarManager.onModeSelected = { mode ->
            binding.canvasView.setMode(mode)
            toolbarManager.updateActiveMode(mode)
        }
        toolbarManager.onLassoModeSelected = { lassoMode ->
            binding.canvasView.setLassoMode(lassoMode)
        }
        toolbarManager.onUndoClicked = {
            binding.canvasView.undo()
        }
        toolbarManager.onRedoClicked = {
            binding.canvasView.redo()
        }
        toolbarManager.onFocusModeToggled = {
            toggleFocusMode()
        }
        toolbarManager.onExportClicked = {
            showExportDialog()
        }
        toolbarManager.onOcrClicked = {
            showOcrDialog()
        }
        toolbarManager.onFlashcardClicked = {
            showFlashcardDialog()
        }
        toolbarManager.onBrushSettingsChanged = { brush ->
            viewModel.setCurrentBrush(brush)
            binding.canvasView.setBrush(brush)
        }
        toolbarManager.updateActiveMode(CanvasMode.DRAW)
    }

    private fun setupSidebar() {
        sidebarManager = SidebarManager(binding, this)
        sidebarManager.onPageSelected = { boardIndex ->
            viewModel.switchBoard(boardIndex)
        }
        sidebarManager.onNewPageClicked = {
            viewModel.addNewBoard()
        }
        sidebarManager.onInsertBlockClicked = { blockType ->
            insertBlock(blockType)
        }
        sidebarManager.onInsertHandwritingBlock = {
            insertHandwritingBlock()
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.currentBoard.collect { board ->
                board?.let {
                    binding.canvasView.setBoard(it)
                    updateHeaderStatus()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentBrush.collect { brush ->
                toolbarManager.updateBrushIndicator(brush)
            }
        }

        lifecycleScope.launch {
            viewModel.currentColor.collect { color ->
                toolbarManager.updateColorIndicator(color)
            }
        }

        lifecycleScope.launch {
            viewModel.currentNotebook.collect { notebook ->
                notebook?.let {
                    sidebarManager.updatePages(it.boards, viewModel.activeBoardIndex())
                    updateHeaderStatus()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentBoard.collect {
                val notebook = viewModel.currentNotebook.value ?: return@collect
                sidebarManager.updatePages(notebook.boards, viewModel.activeBoardIndex())
                updateHeaderStatus()
            }
        }
    }

    private fun toggleFocusMode() {
        focusModeActive = !focusModeActive
        if (focusModeActive) {
            binding.toolbarScroll.visibility = View.GONE
            binding.topHeaderBar.visibility = View.GONE
        } else {
            binding.toolbarScroll.visibility = View.VISIBLE
            binding.topHeaderBar.visibility = View.VISIBLE
        }
    }

    private fun setupQuickActions() {
        binding.noteTitle.setOnLongClickListener {
            binding.canvasView.zoomToFit()
            Toast.makeText(this, "Canvas ajustado à tela", Toast.LENGTH_SHORT).show()
            true
        }

        binding.sidebarToggle.setOnLongClickListener {
            binding.canvasView.resetView()
            Toast.makeText(this, "Zoom redefinido", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun insertBlock(type: SpatialObjectType) {
        val obj = SpatialObject(
            type = type,
            x = 100f,
            y = 100f,
            width = 200f,
            height = 150f,
            content = when (type) {
                SpatialObjectType.TEXT -> ObjectContent.Text("", 14f)
                SpatialObjectType.CHECKLIST -> ObjectContent.Checklist(emptyList())
                else -> ObjectContent.Empty
            }
        )
        viewModel.addSpatialObject(obj)
        binding.canvasView.setMode(CanvasMode.SELECT)
        toolbarManager.updateActiveMode(CanvasMode.SELECT)
    }

    private fun insertHandwritingBlock() {
        // Place the block at the center of the current screen view
        val centerX = binding.canvasView.width / 2f
        val centerY = binding.canvasView.height / 2f
        binding.canvasView.addHandwritingBlock(centerX, centerY, 350f, 200f)
        binding.canvasView.setMode(CanvasMode.DRAW)
        toolbarManager.updateActiveMode(CanvasMode.DRAW)
        Toast.makeText(this, "Bloco de texto inserido. Escreva dentro!", Toast.LENGTH_SHORT).show()
    }

    private fun processHandwritingStroke(blockId: String, points: List<StrokePoint>) {
        if (!handwritingReady) {
            Toast.makeText(this, "Modelo de reconhecimento carregando...", Toast.LENGTH_SHORT).show()
            return
        }

        // Convert StrokePoints to recognition format
        val strokeData = points.map { p ->
            HandwritingRecognizer.StrokePointData(p.x, p.y, p.timestamp)
        }

        // Accumulate strokes per block (user writes multiple strokes per word)
        val blockStrokes = pendingHandwritingStrokes.getOrPut(blockId) { mutableListOf() }
        blockStrokes.add(strokeData)

        // Reset the debounce timer (wait for 1.2s of no new strokes before recognizing)
        val handler = handwritingTimers.getOrPut(blockId) { android.os.Handler(mainLooper) }
        handler.removeCallbacksAndMessages(null)

        binding.canvasView.setHandwritingBlockRecognizing(blockId, true)

        handler.postDelayed({
            val strokes = pendingHandwritingStrokes.remove(blockId) ?: return@postDelayed
            if (strokes.isEmpty()) return@postDelayed

            handwritingRecognizer.recognize(
                strokes = strokes,
                onResult = { text ->
                    runOnUiThread {
                        if (text.isNotBlank()) {
                            binding.canvasView.updateHandwritingBlockText(blockId, text)
                            // Remove the handwritten strokes from canvas (they're now text)
                            removeStrokesInBlock(blockId)
                        } else {
                            binding.canvasView.setHandwritingBlockRecognizing(blockId, false)
                        }
                    }
                },
                onError = { e ->
                    runOnUiThread {
                        binding.canvasView.setHandwritingBlockRecognizing(blockId, false)
                        Toast.makeText(this, "Erro no reconhecimento: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }, 1200)
    }

    private fun removeStrokesInBlock(blockId: String) {
        val board = viewModel.currentBoard.value ?: return
        val layer = board.activeLayer
        val block = layer.objects.find { it.id == blockId } ?: return

        // Remove strokes that fall within the block area
        val toRemove = layer.strokes.filter { stroke ->
            val points = stroke.points
            if (points.isEmpty()) return@filter false
            val avgX = points.map { it.x }.average().toFloat()
            val avgY = points.map { it.y }.average().toFloat()
            avgX >= block.x && avgX <= block.x + block.width &&
            avgY >= block.y && avgY <= block.y + block.height
        }
        layer.strokes.removeAll(toRemove.toSet())
        binding.canvasView.invalidate()
    }

    private fun showExportDialog() {
        ExportDialog(this, viewModel).show()
    }

    private fun showOcrDialog() {
        OcrDialog(this, viewModel) { captureCanvasBitmap() }.show()
    }

    private fun showFlashcardDialog() {
        FlashcardDialog(this, viewModel).apply {
            setOnDismissListener {
                binding.canvasView.invalidate()
            }
        }.show()
    }

    // CanvasListener implementations

    override fun onStrokeCompleted(stroke: Stroke) {
        viewModel.saveStroke(stroke)
        refreshDebugOverlay()
    }

    override fun onStrokeErased(strokeId: String) {
        viewModel.deleteStroke(strokeId)
    }

    override fun onObjectSelected(obj: SpatialObject?) {
        // Update UI to show object properties
    }

    override fun onObjectCreated(obj: SpatialObject) {
        viewModel.updateSpatialObject(obj)
    }

    override fun onObjectMoved(obj: SpatialObject, newX: Float, newY: Float) {
        viewModel.updateSpatialObject(obj)
    }

    override fun onCanvasTransformChanged(scale: Float, translateX: Float, translateY: Float) {
        updateHeaderStatus()
    }

    override fun onModeChanged(mode: CanvasMode, isTemporary: Boolean) {
        toolbarManager.updateActiveMode(mode, isTemporary)
    }

    override fun onFlashcardBlockTapped(obj: SpatialObject) {
        com.cnnt.app.ui.dialogs.CanvasFlashcardEditorDialog(this, viewModel, obj).apply {
            setOnDismissListener {
                binding.canvasView.invalidate()
            }
        }.show()
    }

    private fun captureCanvasBitmap(): Bitmap? {
        if (binding.canvasView.width <= 0 || binding.canvasView.height <= 0) return null
        val bitmap = Bitmap.createBitmap(
            binding.canvasView.width,
            binding.canvasView.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        binding.canvasView.draw(canvas)
        return bitmap
    }

    private fun updateHeaderStatus() {
        val notebook = viewModel.currentNotebook.value
        val page = viewModel.currentBoard.value
        if (notebook == null || page == null) {
            binding.noteTitle.text = getString(com.cnnt.app.R.string.app_name)
            return
        }
        val pageIndex = viewModel.activeBoardIndex() + 1
        val totalPages = notebook.boards.size
        val zoom = binding.canvasView.getZoomPercent().toInt()
        binding.noteTitle.text = "${notebook.name} • P$pageIndex/$totalPages • ${page.name} • ${zoom}%"
    }

    override fun onHandwritingStrokeInBlock(blockId: String, points: List<StrokePoint>) {
        processHandwritingStroke(blockId, points)
    }

    private var debugTapCount = 0

    private fun setupDebugOverlay() {
        binding.noteTitle.setOnClickListener {
            debugTapCount++
            if (debugTapCount >= 5) {
                debugTapCount = 0
                val on = DebugDiagnostics.toggle()
                Toast.makeText(
                    this,
                    if (on) "Modo diagnóstico ON (5 toques no título para desligar)" else "Diagnóstico OFF",
                    Toast.LENGTH_SHORT
                ).show()
                refreshDebugOverlay()
            }
        }
    }

    private fun refreshDebugOverlay() {
        DebugDiagnostics.updateOverlay(this, binding.debugOverlay, viewModel, binding.canvasView)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            binding.canvasView.trimMemory()
            viewModel.flushSave()
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.flushSave()
    }

    override fun onPause() {
        super.onPause()
        viewModel.flushSave()
    }

    override fun onDestroy() {
        super.onDestroy()
        handwritingRecognizer.close()
        handwritingTimers.values.forEach { it.removeCallbacksAndMessages(null) }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else if (focusModeActive) {
            toggleFocusMode()
        } else {
            super.onBackPressed()
        }
    }
}
