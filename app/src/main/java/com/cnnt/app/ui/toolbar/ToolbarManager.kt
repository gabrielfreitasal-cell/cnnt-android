package com.cnnt.app.ui.toolbar

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.PopupMenu
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.cnnt.app.canvas.CanvasMode
import com.cnnt.app.canvas.CanvasScale
import com.cnnt.app.canvas.LassoMode
import com.cnnt.app.data.model.BrushPreset
import com.cnnt.app.databinding.ActivityMainBinding
import com.cnnt.app.ui.dialogs.BrushPickerDialog
import com.cnnt.app.ui.dialogs.BrushSettingsDialog
import com.cnnt.app.ui.dialogs.ColorPickerDialog

class ToolbarManager(
    private val binding: ActivityMainBinding,
    private val activity: AppCompatActivity
) {
    var onBrushSelected: ((BrushPreset) -> Unit)? = null
    var onColorSelected: ((Int) -> Unit)? = null
    var onSizeChanged: ((Float) -> Unit)? = null
    var onModeSelected: ((CanvasMode) -> Unit)? = null
    var onLassoModeSelected: ((LassoMode) -> Unit)? = null
    var onUndoClicked: (() -> Unit)? = null
    var onRedoClicked: (() -> Unit)? = null
    var onFocusModeToggled: (() -> Unit)? = null
    var onExportClicked: (() -> Unit)? = null
    var onOcrClicked: (() -> Unit)? = null
    var onFlashcardClicked: (() -> Unit)? = null
    var onBrushSettingsChanged: ((BrushPreset) -> Unit)? = null
    var onDeleteSelectionClicked: (() -> Unit)? = null

    private var currentBrush: BrushPreset = BrushPreset.gelPen()
    private var currentColor: Int = Color.WHITE
    private var lastLassoMode: LassoMode = LassoMode.FREE

    init {
        setupButtons()
        setupSizeSlider()
    }

    private fun setupButtons() {
        binding.btnBrush.setOnClickListener {
            BrushPickerDialog(activity) { brush ->
                currentBrush = brush
                onBrushSelected?.invoke(brush)
                updateActiveMode(CanvasMode.DRAW)
            }.show()
        }

        binding.btnColor.setOnClickListener {
            ColorPickerDialog(activity, currentColor) { color ->
                currentColor = color
                onColorSelected?.invoke(color)
                updateColorIndicator(color)
            }.show()
        }

        binding.btnEraser.setOnClickListener {
            onModeSelected?.invoke(CanvasMode.ERASE)
            highlightButton(binding.btnEraser)
        }

        binding.btnSelect.setOnClickListener {
            onModeSelected?.invoke(CanvasMode.SELECT)
            highlightButton(binding.btnSelect)
        }

        // Lasso: click = use last mode, long-press = show popup with options
        binding.btnLasso.setOnClickListener {
            onLassoModeSelected?.invoke(lastLassoMode)
            onModeSelected?.invoke(CanvasMode.LASSO)
            highlightButton(binding.btnLasso)
        }

        binding.btnLasso.setOnLongClickListener {
            showLassoModePopup()
            true
        }

        binding.btnUndo.setOnClickListener {
            onUndoClicked?.invoke()
        }

        binding.btnRedo.setOnClickListener {
            onRedoClicked?.invoke()
        }

        binding.btnFocus.setOnClickListener {
            onFocusModeToggled?.invoke()
        }

        binding.btnExport.setOnClickListener {
            onExportClicked?.invoke()
        }

        binding.btnOcr.setOnClickListener {
            onOcrClicked?.invoke()
        }

        binding.btnFlashcard.setOnClickListener {
            onFlashcardClicked?.invoke()
        }

        binding.btnFlashcardTool.setOnClickListener {
            onModeSelected?.invoke(CanvasMode.FLASHCARD)
            highlightButton(binding.btnFlashcardTool)
        }

        binding.btnBrushSettings.setOnClickListener {
            showBrushSettings()
        }

        binding.btnDeleteSelection.setOnClickListener {
            onDeleteSelectionClicked?.invoke()
        }
    }

    private fun showLassoModePopup() {
        val popup = PopupMenu(activity, binding.btnLasso)
        popup.menu.add(0, 0, 0, "Seleção Livre")
        popup.menu.add(0, 1, 1, "Seleção Retangular")
        popup.setOnMenuItemClickListener { item ->
            val mode = if (item.itemId == 0) LassoMode.FREE else LassoMode.RECTANGLE
            lastLassoMode = mode
            onLassoModeSelected?.invoke(mode)
            onModeSelected?.invoke(CanvasMode.LASSO)
            highlightButton(binding.btnLasso)
            true
        }
        popup.show()
    }

    private fun showBrushSettings() {
        BrushSettingsDialog(activity, currentBrush) { updatedBrush ->
            currentBrush = updatedBrush
            onBrushSettingsChanged?.invoke(updatedBrush)
        }.show()
    }

    private fun setupSizeSlider() {
        val range = CanvasScale.MAX_BRUSH_MM - CanvasScale.MIN_BRUSH_MM
        binding.sizeSlider.progress = ((CanvasScale.DEFAULT_BRUSH_MM - CanvasScale.MIN_BRUSH_MM) / range * 100).toInt()
        binding.sizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sizeMm = CanvasScale.MIN_BRUSH_MM + (progress / 100f) * range
                onSizeChanged?.invoke(sizeMm)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    fun updateBrushIndicator(brush: BrushPreset) {
        currentBrush = brush
        binding.btnBrush.text = brush.name
    }

    fun updateColorIndicator(color: Int) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2, Color.WHITE)
        }
        binding.btnColor.background = drawable
    }

    private fun highlightButton(activeBtn: View) {
        listOf<View>(
            binding.btnBrush,
            binding.btnEraser,
            binding.btnSelect,
            binding.btnLasso,
            binding.btnFlashcardTool
        ).forEach { btn ->
            btn.alpha = if (btn == activeBtn) 1.0f else 0.6f
        }
    }

    fun updateActiveMode(mode: CanvasMode, isTemporary: Boolean = false) {
        when (mode) {
            CanvasMode.DRAW -> highlightButton(binding.btnBrush)
            CanvasMode.ERASE -> highlightButton(binding.btnEraser)
            CanvasMode.SELECT -> highlightButton(binding.btnSelect)
            CanvasMode.LASSO -> highlightButton(binding.btnLasso)
            CanvasMode.FLASHCARD -> highlightButton(binding.btnFlashcardTool)
            CanvasMode.REGION_OCR, CanvasMode.PAN, CanvasMode.INSERT -> {
                listOf<View>(
                    binding.btnBrush,
                    binding.btnEraser,
                    binding.btnSelect,
                    binding.btnLasso,
                    binding.btnFlashcardTool
                ).forEach { it.alpha = 0.6f }
            }
        }

        if (isTemporary) {
            when (mode) {
                CanvasMode.ERASE -> binding.btnEraser.alpha = 1.0f
                CanvasMode.LASSO -> binding.btnLasso.alpha = 1.0f
                else -> {}
            }
        }
    }

    fun setDeleteSelectionVisible(visible: Boolean) {
        binding.btnDeleteSelection.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
