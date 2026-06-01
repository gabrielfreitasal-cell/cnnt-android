package com.cnnt.app.data

import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * Emergency persistence hook — invoked from uncaught exception handler before process death.
 */
object NotebookSnapshot {
    private val saveAction = AtomicReference<(() -> Unit)?>(null)

    fun register(action: () -> Unit) {
        saveAction.set(action)
    }

    fun unregister() {
        saveAction.set(null)
    }

    fun emergencySave() {
        try {
            saveAction.get()?.invoke()
        } catch (e: Exception) {
            Log.e("CNNT", "Emergency save failed: ${e.message}", e)
        }
    }
}
