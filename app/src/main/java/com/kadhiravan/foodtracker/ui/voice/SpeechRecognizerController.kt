package com.kadhiravan.foodtracker.ui.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.kadhiravan.foodtracker.data.prefs.SpeechLanguage

/**
 * Thin wrapper around [SpeechRecognizer] that drives an in-app mic UI (rather than
 * launching the system dialog) and keeps listening across natural pauses in speech —
 * like a real conversational voice input — until [stopListening] is called explicitly.
 *
 * Android's recognizer treats any brief silence as "done" and ends the session on its
 * own; left alone that cuts the user off mid-sentence. We ask for generous silence
 * thresholds AND transparently restart listening whenever the recognizer ends a segment
 * on its own, accumulating each segment's text so the pause is invisible to the user.
 * Each restart re-triggers Android's audible "start listening" tone — trying to time a
 * mute/unmute around each individual restart was fragile (the beep's exact timing and
 * audio stream vary by device), so instead we mute for the entire session, start to
 * finish, and only unmute once the user actually stops.
 */
class SpeechRecognizerController(
    context: Context,
    private val onTranscriptUpdate: (String) -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStopped: (finalText: String) -> Unit
) {
    private var languageTag: String = "en-IN"
    private var recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    // The recognizer's "start/end listening" tone shows up on different streams
    // depending on device/OEM — mute all the plausible candidates to be safe.
    private val mutedStreams = intArrayOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM
    )

    val isAvailable: Boolean get() = recognizer != null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val finalizedSegments = mutableListOf<String>()
    private var currentPartial = ""
    private var manuallyStopped = true

    private val maxSessionRunnable = Runnable { stopListening() }

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = onAmplitudeChanged((rmsdB / 10f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) finalizedSegments.add(text)
                currentPartial = ""

                if (manuallyStopped) {
                    finish()
                } else {
                    onTranscriptUpdate(fullTranscript())
                    beginListeningSegment()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) {
                    currentPartial = text
                    onTranscriptUpdate(fullTranscript())
                }
            }

            override fun onError(error: Int) {
                if (manuallyStopped) {
                    finish()
                    return
                }
                when (error) {
                    // Benign mid-conversation hiccups — a brief pause or the recognizer
                    // needing a beat to restart. Keep the session going transparently.
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_CLIENT -> mainHandler.postDelayed({ beginListeningSegment() }, 250)
                    else -> {
                        onError(errorMessage(error))
                        finish()
                    }
                }
            }
        })
    }

    fun startListening(languageTag: String) {
        this.languageTag = languageTag
        manuallyStopped = false
        finalizedSegments.clear()
        currentPartial = ""
        mainHandler.removeCallbacks(maxSessionRunnable)
        mainHandler.postDelayed(maxSessionRunnable, MAX_SESSION_MILLIS)
        // Muted for the whole session (every internal restart included) rather than
        // toggled per-restart — see the class doc for why.
        setSystemSoundsMuted(true)
        beginListeningSegment()
    }

    fun stopListening() {
        manuallyStopped = true
        mainHandler.removeCallbacks(maxSessionRunnable)
        onAmplitudeChanged(0f)
        recognizer?.stopListening()
    }

    fun destroy() {
        manuallyStopped = true
        mainHandler.removeCallbacks(maxSessionRunnable)
        setSystemSoundsMuted(false)
        recognizer?.destroy()
        recognizer = null
    }

    private fun beginListeningSegment() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Generous pause tolerance so a natural breath/pause mid-sentence doesn't
            // read as "done" — and even if it does, onResults() restarts us anyway.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
            // On-device auto language switching (Android 14+): lets a single utterance mix
            // languages — e.g. an English sentence with a Tamil dish name — without the user
            // having to pick one language up front. Silently no-ops on older OS versions.
            if (Build.VERSION.SDK_INT >= 34) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                    ArrayList((SpeechLanguage.SWITCH_LANGUAGES + languageTag).distinct())
                )
            }
        }
        recognizer?.startListening(intent)
    }

    private fun setSystemSoundsMuted(muted: Boolean) {
        val direction = if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        for (stream in mutedStreams) {
            try {
                audioManager?.adjustStreamVolume(stream, direction, 0)
            } catch (e: SecurityException) {
                // Some OEMs restrict muting certain streams; that one just beeps as normal.
            }
        }
    }

    private fun finish() {
        mainHandler.removeCallbacks(maxSessionRunnable)
        setSystemSoundsMuted(false)
        onListeningStopped(fullTranscript())
    }

    private fun fullTranscript(): String =
        (finalizedSegments + listOfNotNull(currentPartial.takeIf { it.isNotBlank() })).joinToString(" ").trim()

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy — try again."
        else -> "Speech recognition error ($error)."
    }

    companion object {
        private const val MAX_SESSION_MILLIS = 120_000L
    }
}
