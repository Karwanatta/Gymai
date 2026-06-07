package com.example.data

import kotlinx.coroutines.flow.Flow

class MealRepository(private val mealDao: MealDao) {
    val allMeals: Flow<List<MealLog>> = mealDao.getAllMeals()

    suspend fun insertMeal(meal: MealLog): Long {
        return mealDao.insertMeal(meal)
    }

    suspend fun deleteMeal(meal: MealLog) {
        mealDao.deleteMeal(meal)
    }

    suspend fun clearAllMeals() {
        mealDao.clearAllMeals()
    }
}
