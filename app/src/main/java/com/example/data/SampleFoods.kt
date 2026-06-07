package com.example.data

import com.example.api.MealAnalysis

data class SampleFood(
    val id: String,
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val description: String,
    val healthTips: String,
    val imageUrl: String
)

val SAMPLE_FOODS = listOf(
    SampleFood(
        id = "avocado_toast",
        name = "Avocado Toast",
        calories = 340,
        protein = 8.5,
        carbs = 32.0,
        fat = 19.5,
        description = "Toasted artisanal sourdough bread topped with creamy mashed Hass avocado, sweet cherry tomatoes, a pinch of sea salt, and red pepper flakes.",
        healthTips = "Rich in monounsaturated heart-healthy fats and dietary fiber. Pair with a poached egg to boost the protein content and keep you full longer.",
        imageUrl = "https://images.unsplash.com/photo-1541532713592-79a0317b6b77?w=500&auto=format&fit=crop&q=80"
    ),
    SampleFood(
        id = "pepperoni_pizza",
        name = "Pepperoni Pizza Slice",
        calories = 290,
        protein = 12.0,
        carbs = 30.0,
        fat = 11.5,
        description = "A slice of thin-crust Italian-style pizza baked with tangy marinara tomato sauce, low-moisture skim mozzarella, and premium pepperoni.",
        healthTips = "Provides a good source of calcium and protein, but is relatively high in sodium. Balance this slice with fresh leafy greens and lots of hydration.",
        imageUrl = "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=500&auto=format&fit=crop&q=80"
    ),
    SampleFood(
        id = "greek_salad",
        name = "Greek Feta Salad",
        calories = 180,
        protein = 5.0,
        carbs = 9.0,
        fat = 14.0,
        description = "A traditional assembly of crisp cucumbers, vine-ripened tomatoes, red onions, block Feta cheese, and Kalamata olives tossed in premium olive oil.",
        healthTips = "Full of powerful antioxidants and healthy monosaturated fats. Keeping the olive oil to one tablespoon maintains excellent moderate calorie limits.",
        imageUrl = "https://images.unsplash.com/photo-1540420773420-3366772f4999?w=500&auto=format&fit=crop&q=80"
    ),
    SampleFood(
        id = "cheeseburger",
        name = "Double Cheeseburger",
        calories = 620,
        protein = 38.0,
        carbs = 40.0,
        fat = 34.0,
        description = "Two seasoned beef patties layered with melted cheddar, crisp lettuce, red onions, ketchup, and honey mustard on a toasted brioche bun.",
        healthTips = "Extremely rich source of protein and iron. Consider enjoying it open-faced or swapping fries for a side salad to cut down extra refined carbs.",
        imageUrl = "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=500&auto=format&fit=crop&q=80"
    ),
    SampleFood(
        id = "bluepancakes",
        name = "Blueberry Pancakes",
        calories = 450,
        protein = 9.0,
        carbs = 78.0,
        fat = 10.0,
        description = "Three golden buttermilk pancakes loaded with wild blueberries and drizzled with amber maple syrup alongside structured butter dollops.",
        healthTips = "Carbo-loaded energy booster, ideal for active pre-workout mornings. You can substitute heavy maple syrup with berry compote to limit glycemic spikes.",
        imageUrl = "https://images.unsplash.com/photo-1528207776546-365bb710ee93?w=500&auto=format&fit=crop&q=80"
    )
)

fun getMockMealAnalysis(guessedName: String? = null): MealAnalysis {
    val cleanGuess = (guessedName ?: "").trim().lowercase()
    
    val matched = SAMPLE_FOODS.firstOrNull { 
        it.name.lowercase().contains(cleanGuess) || cleanGuess.contains(it.name.lowercase()) 
    }
    
    if (matched != null) {
        return MealAnalysis(
            foodName = matched.name,
            calories = matched.calories,
            protein = matched.protein,
            carbs = matched.carbs,
            fat = matched.fat,
            description = matched.description,
            healthTips = matched.healthTips
        )
    }
    
    val options = listOf(
        MealAnalysis(
            foodName = if (guessedName.isNullOrBlank()) "Healthy Garden Salad" else guessedName,
            calories = 160,
            protein = 5.0,
            carbs = 14.0,
            fat = 9.0,
            description = "Estimated portion of organic leafy greens, diced cucumber, tomatoes, and light olive oil vinaigrette dressing.",
            healthTips = "Phenomenal choice! Packed with high vitamins, active fiber and water weight that sustains high energy levels."
        ),
        MealAnalysis(
            foodName = if (guessedName.isNullOrBlank()) "Nutritious Protein Plate" else guessedName,
            calories = 380,
            protein = 24.0,
            carbs = 28.0,
            fat = 15.0,
            description = "Clean protein portions containing lean meat, low-carb fiber vegetables, and smart whole grain carbohydrate compounds.",
            healthTips = "Very balanced! The protein content will help build musculature and keep your metabolism active throughout the day."
        )
    )
    return options.random()
}
