package com.cnnt.app.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.util.Log
import com.cnnt.app.data.model.Board
import com.cnnt.app.data.model.BrushPreset
import com.cnnt.app.data.model.Layer
import com.cnnt.app.data.model.ObjectStyle
import com.cnnt.app.data.model.SpatialObject
import com.cnnt.app.data.model.SpatialObjectType
import com.cnnt.app.data.model.Stroke
import com.cnnt.app.data.model.StrokePoint
import com.cnnt.app.ink.InkEngine
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class InfiniteCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface CanvasListener {
        fun onStrokeCompleted(stroke: Stroke)
        fun onStrokeErased(strokeId: String)
        fun onObjectSelected(obj: SpatialObject?)
        fun onObjectCreated(obj: SpatialObject)
        fun onObjectMoved(obj: SpatialObject, newX: Float, newY: Float)
        fun onCanvasTransformChanged(scale: Float, translateX: Float, translateY: Float)
        fun onHandwritingStrokeInBlock(blockId: String, points: List<StrokePoint>)
        fun onModeChanged(mode: CanvasMode, isTemporary: Boolean)
        fun onFlashcardBlockTapped(obj: SpatialObject)
        fun onSelectionChanged(selectedObjects: List<SpatialObject>)
    }

    var listener: CanvasListener? = null

    // Canvas transform
    private val canvasMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private var scale = CanvasScale.MIN_ZOOM_FACTOR
    private var translateX = 0f
    private var translateY = 0f
    private var brushSizeMm = CanvasScale.DEFAULT_BRUSH_MM
    private var lastDrawPointX = 0f
    private var lastDrawPointY = 0f

    // Drawing state
    private var currentMode: CanvasMode = CanvasMode.DRAW
    private var persistentMode: CanvasMode = CanvasMode.DRAW
    private var currentStroke: Stroke? = null
    private var currentBrush: BrushPreset = BrushPreset.gelPen()
    private var currentColor: Int = Color.WHITE
    private var currentSize: Float = 4f
    private var currentOpacity: Float = 1.0f

    // Board data
    private var board: Board? = null

    // Engine
    private val inkEngine = InkEngine()

    // Gesture handling
    private var isPanning = false
    private var isDrawing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activeStylusId = -1

    // Multi-touch zoom
    private val scaleDetector: ScaleGestureDetector
    private var isScaling = false

    // Eraser
    private var eraserRadius = 20f

    // Selection
    private var selectedObject: SpatialObject? = null
    private val selectedObjects = mutableListOf<SpatialObject>()
    private var isDraggingObject = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var groupDragAnchor = PointF()
    private val groupDragStartPositions = linkedMapOf<String, PointF>()

    // Lasso selection
    private val lassoPoints = mutableListOf<PointF>()
    private val lassoPath = Path()
    private var isLassoing = false
    private var lassoMode: LassoMode = LassoMode.FREE
    private var lassoStartPoint: PointF? = null
    private var pendingRegionSelection = false
    private var pendingRegionRect: RectF? = null

    // Palm rejection
    private var palmRejectionEnabled = true
    private var fingerDrawingEnabled = false

    // Undo/Redo (limited to prevent memory leak)
    private val undoStack = mutableListOf<CanvasAction>()
    private val redoStack = mutableListOf<CanvasAction>()
    private val maxUndoStackSize = 100

    // Background
    private val bgPaint = Paint().apply {
        color = 0xFF141414.toInt()
        style = Paint.Style.FILL
    }

    // Grid paint (subtle)
    private val gridPaint = Paint().apply {
        color = 0x0DFFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
    }

    // Layer cache (completed strokes rasterized in canvas space)
    private var layerCache: Bitmap? = null
    private var cacheCanvas: Canvas? = null
    private var cacheOffsetX = 0f
    private val maxCacheDimension = 2048
    private val maxCachedStrokeCount = 350
    private var bitmapCacheEnabled = true
    private var cacheOffsetY = 0f
    private var cacheValid = false
    private var cachedStrokeCount = 0

    // Cached brush lookup (avoid creating 12 objects per stroke per frame)
    private val cachedBrushMap: Map<String, BrushPreset> = BrushPreset.defaultBrushes().associateBy { it.id }
    private val defaultBrush = BrushPreset.gelPen()

    // Stylus button eraser
    private var stylusButtonDown = false
    private var stylusButtonDownStartedAt = 0L
    private var lastStylusShortPressUpTime = 0L
    private val stylusDoubleTapWindowMs = 300L
    private val stylusShortPressMaxMs = 300L

    // Eraser cursor position (screen coords for visual indicator)
    private var eraserCursorX = -1f
    private var eraserCursorY = -1f
    private var showEraserCursor = false
    private val eraserCursorPaint = Paint().apply {
        color = 0xAAFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    // Handwriting block tracking
    private var activeHandwritingBlockId: String? = null
    private val handwritingStrokePoints = mutableListOf<StrokePoint>()
    private var isWritingInBlock = false
    private var flashcardDragStart: PointF? = null
    private var flashcardPreviewRect: RectF? = null
    private var downSelectedObject: SpatialObject? = null
    private var objectTapCandidate = false
    private var draggedDuringSelection = false

    // Handwriting block paints
    private val hwBlockBorderPaint = Paint().apply {
        color = 0xFF00B0FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }
    private val hwTextPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 16f
        isAntiAlias = true
    }
    private val hwLabelPaint = Paint().apply {
        color = 0x88FFFFFF.toInt()
        textSize = 11f
        isAntiAlias = true
    }

    // Resize handles
    private var isResizingBlock = false
    private var resizeCorner = -1  // 0=TL, 1=TR, 2=BL, 3=BR
    private var resizeBlockId: String? = null

    // Reusable Paint objects for onDraw (avoid GC pressure)
    private val lassoPaint = Paint().apply {
        color = 0xFF00B0FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val selectPaint = Paint().apply {
        color = 0xFF00B0FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val handlePaint = Paint().apply {
        color = 0xFF00B0FF.toInt()
        style = Paint.Style.FILL
    }
    private val hwHandlePaint = Paint().apply {
        color = 0xFF00B0FF.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val objPaint = Paint().apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
    }

    init {
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
    }

    fun setBoard(board: Board) {
        this.board = board
        bgPaint.color = board.backgroundColor
        undoStack.clear()
        redoStack.clear()
        invalidateCache()
        rebuildStrokeCache()
        invalidate()
    }

    fun setMode(mode: CanvasMode) {
        setModeInternal(mode, isTemporary = false, updatePersistentMode = true)
    }

    private fun setModeInternal(
        mode: CanvasMode,
        isTemporary: Boolean,
        updatePersistentMode: Boolean
    ) {
        if (updatePersistentMode) {
            persistentMode = mode
        }
        currentMode = mode
        if (mode != CanvasMode.SELECT && mode != CanvasMode.LASSO && mode != CanvasMode.REGION_OCR) {
            selectedObject = null
            listener?.onObjectSelected(null)
        }
        if (mode != CanvasMode.LASSO && mode != CanvasMode.REGION_OCR) {
            clearMultiSelection()
            clearLassoOverlay()
        }
        listener?.onModeChanged(mode, isTemporary)
        invalidate()
    }

    fun setBrush(brush: BrushPreset) {
        currentBrush = brush
        brushSizeMm = CanvasScale.canvasPixelsToMm(brush.baseSize).coerceIn(
            CanvasScale.MIN_BRUSH_MM, CanvasScale.MAX_BRUSH_MM
        )
        currentSize = CanvasScale.brushSizeAtZoom(brushSizeMm, scale)
        currentOpacity = brush.opacity
    }

    fun setDrawColor(color: Int) {
        currentColor = color
    }

    /** @param sizeMm brush diameter in millimeters */
    fun setDrawSizeMm(sizeMm: Float) {
        brushSizeMm = sizeMm.coerceIn(CanvasScale.MIN_BRUSH_MM, CanvasScale.MAX_BRUSH_MM)
        currentSize = CanvasScale.brushSizeAtZoom(brushSizeMm, scale)
    }

    fun getBrushSizeMm(): Float = brushSizeMm

    fun getZoomPercent(): Float = CanvasScale.zoomFactorToPercent(scale)

    fun getTransform(): Triple<Float, Float, Float> = Triple(scale, translateX, translateY)

    fun screenPointToCanvasPoint(x: Float, y: Float): PointF = screenToCanvas(x, y)

    fun centerOnWorldPoint(worldX: Float, worldY: Float) {
        translateX = width / 2f - worldX * scale
        translateY = height / 2f - worldY * scale
        updateMatrix()
        invalidate()
    }

    fun getVisibleWorldRect(): RectF {
        return getVisibleCanvasRect()
    }

    fun setDrawOpacity(opacity: Float) {
        currentOpacity = opacity
    }

    fun setPalmRejection(enabled: Boolean) {
        palmRejectionEnabled = enabled
    }

    fun setFingerDrawing(enabled: Boolean) {
        fingerDrawingEnabled = enabled
    }

    fun setEraserRadius(radius: Float) {
        eraserRadius = radius
    }

    fun setLassoMode(mode: LassoMode) {
        lassoMode = mode
    }

    fun beginOcrRegionSelection() {
        pendingRegionSelection = true
        setModeInternal(CanvasMode.REGION_OCR, isTemporary = false, updatePersistentMode = false)
    }

    fun consumePendingOcrRegion(): Rect? {
        val rect = pendingRegionRect ?: return null
        pendingRegionRect = null
        return Rect(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt()
        )
    }

    fun hasSelectedObjects(): Boolean = selectedObjects.isNotEmpty()

    fun deleteSelectedObjects(): List<String> {
        val ids = selectedObjects.map { it.id }
        val layer = board?.activeLayer ?: return emptyList()
        if (ids.isEmpty()) return emptyList()
        layer.objects.removeAll { ids.contains(it.id) }
        clearMultiSelection()
        selectedObject = null
        listener?.onObjectSelected(null)
        invalidateCache()
        invalidate()
        return ids
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val action = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(action)
        when (action) {
            is CanvasAction.AddStroke -> {
                board?.activeLayer?.strokes?.removeAll { it.id == action.stroke.id }
            }
            is CanvasAction.RemoveStroke -> {
                board?.activeLayer?.strokes?.add(action.stroke)
            }
            is CanvasAction.MoveObject -> {
                val obj = board?.activeLayer?.objects?.find { it.id == action.objectId }
                if (obj != null) {
                    val idx = board!!.activeLayer.objects.indexOf(obj)
                    board!!.activeLayer.objects[idx] = obj.copy(x = action.oldX, y = action.oldY)
                }
            }
        }
        invalidateCache()
        rebuildStrokeCache()
        invalidate()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val action = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(action)
        when (action) {
            is CanvasAction.AddStroke -> {
                board?.activeLayer?.strokes?.add(action.stroke)
            }
            is CanvasAction.RemoveStroke -> {
                board?.activeLayer?.strokes?.removeAll { it.id == action.stroke.id }
            }
            is CanvasAction.MoveObject -> {
                val obj = board?.activeLayer?.objects?.find { it.id == action.objectId }
                if (obj != null) {
                    val idx = board!!.activeLayer.objects.indexOf(obj)
                    board!!.activeLayer.objects[idx] = obj.copy(x = action.newX, y = action.newY)
                }
            }
        }
        invalidateCache()
        rebuildStrokeCache()
        invalidate()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun resetView() {
        scale = CanvasScale.MIN_ZOOM_FACTOR
        translateX = 0f
        translateY = 0f
        applyBrushSizeForZoom()
        updateMatrix()
        invalidate()
    }

    fun zoomToFit() {
        val board = this.board ?: return
        val allStrokes = board.activeLayer.strokes
        if (allStrokes.isEmpty()) {
            resetView()
            return
        }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (stroke in allStrokes) {
            val bounds = stroke.getBounds()
            if (bounds[0] < minX) minX = bounds[0]
            if (bounds[1] < minY) minY = bounds[1]
            if (bounds[2] > maxX) maxX = bounds[2]
            if (bounds[3] > maxY) maxY = bounds[3]
        }
        val contentWidth = maxX - minX
        val contentHeight = maxY - minY
        if (contentWidth <= 0 || contentHeight <= 0) return

        val padding = 50f
        val scaleX = (width - padding * 2) / contentWidth
        val scaleY = (height - padding * 2) / contentHeight
        scale = minOf(scaleX, scaleY, CanvasScale.MAX_ZOOM_FACTOR)
            .coerceAtLeast(CanvasScale.MIN_ZOOM_FACTOR)
        translateX = width / 2f - (minX + contentWidth / 2f) * scale
        translateY = height / 2f - (minY + contentHeight / 2f) * scale
        applyBrushSizeForZoom()
        updateMatrix()
        invalidate()
    }

    private fun applyBrushSizeForZoom() {
        currentSize = CanvasScale.brushSizeAtZoom(brushSizeMm, scale)
    }

    private fun updateMatrix() {
        canvasMatrix.reset()
        canvasMatrix.postScale(scale, scale)
        canvasMatrix.postTranslate(translateX, translateY)
        canvasMatrix.invert(inverseMatrix)
        listener?.onCanvasTransformChanged(scale, translateX, translateY)
    }

    private fun screenToCanvas(x: Float, y: Float): PointF {
        val pts = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
      try {
        // Don't process scale gestures when stylus button is held (eraser mode)
        if (!stylusButtonDown) {
            scaleDetector.onTouchEvent(event)
        }
        if (isScaling) return true

        val toolType = event.getToolType(0)
        val isStylusInput = toolType == MotionEvent.TOOL_TYPE_STYLUS
        val isEraserInput = toolType == MotionEvent.TOOL_TYPE_ERASER
        val isFingerInput = toolType == MotionEvent.TOOL_TYPE_FINGER

        // Palm rejection: finger = pan only (unless finger drawing enabled)
        if (isFingerInput && palmRejectionEnabled && !fingerDrawingEnabled) {
            return handlePan(event)
        }

        // Stylus button eraser: hold button = erase, release = restore previous tool
        if (isStylusInput) {
            val buttonPressed = (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
            if (buttonPressed && !stylusButtonDown) {
                stylusButtonDown = true
                stylusButtonDownStartedAt = event.eventTime
                setModeInternal(CanvasMode.ERASE, isTemporary = true, updatePersistentMode = false)
            } else if (!buttonPressed && stylusButtonDown) {
                stylusButtonDown = false
                val shortPress = event.eventTime - stylusButtonDownStartedAt <= stylusShortPressMaxMs
                val isDoubleTap = shortPress &&
                    lastStylusShortPressUpTime > 0L &&
                    event.eventTime - lastStylusShortPressUpTime <= stylusDoubleTapWindowMs
                if (shortPress) {
                    lastStylusShortPressUpTime = event.eventTime
                }
                if (isDoubleTap) {
                    lassoMode = LassoMode.RECTANGLE
                    setModeInternal(CanvasMode.LASSO, isTemporary = false, updatePersistentMode = true)
                } else {
                    setModeInternal(persistentMode, isTemporary = false, updatePersistentMode = false)
                }
            }
        }

        // Eraser tool from stylus button or eraser end
        if (isEraserInput || (isStylusInput && currentMode == CanvasMode.ERASE)) {
            return handleErase(event)
        }

        when (currentMode) {
            CanvasMode.DRAW -> return handleDraw(event, isStylusInput || isFingerInput)
            CanvasMode.ERASE -> return handleErase(event)
            CanvasMode.SELECT -> return handleSelect(event)
            CanvasMode.LASSO -> return handleLasso(event)
            CanvasMode.FLASHCARD -> return handleFlashcard(event)
            CanvasMode.REGION_OCR -> return handleLasso(event)
            CanvasMode.PAN -> return handlePan(event)
            CanvasMode.INSERT -> return handleInsert(event)
        }
        return true
      } catch (e: Exception) {
          Log.e("CNNT", "onTouchEvent error: ${e.message}", e)
          return true
      }
    }

    private fun findHandwritingBlockAt(canvasX: Float, canvasY: Float): SpatialObject? {
        val layer = board?.activeLayer ?: return null
        return layer.objects.lastOrNull { obj ->
            obj.type == com.cnnt.app.data.model.SpatialObjectType.HANDWRITING &&
            canvasX >= obj.x && canvasX <= obj.x + obj.width &&
            canvasY >= obj.y && canvasY <= obj.y + obj.height
        }
    }

    private fun handleDraw(event: MotionEvent, validInput: Boolean): Boolean {
        if (!validInput) return handlePan(event)

        val canvasPoint = screenToCanvas(event.x, event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Check if starting inside a handwriting block
                val hwBlock = findHandwritingBlockAt(canvasPoint.x, canvasPoint.y)
                if (hwBlock != null) {
                    isWritingInBlock = true
                    activeHandwritingBlockId = hwBlock.id
                    handwritingStrokePoints.clear()
                    handwritingStrokePoints.add(StrokePoint(
                        x = canvasPoint.x, y = canvasPoint.y,
                        pressure = event.pressure,
                        timestamp = event.eventTime
                    ))
                    // Also draw the stroke visually
                }

                isDrawing = true
                val stroke = Stroke(
                    brushId = currentBrush.id,
                    color = currentColor,
                    size = currentSize,
                    opacity = currentOpacity,
                    layerId = board?.activeLayer?.id ?: ""
                )
                for (h in 0 until event.historySize) {
                    val hp = screenToCanvas(event.getHistoricalX(h), event.getHistoricalY(h))
                    stroke.addPoint(StrokePoint(
                        x = hp.x, y = hp.y,
                        pressure = event.getHistoricalPressure(h),
                        tiltX = event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, h),
                        orientation = event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, h),
                        timestamp = event.getHistoricalEventTime(h)
                    ))
                }
                stroke.addPoint(StrokePoint(
                    x = canvasPoint.x, y = canvasPoint.y,
                    pressure = event.pressure,
                    tiltX = event.getAxisValue(MotionEvent.AXIS_TILT),
                    orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION),
                    timestamp = event.eventTime
                ))
                lastDrawPointX = canvasPoint.x
                lastDrawPointY = canvasPoint.y
                currentStroke = stroke
                postInvalidateOnAnimation()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isWritingInBlock) {
                    for (h in 0 until event.historySize) {
                        val hp = screenToCanvas(event.getHistoricalX(h), event.getHistoricalY(h))
                        handwritingStrokePoints.add(StrokePoint(
                            x = hp.x, y = hp.y,
                            pressure = event.getHistoricalPressure(h),
                            timestamp = event.getHistoricalEventTime(h)
                        ))
                    }
                    handwritingStrokePoints.add(StrokePoint(
                        x = canvasPoint.x, y = canvasPoint.y,
                        pressure = event.pressure,
                        timestamp = event.eventTime
                    ))
                }
                currentStroke?.let { stroke ->
                    for (h in 0 until event.historySize) {
                        val hp = screenToCanvas(event.getHistoricalX(h), event.getHistoricalY(h))
                        if (shouldAddPoint(hp.x, hp.y)) {
                            stroke.addPoint(StrokePoint(
                                x = hp.x, y = hp.y,
                                pressure = event.getHistoricalPressure(h),
                                tiltX = event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, h),
                                orientation = event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, h),
                                timestamp = event.getHistoricalEventTime(h)
                            ))
                            lastDrawPointX = hp.x
                            lastDrawPointY = hp.y
                        }
                    }
                    if (shouldAddPoint(canvasPoint.x, canvasPoint.y)) {
                        stroke.addPoint(StrokePoint(
                            x = canvasPoint.x, y = canvasPoint.y,
                            pressure = event.pressure,
                            tiltX = event.getAxisValue(MotionEvent.AXIS_TILT),
                            orientation = event.getAxisValue(MotionEvent.AXIS_ORIENTATION),
                            timestamp = event.eventTime
                        ))
                        lastDrawPointX = canvasPoint.x
                        lastDrawPointY = canvasPoint.y
                    }
                    postInvalidateOnAnimation()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // If writing in a handwriting block, send points for recognition
                if (isWritingInBlock && activeHandwritingBlockId != null && handwritingStrokePoints.isNotEmpty()) {
                    listener?.onHandwritingStrokeInBlock(
                        activeHandwritingBlockId!!,
                        ArrayList(handwritingStrokePoints)
                    )
                    handwritingStrokePoints.clear()
                    isWritingInBlock = false
                    activeHandwritingBlockId = null
                }

                currentStroke?.let { stroke ->
                    if (!stroke.isEmpty()) {
                        val layerId = board?.activeLayer?.id ?: ""
                        val finalized = stroke.copy(layerId = layerId)
                        board?.activeLayer?.strokes?.add(finalized)
                        undoStack.add(CanvasAction.AddStroke(finalized))
                        if (undoStack.size > maxUndoStackSize) undoStack.removeAt(0)
                        redoStack.clear()
                        listener?.onStrokeCompleted(finalized)
                        commitStrokeToCache(finalized)
                    }
                }
                currentStroke = null
                isDrawing = false
                invalidate()
            }
        }
        return true
    }

    private fun handleErase(event: MotionEvent): Boolean {
        val canvasPoint = screenToCanvas(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Update eraser cursor for visual indicator
                eraserCursorX = event.x
                eraserCursorY = event.y
                showEraserCursor = true

                val layer = board?.activeLayer ?: return true
                val toRemove = mutableListOf<Stroke>()
                for (stroke in layer.strokes) {
                    if (inkEngine.isPointNearStroke(stroke, canvasPoint.x, canvasPoint.y, eraserRadius / scale)) {
                        toRemove.add(stroke)
                    }
                }
                for (stroke in toRemove) {
                    layer.strokes.remove(stroke)
                    undoStack.add(CanvasAction.RemoveStroke(stroke))
                    redoStack.clear()
                    listener?.onStrokeErased(stroke.id)
                }
                invalidateCache()
                postInvalidateOnAnimation()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                showEraserCursor = false
                rebuildStrokeCache()
                invalidate()
            }
        }
        return true
    }

    private fun handleSelect(event: MotionEvent): Boolean {
        val canvasPoint = screenToCanvas(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val layer = board?.activeLayer ?: return true
                var found: SpatialObject? = null
                for (obj in layer.objects.reversed()) {
                    if (canvasPoint.x >= obj.x && canvasPoint.x <= obj.x + obj.width &&
                        canvasPoint.y >= obj.y && canvasPoint.y <= obj.y + obj.height) {
                        found = obj
                        break
                    }
                }
                selectedObject = found
                downSelectedObject = found
                objectTapCandidate = found != null
                draggedDuringSelection = false
                listener?.onObjectSelected(found)
                if (found != null && !found.locked) {
                    if (selectedObjects.any { it.id == found.id }) {
                        startGroupDrag(canvasPoint)
                    } else {
                        isDraggingObject = true
                        dragOffsetX = canvasPoint.x - found.x
                        dragOffsetY = canvasPoint.y - found.y
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingObject && selectedObjects.size > 1) {
                    moveSelectedGroup(canvasPoint)
                    invalidate()
                } else if (isDraggingObject && selectedObject != null) {
                    val newX = canvasPoint.x - dragOffsetX
                    val newY = canvasPoint.y - dragOffsetY
                    val obj = selectedObject!!
                    if (abs(newX - obj.x) > 2f || abs(newY - obj.y) > 2f) {
                        draggedDuringSelection = true
                        objectTapCandidate = false
                    }
                    val idx = board!!.activeLayer.objects.indexOf(obj)
                    if (idx >= 0) {
                        val oldX = obj.x
                        val oldY = obj.y
                        val updated = obj.copy(x = newX, y = newY)
                        board!!.activeLayer.objects[idx] = updated
                        selectedObject = updated
                        undoStack.add(CanvasAction.MoveObject(obj.id, oldX, oldY, newX, newY))
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDraggingObject && selectedObjects.size > 1) {
                    selectedObjects.forEach { listener?.onObjectMoved(it, it.x, it.y) }
                } else if (isDraggingObject && selectedObject != null) {
                    listener?.onObjectMoved(selectedObject!!, selectedObject!!.x, selectedObject!!.y)
                }
                if (!draggedDuringSelection && objectTapCandidate) {
                    val tapped = downSelectedObject
                    if (tapped?.type == SpatialObjectType.FLASHCARD) {
                        listener?.onFlashcardBlockTapped(tapped)
                    }
                }
                isDraggingObject = false
                groupDragStartPositions.clear()
                downSelectedObject = null
                objectTapCandidate = false
            }
        }
        return true
    }

    private fun handleLasso(event: MotionEvent): Boolean {
        val canvasPoint = screenToCanvas(event.x, event.y)
        when (lassoMode) {
            LassoMode.FREE -> handleLassoFree(event, canvasPoint)
            LassoMode.RECTANGLE -> handleLassoRect(event, canvasPoint)
        }
        return true
    }

    private fun handleLassoFree(event: MotionEvent, canvasPoint: PointF) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lassoPoints.clear()
                lassoPoints.add(canvasPoint)
                isLassoing = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isLassoing) {
                    lassoPoints.add(canvasPoint)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                isLassoing = false
                finishLassoSelection()
                invalidate()
            }
        }
    }

    private fun handleLassoRect(event: MotionEvent, canvasPoint: PointF) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lassoStartPoint = canvasPoint
                lassoPoints.clear()
                isLassoing = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isLassoing) {
                    val start = lassoStartPoint ?: return
                    lassoPoints.clear()
                    lassoPoints.add(PointF(start.x, start.y))
                    lassoPoints.add(PointF(canvasPoint.x, start.y))
                    lassoPoints.add(PointF(canvasPoint.x, canvasPoint.y))
                    lassoPoints.add(PointF(start.x, canvasPoint.y))
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                isLassoing = false
                finishLassoSelection()
                invalidate()
            }
        }
    }

    private fun finishLassoSelection() {
        if (lassoPoints.size < 3) return
        if (currentMode == CanvasMode.REGION_OCR) {
            val bounds = computeSelectionBounds(lassoPoints) ?: return
            pendingRegionRect = bounds
            clearLassoOverlay()
            setModeInternal(persistentMode, isTemporary = false, updatePersistentMode = false)
            invalidate()
            return
        }
        selectObjectsInLasso()
    }

    private fun selectObjectsInLasso() {
        val layer = board?.activeLayer ?: return
        val matches = layer.objects.filter { obj ->
            val centerX = obj.x + obj.width / 2f
            val centerY = obj.y + obj.height / 2f
            isPointInPolygon(centerX, centerY, lassoPoints)
        }
        selectedObjects.clear()
        selectedObjects.addAll(matches)
        selectedObject = matches.firstOrNull()
        listener?.onObjectSelected(selectedObject)
        listener?.onSelectionChanged(selectedObjects.toList())
        clearLassoOverlay()
        invalidate()
    }

    private fun clearLassoOverlay() {
        lassoPoints.clear()
        lassoPath.reset()
        isLassoing = false
    }

    private fun clearMultiSelection() {
        if (selectedObjects.isEmpty()) return
        selectedObjects.clear()
        listener?.onSelectionChanged(emptyList())
    }

    private fun computeSelectionBounds(points: List<PointF>): RectF? {
        if (points.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        points.forEach { point ->
            minX = min(minX, point.x)
            minY = min(minY, point.y)
            maxX = max(maxX, point.x)
            maxY = max(maxY, point.y)
        }
        if (maxX <= minX || maxY <= minY) return null
        return RectF(minX, minY, maxX, maxY)
    }

    private fun startGroupDrag(canvasPoint: PointF) {
        isDraggingObject = true
        groupDragAnchor = PointF(canvasPoint.x, canvasPoint.y)
        groupDragStartPositions.clear()
        selectedObjects.forEach { groupDragStartPositions[it.id] = PointF(it.x, it.y) }
    }

    private fun moveSelectedGroup(canvasPoint: PointF) {
        val dx = canvasPoint.x - groupDragAnchor.x
        val dy = canvasPoint.y - groupDragAnchor.y
        val layer = board?.activeLayer ?: return
        val moved = mutableListOf<SpatialObject>()
        selectedObjects.forEach { selected ->
            val origin = groupDragStartPositions[selected.id] ?: return@forEach
            val updated = selected.copy(x = origin.x + dx, y = origin.y + dy)
            val index = layer.objects.indexOfFirst { it.id == selected.id }
            if (index >= 0) {
                layer.objects[index] = updated
                moved.add(updated)
            }
        }
        if (moved.isNotEmpty()) {
            selectedObjects.clear()
            selectedObjects.addAll(moved)
            selectedObject = moved.firstOrNull()
            listener?.onSelectionChanged(selectedObjects.toList())
        }
    }

    private fun isPointInPolygon(x: Float, y: Float, polygon: List<PointF>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.y > y) != (pj.y > y) &&
                x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun handlePan(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPanning = true
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPanning && event.pointerCount == 1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    translateX += dx
                    translateY += dy
                    lastTouchX = event.x
                    lastTouchY = event.y
                    updateMatrix()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
            }
        }
        return true
    }

    private fun handleFlashcard(event: MotionEvent): Boolean {
        val canvasPoint = screenToCanvas(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                flashcardDragStart = canvasPoint
                flashcardPreviewRect = RectF(canvasPoint.x, canvasPoint.y, canvasPoint.x, canvasPoint.y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val start = flashcardDragStart ?: return true
                flashcardPreviewRect = RectF(
                    min(start.x, canvasPoint.x),
                    min(start.y, canvasPoint.y),
                    max(start.x, canvasPoint.x),
                    max(start.y, canvasPoint.y)
                )
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val start = flashcardDragStart ?: return true
                val rect = flashcardPreviewRect ?: RectF(
                    min(start.x, canvasPoint.x),
                    min(start.y, canvasPoint.y),
                    max(start.x, canvasPoint.x),
                    max(start.y, canvasPoint.y)
                )
                val width = (rect.width()).coerceAtLeast(180f)
                val height = (rect.height()).coerceAtLeast(120f)
                val left = if (rect.width() < 180f) rect.left - (180f - rect.width()) / 2f else rect.left
                val top = if (rect.height() < 120f) rect.top - (120f - rect.height()) / 2f else rect.top
                val layer = board?.activeLayer ?: return true
                val block = SpatialObject(
                    type = SpatialObjectType.FLASHCARD,
                    x = left,
                    y = top,
                    width = width,
                    height = height,
                    layerId = layer.id,
                    content = com.cnnt.app.data.model.ObjectContent.FlashcardContent(
                        previewText = "Novo flashcard",
                        noteType = "basic"
                    ),
                    style = ObjectStyle(
                        backgroundColor = 0x332E7DFF,
                        borderColor = 0xFF88CCFF.toInt(),
                        borderWidth = 2f,
                        cornerRadius = 10f,
                        padding = 12f
                    )
                )
                layer.objects.add(block)
                selectedObject = block
                listener?.onObjectSelected(block)
                listener?.onObjectCreated(block)
                listener?.onFlashcardBlockTapped(block)
                flashcardDragStart = null
                flashcardPreviewRect = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                flashcardDragStart = null
                flashcardPreviewRect = null
                invalidate()
            }
        }
        return true
    }

    private fun handleInsert(event: MotionEvent): Boolean {
        // Insert mode: tap to place a new block
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val canvasPoint = screenToCanvas(event.x, event.y)
            // Notify listener to insert block at this position
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
      try {
        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Apply canvas transform
        canvas.save()
        canvas.concat(canvasMatrix)

        // Draw grid
        drawGrid(canvas)

        // Draw layers
        board?.let { board ->
            for (layer in board.layers) {
                if (!layer.visible) continue
                drawLayer(canvas, layer)
            }
        }

        // Draw current stroke in progress
        currentStroke?.let { stroke ->
            inkEngine.renderStroke(canvas, stroke, currentBrush, scale)
        }

        // Draw lasso
        if (isLassoing && lassoPoints.size > 1) {
            lassoPaint.strokeWidth = 2f / scale
            lassoPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f / scale, 5f / scale), 0f)
            for (i in 1 until lassoPoints.size) {
                canvas.drawLine(
                    lassoPoints[i - 1].x, lassoPoints[i - 1].y,
                    lassoPoints[i].x, lassoPoints[i].y, lassoPaint
                )
            }
        }

        if (selectedObjects.isNotEmpty()) {
            drawSelectionGroup(canvas)
        }

        flashcardPreviewRect?.let { rect ->
            selectPaint.strokeWidth = 2f / scale
            objPaint.color = 0x222E7DFF
            canvas.drawRoundRect(rect, 10f, 10f, objPaint)
            canvas.drawRoundRect(rect, 10f, 10f, selectPaint)
        }

        // Draw selection indicator
        selectedObject?.let { obj ->
            selectPaint.strokeWidth = 2f / scale
            canvas.drawRect(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height, selectPaint)
            val handleSize = 8f / scale
            canvas.drawRect(obj.x - handleSize, obj.y - handleSize, obj.x + handleSize, obj.y + handleSize, handlePaint)
            canvas.drawRect(obj.x + obj.width - handleSize, obj.y - handleSize, obj.x + obj.width + handleSize, obj.y + handleSize, handlePaint)
            canvas.drawRect(obj.x - handleSize, obj.y + obj.height - handleSize, obj.x + handleSize, obj.y + obj.height + handleSize, handlePaint)
            canvas.drawRect(obj.x + obj.width - handleSize, obj.y + obj.height - handleSize, obj.x + obj.width + handleSize, obj.y + obj.height + handleSize, handlePaint)
        }

        canvas.restore()

        // Draw eraser cursor indicator (in screen coords, not canvas coords)
        if (showEraserCursor && eraserCursorX >= 0) {
            canvas.drawCircle(eraserCursorX, eraserCursorY, eraserRadius, eraserCursorPaint)
        }
      } catch (e: Exception) {
          Log.e("CNNT", "onDraw error: ${e.message}", e)
      }
    }

    private fun drawGrid(canvas: Canvas) {
        val gridSize = 50f
        val visibleRect = getVisibleCanvasRect()
        val startX = (visibleRect.left / gridSize).toInt() * gridSize
        val startY = (visibleRect.top / gridSize).toInt() * gridSize

        var x = startX
        while (x <= visibleRect.right) {
            canvas.drawLine(x, visibleRect.top, x, visibleRect.bottom, gridPaint)
            x += gridSize
        }
        var y = startY
        while (y <= visibleRect.bottom) {
            canvas.drawLine(visibleRect.left, y, visibleRect.right, y, gridPaint)
            y += gridSize
        }
    }

    private fun drawLayer(canvas: Canvas, layer: Layer) {
        val alpha = (layer.opacity * 255).toInt()

        if (layer.strokes.isNotEmpty()) {
            val isActiveLayer = layer === board?.activeLayer
            if (isActiveLayer && shouldUseBitmapCache()) {
                if (!cacheValid || cachedStrokeCount != layer.strokes.size) {
                    rebuildStrokeCache()
                }
                if (cacheValid && layerCache != null) {
                    canvas.drawBitmap(layerCache!!, cacheOffsetX, cacheOffsetY, null)
                } else {
                    drawStrokesDirect(canvas, layer)
                }
            } else {
                drawStrokesDirect(canvas, layer)
            }
        }

        for (obj in layer.objects) {
            drawSpatialObject(canvas, obj, alpha)
        }
    }

    private fun drawSpatialObject(canvas: Canvas, obj: SpatialObject, layerAlpha: Int) {
        objPaint.color = obj.style.backgroundColor
        objPaint.alpha = layerAlpha

        borderPaint.color = obj.style.borderColor
        borderPaint.alpha = layerAlpha
        borderPaint.strokeWidth = obj.style.borderWidth

        val rect = RectF(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height)
        val cr = obj.style.cornerRadius

        if (obj.style.backgroundColor != 0) {
            canvas.drawRoundRect(rect, cr, cr, objPaint)
        }
        canvas.drawRoundRect(rect, cr, cr, borderPaint)

        when (val content = obj.content) {
            is com.cnnt.app.data.model.ObjectContent.Text -> {
                textPaint.color = content.fontColor
                textPaint.textSize = content.fontSize
                val padding = obj.style.padding
                canvas.drawText(
                    content.text,
                    obj.x + padding,
                    obj.y + padding + content.fontSize,
                    textPaint
                )
            }
            is com.cnnt.app.data.model.ObjectContent.Checklist -> {
                textPaint.color = 0xFFE0E0E0.toInt()
                textPaint.textSize = 12f
                var yOffset = obj.y + obj.style.padding + 14f
                for (item in content.items.take(5)) {
                    val prefix = if (item.checked) "☑ " else "☐ "
                    canvas.drawText(prefix + item.text, obj.x + obj.style.padding, yOffset, textPaint)
                    yOffset += 18f
                }
            }
            is com.cnnt.app.data.model.ObjectContent.Handwriting -> {
                drawHandwritingBlock(canvas, obj, content)
            }
            is com.cnnt.app.data.model.ObjectContent.FlashcardContent -> {
                drawFlashcardBlock(canvas, obj, content)
            }
            else -> {}
        }
    }

    private fun drawSelectionGroup(canvas: Canvas) {
        val bounds = selectedObjectsBounds() ?: return
        selectPaint.strokeWidth = 2f / scale
        objPaint.color = 0x182E7DFF
        canvas.drawRoundRect(bounds, 10f / scale, 10f / scale, objPaint)
        canvas.drawRoundRect(bounds, 10f / scale, 10f / scale, selectPaint)
    }

    private fun selectedObjectsBounds(): RectF? {
        if (selectedObjects.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        selectedObjects.forEach { obj ->
            minX = min(minX, obj.x)
            minY = min(minY, obj.y)
            maxX = max(maxX, obj.x + obj.width)
            maxY = max(maxY, obj.y + obj.height)
        }
        return RectF(minX, minY, maxX, maxY)
    }

    private fun drawFlashcardBlock(
        canvas: Canvas,
        obj: SpatialObject,
        content: com.cnnt.app.data.model.ObjectContent.FlashcardContent
    ) {
        val padding = obj.style.padding
        textPaint.color = 0xFFEAF3FF.toInt()
        textPaint.textSize = 12f / scale.coerceIn(0.6f, 2f)
        canvas.drawText(
            if (content.noteType.equals("cloze", ignoreCase = true)) "Cloze" else "Basic",
            obj.x + padding,
            obj.y + padding + textPaint.textSize,
            textPaint
        )

        textPaint.color = 0xFFFFFFFF.toInt()
        textPaint.textSize = 16f / scale.coerceIn(0.6f, 2f)
        val preview = content.previewText.ifBlank { "Novo flashcard" }
        val maxChars = if (preview.length > 48) preview.take(45) + "..." else preview
        canvas.drawText(
            maxChars,
            obj.x + padding,
            obj.y + padding + 32f / scale.coerceIn(0.6f, 2f),
            textPaint
        )
    }

    private fun drawHandwritingBlock(canvas: Canvas, obj: SpatialObject, content: com.cnnt.app.data.model.ObjectContent.Handwriting) {
        val rect = RectF(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height)

        // Dark semi-transparent background
        objPaint.color = 0xCC222222.toInt()
        objPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, 6f, 6f, objPaint)

        // Dashed border (blue for active, gray for idle)
        val isActive = activeHandwritingBlockId == obj.id
        hwBlockBorderPaint.color = if (isActive) 0xFF00B0FF.toInt() else 0x66AAAAAA.toInt()
        hwBlockBorderPaint.strokeWidth = if (isActive) 2.5f / scale else 1.5f / scale
        hwBlockBorderPaint.pathEffect = android.graphics.DashPathEffect(
            floatArrayOf(8f / scale, 4f / scale), 0f
        )
        canvas.drawRoundRect(rect, 6f, 6f, hwBlockBorderPaint)

        val padding = obj.style.padding
        val textX = obj.x + padding
        var textY = obj.y + padding + content.fontSize

        if (content.recognizedText.isNotEmpty()) {
            // Draw recognized text
            hwTextPaint.color = content.fontColor
            hwTextPaint.textSize = content.fontSize

            // Word-wrap text within block width
            val maxWidth = obj.width - padding * 2
            val words = content.recognizedText.split(" ")
            var currentLine = StringBuilder()
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                val textWidth = hwTextPaint.measureText(testLine)
                if (textWidth > maxWidth && currentLine.isNotEmpty()) {
                    canvas.drawText(currentLine.toString(), textX, textY, hwTextPaint)
                    textY += content.fontSize * 1.4f
                    currentLine = StringBuilder(word)
                } else {
                    currentLine = StringBuilder(testLine)
                }
            }
            if (currentLine.isNotEmpty()) {
                canvas.drawText(currentLine.toString(), textX, textY, hwTextPaint)
            }
        } else if (content.isRecognizing) {
            // Show recognizing indicator
            hwLabelPaint.color = 0xAAFFCC00.toInt()
            hwLabelPaint.textSize = 12f / scale.coerceIn(0.3f, 3f)
            canvas.drawText("Reconhecendo...", textX, textY, hwLabelPaint)
        } else {
            // Show placeholder
            hwLabelPaint.color = 0x66FFFFFF.toInt()
            hwLabelPaint.textSize = 12f / scale.coerceIn(0.3f, 3f)
            canvas.drawText("Escreva aqui...", textX, textY, hwLabelPaint)
        }

        val handleSize = 6f / scale.coerceAtLeast(0.5f)
        canvas.drawCircle(obj.x + obj.width, obj.y + obj.height, handleSize, hwHandlePaint)
        canvas.drawCircle(obj.x, obj.y + obj.height, handleSize * 0.7f, hwHandlePaint)
    }

    fun addHandwritingBlock(x: Float, y: Float, width: Float = 300f, height: Float = 200f) {
        val canvasPoint = screenToCanvas(x, y)
        val obj = SpatialObject(
            type = com.cnnt.app.data.model.SpatialObjectType.HANDWRITING,
            x = canvasPoint.x - width / 2f,
            y = canvasPoint.y - height / 2f,
            width = width,
            height = height,
            content = com.cnnt.app.data.model.ObjectContent.Handwriting(),
            style = ObjectStyle(
                backgroundColor = 0x00000000,
                borderColor = 0xFF00B0FF.toInt(),
                borderWidth = 2f,
                cornerRadius = 6f,
                padding = 10f
            )
        )
        board?.activeLayer?.objects?.add(obj)
        invalidateCache()
        invalidate()
        listener?.onObjectSelected(obj)
        listener?.onObjectCreated(obj.copy(layerId = board?.activeLayer?.id.orEmpty()))
    }

    fun updateHandwritingBlockText(blockId: String, text: String) {
        val layer = board?.activeLayer ?: return
        val idx = layer.objects.indexOfFirst { it.id == blockId }
        if (idx >= 0) {
            val obj = layer.objects[idx]
            val content = obj.content
            if (content is com.cnnt.app.data.model.ObjectContent.Handwriting) {
                val existingText = content.recognizedText
                val newText = if (existingText.isEmpty()) text else "$existingText $text"
                layer.objects[idx] = obj.copy(
                    content = content.copy(
                        recognizedText = newText,
                        isRecognizing = false
                    )
                )
                invalidateCache()
                invalidate()
            }
        }
    }

    fun setHandwritingBlockRecognizing(blockId: String, recognizing: Boolean) {
        val layer = board?.activeLayer ?: return
        val idx = layer.objects.indexOfFirst { it.id == blockId }
        if (idx >= 0) {
            val obj = layer.objects[idx]
            val content = obj.content
            if (content is com.cnnt.app.data.model.ObjectContent.Handwriting) {
                layer.objects[idx] = obj.copy(
                    content = content.copy(isRecognizing = recognizing)
                )
                invalidate()
            }
        }
    }

    private fun getBrushForStroke(stroke: Stroke): BrushPreset {
        return cachedBrushMap[stroke.brushId] ?: defaultBrush
    }

    private fun getVisibleCanvasRect(): RectF {
        val topLeft = screenToCanvas(0f, 0f)
        val bottomRight = screenToCanvas(width.toFloat(), height.toFloat())
        return RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    private fun drawStrokesDirect(canvas: Canvas, layer: Layer) {
        for (stroke in layer.strokes) {
            inkEngine.renderStroke(canvas, stroke, getBrushForStroke(stroke), scale)
        }
    }

    fun debugCacheInfo(): String {
        val w = layerCache?.width ?: 0
        val h = layerCache?.height ?: 0
        val mb = if (w > 0 && h > 0) (w * h * 2) / (1024 * 1024) else 0
        return if (bitmapCacheEnabled && cacheValid) "${w}x$h ~${mb}MB" else "off"
    }

    fun trimMemory() {
        layerCache?.recycle()
        layerCache = null
        cacheCanvas = null
        invalidateCache()
        invalidate()
    }

    private fun shouldUseBitmapCache(): Boolean {
        if (!bitmapCacheEnabled) return false
        val count = board?.activeLayer?.strokes?.size ?: 0
        return count <= maxCachedStrokeCount
    }

    private fun invalidateCache() {
        cacheValid = false
        cachedStrokeCount = -1
    }

    /**
     * Incrementally rasterize one new stroke instead of rebuilding the full cache.
     */
    private fun commitStrokeToCache(stroke: Stroke) {
        val layer = board?.activeLayer ?: run {
            invalidateCache()
            postInvalidateOnAnimation()
            return
        }
        if (layer.strokes.isEmpty()) {
            invalidateCache()
            rebuildStrokeCache()
            postInvalidateOnAnimation()
            return
        }
        if (!shouldUseBitmapCache() || !cacheValid || layerCache == null ||
            cachedStrokeCount != layer.strokes.size - 1
        ) {
            rebuildStrokeCache()
            postInvalidateOnAnimation()
            return
        }

        val b = stroke.getBounds()
        val pad = 100f
        val strokeMinX = b[0] - pad
        val strokeMinY = b[1] - pad
        val strokeMaxX = b[2] + pad
        val strokeMaxY = b[3] + pad
        val cacheMaxX = cacheOffsetX + (layerCache?.width ?: 0)
        val cacheMaxY = cacheOffsetY + (layerCache?.height ?: 0)

        if (strokeMinX < cacheOffsetX || strokeMinY < cacheOffsetY ||
            strokeMaxX > cacheMaxX || strokeMaxY > cacheMaxY
        ) {
            invalidateCache()
            rebuildStrokeCache()
            postInvalidateOnAnimation()
            return
        }

        cacheCanvas?.save()
        cacheCanvas?.translate(-cacheOffsetX, -cacheOffsetY)
        inkEngine.renderStroke(cacheCanvas!!, stroke, getBrushForStroke(stroke), scale)
        cacheCanvas?.restore()
        cachedStrokeCount = layer.strokes.size
        cacheValid = true
        postInvalidateOnAnimation()
    }

    private fun rebuildStrokeCache() {
        val layer = board?.activeLayer
        if (layer == null || width <= 0 || height <= 0) {
            cacheValid = false
            return
        }

        if (!shouldUseBitmapCache()) {
            cacheValid = false
            cachedStrokeCount = layer.strokes.size
            return
        }

        val strokes = layer.strokes
        if (strokes.isEmpty()) {
            layerCache?.eraseColor(Color.TRANSPARENT)
            cacheValid = true
            cachedStrokeCount = 0
            return
        }

        if (cacheValid && cachedStrokeCount == strokes.size && layerCache != null) return

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (stroke in strokes) {
            val b = stroke.getBounds()
            if (b[0] < minX) minX = b[0]
            if (b[1] < minY) minY = b[1]
            if (b[2] > maxX) maxX = b[2]
            if (b[3] > maxY) maxY = b[3]
        }

        val pad = 100f
        cacheOffsetX = minX - pad
        cacheOffsetY = minY - pad
        val cacheW = ((maxX - minX) + pad * 2).toInt().coerceIn(1, maxCacheDimension)
        val cacheH = ((maxY - minY) + pad * 2).toInt().coerceIn(1, maxCacheDimension)

        if (cacheW >= maxCacheDimension || cacheH >= maxCacheDimension) {
            cacheValid = false
            cachedStrokeCount = -1
            return
        }

        if (layerCache == null || layerCache!!.width != cacheW || layerCache!!.height != cacheH) {
            layerCache?.recycle()
            layerCache = Bitmap.createBitmap(cacheW, cacheH, Bitmap.Config.RGB_565)
            cacheCanvas = Canvas(layerCache!!)
        } else {
            layerCache!!.eraseColor(Color.TRANSPARENT)
        }

        cacheCanvas!!.save()
        cacheCanvas!!.translate(-cacheOffsetX, -cacheOffsetY)
        for (stroke in strokes) {
            inkEngine.renderStroke(cacheCanvas!!, stroke, getBrushForStroke(stroke), scale)
        }
        cacheCanvas!!.restore()
        cacheValid = true
        cachedStrokeCount = strokes.size
    }

    private fun shouldAddPoint(x: Float, y: Float): Boolean {
        val minDist = 1.8f / scale.coerceAtLeast(CanvasScale.MIN_ZOOM_FACTOR)
        val d = hypot((x - lastDrawPointX).toDouble(), (y - lastDrawPointY).toDouble())
        return d >= minDist
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val focusX = detector.focusX
            val focusY = detector.focusY
            val prevScale = scale
            val raw = detector.scaleFactor
            val damped = if (raw > 1f) 1f + (raw - 1f) * 0.32f else 1f - (1f - raw) * 0.32f
            val newScale = (scale * damped)
                .coerceIn(CanvasScale.MIN_ZOOM_FACTOR, CanvasScale.MAX_ZOOM_FACTOR)
            if (newScale == prevScale) return true

            val canvasX = (focusX - translateX) / prevScale
            val canvasY = (focusY - translateY) / prevScale
            scale = newScale
            translateX = focusX - canvasX * scale
            translateY = focusY - canvasY * scale

            applyBrushSizeForZoom()
            updateMatrix()
            postInvalidateOnAnimation()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            updateMatrix()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrix()
    }

    override fun onDetachedFromWindow() {
        layerCache?.recycle()
        layerCache = null
        cacheCanvas = null
        super.onDetachedFromWindow()
    }
}

enum class CanvasMode {
    DRAW, ERASE, SELECT, LASSO, FLASHCARD, REGION_OCR, PAN, INSERT
}

enum class LassoMode {
    FREE, RECTANGLE
}

sealed class CanvasAction {
    data class AddStroke(val stroke: Stroke) : CanvasAction()
    data class RemoveStroke(val stroke: Stroke) : CanvasAction()
    data class MoveObject(val objectId: String, val oldX: Float, val oldY: Float, val newX: Float, val newY: Float) : CanvasAction()
}
