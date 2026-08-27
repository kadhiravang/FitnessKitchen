package com.kadhiravan.foodtracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.repository.FoodRepository
import com.kadhiravan.foodtracker.data.repository.LogRepository
import com.kadhiravan.foodtracker.data.repository.VoiceParsingRepository
import com.kadhiravan.foodtracker.ui.fooddb.FoodDatabaseScreen
import com.kadhiravan.foodtracker.ui.fooddb.FoodDatabaseViewModel
import com.kadhiravan.foodtracker.ui.history.HistoryScreen
import com.kadhiravan.foodtracker.ui.history.HistoryViewModel
import com.kadhiravan.foodtracker.ui.home.HomeScreen
import com.kadhiravan.foodtracker.ui.home.HomeViewModel
import com.kadhiravan.foodtracker.ui.settings.SettingsScreen
import com.kadhiravan.foodtracker.ui.voice.VoiceCaptureScreen
import com.kadhiravan.foodtracker.ui.voice.VoiceCaptureViewModel

private object Routes {
    const val HOME = "home"
    const val VOICE = "voice"
    const val FOOD_DB = "food_db"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Today", Icons.Default.Home),
    BottomTab(Routes.FOOD_DB, "Foods", Icons.Default.Restaurant),
    BottomTab(Routes.HISTORY, "History", Icons.Default.History),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Default.Settings)
)

@Composable
fun AppNavHost(
    foodRepository: FoodRepository,
    logRepository: LogRepository,
    voiceParsingRepository: VoiceParsingRepository,
    securePrefs: SecurePrefs,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(logRepository, securePrefs))

    Scaffold(
        modifier = modifier,
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination
            NavigationBar {
                bottomTabs.forEach { tab ->
                    val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onAddViaVoice = { navController.navigate(Routes.VOICE) }
                )
            }
            composable(Routes.VOICE) {
                val voiceViewModel: VoiceCaptureViewModel = viewModel(
                    factory = VoiceCaptureViewModel.Factory(voiceParsingRepository, logRepository, foodRepository)
                )
                VoiceCaptureScreen(
                    viewModel = voiceViewModel,
                    date = homeViewModel.uiState.value.date,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Routes.FOOD_DB) {
                val foodDbViewModel: FoodDatabaseViewModel = viewModel(
                    factory = FoodDatabaseViewModel.Factory(foodRepository)
                )
                FoodDatabaseScreen(viewModel = foodDbViewModel)
            }
            composable(Routes.HISTORY) {
                val historyViewModel: HistoryViewModel = viewModel(
                    factory = HistoryViewModel.Factory(logRepository)
                )
                HistoryScreen(
                    viewModel = historyViewModel,
                    onDaySelected = { date ->
                        homeViewModel.selectDate(date)
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(securePrefs = securePrefs)
            }
        }
    }
}
