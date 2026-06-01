package com.cnnt.app.ink

import android.content.Context
import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink

class HandwritingRecognizer(private val context: Context) {

    private var recognizer: DigitalInkRecognizer? = null
    private var modelDownloaded = false
    private var currentLanguage = "pt-BR"

    companion object {
        private const val TAG = "HandwritingRecognizer"
    }

    fun initialize(language: String = "pt-BR", onReady: (Boolean) -> Unit) {
        currentLanguage = language
        val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(language)
        if (modelIdentifier == null) {
            Log.e(TAG, "No model for language: $language, falling back to en-US")
            val fallback = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
            if (fallback == null) {
                onReady(false)
                return
            }
            setupModel(fallback, onReady)
        } else {
            setupModel(modelIdentifier, onReady)
        }
    }

    private fun setupModel(identifier: DigitalInkRecognitionModelIdentifier, onReady: (Boolean) -> Unit) {
        val model = DigitalInkRecognitionModel.builder(identifier).build()
        val remoteModelManager = RemoteModelManager.getInstance()

        // Check if already downloaded
        remoteModelManager.isModelDownloaded(model)
            .addOnSuccessListener { isDownloaded ->
                if (isDownloaded) {
                    createRecognizer(model)
                    modelDownloaded = true
                    onReady(true)
                } else {
                    // Download the model
                    val conditions = DownloadConditions.Builder().build()
                    remoteModelManager.download(model, conditions)
                        .addOnSuccessListener {
                            createRecognizer(model)
                            modelDownloaded = true
                            Log.i(TAG, "Model downloaded for $currentLanguage")
                            onReady(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Model download failed: ${e.message}", e)
                            onReady(false)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Model check failed: ${e.message}", e)
                onReady(false)
            }
    }

    private fun createRecognizer(model: DigitalInkRecognitionModel) {
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )
    }

    fun isReady(): Boolean = modelDownloaded && recognizer != null

    /**
     * Recognize handwriting from a list of strokes.
     * Each stroke is a list of (x, y, timestamp) points.
     */
    fun recognize(
        strokes: List<List<StrokePointData>>,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val rec = recognizer
        if (rec == null) {
            onError(IllegalStateException("Recognizer not initialized"))
            return
        }

        val inkBuilder = Ink.builder()
        for (strokePoints in strokes) {
            if (strokePoints.isEmpty()) continue
            val strokeBuilder = Ink.Stroke.builder()
            for (point in strokePoints) {
                strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.t))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }

        val ink = inkBuilder.build()
        rec.recognize(ink)
            .addOnSuccessListener { result ->
                val text = result.candidates.firstOrNull()?.text ?: ""
                onResult(text)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Recognition failed: ${e.message}", e)
                onError(e)
            }
    }

    fun close() {
        try {
            recognizer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Close error: ${e.message}")
        }
    }

    data class StrokePointData(val x: Float, val y: Float, val t: Long)
}
