package com.cnnt.app.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.cnnt.app.R
import com.cnnt.app.data.model.BrushCategory
import com.cnnt.app.data.model.BrushPreset
import com.cnnt.app.data.model.TipShape

class BrushSettingsDialog(
    context: Context,
    private var brush: BrushPreset,
    private val onSettingsChanged: (BrushPreset) -> Unit
) : Dialog(context, R.style.Theme_CNNT_Dialog) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_brush_settings)

        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val title = findViewById<TextView>(R.id.settingsTitle)
        title?.text = "Configurar: ${brush.name}"

        val container = findViewById<LinearLayout>(R.id.settingsContainer)
        container ?: return

        // === Common settings for all brushes ===
        addSlider(container, "Opacidade", (brush.opacity * 100).toInt(), 5, 100) { value ->
            brush = brush.copy(opacity = value / 100f)
            onSettingsChanged(brush)
        }

        addSlider(container, "Suavização", (brush.smoothing * 100).toInt(), 0, 100) { value ->
            brush = brush.copy(smoothing = value / 100f)
            onSettingsChanged(brush)
        }

        addSlider(container, "Sensibilidade à Pressão", (brush.pressureSensitivity * 50).toInt(), 0, 100) { value ->
            brush = brush.copy(pressureSensitivity = value / 50f)
            onSettingsChanged(brush)
        }

        // === Category-specific settings ===
        when (brush.category) {
            BrushCategory.CALLIGRAPHY, BrushCategory.SPECIAL -> {
                addSlider(container, "Sensibilidade Direcional", (brush.directionSensitivity * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(directionSensitivity = value / 100f)
                    onSettingsChanged(brush)
                }
                addSlider(container, "Sensibilidade ao Tilt", (brush.tiltSensitivity * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(tiltSensitivity = value / 100f)
                    onSettingsChanged(brush)
                }
            }
            BrushCategory.PENCIL, BrushCategory.TEXTURE -> {
                addSlider(container, "Grão / Textura", (brush.grain * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(grain = value / 100f)
                    onSettingsChanged(brush)
                }
            }
            BrushCategory.SPRAY -> {
                addSlider(container, "Dispersão (Jitter)", (brush.jitter * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(jitter = value / 100f)
                    onSettingsChanged(brush)
                }
                addSlider(container, "Espaçamento", (brush.spacing * 100).toInt(), 1, 50) { value ->
                    brush = brush.copy(spacing = value / 100f)
                    onSettingsChanged(brush)
                }
            }
            BrushCategory.INK -> {
                addSlider(container, "Sensibilidade à Velocidade", (brush.velocitySensitivity * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(velocitySensitivity = value / 100f)
                    onSettingsChanged(brush)
                }
                addSlider(container, "Dispersão (Jitter)", (brush.jitter * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(jitter = value / 100f)
                    onSettingsChanged(brush)
                }
            }
            BrushCategory.MARKER, BrushCategory.HIGHLIGHTER -> {
                addSlider(container, "Sensibilidade Direcional", (brush.directionSensitivity * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(directionSensitivity = value / 100f)
                    onSettingsChanged(brush)
                }
            }
            BrushCategory.EFFECT -> {
                addSlider(container, "Sensibilidade à Velocidade", (brush.velocitySensitivity * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(velocitySensitivity = value / 100f)
                    onSettingsChanged(brush)
                }
            }
            BrushCategory.EXPERIMENTAL -> {
                addSlider(container, "Dispersão (Jitter)", (brush.jitter * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(jitter = value / 100f)
                    onSettingsChanged(brush)
                }
                addSlider(container, "Sensibilidade à Velocidade", (brush.velocitySensitivity * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(velocitySensitivity = value / 100f)
                    onSettingsChanged(brush)
                }
                addSlider(container, "Sensibilidade Direcional", (brush.directionSensitivity * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(directionSensitivity = value / 100f)
                    onSettingsChanged(brush)
                }
                addSlider(container, "Grão / Textura", (brush.grain * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(grain = value / 100f)
                    onSettingsChanged(brush)
                }
            }
            else -> {
                addSlider(container, "Sensibilidade à Velocidade", (brush.velocitySensitivity * 100).toInt(), 0, 100) { value ->
                    brush = brush.copy(velocitySensitivity = value / 100f)
                    onSettingsChanged(brush)
                }
            }
        }
    }

    private fun addSlider(
        container: LinearLayout,
        label: String,
        currentValue: Int,
        min: Int,
        max: Int,
        onChange: (Int) -> Unit
    ) {
        val labelView = TextView(context).apply {
            text = "$label: $currentValue%"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setPadding(0, 16, 0, 4)
        }
        container.addView(labelView)

        val seekBar = SeekBar(context).apply {
            this.max = max - min
            progress = currentValue - min
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF00B0FF.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFF00B0FF.toInt())

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + min
                    labelView.text = "$label: $value%"
                    onChange(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(seekBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }
}
