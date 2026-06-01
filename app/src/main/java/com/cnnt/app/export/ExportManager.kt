package com.cnnt.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.cnnt.app.canvas.CanvasScale
import com.cnnt.app.data.model.*
import com.cnnt.app.ink.InkEngine
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportManager(private val context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val inkEngine = InkEngine()

    fun exportAsJson(notebook: Notebook, outputFile: File) {
        val json = gson.toJson(notebook)
        FileWriter(outputFile).use { it.write(json) }
    }

    fun exportAsMarkdown(notebook: Notebook, outputFile: File) {
        val sb = StringBuilder()
        sb.appendLine("# ${notebook.name}")
        sb.appendLine()
        sb.appendLine("Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(notebook.createdAt))}")
        sb.appendLine()

        for ((boardIndex, board) in notebook.boards.withIndex()) {
            sb.appendLine("## Page ${boardIndex + 1}: ${board.name}")
            sb.appendLine()

            for (layer in board.layers) {
                for (obj in layer.objects) {
                    when (val content = obj.content) {
                        is ObjectContent.Text -> {
                            sb.appendLine(content.text)
                            sb.appendLine()
                        }
                        is ObjectContent.Checklist -> {
                            for (item in content.items) {
                                val check = if (item.checked) "x" else " "
                                sb.appendLine("- [$check] ${item.text}")
                            }
                            sb.appendLine()
                        }
                        is ObjectContent.Link -> {
                            sb.appendLine("[${content.title.ifEmpty { content.url }}](${content.url})")
                            sb.appendLine()
                        }
                        else -> {}
                    }
                }

                if (layer.strokes.isNotEmpty()) {
                    sb.appendLine("*[${layer.strokes.size} handwritten strokes]*")
                    sb.appendLine()
                }
            }
        }

        FileWriter(outputFile).use { it.write(sb.toString()) }
    }

    fun exportAsObsidianCanvas(notebook: Notebook, outputFile: File) {
        val nodes = mutableListOf<Map<String, Any>>()
        val edges = mutableListOf<Map<String, Any>>()

        for (board in notebook.boards) {
            for (layer in board.layers) {
                for (obj in layer.objects) {
                    val node = mutableMapOf<String, Any>(
                        "id" to obj.id,
                        "x" to obj.x.toInt(),
                        "y" to obj.y.toInt(),
                        "width" to obj.width.toInt(),
                        "height" to obj.height.toInt(),
                        "type" to "text"
                    )
                    when (val content = obj.content) {
                        is ObjectContent.Text -> node["text"] = content.text
                        is ObjectContent.Link -> {
                            node["type"] = "link"
                            node["url"] = content.url
                        }
                        is ObjectContent.Checklist -> {
                            node["text"] = content.items.joinToString("\n") { item ->
                                val check = if (item.checked) "x" else " "
                                "- [$check] ${item.text}"
                            }
                        }
                        else -> node["text"] = "[${obj.type.name}]"
                    }
                    nodes.add(node)
                }
            }
        }

        val canvasData = mapOf("nodes" to nodes, "edges" to edges)
        val json = gson.toJson(canvasData)
        FileWriter(outputFile).use { it.write(json) }
    }

    fun exportAsPng(
        board: Board,
        outputFile: File,
        width: Int = CanvasScale.EXPORT_WIDTH_PX,
        height: Int = CanvasScale.EXPORT_HEIGHT_PX
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(board.backgroundColor)

        // Calculate bounds and scale
        val allStrokes = board.layers.flatMap { it.strokes }
        if (allStrokes.isEmpty()) {
            saveBitmap(bitmap, outputFile)
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
        val padding = 50f
        val scaleX = (width - padding * 2) / contentWidth
        val scaleY = (height - padding * 2) / contentHeight
        val scale = minOf(scaleX, scaleY)

        canvas.translate(padding - minX * scale, padding - minY * scale)
        canvas.scale(scale, scale)

        for (layer in board.layers) {
            if (!layer.visible) continue
            for (stroke in layer.strokes) {
                val brush = BrushPreset.defaultBrushes().find { it.id == stroke.brushId }
                    ?: BrushPreset.gelPen()
                inkEngine.renderStroke(canvas, stroke, brush)
            }
        }

        saveBitmap(bitmap, outputFile)
    }

    fun exportAsPdf(notebook: Notebook, outputFile: File) {
        val document = PdfDocument()
        val pageWidth = 595 // A4
        val pageHeight = 842

        for ((index, board) in notebook.boards.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawColor(Color.WHITE)

            // Render strokes scaled to page
            val allStrokes = board.layers.flatMap { it.strokes }
            if (allStrokes.isNotEmpty()) {
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
                val margin = 40f
                val scaleX = (pageWidth - margin * 2) / contentWidth
                val scaleY = (pageHeight - margin * 2) / contentHeight
                val scale = minOf(scaleX, scaleY)

                canvas.translate(margin - minX * scale, margin - minY * scale)
                canvas.scale(scale, scale)

                for (layer in board.layers) {
                    if (!layer.visible) continue
                    for (stroke in layer.strokes) {
                        val brush = BrushPreset.defaultBrushes().find { it.id == stroke.brushId }
                            ?: BrushPreset.gelPen()
                        inkEngine.renderStroke(canvas, stroke, brush)
                    }
                }
            }

            document.finishPage(page)
        }

        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    fun exportBackup(workspace: Workspace, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            // Workspace metadata
            zip.putNextEntry(ZipEntry("workspace.json"))
            zip.write(gson.toJson(workspace).toByteArray())
            zip.closeEntry()

            // Individual notebooks
            for (notebook in workspace.notebooks) {
                zip.putNextEntry(ZipEntry("notebooks/${notebook.id}.json"))
                zip.write(gson.toJson(notebook).toByteArray())
                zip.closeEntry()
            }

            // Brush presets
            zip.putNextEntry(ZipEntry("brushes.json"))
            zip.write(gson.toJson(workspace.brushPresets).toByteArray())
            zip.closeEntry()

            // Palettes
            zip.putNextEntry(ZipEntry("palettes.json"))
            zip.write(gson.toJson(workspace.palettes).toByteArray())
            zip.closeEntry()

            // Settings
            zip.putNextEntry(ZipEntry("settings.json"))
            zip.write(gson.toJson(workspace.settings).toByteArray())
            zip.closeEntry()
        }
    }

    fun exportFlashcardsAsCsv(flashcards: List<Flashcard>, outputFile: File) {
        FileWriter(outputFile).use { writer ->
            writer.write("front,back,tags,difficulty\n")
            for (card in flashcards) {
                val front = card.front.replace("\"", "\"\"")
                val back = card.back.replace("\"", "\"\"")
                val tags = card.tags.joinToString(";")
                writer.write("\"$front\",\"$back\",\"$tags\",\"${card.difficulty}\"\n")
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
