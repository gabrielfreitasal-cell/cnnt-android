package com.cnnt.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.cnnt.app.canvas.CanvasScale
import com.cnnt.app.data.dao.BoardEntity
import com.cnnt.app.data.dao.ContentBlockEntity
import com.cnnt.app.data.dao.FlashcardEntity
import com.cnnt.app.data.dao.LayerEntity
import com.cnnt.app.data.dao.LinkEdgeEntity
import com.cnnt.app.data.dao.NotebookEntity
import com.cnnt.app.data.dao.SpatialObjectEntity
import com.cnnt.app.data.dao.StrokeEntity
import com.cnnt.app.data.model.*
import com.cnnt.app.ink.InkEngine
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportManager(private val context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val inkEngine = InkEngine()

    fun exportAsJson(notebook: Notebook, outputFile: File) {
        val json = gson.toJson(
            mapOf(
                "notebook" to notebook,
                "blocks" to notebook.boards.flatMap { board -> board.layers.flatMap { it.contentBlocks } },
                "links" to notebook.boards.flatMap { board -> board.layers.flatMap { it.linkEdges } }
            )
        )
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

                for (block in layer.contentBlocks) {
                    when (val content = block.content) {
                        is BlockContent.TextNote -> {
                            if (content.text.isNotBlank()) {
                                sb.appendLine(content.text)
                                sb.appendLine()
                            }
                        }
                        is BlockContent.Markdown -> {
                            sb.appendLine(content.markdown)
                            sb.appendLine()
                        }
                        is BlockContent.InteractiveText -> {
                            sb.appendLine("### ${content.question}")
                            content.alternatives.forEach { alternative ->
                                sb.appendLine("- ${alternative.text}")
                            }
                            if (content.explanation.isNotBlank()) {
                                sb.appendLine()
                                sb.appendLine(content.explanation)
                            }
                            sb.appendLine()
                        }
                        is BlockContent.Flashcard -> {
                            sb.appendLine("> Flashcard: ${content.previewText}")
                            sb.appendLine()
                        }
                        is BlockContent.Image -> sb.appendLine("![${content.displayName}](${content.uri})")
                        is BlockContent.Pdf -> sb.appendLine("[PDF] ${content.displayName} (${content.uri})")
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
                for (block in layer.contentBlocks) {
                    val node = mutableMapOf<String, Any>(
                        "id" to block.id,
                        "x" to block.posX.toInt(),
                        "y" to block.posY.toInt(),
                        "width" to block.width.toInt(),
                        "height" to block.height.toInt(),
                        "type" to "text"
                    )
                    when (val content = block.content) {
                        is BlockContent.TextNote -> node["text"] = content.text
                        is BlockContent.Markdown -> node["text"] = content.markdown
                        is BlockContent.Flashcard -> node["text"] = content.previewText
                        is BlockContent.InteractiveText -> node["text"] = content.question
                        is BlockContent.Image -> {
                            node["type"] = "file"
                            node["file"] = content.uri
                        }
                        is BlockContent.Pdf -> {
                            node["type"] = "file"
                            node["file"] = content.uri
                        }
                    }
                    nodes.add(node)
                }
                layer.linkEdges.forEach { edge ->
                    edges.add(
                        mapOf(
                            "id" to edge.id,
                            "fromNode" to edge.sourceBlockId,
                            "toNode" to edge.targetBlockId,
                            "label" to edge.label
                        )
                    )
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
        val allBlocks = board.layers.flatMap { it.contentBlocks }
        if (allStrokes.isEmpty() && allBlocks.isEmpty()) {
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
        allBlocks.forEach { block ->
            minX = minOf(minX, block.posX)
            minY = minOf(minY, block.posY)
            maxX = maxOf(maxX, block.posX + block.width)
            maxY = maxOf(maxY, block.posY + block.height)
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
            drawBlocks(canvas, layer.contentBlocks)
            drawLinks(canvas, layer.contentBlocks, layer.linkEdges)
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
            val allBlocks = board.layers.flatMap { it.contentBlocks }
            if (allStrokes.isNotEmpty() || allBlocks.isNotEmpty()) {
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
                allBlocks.forEach { block ->
                    minX = minOf(minX, block.posX)
                    minY = minOf(minY, block.posY)
                    maxX = maxOf(maxX, block.posX + block.width)
                    maxY = maxOf(maxY, block.posY + block.height)
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
                    drawBlocks(canvas, layer.contentBlocks)
                    drawLinks(canvas, layer.contentBlocks, layer.linkEdges)
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

    fun exportFullBackupZip(
        outputUri: Uri,
        versionName: String,
        notebookEntities: List<NotebookEntity>,
        boardEntities: List<BoardEntity>,
        layerEntities: List<LayerEntity>,
        strokeEntities: List<StrokeEntity>,
        spatialObjectEntities: List<SpatialObjectEntity>,
        flashcardEntities: List<FlashcardEntity>,
        contentBlockEntities: List<ContentBlockEntity>,
        linkEdgeEntities: List<LinkEdgeEntity>,
        assetFiles: List<File>,
        cachedPngFiles: List<File>
    ) {
        val outputStream = context.contentResolver.openOutputStream(outputUri)
            ?: throw IllegalStateException("Não foi possível abrir o destino para backup")

        outputStream.use { stream ->
            ZipOutputStream(stream).use { zip ->
                writeJsonEntry(zip, "manifest.json", mapOf(
                    "appVersion" to versionName,
                    "exportedAt" to System.currentTimeMillis(),
                    "format" to "cnnt-full-backup-v1"
                ))
                writeJsonEntry(zip, "room/notebooks.json", notebookEntities)
                writeJsonEntry(zip, "room/boards.json", boardEntities)
                writeJsonEntry(zip, "room/layers.json", layerEntities)
                writeJsonEntry(zip, "room/strokes.json", strokeEntities)
                writeJsonEntry(zip, "room/spatial_objects.json", spatialObjectEntities)
                writeJsonEntry(zip, "room/flashcards.json", flashcardEntities)
                writeJsonEntry(zip, "room/blocks.json", contentBlockEntities)
                writeJsonEntry(zip, "room/links.json", linkEdgeEntities)

                assetFiles.filter { it.exists() && it.isFile }.forEach { file ->
                    val folder = if (file.extension.equals("pdf", true)) "assets/pdfs" else "assets/images"
                    zip.putNextEntry(ZipEntry("$folder/${file.name}"))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }

                cachedPngFiles.filter { it.exists() && it.isFile }.forEach { file ->
                    zip.putNextEntry(ZipEntry("cache_png/${file.name}"))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
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

    private fun drawBlocks(canvas: Canvas, blocks: List<ContentBlock>) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EE20232A") }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
        }
        blocks.sortedBy { it.zIndex }.forEach { block ->
            val rect = android.graphics.RectF(block.posX, block.posY, block.posX + block.width, block.posY + block.height)
            canvas.drawRoundRect(rect, 18f, 18f, fill)
            canvas.drawRoundRect(rect, 18f, 18f, stroke)
            val label = when (val content = block.content) {
                is BlockContent.TextNote -> content.text.ifBlank { "Texto" }
                is BlockContent.Markdown -> content.markdown.lineSequence().firstOrNull().orEmpty().ifBlank { "Markdown" }
                is BlockContent.Flashcard -> content.previewText.ifBlank { "Flashcard" }
                is BlockContent.InteractiveText -> content.question.ifBlank { "Questão" }
                is BlockContent.Image -> content.displayName.ifBlank { "Imagem" }
                is BlockContent.Pdf -> content.displayName.ifBlank { "PDF" }
            }.take(48)
            canvas.drawText(label, block.posX + 16f, block.posY + 32f, text)
        }
    }

    private fun drawLinks(canvas: Canvas, blocks: List<ContentBlock>, links: List<LinkEdge>) {
        if (links.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#88CCFF")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16f
        }
        val blocksById = blocks.associateBy { it.id }
        links.forEach { edge ->
            val source = blocksById[edge.sourceBlockId] ?: return@forEach
            val target = blocksById[edge.targetBlockId] ?: return@forEach
            val startX = source.posX + source.width
            val startY = source.posY + source.height / 2f
            val endX = target.posX
            val endY = target.posY + target.height / 2f
            val controlOffset = kotlin.math.abs(endX - startX) * 0.45f
            val path = android.graphics.Path().apply {
                moveTo(startX, startY)
                cubicTo(startX + controlOffset, startY, endX - controlOffset, endY, endX, endY)
            }
            canvas.drawPath(path, paint)
            if (edge.label.isNotBlank()) {
                canvas.drawText(edge.label, (startX + endX) / 2f, (startY + endY) / 2f, textPaint)
            }
        }
    }

    private fun writeJsonEntry(zip: ZipOutputStream, name: String, payload: Any) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(gson.toJson(payload).toByteArray())
        zip.closeEntry()
    }
}
