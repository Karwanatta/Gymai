package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.api.MealAnalysis
import com.example.api.MealAnalyzer
import com.example.data.MealLog
import com.example.data.SAMPLE_FOODS
import com.example.data.SampleFood
import com.example.ui.theme.*
import com.example.viewmodel.AnalysisState
import com.example.viewmodel.TrackerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val meals by viewModel.allMeals.collectAsState()
    val analysisState by viewModel.analysisState.collectAsState()
    val calorieGoal by viewModel.calorieGoal.collectAsState()

    var showManualDialog by remember { mutableStateOf(false) }
    var showGoalSettings by remember { mutableStateOf(false) }
    var selectedMealForDetails by remember { mutableStateOf<MealLog?>(null) }

    // Media picking launchers
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.analyzeImage(context, bitmap, null, null)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.analyzeImage(context, null, uri, null)
        }
    }

    // Calculations
    val totalCalories = meals.sumOf { it.calories }
    val totalProtein = meals.sumOf { it.protein }
    val totalCarbs = meals.sumOf { it.carbs }
    val totalFat = meals.sumOf { it.fat }

    // Macros limits targets (generic ratio)
    val proteinGoal = (calorieGoal * 0.3) / 4.0
    val carbsGoal = (calorieGoal * 0.45) / 4.0
    val fatGoal = (calorieGoal * 0.25) / 9.0

    Scaffold(
        topBar = {
            SleekHeader(
                onSettingsClick = { showGoalSettings = true },
                onResetClick = { viewModel.clearAllMeals() }
            )
        },
        bottomBar = {
            SleekBottomNavBar()
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { cameraLauncher.launch(null) },
                    containerColor = SleekPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("camera_fab"),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = "Capture Food Picture"
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // API Status banner notice
            if (!MealAnalyzer.isApiKeyConfigured()) {
                item {
                    ApiKeyWarningBanner()
                }
            }

            // Target Rings and Goals dashboard
            item {
                DashboardCard(
                    consumed = totalCalories,
                    goal = calorieGoal,
                    protein = totalProtein,
                    proteinGoal = proteinGoal,
                    carbs = totalCarbs,
                    carbsGoal = carbsGoal,
                    fat = totalFat,
                    fatGoal = fatGoal
                )
            }

            // Beautiful Aspect-ratio sleek scan banner
            item {
                ScanYourMealBanner(
                    onClick = { cameraLauncher.launch(null) }
                )
            }

            // Select & Search Option
            item {
                SectionHeader(title = "Instant Food Simulation", subtitle = "Tap to test with beautiful sample foods")
                Spacer(modifier = Modifier.height(8.dp))
                SampleFoodsRow { sampleFood ->
                    // Set simulation guess
                    viewModel.analyzeImage(context, null, null, sampleFood.name)
                }
            }

            // Input Buttons Row
            item {
                ButtonsRow(
                    onGalleryClick = { galleryLauncher.launch("image/*") },
                    onManualClick = { showManualDialog = true }
                )
            }

            // Meal Log section
            item {
                SectionHeader(
                    title = "Daily Meal Logs",
                    subtitle = "${meals.size} entries saved today"
                )
            }

            if (meals.isEmpty()) {
                item {
                    EmptyLogCard(
                        onQuickAdd = {
                            viewModel.logManualMeal("Apple with Almond Butter", 220, 4.0, 25.0, 12.0)
                        }
                    )
                }
            } else {
                items(meals, key = { it.id }) { meal ->
                    MealLogItem(
                        meal = meal,
                        onDelete = { viewModel.deleteMeal(meal) },
                        onTap = { selectedMealForDetails = meal }
                    )
                }
            }
        }

        // Active Analysis Loading / Success Dialog overlay
        if (analysisState != AnalysisState.Idle) {
            AnalysisOverlayDialog(
                state = analysisState,
                onDismiss = { viewModel.resetAnalysis() },
                onLog = { result, imageUri ->
                    viewModel.logAnalyzedMeal(result, imageUri)
                }
            )
        }

        // Edit calorie target dialog
        if (showGoalSettings) {
            GoalSettingsDialog(
                currentGoal = calorieGoal,
                onDismiss = { showGoalSettings = false },
                onConfirm = { newGoal ->
                    viewModel.updateCalorieGoal(newGoal)
                    showGoalSettings = false
                }
            )
        }

        // Manual log input dialog
        if (showManualDialog) {
            ManualLogDialog(
                onDismiss = { showManualDialog = false },
                onConfirm = { name, cal, pro, carb, fat ->
                    viewModel.logManualMeal(name, cal, pro, carb, fat)
                    showManualDialog = false
                }
            )
        }

        // Meal detail dialog
        if (selectedMealForDetails != null) {
            MealDetailDialog(
                meal = selectedMealForDetails!!,
                onDismiss = { selectedMealForDetails = null }
            )
        }
    }
}

