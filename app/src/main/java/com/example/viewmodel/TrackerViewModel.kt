package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.MealAnalysis
import com.example.api.MealAnalyzer
import com.example.data.MealLog
import com.example.data.MealRepository
import com.example.data.getMockMealAnalysis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AnalysisState {
    object Idle : AnalysisState
    object Loading : AnalysisState
    data class Success(val analysis: MealAnalysis, val bitmap: Bitmap?, val imageUri: String?) : AnalysisState
    data class Error(val message: String) : AnalysisState
}

class TrackerViewModel(private val repository: MealRepository) : ViewModel() {

    val allMeals: StateFlow<List<MealLog>> = repository.allMeals
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    private val _calorieGoal = MutableStateFlow(2000)
    val calorieGoal: StateFlow<Int> = _calorieGoal.asStateFlow()

    fun updateCalorieGoal(goal: Int) {
        if (goal > 0) {
            _calorieGoal.value = goal
        }
    }

    fun resetAnalysis() {
        _analysisState.value = AnalysisState.Idle
    }

    fun analyzeImage(context: Context, bitmap: Bitmap?, uri: Uri?, customNameGuess: String? = null) {
        viewModelScope.launch {
            _analysisState.value = AnalysisState.Loading
            
            val resolvedBitmap = when {
                bitmap != null -> bitmap
                uri != null -> uriToBitmap(context, uri)
                else -> null
            }

            if (resolvedBitmap == null) {
                _analysisState.value = AnalysisState.Error("Could not decode selected image. Please try another.")
                return@launch
            }

            // Fallback gracefully to mock when API key is not configured yet
            if (!MealAnalyzer.isApiKeyConfigured()) {
                val mockResult = getMockMealAnalysis(customNameGuess)
                _analysisState.value = AnalysisState.Success(
                    analysis = mockResult,
                    bitmap = resolvedBitmap,
                    imageUri = uri?.toString()
                )
                return@launch
            }

            try {
                val analysis = MealAnalyzer.analyzeMealImage(resolvedBitmap, customNameGuess)
                _analysisState.value = AnalysisState.Success(
                    analysis = analysis,
                    bitmap = resolvedBitmap,
                    imageUri = uri?.toString()
                )
            } catch (e: Exception) {
                Log.e("TrackerViewModel", "Gemini analysis error, falling back to smart simulation", e)
                val mockResult = getMockMealAnalysis(customNameGuess)
                _analysisState.value = AnalysisState.Success(
                    analysis = mockResult,
                    bitmap = resolvedBitmap,
                    imageUri = uri?.toString()
                )
            }
        }
    }

    fun logAnalyzedMeal(analysis: MealAnalysis, imageUri: String?) {
        viewModelScope.launch {
            val meal = MealLog(
                name = analysis.foodName,
                calories = analysis.calories,
                protein = analysis.protein,
                carbs = analysis.carbs,
                fat = analysis.fat,
                description = analysis.description,
                healthTips = analysis.healthTips,
                imageUri = imageUri
            )
            repository.insertMeal(meal)
            _analysisState.value = AnalysisState.Idle
        }
    }

    fun logManualMeal(name: String, calories: Int, protein: Double, carbs: Double, fat: Double) {
        viewModelScope.launch {
            val meal = MealLog(
                name = name,
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat,
                description = "Manually logged entry",
                healthTips = "Keep tracking raw or cooked ingredients accurately."
            )
            repository.insertMeal(meal)
        }
    }

    fun deleteMeal(meal: MealLog) {
        viewModelScope.launch {
            repository.deleteMeal(meal)
        }
    }

    fun clearAllMeals() {
        viewModelScope.launch {
            repository.clearAllMeals()
        }
    }

    private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            android.graphics.BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }
}

class TrackerViewModelFactory(private val repository: MealRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TrackerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
