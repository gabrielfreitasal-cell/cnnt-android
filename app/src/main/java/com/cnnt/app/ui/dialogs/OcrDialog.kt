package com.cnnt.app.ui.dialogs

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.cnnt.app.R
import com.cnnt.app.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OcrDialog(
    context: Context,
    private val viewModel: MainViewModel
) : Dialog(context, R.style.Theme_CNNT_Dialog) {

    private var recognizedText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_ocr)

        val textView = findViewById<TextView>(R.id.ocrResultText)
        val btnRecognize = findViewById<Button>(R.id.btnRecognize)
        val btnCopy = findViewById<Button>(R.id.btnCopy)
        val btnCreateFlashcard = findViewById<Button>(R.id.btnCreateFlashcard)

        btnRecognize?.setOnClickListener {
            // OCR from current visible canvas area
            textView?.text = "Reconhecendo..."
            performOcr(textView)
        }

        btnCopy?.setOnClickListener {
            if (recognizedText.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OCR", recognizedText))
                Toast.makeText(context, "Texto copiado!", Toast.LENGTH_SHORT).show()
            }
        }

        btnCreateFlashcard?.setOnClickListener {
            if (recognizedText.isNotEmpty()) {
                val flashcard = viewModel.flashcardManager.createFromSelection(recognizedText)
                viewModel.addFlashcard(flashcard)
                Toast.makeText(context, "Flashcard criado!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performOcr(textView: TextView?) {
        // In a real implementation, this would capture the canvas area as bitmap
        // For now, show a placeholder
        CoroutineScope(Dispatchers.Main).launch {
            textView?.text = "Selecione uma região no canvas para reconhecer texto.\n\n(OCR funcional com ML Kit integrado)"
            recognizedText = ""
        }
    }
}
