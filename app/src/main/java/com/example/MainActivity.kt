package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.CancelBeforeRepository
import com.example.ui.navigation.Screen
import com.example.ui.screens.AddEditScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.theme.CancelBeforeTheme
import com.example.viewmodel.SubscriptionViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as CancelBeforeApp
        val repository = CancelBeforeRepository(
            context = applicationContext,
            database = app.database,
            preferencesRepository = app.preferencesRepository
        )

        val targetRecordId = intent?.getLongExtra("target_record_id", -1L) ?: -1L
        val targetDetectionId = intent?.getLongExtra("target_detection_id", -1L) ?: -1L

        setContent {
            val viewModel: SubscriptionViewModel = viewModel(
                factory = SubscriptionViewModel.provideFactory(repository)
            )

            val themeMode by viewModel.themeFlow.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            // Notification permission request for Android 13+
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* granted / denied handled gracefully */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            CancelBeforeTheme(darkTheme = isDark) {
                CancelBeforeAppNav(
                    viewModel = viewModel,
                    initialTargetRecordId = targetRecordId,
                    initialTargetDetectionId = targetDetectionId
                )
            }
        }
    }
}

data class BottomNavItem(
    val screen: Screen,
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun CancelBeforeAppNav(
    viewModel: SubscriptionViewModel,
    initialTargetRecordId: Long = -1L,
    initialTargetDetectionId: Long = -1L
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Direct navigation if launched from notification
    LaunchedEffect(initialTargetRecordId, initialTargetDetectionId) {
        if (initialTargetRecordId > 0) {
            navController.navigate(Screen.Detail.createRoute(initialTargetRecordId))
        } else if (initialTargetDetectionId > 0) {
            navController.navigate(Screen.AddEdit.createRoute(prefillDetectionId = initialTargetDetectionId))
        }
    }

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Home, "Home", Icons.Default.Home, "bottom_nav_home"),
        BottomNavItem(Screen.AddEdit, "Add", Icons.Default.AddCircle, "bottom_nav_add"),
        BottomNavItem(Screen.Insights, "Insights", Icons.Default.TrendingUp, "bottom_nav_insights"),
        BottomNavItem(Screen.Settings, "Settings", Icons.Default.Settings, "bottom_nav_settings")
    )

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Insights.route,
        Screen.Settings.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    bottomNavItems.forEach { item ->
                        val isSelected = when (item.screen) {
                            Screen.Home -> currentRoute == Screen.Home.route
                            Screen.Insights -> currentRoute == Screen.Insights.route
                            Screen.Settings -> currentRoute == Screen.Settings.route
                            else -> false
                        }

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (item.screen == Screen.AddEdit) {
                                    navController.navigate(Screen.AddEdit.createRoute())
                                } else {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToAdd = { navController.navigate(Screen.AddEdit.createRoute()) },
                    onNavigateToEdit = { id -> navController.navigate(Screen.AddEdit.createRoute(recordId = id)) },
                    onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) },
                    onNavigateToPrefillDetection = { detId ->
                        navController.navigate(Screen.AddEdit.createRoute(prefillDetectionId = detId))
                    }
                )
            }

            composable(
                route = Screen.AddEdit.route,
                arguments = listOf(
                    navArgument("recordId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("prefillDetectionId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val recordId = backStackEntry.arguments?.getLong("recordId") ?: -1L
                val prefillId = backStackEntry.arguments?.getLong("prefillDetectionId") ?: -1L
                AddEditScreen(
                    recordId = recordId,
                    prefillDetectionId = prefillId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Detail.route,
                arguments = listOf(
                    navArgument("recordId") {
                        type = NavType.LongType
                    }
                )
            ) { backStackEntry ->
                val recordId = backStackEntry.arguments?.getLong("recordId") ?: -1L
                DetailScreen(
                    recordId = recordId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id ->
                        navController.navigate(Screen.AddEdit.createRoute(recordId = id))
                    }
                )
            }

            composable(Screen.Insights.route) {
                InsightsScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) }
                )
            }

            composable(Screen.Diagnostics.route) {
                DiagnosticsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
