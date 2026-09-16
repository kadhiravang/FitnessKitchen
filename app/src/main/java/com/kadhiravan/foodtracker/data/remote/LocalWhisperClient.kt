package com.kadhiravan.foodtracker.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
private data class TranscribeResponse(val text: String)

/**
 * Talks to the local Whisper transcription server run on the user's own laptop (see
 * whisper-server/ setup) — noticeably higher accuracy than Android's built-in
 * recognizer, but only reachable on the same Wi-Fi network with the server running.
 * [isReachable] lets the caller decide once per session whether to use this path or
 * fall back to the on-device recognizer, rather than failing mid-recording.
 */
class LocalWhisperClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(800, TimeUnit.MILLISECONDS)
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .build()

    private val transcribeClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun isReachable(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (serverUrl.isBlank()) {
            Log.d(TAG, "isReachable: serverUrl is blank, skipping")
            return@withContext false
        }
        try {
            val request = Request.Builder().url("${serverUrl.trimEnd('/')}/health").get().build()
            probeClient.newCall(request).execute().use {
                Log.d(TAG, "isReachable: $serverUrl -> HTTP ${it.code}")
                it.isSuccessful
            }
        } catch (e: IOException) {
            Log.d(TAG, "isReachable: $serverUrl -> ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    private companion object {
        const val TAG = "LocalWhisperClient"
    }

    suspend fun transcribe(serverUrl: String, wavBytes: ByteArray, language: String? = null): String = withContext(Dispatchers.IO) {
        val body = wavBytes.toRequestBody("audio/wav".toMediaType())
        val url = "${serverUrl.trimEnd('/')}/transcribe".let {
            if (language.isNullOrBlank()) it else "$it?language=$language"
        }
        val request = Request.Builder().url(url).post(body).build()
        transcribeClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Local Whisper server error ${response.code}")
            }
            json.decodeFromString<TranscribeResponse>(bodyText).text
        }
    }
}
