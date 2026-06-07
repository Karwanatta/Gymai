package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object MealAnalyzer {
    private const val TAG = "MealAnalyzer"

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && 
               key != "MY_GEMINI_API_KEY" && 
               !key.contains("PLACEHOLDER")
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Resize bitmap if too large to optimize payload size and response latency
        val maxDimension = 800
        val resized = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val (newWidth, newHeight) = if (ratio > 1f) {
                Pair(maxDimension, (maxDimension / ratio).toInt())
            } else {
                Pair((maxDimension * ratio).toInt(), maxDimension)
            }
            Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
        } else {
            this
        }
        resized.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun analyzeMealImage(bitmap: Bitmap, customPrompt: String? = null): MealAnalysis = withContext(Dispatchers.IO) {
        if (!isApiKeyConfigured()) {
            throw IllegalStateException("API_KEY_MISSING")
        }

        val base64Image = bitmap.toBase64()
        val defaultPrompt = """
            Analyze the provided food image and return a JSON object with precisely these keys:
            - foodName: (string) The name of the food or meal.
            - calories: (integer kcal) total estimated calories.
            - protein: (number of grams) estimated protein.
            - carbs: (number of grams) estimated carbohydrates.
            - fat: (number of grams) estimated dietary fat.
            - description: (string) brief overview of estimated portion sizes, weight, ingredients seen in photo.
            - healthTips: (string) a highly actionable dietary feedback or nutrition suggestion.
            
            Do not output any markdown block, prefix or suffix. Return raw JSON text representing the parsed object.
        """.trimIndent()

        val prompt = customPrompt ?: defaultPrompt

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.4
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("No prediction returned from the model.")

            Log.d(TAG, "Raw Response: $jsonText")
            val cleanJson = extractJson(jsonText)
            
            val adapter: JsonAdapter<MealAnalysis> = RetrofitClient.moshiInstance.adapter(MealAnalysis::class.java)
            adapter.fromJson(cleanJson) ?: throw Exception("Failed to decode JSON to MealAnalysis")
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Analysis Failure", e)
            throw e
        }
    }

    private fun extractJson(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        return clean.trim()
    }
}
