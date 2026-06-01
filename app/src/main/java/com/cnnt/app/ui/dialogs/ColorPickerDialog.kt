package com.cnnt.app.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import com.cnnt.app.R
import com.cnnt.app.ui.views.BrightnessSliderView
import com.cnnt.app.ui.views.ColorWheelView

class ColorPickerDialog(
    context: Context,
    private val initialColor: Int = Color.WHITE,
    private val onColorSelected: (Int) -> Unit
) : Dialog(context, R.style.Theme_CNNT_Dialog) {

    private var selectedColor = initialColor
    private var selectedAlpha = 255
    private var isUpdatingHex = false

    private val presetColors = mutableListOf(
        0xFFFF3333.toInt(),
        0xFFFF8800.toInt(),
        0xFFFFEE00.toInt(),
        0xFF4466FF.toInt(),
        0xFFFFFFFF.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_color_picker_pro)

        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val colorWheel = findViewById<ColorWheelView>(R.id.colorWheel)
        val brightnessSlider = findViewById<BrightnessSliderView>(R.id.brightnessSlider)
        val hexInput = findViewById<EditText>(R.id.hexInput)
        val colorPreview = findViewById<View>(R.id.colorPreview)
        val opacitySlider = findViewById<SeekBar>(R.id.opacitySlider)
        val opacityLabel = findViewById<TextView>(R.id.opacityLabel)

        // Initialize with current color
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        selectedAlpha = Color.alpha(initialColor)

        colorWheel.setColor(initialColor)
        brightnessSlider.setHueSat(hsv[0], hsv[1])
        brightnessSlider.setBrightness(hsv[2])
        opacitySlider.progress = (selectedAlpha / 255f * 100).toInt()
        opacityLabel.text = "${opacitySlider.progress}%"

        updateColorPreview(colorPreview, initialColor)
        updateHexInput(hexInput, initialColor)

        // Color wheel changes
        colorWheel.onColorChanged = { color ->
            selectedColor = color
            val h = FloatArray(3)
            Color.colorToHSV(color, h)
            brightnessSlider.setHueSat(h[0], h[1])
            updateColorPreview(colorPreview, applyAlpha(color))
            updateHexInput(hexInput, color)
        }

        // Brightness slider changes
        brightnessSlider.onBrightnessChanged = { brightness ->
            colorWheel.brightness = brightness
            selectedColor = colorWheel.getSelectedColor()
            updateColorPreview(colorPreview, applyAlpha(selectedColor))
            updateHexInput(hexInput, selectedColor)
        }

        // Opacity slider
        opacitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedAlpha = (progress / 100f * 255).toInt()
                opacityLabel.text = "$progress%"
                updateColorPreview(colorPreview, applyAlpha(selectedColor))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Hex input
        hexInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyHexInput(hexInput, colorWheel, brightnessSlider, colorPreview)
                true
            } else false
        }

        // Preset swatches
        setupPresets(colorWheel, brightnessSlider, colorPreview, hexInput)

        // Add preset button
        findViewById<View>(R.id.btnAddPreset)?.setOnClickListener {
            if (presetColors.size < 10) {
                presetColors.add(selectedColor)
            }
        }

        // Apply button
        findViewById<View>(R.id.btnApply)?.setOnClickListener {
            onColorSelected(applyAlpha(selectedColor))
            dismiss()
        }
    }

    private fun applyAlpha(color: Int): Int {
        return Color.argb(selectedAlpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun updateColorPreview(view: View, color: Int) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2, 0x44FFFFFF.toInt())
        }
        view.background = drawable
    }

    private fun updateHexInput(editText: EditText, color: Int) {
        if (isUpdatingHex) return
        isUpdatingHex = true
        val hex = String.format("#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color))
        editText.setText(hex)
        isUpdatingHex = false
    }

    private fun applyHexInput(
        editText: EditText,
        wheel: ColorWheelView,
        slider: BrightnessSliderView,
        preview: View
    ) {
        try {
            val hex = editText.text.toString().trim()
            val color = Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
            selectedColor = color
            wheel.setColor(color)
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            slider.setHueSat(hsv[0], hsv[1])
            slider.setBrightness(hsv[2])
            updateColorPreview(preview, applyAlpha(color))
        } catch (_: Exception) {
            // Invalid hex, ignore
        }
    }

    private fun setupPresets(
        wheel: ColorWheelView,
        slider: BrightnessSliderView,
        preview: View,
        hexInput: EditText
    ) {
        val presetIds = listOf(R.id.preset1, R.id.preset2, R.id.preset3, R.id.preset4, R.id.preset5)
        for ((index, id) in presetIds.withIndex()) {
            val presetView = findViewById<View>(id) ?: continue
            val color = presetColors.getOrNull(index) ?: continue

            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
                cornerRadius = 4f
                setStroke(2, 0x44FFFFFF.toInt())
            }
            presetView.background = drawable

            presetView.setOnClickListener {
                selectedColor = color
                wheel.setColor(color)
                val hsv = FloatArray(3)
                Color.colorToHSV(color, hsv)
                slider.setHueSat(hsv[0], hsv[1])
                slider.setBrightness(hsv[2])
                updateColorPreview(preview, applyAlpha(color))
                updateHexInput(hexInput, color)
            }
        }
    }
}
