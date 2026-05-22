package gr.dkaratzas.tanrenkiroku.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import gr.dkaratzas.tanrenkiroku.ui.screens.ExercisePickerScreen
import gr.dkaratzas.tanrenkiroku.ui.screens.HomeScreen
import gr.dkaratzas.tanrenkiroku.ui.screens.InfoScreen
import gr.dkaratzas.tanrenkiroku.ui.screens.SettingsScreen
import gr.dkaratzas.tanrenkiroku.ui.screens.SyncScreen
import gr.dkaratzas.tanrenkiroku.ui.viewmodel.SyncViewModel
import gr.dkaratzas.tanrenkiroku.ui.viewmodel.WorkoutViewModel

private const val FADE_DURATION = 350

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val viewModel: WorkoutViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { fadeIn(tween(FADE_DURATION)) },
        exitTransition = { fadeOut(tween(FADE_DURATION)) },
        popEnterTransition = { fadeIn(tween(FADE_DURATION)) },
        popExitTransition = { fadeOut(tween(FADE_DURATION)) }
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onAddExercise = { navController.navigate("exercise_picker") },
                onSync = { navController.navigate("sync") },
                onSettings = { navController.navigate("settings") },
                onInfo = { navController.navigate("info") }
            )
        }
        composable("exercise_picker") {
            ExercisePickerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("info") {
            InfoScreen(onBack = { navController.popBackStack() })
        }
        composable("sync") {
            val syncViewModel: SyncViewModel = viewModel()
            SyncScreen(
                viewModel = syncViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