@Composable
fun ApiKeyWarningBanner() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                tint = MaterialTheme.colorScheme.tertiary,
                contentDescription = "Sandbox Notice",
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Demo Mode Equipped",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Running intelligent local nutrition estimates. Input your model API key in the AI Studio Secrets panel for vision-based scanning!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun DashboardCard(
    consumed: Int,
    goal: Int,
    protein: Double,
    proteinGoal: Double,
    carbs: Double,
    carbsGoal: Double,
    fat: Double,
    fatGoal: Double
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SleekContainer),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Daily Progress",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = SleekOnContainer
                    )
                    val remaining = (goal - consumed).coerceAtLeast(0)
                    Text(
                        text = "$remaining kcal remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SleekTextMedium
                    )
                }
                
                // Goal chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color.White.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Goal: $goal",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SleekOnContainer
                    )
                }
            }

            // Sleek Horizontal thick progress bar
            val progressFactor = if (goal > 0) (consumed.toFloat() / goal.toFloat()).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ProgressTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFactor)
                        .background(SleekPrimary)
                )
            }

            // Circular progress ring to keep test assets happy + add details
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CalorieProgressRing(consumed = consumed, goal = goal)
            }

            Divider(color = SleekOnContainer.copy(alpha = 0.1f), thickness = 1.dp)

            // Macronrients progress items
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MacroProgressWidget(
                        label = "Protein",
                        current = protein,
                        target = proteinGoal,
                        color = CoralNutrition,
                        unit = "g"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    MacroProgressWidget(
                        label = "Carbs",
                        current = carbs,
                        target = carbsGoal,
                        color = AmberCarbs,
                        unit = "g"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    MacroProgressWidget(
                        label = "Fats",
                        current = fat,
                        target = fatGoal,
                        color = MintFats,
                        unit = "g"
                    )
                }
            }
        }
    }
}

@Composable
fun CalorieProgressRing(
    consumed: Int,
    goal: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (goal > 0) consumed.toFloat() / goal.toFloat() else 0f
    val sweptAngle = progress.coerceIn(0f, 1f) * 360f
    val remaining = (goal - consumed).coerceAtLeast(0)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(150.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Background arc
            drawArc(
                color = ProgressTrack.copy(alpha = 0.35f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
            // Foreground progression arc
            drawArc(
                color = SleekPrimary,
                startAngle = -90f,
                sweepAngle = sweptAngle,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "$consumed",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, color = SleekOnContainer)
            )
            Text(
                text = "of $goal kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = SleekTextMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (goal >= consumed) "$remaining kcal left" else "${consumed - goal} kcal over",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (goal >= consumed) SleekPrimary else CoralNutrition
            )
        }
    }
}

@Composable
fun MacroProgressWidget(
    label: String,
    current: Double,
    target: Double,
    color: Color,
    unit: String
) {
    val progress = if (target > 0) (current / target).toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = SleekTextDark
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${current.toInt()}/${target.toInt()}$unit",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = SleekOnContainer
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = color,
            trackColor = ProgressTrack.copy(alpha = 0.4f),
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        )
    }
}

