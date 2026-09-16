package com.kadhiravan.foodtracker.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
private data class CloudTranscribeResponse(val text: String)

/**
 * Talks to OpenAI's hosted Whisper transcription endpoint — the alternative to
 * [LocalWhisperClient] for users who don't want to run whisper-server/ on their own
 * machine. Needs an OpenAI API key with billing set up (Whisper isn't on the free tier).
 */
class CloudWhisperClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(apiKey: String, wavBytes: ByteArray, language: String? = null): String = withContext(Dispatchers.IO) {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("response_format", "json")
            .addFormDataPart("file", "audio.wav", wavBytes.toRequestBody("audio/wav".toMediaType()))

        // The API expects a bare ISO-639-1 code (e.g. "en"), not our stored BCP-47 tag
        // (e.g. "en-IN") — trim off the region subtag rather than sending it as-is.
        val isoLanguage = language?.substringBefore('-')?.takeIf { it.isNotBlank() }
        if (isoLanguage != null) bodyBuilder.addFormDataPart("language", isoLanguage)

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(bodyBuilder.build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Cloud Whisper API error ${response.code}: $bodyText")
            }
            json.decodeFromString<CloudTranscribeResponse>(bodyText).text
        }
    }
}
