package com.cnnt.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.cnnt.app.R
import com.cnnt.app.canvas.CanvasMode
import com.cnnt.app.canvas.InfiniteCanvasView
import com.cnnt.app.data.model.*
import com.cnnt.app.databinding.ActivityMainBinding
import com.cnnt.app.ui.block.BlockContentFactory
import com.cnnt.app.ui.block.BlocksOverlayLayout
import com.cnnt.app.ui.block.BlockView
import com.cnnt.app.ui.block.LinksOverlayView
import com.cnnt.app.ui.toolbar.ToolbarManager
import com.cnnt.app.ui.sidebar.SidebarManager
import com.cnnt.app.ui.dialogs.BrushPickerDialog
import com.cnnt.app.ui.dialogs.ColorPickerDialog
import com.cnnt.app.ui.dialogs.ExportDialog
import com.cnnt.app.ui.dialogs.FlashcardDialog
import com.cnnt.app.ui.dialogs.OcrDialog
import com.cnnt.app.debug.DebugDiagnostics
import com.cnnt.app.ink.HandwritingRecognizer
import java.util.UUID
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), InfiniteCanvasView.CanvasListener, BlocksOverlayLayout.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var toolbarManager: ToolbarManager
    private lateinit var sidebarManager: SidebarManager
    private lateinit var handwritingRecognizer: HandwritingRecognizer

    private var focusModeActive = false
    private var handwritingReady = false
    private var currentSelectionIds: List<String> = emptyList()
    private var exportDialog: ExportDialog? = null
    private var selectedBlockIds = linkedSetOf<String>()
    private var selectedLinkId: String? = null
    private var linkingSourceBlockId: String? = null
    private var pendingMoveBlockId: String? = null
    private var pendingResizeBlockId: String? = null
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var linkModeEnabled = false
    private var miniMapVisible = false

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
        setupBlockOverlays()
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
        binding.canvasView.setOnDragListener { _, event ->
            when (event.action) {
                android.view.DragEvent.ACTION_DROP -> {
                    val item = event.clipData?.getItemAt(0)?.text?.toString() ?: return@setOnDragListener false
                    val type = runCatching { BlockType.fromKey(item) }.getOrNull() ?: return@setOnDragListener false
                    createContentBlockFromDrop(type, event.x, event.y)
                    true
                }
                else -> true
            }
        }
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
        toolbarManager.onDeleteSelectionClicked = {
            if (selectedBlockIds.isNotEmpty()) {
                deleteSelectedBlocks()
            } else {
                deleteCurrentSelection()
            }
        }
        toolbarManager.onLinkModeToggled = {
            linkModeEnabled = !linkModeEnabled
            Toast.makeText(this, if (linkModeEnabled) "Modo ligação ativo" else "Modo ligação desativado", Toast.LENGTH_SHORT).show()
            renderBlocksAndLinks()
        }
        toolbarManager.onCenterCanvasClicked = {
            val viewport = binding.canvasView.getVisibleWorldRect()
            binding.canvasView.centerOnWorldPoint(viewport.centerX(), viewport.centerY())
            syncOverlaysWithCanvas()
        }
        toolbarManager.onFitAllClicked = {
            binding.canvasView.zoomToFit()
            syncOverlaysWithCanvas()
        }
        toolbarManager.onMiniMapToggled = {
            miniMapVisible = !miniMapVisible
            binding.miniMapView.visibility = if (miniMapVisible) View.VISIBLE else View.GONE
            if (miniMapVisible) {
                syncMiniMap()
            }
        }
        binding.btnSearchBlocks.setOnClickListener { showBlockSearchDialog() }
        toolbarManager.updateActiveMode(CanvasMode.DRAW)
        toolbarManager.setDeleteSelectionVisible(false)
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
            createContentBlockNearViewportCenter(blockType)
        }
        sidebarManager.onInsertHandwritingBlock = {
            insertHandwritingBlock()
        }
    }

    private fun setupBlockOverlays() {
        binding.blocksOverlay.setListener(this)
        binding.linksOverlay.onLinkTapped = { edge ->
            selectedLinkId = edge.id
            renderBlocksAndLinks()
            showLinkActions(edge)
        }
        binding.linksOverlay.onLinkCreationFinished = { sourceId, targetId ->
            createLinkEdge(sourceId, targetId)
        }
        binding.miniMapView.onNavigate = { worldX, worldY ->
            binding.canvasView.centerOnWorldPoint(worldX, worldY)
            syncOverlaysWithCanvas()
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.currentBoard.collect { board ->
                board?.let {
                    binding.canvasView.setBoard(it)
                    renderBlocksAndLinks()
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
                    renderBlocksAndLinks()
                    updateHeaderStatus()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentBoard.collect {
                val notebook = viewModel.currentNotebook.value ?: return@collect
                sidebarManager.updatePages(notebook.boards, viewModel.activeBoardIndex())
                renderBlocksAndLinks()
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

    private fun createContentBlockNearViewportCenter(type: BlockType) {
        val notebook = viewModel.currentNotebook.value ?: return
        val layer = viewModel.currentBoard.value?.activeLayer ?: return
        val viewport = binding.canvasView.getVisibleWorldRect()
        val centerX = viewport.centerX()
        val centerY = viewport.centerY()
        val block = BlockContentFactory.create(
            type = type,
            notebookId = notebook.id,
            layerId = layer.id,
            x = centerX - 120f,
            y = centerY - 80f,
            zIndex = (layer.contentBlocks.maxOfOrNull { it.zIndex } ?: 0) + 1
        )
        viewModel.addContentBlock(block)
        selectedBlockIds.clear()
        selectedBlockIds.add(block.id)
        binding.blocksOverlay.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        renderBlocksAndLinks()
    }

    private fun createContentBlockFromDrop(type: BlockType, screenX: Float, screenY: Float) {
        val notebook = viewModel.currentNotebook.value ?: return
        val layer = viewModel.currentBoard.value?.activeLayer ?: return
        val point = binding.canvasView.screenPointToCanvasPoint(screenX, screenY)
        val block = BlockContentFactory.create(
            type = type,
            notebookId = notebook.id,
            layerId = layer.id,
            x = point.x,
            y = point.y,
            zIndex = (layer.contentBlocks.maxOfOrNull { it.zIndex } ?: 0) + 1
        )
        viewModel.addContentBlock(block)
        selectedBlockIds.clear()
        selectedBlockIds.add(block.id)
        binding.blocksOverlay.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        renderBlocksAndLinks()
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
        exportDialog = ExportDialog(this, viewModel).also { it.show() }
    }

    private fun showOcrDialog() {
        OcrDialog(
            this,
            viewModel,
            bitmapProvider = { captureCanvasBitmap() },
            requestRegionSelection = {
                binding.canvasView.beginOcrRegionSelection()
                Toast.makeText(this, "Desenhe a região no canvas para OCR", Toast.LENGTH_SHORT).show()
            },
            regionBitmapProvider = { region ->
                captureCanvasBitmap()?.let { bitmap ->
                    cropBitmapSafely(bitmap, region)
                }
            },
            consumeSelectedRegion = {
                binding.canvasView.consumePendingOcrRegion()
            }
        ).show()
    }

    private fun showFlashcardDialog() {
        FlashcardDialog(this, viewModel).apply {
            setOnDismissListener {
                binding.canvasView.invalidate()
                renderBlocksAndLinks()
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
        syncOverlaysWithCanvas()
        updateHeaderStatus()
    }

    override fun onModeChanged(mode: CanvasMode, isTemporary: Boolean) {
        toolbarManager.updateActiveMode(mode, isTemporary)
    }

    override fun onSelectionChanged(selectedObjects: List<SpatialObject>) {
        currentSelectionIds = selectedObjects.map { it.id }
        toolbarManager.setDeleteSelectionVisible(selectedObjects.isNotEmpty() || selectedBlockIds.isNotEmpty())
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

    private fun cropBitmapSafely(source: Bitmap, region: Rect): Bitmap {
        val left = region.left.coerceIn(0, source.width - 1)
        val top = region.top.coerceIn(0, source.height - 1)
        val right = region.right.coerceIn(left + 1, source.width)
        val bottom = region.bottom.coerceIn(top + 1, source.height)
        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        source.recycle()
        return cropped
    }

    private fun deleteCurrentSelection() {
        val deletedIds = binding.canvasView.deleteSelectedObjects()
        if (deletedIds.isEmpty()) return
        viewModel.deleteSpatialObjects(deletedIds)
        currentSelectionIds = emptyList()
        toolbarManager.setDeleteSelectionVisible(false)
        Toast.makeText(this, "Seleção excluída", Toast.LENGTH_SHORT).show()
    }

    private fun deleteSelectedBlocks() {
        if (selectedBlockIds.isEmpty()) return
        val ids = selectedBlockIds.toList()
        selectedBlockIds.clear()
        viewModel.deleteContentBlocks(ids)
        toolbarManager.setDeleteSelectionVisible(currentSelectionIds.isNotEmpty())
        renderBlocksAndLinks()
        Toast.makeText(this, "Blocos excluídos", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_DELETE_SELECTION, 0, "Excluir seleção")
        menu.add(0, MENU_LASSO_FREE, 1, "Laço livre")
        menu.add(0, MENU_LASSO_RECTANGLE, 2, "Laço retangular")
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(MENU_DELETE_SELECTION)?.isVisible = currentSelectionIds.isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_DELETE_SELECTION -> {
                if (selectedBlockIds.isNotEmpty()) deleteSelectedBlocks() else deleteCurrentSelection()
                true
            }
            MENU_LASSO_FREE -> {
                binding.canvasView.setLassoMode(com.cnnt.app.canvas.LassoMode.FREE)
                binding.canvasView.setMode(CanvasMode.LASSO)
                true
            }
            MENU_LASSO_RECTANGLE -> {
                binding.canvasView.setLassoMode(com.cnnt.app.canvas.LassoMode.RECTANGLE)
                binding.canvasView.setMode(CanvasMode.LASSO)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (exportDialog?.handleActivityResult(requestCode, resultCode, data) == true) {
            return
        }
        val selectedBlockId = selectedBlockIds.firstOrNull() ?: return
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK) {
            data?.data?.toString()?.let { uri ->
                updateSelectedBlockFile(selectedBlockId, BlockType.Image, uri)
            }
        }
        if (requestCode == REQUEST_PICK_PDF && resultCode == RESULT_OK) {
            data?.data?.toString()?.let { uri ->
                updateSelectedBlockFile(selectedBlockId, BlockType.Pdf, uri)
            }
        }
    }

    companion object {
        private const val MENU_DELETE_SELECTION = 1001
        private const val MENU_LASSO_FREE = 1002
        private const val MENU_LASSO_RECTANGLE = 1003
        private const val REQUEST_PICK_IMAGE = 5101
        private const val REQUEST_PICK_PDF = 5102
    }

    private fun renderBlocksAndLinks() {
        val blocks = viewModel.currentLayerBlocks()
        val links = viewModel.currentLayerLinks()
        binding.blocksOverlay.submitBlocks(
            blocks = blocks,
            links = links,
            selectedIds = selectedBlockIds,
            linkingSourceId = linkingSourceBlockId
        )
        binding.linksOverlay.submit(
            blocks = binding.blocksOverlay.screenBounds(),
            links = links,
            selectedLinkId = selectedLinkId
        )
        toolbarManager.setDeleteSelectionVisible(currentSelectionIds.isNotEmpty() || selectedBlockIds.isNotEmpty())
        updateBacklinksPanel()
        syncMiniMap()
    }

    private fun syncOverlaysWithCanvas() {
        val (scale, tx, ty) = binding.canvasView.getTransform()
        binding.blocksOverlay.updateTransform(scale, tx, ty)
        binding.linksOverlay.submit(
            blocks = binding.blocksOverlay.screenBounds(),
            links = viewModel.currentLayerLinks(),
            selectedLinkId = selectedLinkId
        )
        syncMiniMap()
    }

    private fun syncMiniMap() {
        if (!miniMapVisible) return
        binding.miniMapView.submit(
            viewModel.currentLayerBlocks(),
            binding.canvasView.getVisibleWorldRect()
        )
    }

    private fun updateBacklinksPanel() {
        val selectedId = selectedBlockIds.firstOrNull()
        if (selectedId == null) {
            binding.backlinksPanel.visibility = View.GONE
            return
        }
        val (incoming, outgoing) = viewModel.findRelatedBlocks(selectedId)
        binding.backlinksPanel.visibility = View.VISIBLE
        val incomingText = if (incoming.isEmpty()) "Nenhuma entrada" else incoming.joinToString("\n") { "← ${blockLabel(it)}" }
        val outgoingText = if (outgoing.isEmpty()) "Nenhuma saída" else outgoing.joinToString("\n") { "→ ${blockLabel(it)}" }
        binding.backlinksContent.text = "$incomingText\n\n$outgoingText"
    }

    private fun blockLabel(block: ContentBlock): String {
        return when (val content = block.content) {
            is BlockContent.TextNote -> content.text.takeIf { it.isNotBlank() } ?: "Texto"
            is BlockContent.Markdown -> content.markdown.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: "Markdown"
            is BlockContent.Flashcard -> content.previewText.ifBlank { "Flashcard" }
            is BlockContent.InteractiveText -> content.question.ifBlank { "Questão interativa" }
            is BlockContent.Image -> content.displayName.ifBlank { "Imagem" }
            is BlockContent.Pdf -> content.displayName.ifBlank { "PDF" }
        }
    }

    private fun createLinkEdge(sourceId: String, targetId: String) {
        val edge = LinkEdge(
            id = UUID.randomUUID().toString(),
            sourceBlockId = sourceId,
            targetBlockId = targetId,
            label = ""
        )
        viewModel.addLinkEdge(edge)
        selectedLinkId = edge.id
        linkingSourceBlockId = null
        binding.linksOverlay.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        renderBlocksAndLinks()
        showLinkActions(edge)
    }

    private fun showLinkActions(edge: LinkEdge) {
        val actions = arrayOf("Editar rótulo", "Excluir ligação")
        AlertDialog.Builder(this, R.style.Theme_CNNT_Dialog)
            .setTitle("Ligação")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showLinkLabelDialog(edge)
                    1 -> {
                        viewModel.deleteLinkEdge(edge.id)
                        selectedLinkId = null
                        renderBlocksAndLinks()
                    }
                }
            }
            .show()
    }

    private fun showLinkLabelDialog(edge: LinkEdge) {
        val input = EditText(this).apply {
            setText(edge.label)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this, R.style.Theme_CNNT_Dialog)
            .setTitle("Rótulo da ligação")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                viewModel.updateLinkEdge(edge.copy(label = input.text.toString()))
                renderBlocksAndLinks()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showBlockSearchDialog() {
        val input = EditText(this).apply {
            hint = "Buscar em texto, markdown e questões"
        }
        AlertDialog.Builder(this, R.style.Theme_CNNT_Dialog)
            .setTitle("Buscar blocos")
            .setView(input)
            .setPositiveButton("Buscar") { _, _ ->
                val results = viewModel.searchBlocks(input.text.toString())
                showSearchResults(results)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showSearchResults(results: List<ContentBlock>) {
        if (results.isEmpty()) {
            Toast.makeText(this, "Nenhum bloco encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        val listView = ListView(this)
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, results.map(::blockLabel))
        AlertDialog.Builder(this, R.style.Theme_CNNT_Dialog)
            .setTitle("Resultados")
            .setView(listView)
            .setNegativeButton("Fechar", null)
            .show()
            .also { dialog ->
                listView.setOnItemClickListener { _, _, position, _ ->
                    val block = results[position]
                    focusBlock(block)
                    dialog.dismiss()
                }
            }
    }

    private fun focusBlock(block: ContentBlock) {
        binding.canvasView.centerOnWorldPoint(block.posX + block.width / 2f, block.posY + block.height / 2f)
        selectedBlockIds.clear()
        selectedBlockIds.add(block.id)
        renderBlocksAndLinks()
    }

    private fun updateSelectedBlockFile(blockId: String, type: BlockType, uri: String) {
        val block = viewModel.currentLayerBlocks().firstOrNull { it.id == blockId } ?: return
        val updated = when (type) {
            BlockType.Image -> block.copy(content = BlockContent.Image(uri = uri, displayName = "Imagem"))
            BlockType.Pdf -> block.copy(content = BlockContent.Pdf(uri = uri, displayName = "PDF"))
            else -> block
        }.copy(updatedAt = System.currentTimeMillis())
        viewModel.updateContentBlock(updated)
        renderBlocksAndLinks()
    }

    private fun requestFileForBlock(blockId: String, type: BlockType) {
        selectedBlockIds.clear()
        selectedBlockIds.add(blockId)
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            this.type = if (type == BlockType.Image) "image/*" else "application/pdf"
        }
        startActivityForResult(intent, if (type == BlockType.Image) REQUEST_PICK_IMAGE else REQUEST_PICK_PDF)
    }

    override fun onBlockSelected(blockId: String) {
        selectedBlockIds.clear()
        selectedBlockIds.add(blockId)
        selectedLinkId = null
        renderBlocksAndLinks()
    }

    override fun onBlockUpdated(block: ContentBlock) {
        viewModel.updateContentBlock(block)
        renderBlocksAndLinks()
    }

    override fun onBlockMoved(blockId: String, rawDx: Float, rawDy: Float) {
        if (pendingMoveBlockId != blockId) {
            pendingMoveBlockId = blockId
            lastRawX = rawDx
            lastRawY = rawDy
            return
        }
        val deltaX = (rawDx - lastRawX) / binding.canvasView.getTransform().first
        val deltaY = (rawDy - lastRawY) / binding.canvasView.getTransform().first
        lastRawX = rawDx
        lastRawY = rawDy
        val targetIds = if (selectedBlockIds.contains(blockId)) selectedBlockIds else linkedSetOf(blockId)
        val blocks = viewModel.currentLayerBlocks().associateBy { it.id }
        targetIds.forEach { id ->
            val block = blocks[id] ?: return@forEach
            viewModel.updateContentBlock(
                block.copy(
                    posX = block.posX + deltaX,
                    posY = block.posY + deltaY,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        renderBlocksAndLinks()
    }

    override fun onBlockMoveFinished(blockId: String) {
        pendingMoveBlockId = null
    }

    override fun onResize(blockId: String, direction: BlockView.ResizeDirection, rawDx: Float, rawDy: Float) {
        if (pendingResizeBlockId != blockId) {
            pendingResizeBlockId = blockId
            lastRawX = rawDx
            lastRawY = rawDy
            return
        }
        val deltaX = (rawDx - lastRawX) / binding.canvasView.getTransform().first
        val deltaY = (rawDy - lastRawY) / binding.canvasView.getTransform().first
        lastRawX = rawDx
        lastRawY = rawDy
        val block = viewModel.currentLayerBlocks().firstOrNull { it.id == blockId } ?: return
        var newX = block.posX
        var newY = block.posY
        var newWidth = block.width
        var newHeight = block.height
        when (direction) {
            BlockView.ResizeDirection.TOP_LEFT -> {
                newX += deltaX
                newY += deltaY
                newWidth -= deltaX
                newHeight -= deltaY
            }
            BlockView.ResizeDirection.TOP -> {
                newY += deltaY
                newHeight -= deltaY
            }
            BlockView.ResizeDirection.TOP_RIGHT -> {
                newY += deltaY
                newWidth += deltaX
                newHeight -= deltaY
            }
            BlockView.ResizeDirection.RIGHT -> newWidth += deltaX
            BlockView.ResizeDirection.BOTTOM_RIGHT -> {
                newWidth += deltaX
                newHeight += deltaY
            }
            BlockView.ResizeDirection.BOTTOM -> newHeight += deltaY
            BlockView.ResizeDirection.BOTTOM_LEFT -> {
                newX += deltaX
                newWidth -= deltaX
                newHeight += deltaY
            }
            BlockView.ResizeDirection.LEFT -> {
                newX += deltaX
                newWidth -= deltaX
            }
        }
        val updated = block.copy(
            posX = newX,
            posY = newY,
            width = newWidth.coerceAtLeast(120f),
            height = newHeight.coerceAtLeast(80f),
            updatedAt = System.currentTimeMillis()
        )
        viewModel.updateContentBlock(updated)
        renderBlocksAndLinks()
    }

    override fun onResizeFinished(blockId: String) {
        pendingResizeBlockId = null
    }

    override fun onStartLink(blockId: String, anchorX: Float, anchorY: Float) {
        linkingSourceBlockId = blockId
        binding.linksOverlay.startLinkPreview(blockId, PointF(anchorX, anchorY))
        renderBlocksAndLinks()
    }

    override fun onLinkMove(rawX: Float, rawY: Float) {
        binding.linksOverlay.updateLinkPreview(PointF(rawX, rawY))
    }

    override fun onLinkFinish(rawX: Float, rawY: Float) {
        binding.linksOverlay.finishLinkPreview(PointF(rawX, rawY))
    }

    override fun onContextAction(blockId: String, action: BlockView.BlockContextAction) {
        when (action) {
            BlockView.BlockContextAction.EDIT -> {
                val block = viewModel.currentLayerBlocks().firstOrNull { it.id == blockId } ?: return
                when (block.type) {
                    BlockType.Image, BlockType.Pdf -> requestFileForBlock(blockId, block.type)
                    BlockType.Flashcard -> showFlashcardDialog()
                    BlockType.InteractiveText -> onBlockSelected(blockId)
                    BlockType.Markdown, BlockType.Text -> onBlockSelected(blockId)
                }
            }
            BlockView.BlockContextAction.DUPLICATE -> viewModel.duplicateContentBlock(blockId)
            BlockView.BlockContextAction.BRING_TO_FRONT -> viewModel.bringContentBlockToFront(blockId)
            BlockView.BlockContextAction.SEND_TO_BACK -> viewModel.sendContentBlockToBack(blockId)
            BlockView.BlockContextAction.DELETE -> viewModel.deleteContentBlock(blockId)
        }
        renderBlocksAndLinks()
    }

    override fun onBadgeClicked(blockId: String, incoming: Boolean) {
        val (incomingBlocks, outgoingBlocks) = viewModel.findRelatedBlocks(blockId)
        val targets = if (incoming) incomingBlocks else outgoingBlocks
        if (targets.isEmpty()) {
            Toast.makeText(this, "Nenhum bloco relacionado", Toast.LENGTH_SHORT).show()
            return
        }
        showRelatedBlocksDialog(targets)
    }

    override fun onRequestFile(blockId: String, type: BlockType) {
        requestFileForBlock(blockId, type)
    }

    override fun onOpenFullscreenPdf(block: ContentBlock) {
        Toast.makeText(this, "Leitor fullscreen de PDF em breve", Toast.LENGTH_SHORT).show()
    }

    private fun showRelatedBlocksDialog(blocks: List<ContentBlock>) {
        val listView = ListView(this)
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, blocks.map(::blockLabel))
        AlertDialog.Builder(this, R.style.Theme_CNNT_Dialog)
            .setTitle("Blocos relacionados")
            .setView(listView)
            .setNegativeButton("Fechar", null)
            .show()
            .also { dialog ->
                listView.setOnItemClickListener { _, _, position, _ ->
                    focusBlock(blocks[position])
                    dialog.dismiss()
                }
            }
    }
}
