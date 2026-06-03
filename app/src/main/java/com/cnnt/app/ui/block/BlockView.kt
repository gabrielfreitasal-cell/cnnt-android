package com.cnnt.app.ui.block

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.VERTICAL
import androidx.appcompat.app.AlertDialog
import androidx.core.view.setPadding
import com.bumptech.glide.Glide
import com.cnnt.app.R
import com.cnnt.app.data.model.BlockContent
import com.cnnt.app.data.model.BlockType
import com.cnnt.app.data.model.ContentBlock
import com.cnnt.app.data.model.InteractiveAlternative
import com.github.barteksc.pdfviewer.PDFView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin

class BlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Listener {
        fun onBlockSelected(blockId: String)
        fun onBlockUpdated(block: ContentBlock)
        fun onBlockMoved(blockId: String, rawDx: Float, rawDy: Float)
        fun onBlockMoveFinished(blockId: String)
        fun onResize(blockId: String, direction: ResizeDirection, rawDx: Float, rawDy: Float)
        fun onResizeFinished(blockId: String)
        fun onStartLink(blockId: String, anchorX: Float, anchorY: Float)
        fun onLinkMove(rawX: Float, rawY: Float)
        fun onLinkFinish(rawX: Float, rawY: Float)
        fun onContextAction(blockId: String, action: BlockContextAction)
        fun onBadgeClicked(blockId: String, incoming: Boolean)
        fun onRequestFile(blockId: String, type: BlockType)
        fun onOpenFullscreenPdf(block: ContentBlock)
    }

    enum class ResizeDirection {
        TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT
    }

    enum class BlockContextAction {
        EDIT, DUPLICATE, BRING_TO_FRONT, SEND_TO_BACK, DELETE
    }

    private val markwon by lazy {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .build()
    }

    private val chrome = GradientDrawable().apply {
        cornerRadius = dp(12f)
        setColor(Color.parseColor("#EE20232A"))
        setStroke(dp(1f).toInt(), Color.parseColor("#33FFFFFF"))
    }
    private val selectedChrome = GradientDrawable().apply {
        cornerRadius = dp(12f)
        setColor(Color.parseColor("#F0222630"))
        setStroke(dp(2f).toInt(), Color.parseColor("#4DA3FF"))
    }
    private val linkingChrome = GradientDrawable().apply {
        cornerRadius = dp(12f)
        setColor(Color.parseColor("#F0222630"))
        setStroke(dp(2f).toInt(), Color.parseColor("#FFB74D"))
    }

    private val header = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10f).toInt())
    }
    private val titleView = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
    }
    private val incomingBadge = TextView(context).apply {
        setTextColor(Color.parseColor("#D6E7FF"))
        textSize = 11f
        setPadding(dp(8f).toInt(), dp(4f).toInt(), dp(8f).toInt(), dp(4f).toInt())
        background = badgeBackground()
        visibility = View.GONE
    }
    private val outgoingBadge = TextView(context).apply {
        setTextColor(Color.parseColor("#FFE0B2"))
        textSize = 11f
        setPadding(dp(8f).toInt(), dp(4f).toInt(), dp(8f).toInt(), dp(4f).toInt())
        background = badgeBackground("#33FFB74D")
        visibility = View.GONE
    }
    private val linkHandle = TextView(context).apply {
        text = "→"
        textSize = 18f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#FFB74D"))
        background = badgeBackground("#33FFB74D")
        setPadding(dp(10f).toInt(), dp(6f).toInt(), dp(10f).toInt(), dp(6f).toInt())
        visibility = View.GONE
    }
    private val editToggle = ImageButton(context).apply {
        setImageResource(android.R.drawable.ic_menu_edit)
        background = null
        visibility = View.GONE
        setColorFilter(Color.parseColor("#88CCFF"))
    }
    private val contentContainer = FrameLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0).apply {
            topMargin = dp(4f).toInt()
        }
    }
    private val loading = ProgressBar(context).apply {
        visibility = View.GONE
    }
    private val fallback = TextView(context).apply {
        setTextColor(Color.parseColor("#B3FFFFFF"))
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(16f).toInt())
        visibility = View.GONE
    }
    private val resizeHandles = mutableMapOf<ResizeDirection, View>()
    private val handler = Handler(Looper.getMainLooper())

    private var listener: Listener? = null
    private var block: ContentBlock? = null
    private var selected = false
    private var linking = false
    private var autoSaveRunnable: Runnable? = null
    private var markdownPreview = true
    private var interactiveSubmitted = false

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            block?.let { listener?.onBlockSelected(it.id) }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            block?.let(::performDoubleTapAction)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            block?.let {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showContextMenu(it)
            }
        }
    })

    init {
        elevation = dp(8f)
        setBackground(chrome)
        clipToOutline = true
        isClickable = true
        isFocusable = true
        setPadding(dp(6f).toInt())
        setLayerType(LAYER_TYPE_HARDWARE, null)

        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            addView(header)
            addView(contentContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        })

        header.addView(titleView)
        header.addView(incomingBadge)
        header.addView(space())
        header.addView(outgoingBadge)
        header.addView(space())
        header.addView(editToggle)
        header.addView(space())
        header.addView(linkHandle)

        addView(loading, LayoutParams(dp(28f).toInt(), dp(28f).toInt(), Gravity.CENTER))
        addView(fallback, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        createResizeHandles()
        setupHeaderInteractions()
    }

    fun bind(
        block: ContentBlock,
        isSelected: Boolean,
        isLinkingSource: Boolean,
        incomingCount: Int,
        outgoingCount: Int,
        listener: Listener
    ) {
        this.listener = listener
        this.block = block
        this.selected = isSelected
        this.linking = isLinkingSource
        this.markdownPreview = (block.content as? BlockContent.Markdown)?.previewMode ?: true
        this.interactiveSubmitted = (block.content as? BlockContent.InteractiveText)?.submitted ?: false

        titleView.text = titleFor(block)
        incomingBadge.text = "← $incomingCount"
        outgoingBadge.text = "→ $outgoingCount"
        incomingBadge.visibility = if (incomingCount > 0) View.VISIBLE else View.GONE
        outgoingBadge.visibility = if (outgoingCount > 0) View.VISIBLE else View.GONE
        linkHandle.visibility = if (isSelected) View.VISIBLE else View.GONE
        editToggle.visibility = if (block.type == BlockType.Markdown || block.type == BlockType.Text) View.VISIBLE else View.GONE

        renderBlock(block)
        updateChrome()
        animateSelectionState()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    private fun setupHeaderInteractions() {
        incomingBadge.setOnClickListener { block?.let { listener?.onBadgeClicked(it.id, true) } }
        outgoingBadge.setOnClickListener { block?.let { listener?.onBadgeClicked(it.id, false) } }
        linkHandle.setOnTouchListener { _, event ->
            val blockId = block?.id ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onStartLink(blockId, x + width, y + height / 2f)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    listener?.onLinkMove(x + event.x, y + event.y)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    listener?.onLinkFinish(x + event.x, y + event.y)
                    true
                }
                else -> false
            }
        }
        editToggle.setOnClickListener {
            block?.let(::performDoubleTapAction)
        }
        setOnTouchListener { _, event ->
            val current = block ?: return@setOnTouchListener false
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    listener?.onBlockSelected(current.id)
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    listener?.onBlockMoved(current.id, event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    listener?.onBlockMoveFinished(current.id)
                    performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun renderBlock(block: ContentBlock) {
        contentContainer.removeAllViews()
        fallback.visibility = View.GONE
        loading.visibility = View.GONE
        when (val content = block.content) {
            is BlockContent.TextNote -> renderText(content)
            is BlockContent.Markdown -> renderMarkdown(content)
            is BlockContent.Image -> renderImage(block, content)
            is BlockContent.Flashcard -> renderFlashcard(content)
            is BlockContent.InteractiveText -> renderInteractive(block, content)
            is BlockContent.Pdf -> renderPdf(block, content)
        }
    }

    private fun renderText(content: BlockContent.TextNote) {
        val editor = EditText(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            hint = content.hint
            setText(content.text)
            setPadding(dp(12f).toInt())
            textSize = 15f
            addTextChangedListener(autoSaveWatcher { value ->
                updateBlockContent(content.copy(text = value))
            })
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    updateBlockContent(content.copy(text = text.toString()))
                }
            }
        }
        contentContainer.addView(editor, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun renderMarkdown(content: BlockContent.Markdown) {
        if (markdownPreview) {
            val scroll = ScrollView(context)
            val textView = TextView(context).apply {
                setTextColor(Color.WHITE)
                setPadding(dp(12f).toInt())
                textSize = 14f
            }
            markwon.setMarkdown(textView, content.markdown)
            scroll.addView(textView)
            contentContainer.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } else {
            val editor = EditText(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#66FFFFFF"))
                setText(content.markdown)
                setPadding(dp(12f).toInt())
                textSize = 14f
                addTextChangedListener(autoSaveWatcher { value ->
                    updateBlockContent(content.copy(markdown = value, previewMode = false))
                })
            }
            contentContainer.addView(editor, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    private fun renderImage(block: ContentBlock, content: BlockContent.Image) {
        if (content.uri.isBlank()) {
            renderFallback("Toque duas vezes para escolher uma imagem")
            return
        }
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
        }
        contentContainer.addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        loading.visibility = View.VISIBLE
        Glide.with(context)
            .load(content.uri)
            .centerCrop()
            .into(image)
        loading.visibility = View.GONE
        image.setOnClickListener {
            listener?.onBlockSelected(block.id)
        }
    }

    private fun renderFlashcard(content: BlockContent.Flashcard) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f).toInt())
        }
        val chip = TextView(context).apply {
            text = content.noteType.uppercase()
            setTextColor(Color.parseColor("#88CCFF"))
            textSize = 11f
        }
        val preview = TextView(context).apply {
            text = content.previewText.ifBlank { "Novo flashcard" }
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 4
        }
        container.addView(chip)
        container.addView(preview)
        contentContainer.addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun renderInteractive(block: ContentBlock, content: BlockContent.InteractiveText) {
        val scroll = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f).toInt())
        }
        val question = TextView(context).apply {
            text = content.question
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        container.addView(question)
        val selectedIds = content.selectedIds.toMutableSet()
        content.alternatives.forEach { alternative ->
            val option = TextView(context).apply {
                setPadding(dp(12f).toInt())
                text = "• ${alternative.text}"
                setTextColor(Color.parseColor("#D8E6FF"))
                background = badgeBackground("#222D3440")
                setOnClickListener {
                    if (content.multiple) {
                        if (!selectedIds.add(alternative.id)) selectedIds.remove(alternative.id)
                    } else {
                        selectedIds.clear()
                        selectedIds.add(alternative.id)
                    }
                    updateBlockContent(content.copy(selectedIds = selectedIds.toList(), submitted = false))
                }
            }
            val params = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            params.topMargin = dp(8f).toInt()
            container.addView(option, params)
        }
        val submit = TextView(context).apply {
            text = if (interactiveSubmitted) "Editar resposta" else "Responder"
            gravity = Gravity.CENTER
            setPadding(dp(12f).toInt())
            setTextColor(Color.WHITE)
            background = badgeBackground("#334DA3FF")
            setOnClickListener {
                val isCorrect = content.alternatives.filter { it.correct }.map { it.id }.toSet() == selectedIds
                interactiveSubmitted = true
                updateBlockContent(content.copy(selectedIds = selectedIds.toList(), submitted = true))
                animate()
                    .translationX(if (isCorrect) 0f else dp(6f))
                    .setDuration(120)
                    .withEndAction {
                        animate().translationX(0f).duration = 120
                    }
                    .start()
                setBackgroundColor(if (isCorrect) Color.parseColor("#2232B15B") else Color.parseColor("#22D84343"))
            }
        }
        val explanation = TextView(context).apply {
            text = content.explanation
            setTextColor(Color.parseColor("#B3FFFFFF"))
            textSize = 13f
            visibility = if (interactiveSubmitted && content.explanation.isNotBlank()) View.VISIBLE else View.GONE
        }
        container.addView(submit, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12f).toInt()
        })
        container.addView(explanation, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8f).toInt()
        })
        scroll.addView(container)
        contentContainer.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun renderPdf(block: ContentBlock, content: BlockContent.Pdf) {
        if (content.uri.isBlank()) {
            renderFallback("Toque duas vezes para escolher um PDF")
            return
        }
        val container = FrameLayout(context)
        val pdfView = PDFView(context, null)
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f).toInt())
            setBackgroundColor(Color.parseColor("#88141414"))
        }
        val previous = TextView(context).apply {
            text = "◀"
            textSize = 18f
            setTextColor(Color.WHITE)
        }
        val pageLabel = TextView(context).apply {
            text = "${content.currentPage + 1}/${content.pageCount.coerceAtLeast(1)}"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        val next = TextView(context).apply {
            text = "▶"
            textSize = 18f
            setTextColor(Color.WHITE)
        }
        footer.addView(previous)
        footer.addView(pageLabel)
        footer.addView(next)
        container.addView(pdfView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        container.addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        contentContainer.addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        loading.visibility = View.VISIBLE
        try {
            pdfView.fromUri(android.net.Uri.parse(content.uri))
                .defaultPage(content.currentPage)
                .onLoad { totalPages ->
                    loading.visibility = View.GONE
                    pageLabel.text = "${content.currentPage + 1}/$totalPages"
                    updateBlockContent(content.copy(pageCount = totalPages))
                }
                .onError {
                    renderFallback("PDF inválido")
                }
                .load()
        } catch (_: Exception) {
            renderFallback("PDF inválido")
        }
        previous.setOnClickListener {
            val newPage = (content.currentPage - 1).coerceAtLeast(0)
            updateBlockContent(content.copy(currentPage = newPage))
        }
        next.setOnClickListener {
            val newPage = (content.currentPage + 1).coerceAtMost((content.pageCount - 1).coerceAtLeast(0))
            updateBlockContent(content.copy(currentPage = newPage))
        }
        pdfView.setOnLongClickListener {
            listener?.onOpenFullscreenPdf(block)
            true
        }
    }

    private fun renderFallback(text: String) {
        fallback.text = text
        fallback.visibility = View.VISIBLE
        contentContainer.removeAllViews()
        contentContainer.addView(fallback, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun updateBlockContent(content: Any) {
        val current = block ?: return
        val updated = when (content) {
            is BlockContent.TextNote -> current.copy(content = content, updatedAt = System.currentTimeMillis())
            is BlockContent.Markdown -> current.copy(content = content, updatedAt = System.currentTimeMillis())
            is BlockContent.Image -> current.copy(content = content, updatedAt = System.currentTimeMillis())
            is BlockContent.Flashcard -> current.copy(content = content, updatedAt = System.currentTimeMillis())
            is BlockContent.InteractiveText -> current.copy(content = content, updatedAt = System.currentTimeMillis())
            is BlockContent.Pdf -> current.copy(content = content, updatedAt = System.currentTimeMillis())
            else -> current
        }
        block = updated
        autoSaveRunnable?.let(handler::removeCallbacks)
        autoSaveRunnable = Runnable { listener?.onBlockUpdated(updated) }
        handler.postDelayed(autoSaveRunnable!!, 500L)
    }

    private fun performDoubleTapAction(block: ContentBlock) {
        when (block.type) {
            BlockType.Markdown -> {
                val content = block.content as? BlockContent.Markdown ?: return
                markdownPreview = !markdownPreview
                updateBlockContent(content.copy(previewMode = markdownPreview))
                renderBlock(block.copy(content = content.copy(previewMode = markdownPreview)))
            }
            BlockType.Text -> {
                selected = true
                requestFocus()
            }
            BlockType.Image, BlockType.Pdf -> listener?.onRequestFile(block.id, block.type)
            BlockType.InteractiveText -> showInteractiveEditor(block)
            BlockType.Flashcard -> listener?.onContextAction(block.id, BlockContextAction.EDIT)
        }
    }

    private fun showInteractiveEditor(block: ContentBlock) {
        val content = block.content as? BlockContent.InteractiveText ?: return
        val editorView = LayoutInflater.from(context).inflate(R.layout.dialog_interactive_block_editor, null)
        val question = editorView.findViewById<EditText>(R.id.inputQuestion)
        val explanation = editorView.findViewById<EditText>(R.id.inputExplanation)
        val optionA = editorView.findViewById<EditText>(R.id.inputOptionA)
        val optionB = editorView.findViewById<EditText>(R.id.inputOptionB)
        val optionC = editorView.findViewById<EditText>(R.id.inputOptionC)
        val optionD = editorView.findViewById<EditText>(R.id.inputOptionD)
        val checkMultiple = editorView.findViewById<android.widget.CheckBox>(R.id.checkMultiple)
        val correctSpinner = editorView.findViewById<android.widget.Spinner>(R.id.correctAnswerSpinner)
        question.setText(content.question)
        explanation.setText(content.explanation)
        val options = content.alternatives + List((4 - content.alternatives.size).coerceAtLeast(0)) {
            InteractiveAlternative(text = "")
        }
        optionA.setText(options.getOrNull(0)?.text.orEmpty())
        optionB.setText(options.getOrNull(1)?.text.orEmpty())
        optionC.setText(options.getOrNull(2)?.text.orEmpty())
        optionD.setText(options.getOrNull(3)?.text.orEmpty())
        checkMultiple.isChecked = content.multiple
        correctSpinner.adapter = android.widget.ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("A", "B", "C", "D")
        )
        AlertDialog.Builder(context, R.style.Theme_CNNT_Dialog)
            .setTitle("Editar questão")
            .setView(editorView)
            .setPositiveButton("Salvar") { _, _ ->
                val texts = listOf(optionA, optionB, optionC, optionD).map { it.text.toString().trim() }
                val correctIndex = correctSpinner.selectedItemPosition
                val alternatives = texts.filter { it.isNotBlank() }.mapIndexed { index, text ->
                    InteractiveAlternative(
                        text = text,
                        correct = if (checkMultiple.isChecked) index == correctIndex else index == correctIndex
                    )
                }
                updateBlockContent(
                    content.copy(
                        question = question.text.toString(),
                        explanation = explanation.text.toString(),
                        multiple = checkMultiple.isChecked,
                        alternatives = alternatives
                    )
                )
                renderBlock(block.copy(content = content.copy(
                    question = question.text.toString(),
                    explanation = explanation.text.toString(),
                    multiple = checkMultiple.isChecked,
                    alternatives = alternatives
                )))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showContextMenu(block: ContentBlock) {
        val actions = arrayOf("Editar", "Duplicar", "Trazer para frente", "Enviar para trás", "Excluir")
        AlertDialog.Builder(context, R.style.Theme_CNNT_Dialog)
            .setTitle(titleFor(block))
            .setItems(actions) { _, which ->
                val action = when (which) {
                    0 -> BlockContextAction.EDIT
                    1 -> BlockContextAction.DUPLICATE
                    2 -> BlockContextAction.BRING_TO_FRONT
                    3 -> BlockContextAction.SEND_TO_BACK
                    else -> BlockContextAction.DELETE
                }
                listener?.onContextAction(block.id, action)
            }
            .show()
    }

    private fun createResizeHandles() {
        ResizeDirection.entries.forEach { direction ->
            val handle = View(context).apply {
                background = badgeBackground("#FF4DA3FF")
                visibility = View.GONE
                elevation = dp(10f)
                layoutParams = LayoutParams(dp(12f).toInt(), dp(12f).toInt())
                setOnTouchListener { _, event ->
                    val current = block ?: return@setOnTouchListener false
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            listener?.onBlockSelected(current.id)
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            listener?.onResize(current.id, direction, event.rawX, event.rawY)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            listener?.onResizeFinished(current.id)
                            true
                        }
                        else -> false
                    }
                }
            }
            resizeHandles[direction] = handle
            addView(handle)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        positionHandles()
    }

    private fun positionHandles() {
        val size = dp(12f).toInt()
        val half = size / 2
        fun layoutHandle(direction: ResizeDirection, x: Int, y: Int) {
            resizeHandles[direction]?.layout(x - half, y - half, x + half, y + half)
        }
        layoutHandle(ResizeDirection.TOP_LEFT, 0, 0)
        layoutHandle(ResizeDirection.TOP, width / 2, 0)
        layoutHandle(ResizeDirection.TOP_RIGHT, width, 0)
        layoutHandle(ResizeDirection.RIGHT, width, height / 2)
        layoutHandle(ResizeDirection.BOTTOM_RIGHT, width, height)
        layoutHandle(ResizeDirection.BOTTOM, width / 2, height)
        layoutHandle(ResizeDirection.BOTTOM_LEFT, 0, height)
        layoutHandle(ResizeDirection.LEFT, 0, height / 2)
    }

    private fun updateChrome() {
        background = when {
            linking -> linkingChrome
            selected -> selectedChrome
            else -> chrome
        }
        resizeHandles.values.forEach { it.visibility = if (selected) View.VISIBLE else View.GONE }
    }

    private fun animateSelectionState() {
        val targetScale = if (selected) 1.01f else 1f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(this@BlockView, View.SCALE_X, scaleX, targetScale),
                ObjectAnimator.ofFloat(this@BlockView, View.SCALE_Y, scaleY, targetScale),
                ObjectAnimator.ofFloat(this@BlockView, View.ALPHA, alpha, 1f)
            )
            duration = 120
            start()
        }
    }

    fun animateCreation() {
        scaleX = 0.94f
        scaleY = 0.94f
        alpha = 0f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200L).start()
    }

    fun animateRemoval(onEnd: () -> Unit) {
        animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(180L).withEndAction(onEnd).start()
    }

    private fun titleFor(block: ContentBlock): String = when (block.type) {
        BlockType.Image -> "Imagem"
        BlockType.Markdown -> "Markdown"
        BlockType.Flashcard -> "Flashcard"
        BlockType.InteractiveText -> "Questão"
        BlockType.Pdf -> "PDF"
        BlockType.Text -> "Texto"
    }

    private fun autoSaveWatcher(onValue: (String) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                onValue(s?.toString().orEmpty())
            }
        }
    }

    private fun badgeBackground(color: String = "#223D4857") = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(10f)
        setStroke(dp(1f).toInt(), Color.parseColor("#22FFFFFF"))
    }

    private fun space(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(4f).toInt(), 1)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}