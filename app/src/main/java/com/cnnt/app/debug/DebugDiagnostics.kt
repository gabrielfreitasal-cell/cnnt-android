package com.cnnt.app.debug

import android.app.ActivityManager
import android.content.Context
import android.widget.TextView
import com.cnnt.app.canvas.InfiniteCanvasView
import com.cnnt.app.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** On-screen stats for beta testing (enable with 5 taps on workspace title). */
object DebugDiagnostics {

    private var enabled = false
    private var lastSaveLabel = "—"

    fun isEnabled(): Boolean = enabled

    fun toggle(): Boolean {
        enabled = !enabled
        return enabled
    }

    fun onSaveCompleted() {
        lastSaveLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun updateOverlay(
        context: Context,
        view: TextView,
        viewModel: MainViewModel,
        canvas: InfiniteCanvasView
    ) {
        if (!enabled) {
            view.visibility = android.view.View.GONE
            return
        }
        view.visibility = android.view.View.VISIBLE
        val mem = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .getMemoryInfo(it)
        }
        val usedMb = (mem.totalMem - mem.availMem) / (1024 * 1024)
        val notebook = viewModel.currentNotebook.value
        val board = viewModel.currentBoard.value
        val strokes = board?.activeLayer?.strokes?.size ?: 0
        view.text = buildString {
            appendLine("DEBUG CNNT")
            appendLine("zoom: ${"%.0f".format(canvas.getZoomPercent())}%")
            appendLine("traços: $strokes | cache: ${canvas.debugCacheInfo()}")
            appendLine("páginas: ${notebook?.boards?.size ?: 0}")
            appendLine("RAM ~${usedMb}MB | low=${mem.lowMemory}")
            appendLine("último save: $lastSaveLabel")
        }
    }
}
