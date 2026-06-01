package com.cnnt.app.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(
    val fullText: String,
    val blocks: List<OcrBlock>
)

data class OcrBlock(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float,
    val lines: List<OcrLine>
)

data class OcrLine(
    val text: String,
    val boundingBox: Rect?,
    val words: List<OcrWord>
)

data class OcrWord(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float
)

class OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val blocks = result.textBlocks.map { block ->
                    OcrBlock(
                        text = block.text,
                        boundingBox = block.boundingBox,
                        confidence = block.lines.firstOrNull()?.confidence ?: 0f,
                        lines = block.lines.map { line ->
                            OcrLine(
                                text = line.text,
                                boundingBox = line.boundingBox,
                                words = line.elements.map { element ->
                                    OcrWord(
                                        text = element.text,
                                        boundingBox = element.boundingBox,
                                        confidence = element.confidence ?: 0f
                                    )
                                }
                            )
                        }
                    )
                }
                cont.resume(OcrResult(fullText = result.text, blocks = blocks))
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    suspend fun recognizeRegion(bitmap: Bitmap, region: Rect): OcrResult {
        val croppedBitmap = Bitmap.createBitmap(
            bitmap, region.left, region.top, region.width(), region.height()
        )
        return recognizeText(croppedBitmap)
    }

    fun close() {
        recognizer.close()
    }
}
