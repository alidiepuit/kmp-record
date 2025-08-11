import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import home.HomeScreen
import org.koin.core.context.startKoin
import di.appModule
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.Navigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun App() {
    val navigator = rememberNavController()
    val navBackStackEntry = navigator.currentBackStackEntryAsState()
    AppTheme(
        seedColor = Color.Yellow
    ) {
        NavHost(
            modifier = Modifier.fillMaxSize(),
            startDestination = "home",
            navController = navigator
        ) {
            composable(route = "home") {
                HomeScreen()
            }
        }
    }
}