@Composable
fun SampleFoodsRow(onItemClick: (SampleFood) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SAMPLE_FOODS.forEach { food ->
            Card(
                onClick = { onItemClick(food) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .width(135.dp)
                    .height(175.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = food.imageUrl,
                        contentDescription = food.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = food.name,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${food.calories} kcal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ButtonsRow(
    onGalleryClick: () -> Unit,
    onManualClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onGalleryClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("gallery_button"),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = "Gallery",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Gallery Select", style = MaterialTheme.typography.labelLarge)
        }

        Button(
            onClick = onManualClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("manual_log_button"),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.EditCalendar,
                contentDescription = "Add manually",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Manually", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun EmptyLogCard(onQuickAdd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.NoFood,
                contentDescription = "No Food Today",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Your Log is Empty",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Let's track meals to analyze calories, carbs and macros. Tap on an instant food simulation card, log manually, or snap a custom plate picture!",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onQuickAdd,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Simulate Quick Log", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun MealLogItem(
    meal: MealLog,
    onDelete: () -> Unit,
    onTap: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = formatter.format(Date(meal.timestamp))

    Card(
        onClick = onTap,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SleekSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("meal_log_item_${meal.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Food display icon or local small thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SleekContainer),
                contentAlignment = Alignment.Center
            ) {
                if (meal.imageUri != null) {
                    AsyncImage(
                        model = meal.imageUri,
                        contentDescription = meal.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFFFFCC80), Color(0xFFFF9800))
                                )
                            )
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meal.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = SleekTextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "P: ${meal.protein.toInt()}g • C: ${meal.carbs.toInt()}g • F: ${meal.fat.toInt()}g • $formattedTime",
                    style = MaterialTheme.typography.labelMedium,
                    color = SleekTextMedium
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = SleekPrimary, fontSize = 16.sp)) {
                            append("${meal.calories}")
                        }
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Normal, color = SleekTextMedium, fontSize = 11.sp)) {
                            append(" kcal")
                        }
                    }
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    tint = CoralNutrition.copy(alpha = 0.7f),
                    contentDescription = "Delete entry",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AnalysisOverlayDialog(
    state: AnalysisState,
    onDismiss: () -> Unit,
    onLog: (MealAnalysis, String?) -> Unit
) {
    Dialog(onDismissRequest = { if (state !is AnalysisState.Loading) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            when (state) {
                is AnalysisState.Idle -> { /* Done */ }
                is AnalysisState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = SleekPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Analyzing Plate...",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Consulting Gemini AI to identify foods, portions and macronutrients estimate.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                is AnalysisState.Success -> {
                    val analysis = state.analysis
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Analysis Successful",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = SleekPrimary
                        )

                        // Top image thumbnail if available
                        if (state.bitmap != null) {
                            Image(
                                bitmap = state.bitmap.asImageBitmap(),
                                contentDescription = "Plate scanned",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = analysis.foodName,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${analysis.calories} kcal estimated energy",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = SleekPrimary
                            )
                        }

                        // Macro indicators chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MacroChip(label = "Carbs", value = "${analysis.carbs.toInt()}g", color = AmberCarbs)
                            MacroChip(label = "Protein", value = "${analysis.protein.toInt()}g", color = CoralNutrition)
                            MacroChip(label = "Fat", value = "${analysis.fat.toInt()}g", color = MintFats)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Analysis Detail",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = analysis.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }

                        // Dietitian feedback warning tips
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.HealthAndSafety,
                                        contentDescription = "Nutrition Tips",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Smart Advice",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = analysis.healthTips,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = { onLog(analysis, state.imageUri) },
                                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Text("Log This Meal", color = Color.White)
                            }
                        }
                    }
                }
                is AnalysisState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Dangerous,
                            contentDescription = "Error icon",
                            tint = CoralNutrition,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Analysis Stopped",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MacroChip(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun GoalSettingsDialog(
    currentGoal: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var goalText by remember { mutableStateOf(currentGoal.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Daily Goal", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Establish your customizable target daily calorie limits context below:",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("e.g. 2000") },
                    singleLine = true,
                    label = { Text("Calorie Target (kcal)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = goalText.toIntOrNull() ?: 2000
                    onConfirm(parsed)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
            ) {
                Text("Save Target", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ManualLogDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Double, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var calText by remember { mutableStateOf("") }
    var proText by remember { mutableStateOf("") }
    var carbText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual Food Logger", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Meal / Food Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual_name_input")
                )
                OutlinedTextField(
                    value = calText,
                    onValueChange = { calText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Energy (calories)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual_cal_input")
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = proText,
                        onValueChange = { proText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Protein (g)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbText,
                        onValueChange = { carbText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Carbs (g)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = fatText,
                    onValueChange = { fatText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Fat (g)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val calories = calText.toIntOrNull() ?: 0
                        val protein = proText.toDoubleOrNull() ?: 0.0
                        val carbs = carbText.toDoubleOrNull() ?: 0.0
                        val fat = fatText.toDoubleOrNull() ?: 0.0
                        onConfirm(name, calories, protein, carbs, fat)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                modifier = Modifier.testTag("manual_save_confirm")
            ) {
                Text("Log Entry", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MealDetailDialog(
    meal: MealLog,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Meal Overview",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = SleekPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close overview")
                    }
                }

                // If image exists, display it beautiful & large
                if (meal.imageUri != null) {
                    AsyncImage(
                        model = meal.imageUri,
                        contentDescription = meal.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = meal.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${meal.calories} kcal consumed",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = SleekPrimary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MacroChip(label = "Carbs", value = "${meal.carbs.toInt()}g", color = AmberCarbs)
                    MacroChip(label = "Protein", value = "${meal.protein.toInt()}g", color = CoralNutrition)
                    MacroChip(label = "Fat", value = "${meal.fat.toInt()}g", color = MintFats)
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Estimated Composition",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = meal.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Nutritionist Advice",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = meal.healthTips,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go Back", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SleekHeader(
    onSettingsClick: () -> Unit,
    onResetClick: () -> Unit
) {
    val dayOfWeek = remember { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()) }
    val formattedDate = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SleekContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AM", // Elegant custom user name initials (e.g. AM for Sleek Interface setup)
                fontWeight = FontWeight.Bold,
                color = SleekOnContainer,
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Center Date & Title
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = dayOfWeek.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp
                ),
                color = SleekTextMedium
            )
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = SleekTextDark
            )
        }

        // Action Buttons Row (existing Settings and Reset/Delete sweep)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Adjust Goals",
                    tint = SleekPrimary
                )
            }
            IconButton(
                onClick = onResetClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("clear_all_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = "Reset Day",
                    tint = CoralNutrition
                )
            }
        }
    }
}

