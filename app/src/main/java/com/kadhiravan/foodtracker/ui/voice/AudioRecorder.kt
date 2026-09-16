package com.kadhiravan.foodtracker.ui.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * Captures raw mono 16kHz PCM16 audio (the format Whisper expects) straight from the
 * mic, no silence-based segmentation, no restarts, purely "record until [stopAndGetWav]
 * is called." Used for the local-Whisper-server path, where a complete recording is
 * uploaded once rather than streamed segment-by-segment like [SpeechRecognizerController].
 */
class AudioRecorder(private val onAmplitude: (Float) -> Unit) {

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val pcmBuffer = ByteArrayOutputStream()

    @Volatile
    private var isRecording = false

    fun start() {
        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = if (minBufSize > 0) minBufSize * 2 else SAMPLE_RATE
        pcmBuffer.reset()

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )
        audioRecord = record
        isRecording = true
        record.startRecording()

        recordingThread = Thread {
            val chunk = ShortArray(bufSize / 2)
            while (isRecording) {
                val read = record.read(chunk, 0, chunk.size)
                if (read > 0) {
                    var sumSquares = 0.0
                    val bytes = ByteArray(read * 2)
                    for (i in 0 until read) {
                        val sample = chunk[i]
                        sumSquares += (sample.toInt() * sample.toInt()).toDouble()
                        bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                    }
                    synchronized(pcmBuffer) { pcmBuffer.write(bytes) }
                    val rms = sqrt(sumSquares / read)
                    onAmplitude((rms / 6000.0).toFloat().coerceIn(0f, 1f))
                }
            }
        }.also { it.start() }
    }

    /** Stops recording and returns a complete WAV file (16-bit PCM, mono, 16kHz). */
    fun stopAndGetWav(): ByteArray {
        val pcm = stopInternal()
        return buildWavFile(pcm)
    }

    /** Stops recording and discards whatever was captured (e.g. the user cancelled). */
    fun cancel() {
        stopInternal()
    }

    private fun stopInternal(): ByteArray {
        isRecording = false
        try {
            recordingThread?.join(500)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        return synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
    }

    private fun buildWavFile(pcmData: ByteArray): ByteArray {
        val header = ByteArray(44)
        val byteRate = SAMPLE_RATE * 2 // mono, 16-bit

        fun writeString(offset: Int, s: String) {
            for (i in s.indices) header[offset + i] = s[i].code.toByte()
        }
        fun writeIntLE(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
            header[offset + 2] = ((value shr 16) and 0xff).toByte()
            header[offset + 3] = ((value shr 24) and 0xff).toByte()
        }
        fun writeShortLE(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
        }

        writeString(0, "RIFF")
        writeIntLE(4, pcmData.size + 36)
        writeString(8, "WAVE")
        writeString(12, "fmt ")
        writeIntLE(16, 16) // PCM subchunk size
        writeShortLE(20, 1) // AudioFormat = PCM
        writeShortLE(22, 1) // NumChannels = mono
        writeIntLE(24, SAMPLE_RATE)
        writeIntLE(28, byteRate)
        writeShortLE(32, 2) // BlockAlign
        writeShortLE(34, 16) // BitsPerSample
        writeString(36, "data")
        writeIntLE(40, pcmData.size)

        return header + pcmData
    }

    companion object {
        private const val SAMPLE_RATE = 16000
    }
}
