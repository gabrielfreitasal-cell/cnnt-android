package com.cnnt.app.ui.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import com.cnnt.app.R
import com.cnnt.app.ui.MainViewModel
import com.cnnt.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExportDialog(
    context: Context,
    private val viewModel: MainViewModel
) : Dialog(context, R.style.Theme_CNNT_Dialog) {

    private var pendingBackupUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_export)

        val exportDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "CNNT"
        ).also { it.mkdirs() }

        findViewById<Button>(R.id.btnExportJson)?.setOnClickListener {
            exportJson(exportDir)
        }
        findViewById<Button>(R.id.btnExportMarkdown)?.setOnClickListener {
            exportMarkdown(exportDir)
        }
        findViewById<Button>(R.id.btnExportObsidian)?.setOnClickListener {
            exportObsidian(exportDir)
        }
        findViewById<Button>(R.id.btnExportPng)?.setOnClickListener {
            exportPng(exportDir)
        }
        findViewById<Button>(R.id.btnExportPdf)?.setOnClickListener {
            exportPdf(exportDir)
        }
        findViewById<Button>(R.id.btnExportBackup)?.setOnClickListener {
            launchBackupPicker()
        }
    }

    private fun exportJson(dir: File) {
        val notebook = viewModel.currentNotebook.value ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(dir, "${notebook.name}_${System.currentTimeMillis()}.json")
            viewModel.exportManager.exportAsJson(notebook, file)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Exportado: ${file.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    private fun exportMarkdown(dir: File) {
        val notebook = viewModel.currentNotebook.value ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(dir, "${notebook.name}_${System.currentTimeMillis()}.md")
            viewModel.exportManager.exportAsMarkdown(notebook, file)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Exportado: ${file.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    private fun exportObsidian(dir: File) {
        val notebook = viewModel.currentNotebook.value ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(dir, "${notebook.name}_${System.currentTimeMillis()}.canvas")
            viewModel.exportManager.exportAsObsidianCanvas(notebook, file)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Exportado: ${file.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    private fun exportPng(dir: File) {
        val board = viewModel.currentBoard.value ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(dir, "page_${System.currentTimeMillis()}.png")
            viewModel.exportManager.exportAsPng(board, file)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Exportado: ${file.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    private fun exportPdf(dir: File) {
        val notebook = viewModel.currentNotebook.value ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(dir, "${notebook.name}_${System.currentTimeMillis()}.pdf")
            viewModel.exportManager.exportAsPdf(notebook, file)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Exportado: ${file.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    private fun launchBackupPicker() {
        val activity = context as? Activity ?: return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "cnnt_backup_${System.currentTimeMillis()}.zip")
        }
        activity.startActivityForResult(intent, REQUEST_CREATE_BACKUP)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CREATE_BACKUP) return false
        if (resultCode != Activity.RESULT_OK) return true
        val uri = data?.data ?: return true
        exportBackup(uri)
        return true
    }

    private fun exportBackup(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val app = viewModel.getApplication<com.cnnt.app.CnntApplication>()
            val repository = app.repository
            val blockEntities = repository.getAllContentBlockEntities()
            val linkEntities = repository.getAllLinkEdgeEntities()
            val assetUris = blockEntities.mapNotNull { entity ->
                val json = entity.contentJson.lowercase()
                when {
                    "\"uri\"" in json -> runCatching {
                        val regex = Regex("\"uri\"\\s*:\\s*\"([^\"]+)\"")
                        regex.find(entity.contentJson)?.groupValues?.getOrNull(1)
                    }.getOrNull()
                    else -> null
                }
            }.distinct()
            val assetFiles = assetUris.mapNotNull { uriString ->
                runCatching {
                    val uri = Uri.parse(uriString)
                    if (uri.scheme == "file") File(uri.path!!) else null
                }.getOrNull()
            }
            val pngFiles = context.cacheDir
                ?.listFiles()
                ?.filter { it.extension.equals("png", ignoreCase = true) }
                .orEmpty()
            viewModel.exportManager.exportFullBackupZip(
                outputUri = uri,
                versionName = BuildConfig.VERSION_NAME,
                notebookEntities = repository.getAllNotebookEntities(),
                boardEntities = repository.getAllBoardEntities(),
                layerEntities = repository.getAllLayerEntities(),
                strokeEntities = repository.getAllStrokeEntities(),
                spatialObjectEntities = repository.getAllSpatialObjectEntities(),
                flashcardEntities = repository.getAllFlashcardEntities(),
                contentBlockEntities = blockEntities,
                linkEdgeEntities = linkEntities,
                assetFiles = assetFiles,
                cachedPngFiles = pngFiles
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Backup exportado com sucesso.", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    companion object {
        const val REQUEST_CREATE_BACKUP = 4201
    }
}