@Composable
fun ScanYourMealBanner(
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SleekPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .testTag("scan_banner_button")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Camera icon surrounded by transparent backdrop
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Scan icon",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Scan Your Meal",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                text = "Instant AI calorie recognition",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f))
            )
        }
    }
}

@Composable
fun SleekBottomNavBar() {
    var selectedTab by remember { mutableStateOf(0) }
    
    NavigationBar(
        containerColor = SleekSurface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .border(width = (0.5).dp, color = SleekOutline)
            .navigationBarsPadding(),
        content = {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = { Icon(imageVector = Icons.Filled.Home, contentDescription = "Home") },
                label = { Text("Home", style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SleekOnContainer,
                    selectedTextColor = SleekOnContainer,
                    indicatorColor = SleekContainer,
                    unselectedIconColor = SleekTextMedium,
                    unselectedTextColor = SleekTextMedium
                )
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                icon = { Icon(imageVector = Icons.Outlined.History, contentDescription = "Log") },
                label = { Text("Log", style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SleekOnContainer,
                    selectedTextColor = SleekOnContainer,
                    indicatorColor = SleekContainer,
                    unselectedIconColor = SleekTextMedium,
                    unselectedTextColor = SleekTextMedium
                )
            )
            NavigationBarItem(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                icon = { Icon(imageVector = Icons.Outlined.Leaderboard, contentDescription = "Stats") },
                label = { Text("Stats", style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SleekOnContainer,
                    selectedTextColor = SleekOnContainer,
                    indicatorColor = SleekContainer,
                    unselectedIconColor = SleekTextMedium,
                    unselectedTextColor = SleekTextMedium
                )
            )
            NavigationBarItem(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                icon = { Icon(imageVector = Icons.Outlined.Settings, contentDescription = "Profile") },
                label = { Text("Profile", style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SleekOnContainer,
                    selectedTextColor = SleekOnContainer,
                    indicatorColor = SleekContainer,
                    unselectedIconColor = SleekTextMedium,
                    unselectedTextColor = SleekTextMedium
                )
            )
        }
    )
}

