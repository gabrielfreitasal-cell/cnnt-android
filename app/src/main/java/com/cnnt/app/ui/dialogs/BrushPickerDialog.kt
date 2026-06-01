package com.cnnt.app.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cnnt.app.R
import com.cnnt.app.data.model.BrushPreset
import com.cnnt.app.data.model.Stroke
import com.cnnt.app.data.model.StrokePoint
import com.cnnt.app.ink.InkEngine

class BrushPickerDialog(
    context: Context,
    private val onBrushSelected: (BrushPreset) -> Unit
) : Dialog(context, R.style.Theme_CNNT_Dialog) {

    private val brushes = BrushPreset.defaultBrushes()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_brush_picker)

        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.75).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val recyclerView = findViewById<RecyclerView>(R.id.brushGrid)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = BrushAdapter(brushes) { brush ->
            onBrushSelected(brush)
            dismiss()
        }
    }
}

class BrushAdapter(
    private val brushes: List<BrushPreset>,
    private val onClick: (BrushPreset) -> Unit
) : RecyclerView.Adapter<BrushAdapter.ViewHolder>() {

    private val inkEngine = InkEngine()

    // Color for each brush type for the preview stroke
    private val brushColors = listOf(
        0xFF88CCFF.toInt(), // Caneta Gel - blue
        0xFFCCCCCC.toInt(), // Lapiz - gray
        0xFFFF8844.toInt(), // Marcador - orange
        0xFFFFEE44.toInt(), // Marca-texto - yellow
        0xFF88CCFF.toInt(), // Caligrafia - blue
        0xFF44DDAA.toInt(), // CNNT - green
        0xFF6688FF.toInt(), // Tinta Umida - indigo
        0xFFDDDDDD.toInt(), // Caneta Tecnica - white
        0xFFAA88CC.toInt(), // Granulado - purple
        0xFF44FF88.toInt(), // Neon - neon green
        0xFFFF6666.toInt(), // Spray - red
        0xFFFF44AA.toInt()  // Experimental - pink
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.brushName)
        val categoryText: TextView = view.findViewById(R.id.brushCategory)
        val previewView: View = view.findViewById(R.id.brushPreview)
        val iconView: ImageView = view.findViewById(R.id.brushIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_brush, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val brush = brushes[position]
        val color = brushColors.getOrElse(position) { Color.WHITE }

        holder.nameText.text = brush.name
        holder.categoryText.text = brush.category.name.lowercase()
        holder.itemView.setOnClickListener { onClick(brush) }

        // Tint icon with brush color
        holder.iconView.setColorFilter(color, PorterDuff.Mode.SRC_IN)

        // Render stroke preview with brush color
        holder.previewView.post {
            val w = holder.previewView.width
            val h = holder.previewView.height
            if (w > 0 && h > 0) {
                val preview = generatePreview(brush, w, h, color)
                holder.previewView.background = BitmapDrawable(
                    holder.previewView.context.resources, preview
                )
            }
        }
    }

    override fun getItemCount() = brushes.size

    private fun generatePreview(brush: BrushPreset, width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val stroke = Stroke(
            brushId = brush.id,
            color = color,
            size = brush.baseSize,
            opacity = brush.opacity
        )

        val margin = 8f
        val midY = height / 2f
        val steps = 30
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = margin + t * (width - margin * 2)
            val y = midY + sin(t * Math.PI * 2).toFloat() * (height * 0.25f)
            val pressure = 0.3f + 0.5f * sin(t * Math.PI).toFloat()
            stroke.addPoint(StrokePoint(
                x = x, y = y,
                pressure = pressure,
                timestamp = (i * 16).toLong()
            ))
        }

        inkEngine.renderStroke(canvas, stroke, brush)
        return bitmap
    }

    private fun sin(value: Double): Float = kotlin.math.sin(value).toFloat()
}
