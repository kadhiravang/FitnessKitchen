package com.kadhiravan.foodtracker.data.remote

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class OnDeviceTranscription(
    val text: String,
    val audioSeconds: Float,
    val loadMs: Long,
    val decodeMs: Long
)

/**
 * Runs a Whisper model on this phone through sherpa-onnx, no server or network. Model files
 * (encoder, decoder, tokens.txt from a sherpa-onnx Whisper package) live in
 * `<external files>/whisper/<model>/`. The recognizer is kept loaded between calls since
 * loading a large model takes seconds; it is rebuilt only if the model, language or thread
 * count changes.
 */
class OnDeviceWhisper(private val context: Context) {

    private val lock = Any()
    private var recognizer: OfflineRecognizer? = null
    private var loadedKey: String? = null

    private class ModelFiles(val encoder: File, val decoder: File, val tokens: File)

    fun modelDir(model: String): File = File(context.getExternalFilesDir(null), "whisper/$model")

    fun samplesDir(): File = File(context.getExternalFilesDir(null), "whisper-samples").apply { mkdirs() }

    /** Recordings are kept for benchmarking only when a developer has created this folder,
     * so normal use never stores anyone's voice. */
    fun samplesDirIfPresent(): File? =
        File(context.getExternalFilesDir(null), "whisper-samples").takeIf { it.isDirectory }

    fun isModelAvailable(model: String): Boolean = findFiles(model) != null

    private fun findFiles(model: String): ModelFiles? {
        val files = modelDir(model).listFiles().orEmpty()
        // The int8 export is several times smaller and what we want on a phone.
        val encoder = files.firstOrNull { it.name.endsWith("encoder.int8.onnx") }
            ?: files.firstOrNull { it.name.endsWith("encoder.onnx") }
        val decoder = files.firstOrNull { it.name.endsWith("decoder.int8.onnx") }
            ?: files.firstOrNull { it.name.endsWith("decoder.onnx") }
        val tokens = files.firstOrNull { it.name.endsWith("tokens.txt") }
        return if (encoder != null && decoder != null && tokens != null) ModelFiles(encoder, decoder, tokens) else null
    }

    /** Loads the model now (off the main thread) so the first real transcription is fast. */
    suspend fun warmUp(model: String, language: String = "", threads: Int = DEFAULT_THREADS) {
        withContext(Dispatchers.Default) {
            runCatching { synchronized(lock) { ensureLoaded(model, language, threads) } }
                .onFailure { Log.w(TAG, "warmUp failed", it) }
        }
    }

    /** [language] is a Whisper language code ("ta", "en") or "" to auto-detect. */
    suspend fun transcribe(
        wav: ByteArray,
        model: String,
        language: String = "",
        threads: Int = DEFAULT_THREADS
    ): OnDeviceTranscription = withContext(Dispatchers.Default) {
        val samples = wavToFloatSamples(wav)
        synchronized(lock) {
            val loadStart = System.currentTimeMillis()
            val rec = ensureLoaded(model, language, threads)
            val loadMs = System.currentTimeMillis() - loadStart

            val decodeStart = System.currentTimeMillis()
            // Whisper handles at most 30 s per pass, so longer recordings are split.
            val chunks = (samples.indices step CHUNK_SAMPLES).map { start ->
                samples.copyOfRange(start, minOf(start + CHUNK_SAMPLES, samples.size))
            }
            val text = chunks.joinToString(" ") { chunk ->
                val stream = rec.createStream()
                try {
                    stream.acceptWaveform(chunk, SAMPLE_RATE)
                    rec.decode(stream)
                    rec.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }
            }.trim()
            OnDeviceTranscription(
                text = text,
                audioSeconds = samples.size / SAMPLE_RATE.toFloat(),
                loadMs = loadMs,
                decodeMs = System.currentTimeMillis() - decodeStart
            )
        }
    }

    private fun ensureLoaded(model: String, language: String, threads: Int): OfflineRecognizer {
        val key = "$model|$language|$threads"
        recognizer?.let { if (loadedKey == key) return it }
        recognizer?.release()
        recognizer = null

        val files = findFiles(model) ?: error("Whisper model '$model' not found in ${modelDir(model)}")
        val whisper = OfflineWhisperModelConfig().apply {
            encoder = files.encoder.absolutePath
            decoder = files.decoder.absolutePath
            this.language = language
            task = "transcribe"
        }
        val modelConfig = OfflineModelConfig().apply {
            this.whisper = whisper
            tokens = files.tokens.absolutePath
            numThreads = threads
            modelType = "whisper"
        }
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = modelConfig
        )
        return OfflineRecognizer(null, config).also {
            recognizer = it
            loadedKey = key
        }
    }

    /** Our own recorder writes a fixed 44-byte header followed by 16 kHz mono PCM16. */
    private fun wavToFloatSamples(wav: ByteArray): FloatArray {
        val offset = if (wav.size > 44 && String(wav, 0, 4, Charsets.US_ASCII) == "RIFF") 44 else 0
        val count = (wav.size - offset) / 2
        return FloatArray(count) { i ->
            val lo = wav[offset + 2 * i].toInt() and 0xFF
            val hi = wav[offset + 2 * i + 1].toInt()
            ((hi shl 8) or lo).toShort() / 32768f
        }
    }

    companion object {
        private const val TAG = "OnDeviceWhisper"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SAMPLES = 28 * SAMPLE_RATE
        const val DEFAULT_THREADS = 4
    }
}
