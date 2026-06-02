package com.cnnt.app.ui.dialogs

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
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
    private val viewModel: MainViewModel,
    private val bitmapProvider: () -> Bitmap?,
    private val requestRegionSelection: () -> Unit,
    private val regionBitmapProvider: (Rect) -> Bitmap?,
    private val consumeSelectedRegion: () -> Rect?
) : Dialog(context, R.style.Theme_CNNT_Dialog) {

    private var recognizedText = ""
    private enum class OcrMode { REGION, SCREEN }
    private var selectedMode = OcrMode.REGION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_ocr)

        val textView = findViewById<TextView>(R.id.ocrResultText)
        val btnRecognize = findViewById<Button>(R.id.btnRecognize)
        val btnRecognizeVisible = findViewById<Button>(R.id.btnRecognizeVisible)
        val btnCopy = findViewById<Button>(R.id.btnCopy)
        val btnCreateFlashcard = findViewById<Button>(R.id.btnCreateFlashcard)

        btnRecognize?.setOnClickListener {
            selectedMode = OcrMode.REGION
            textView?.text = "Desenhe a região no canvas e toque novamente em OCR por região."
            requestRegionSelection()
        }

        btnRecognizeVisible?.setOnClickListener {
            selectedMode = OcrMode.SCREEN
            textView?.text = "Reconhecendo tela visível..."
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
        CoroutineScope(Dispatchers.Main).launch {
            val bitmap = when (selectedMode) {
                OcrMode.SCREEN -> bitmapProvider()
                OcrMode.REGION -> {
                    val region = consumeSelectedRegion()
                    if (region == null) {
                        textView?.text = "Selecione uma região no canvas primeiro."
                        return@launch
                    }
                    regionBitmapProvider(region)
                }
            }
            if (bitmap == null) {
                textView?.text = "Abra uma página e tente novamente."
                recognizedText = ""
                return@launch
            }

            try {
                val result = viewModel.ocrEngine.recognizeText(bitmap)
                recognizedText = result.fullText.trim()
                textView?.text = if (recognizedText.isNotEmpty()) {
                    recognizedText
                } else {
                    "Nenhum texto reconhecido na área visível."
                }
            } catch (e: Exception) {
                recognizedText = ""
                textView?.text = "Falha ao reconhecer texto."
                Toast.makeText(context, "OCR falhou: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                bitmap.recycle()
            }
        }
    }
}
