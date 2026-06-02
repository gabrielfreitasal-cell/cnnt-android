package com.cnnt.app.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import com.cnnt.app.R
import com.cnnt.app.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExportDialog(
    context: Context,
    private val viewModel: MainViewModel
) : Dialog(context, R.style.Theme_CNNT_Dialog) {

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
            exportBackup(exportDir)
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

    private fun exportBackup(dir: File) {
        val workspace = viewModel.buildWorkspaceSnapshot()
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(dir, "cnnt_backup_${System.currentTimeMillis()}.zip")
            viewModel.exportManager.exportBackup(workspace, file)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Backup: ${file.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }
}
