package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini REST API Request & Response structures (Moshi-compatible) ---

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

// --- Retrofit API Service Interface ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun examineContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// --- Retrofit Singleton ---

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }

    /**
     * Executes content analysis using the Gemini 3.5 Flash Model
     * @return Pair<IsSafe, ReasonString>
     */
    suspend fun analyzeScreenContent(contextDescription: String, userTextSnippet: String): Pair<Boolean, String> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return Pair(false, "API Key is missing. Please add your GEMINI_API_KEY in the Secrets Panel.")
        }

        val prompt = """
            You are "kidsShiield" content scanner. Evaluate this social media post / screen content for a child under 10.
            Screen Node Info: $contextDescription
            Active Text Read: $userTextSnippet

            Determine if it is safe. We do NOT allow adult content, explicit references, gore, gambling, cursing, or online dating triggers.
            Provide your answer EXACTLY as a brief response where the first line is "SAFE_STATUS: TRUE" or "SAFE_STATUS: FALSE".
            On the next output line, provide a short 1-sentence explanation of what was found or why it is clean. This output format is critical!
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )

        try {
            val response = service.examineContent(apiKey, request)
            val outputText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (outputText != null) {
                val isSafe = outputText.contains("SAFE_STATUS: TRUE", ignoreCase = true)
                val lines = outputText.lines()
                val reason = lines.firstOrNull { !it.contains("SAFE_STATUS:") && it.trim().isNotEmpty() }
                    ?: "Analysis processed."
                return Pair(isSafe, reason.trim())
            }
            return Pair(true, "Scanner analyzed the screen with no active violations flagged.")
        } catch (e: Exception) {
            return Pair(false, "API scan failed: ${e.localizedMessage ?: e.message}")
        }
    }
}
