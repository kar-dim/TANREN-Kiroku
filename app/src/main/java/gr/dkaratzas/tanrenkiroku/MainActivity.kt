package gr.dkaratzas.tanrenkiroku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import gr.dkaratzas.tanrenkiroku.data.ThemeMode
import gr.dkaratzas.tanrenkiroku.ui.navigation.NavGraph
import gr.dkaratzas.tanrenkiroku.ui.theme.TANRENKirokuTheme
import gr.dkaratzas.tanrenkiroku.ui.viewmodel.WorkoutViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val workoutViewModel: WorkoutViewModel = viewModel()
            val darkTheme = when (workoutViewModel.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            TANRENKirokuTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph()
                }
            }
        }
    }
}
