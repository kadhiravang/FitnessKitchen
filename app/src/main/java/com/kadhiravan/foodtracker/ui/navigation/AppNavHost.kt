package com.kadhiravan.foodtracker.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kadhiravan.foodtracker.data.backup.BackupManager
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.repository.ChatRepository
import com.kadhiravan.foodtracker.data.repository.FoodRepository
import com.kadhiravan.foodtracker.data.repository.LogRepository
import com.kadhiravan.foodtracker.data.repository.ProgressPhotoRepository
import com.kadhiravan.foodtracker.data.repository.WeightRepository
import com.kadhiravan.foodtracker.ui.chat.ChatScreen
import com.kadhiravan.foodtracker.ui.chat.ChatViewModel
import com.kadhiravan.foodtracker.ui.fooddb.FoodDatabaseViewModel
import com.kadhiravan.foodtracker.ui.history.HistoryScreen
import com.kadhiravan.foodtracker.ui.history.HistoryViewModel
import com.kadhiravan.foodtracker.ui.home.HomeScreen
import com.kadhiravan.foodtracker.ui.home.HomeViewModel
import com.kadhiravan.foodtracker.ui.home.ProgressGalleryScreen
import com.kadhiravan.foodtracker.ui.onboarding.OnboardingScreen
import com.kadhiravan.foodtracker.ui.settings.SettingsScreen
import com.kadhiravan.foodtracker.ui.user.UserScreen

private object Routes {
    const val ONBOARDING = "onboarding"
    const val CHAT = "chat"
    const val DIARY = "diary"
    const val HISTORY = "history"
    const val USER = "user"
    const val SETTINGS = "settings"
    const val PROGRESS_GALLERY = "progress_gallery"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.CHAT, "Chat", Icons.AutoMirrored.Filled.Chat),
    BottomTab(Routes.DIARY, "Diary", Icons.Default.CalendarMonth),
    BottomTab(Routes.HISTORY, "History", Icons.Default.History),
    BottomTab(Routes.USER, "You", Icons.Default.Person),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Default.Settings)
)

@Composable
fun AppNavHost(
    foodRepository: FoodRepository,
    logRepository: LogRepository,
    chatRepository: ChatRepository,
    weightRepository: WeightRepository,
    progressPhotoRepository: ProgressPhotoRepository,
    securePrefs: SecurePrefs,
    backupManager: BackupManager,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val startDestination = remember {
        if (securePrefs.onboardingComplete) Routes.CHAT else Routes.ONBOARDING
    }

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(logRepository, securePrefs, weightRepository, progressPhotoRepository)
    )
    val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(chatRepository))

    Scaffold(
        modifier = modifier,
        // Each screen owns a real TopAppBar (or, for Chat, its own statusBarsPadding) that
        // already handles the status bar inset, without this, the outer Scaffold's default
        // safeDrawing inset stacks on top of that and doubles the gap under the status bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination
            // Onboarding is a full-bleed wizard, not a tab, no bottom nav while it's showing.
            if (currentRoute?.route != Routes.ONBOARDING) {
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
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    securePrefs = securePrefs,
                    weightRepository = weightRepository,
                    backupManager = backupManager,
                    onFinished = {
                        navController.navigate(Routes.CHAT) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.CHAT) {
                ChatScreen(viewModel = chatViewModel, securePrefs = securePrefs)
            }
            composable(Routes.DIARY) {
                HomeScreen(
                    viewModel = homeViewModel,
                    securePrefs = securePrefs,
                    onOpenProgressGallery = { navController.navigate(Routes.PROGRESS_GALLERY) }
                )
            }
            composable(Routes.PROGRESS_GALLERY) {
                ProgressGalleryScreen(viewModel = homeViewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.HISTORY) {
                val historyViewModel: HistoryViewModel = viewModel(
                    factory = HistoryViewModel.Factory(logRepository)
                )
                val foodDbViewModel: FoodDatabaseViewModel = viewModel(
                    factory = FoodDatabaseViewModel.Factory(foodRepository)
                )
                HistoryScreen(
                    historyViewModel = historyViewModel,
                    foodDbViewModel = foodDbViewModel,
                    securePrefs = securePrefs,
                    onDaySelected = { date ->
                        homeViewModel.selectDate(date)
                        navController.navigate(Routes.DIARY) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.USER) {
                UserScreen(securePrefs = securePrefs, weightRepository = weightRepository)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(securePrefs = securePrefs, backupManager = backupManager)
            }
        }
    }
}
