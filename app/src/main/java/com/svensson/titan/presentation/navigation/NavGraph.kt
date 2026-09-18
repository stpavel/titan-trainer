// app/src/main/java/com/svensson/titan/presentation/navigation/NavGraph.kt
package com.svensson.titan.presentation.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.svensson.titan.presentation.active_workout.ActiveWorkoutScreen
import com.svensson.titan.presentation.history.HistoryScreen
import com.svensson.titan.presentation.program_selection.ProgramSelectionScreen
import com.svensson.titan.presentation.settings.SettingsScreen
import com.svensson.titan.presentation.trainer.TrainerReportScreen
import androidx.compose.ui.platform.LocalContext
import com.svensson.titan.util.LogBus
/**
 * Курсы убраны из навигации: тренировки и очки живут сами по себе, стартовый экран —
 * выбор программы. Экран/таблица курса остались в проекте на случай, если вернём
 * планы тренировок в другом виде.
 */
@Composable
fun TitanNavGraph(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val showBottomBar = currentDestination?.hierarchy?.any { dest ->
        bottomNavItems.any { it.screen.route == dest.route }
    } == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.ProgramSelection.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.ProgramSelection.route) {
                ProgramSelectionScreen(
                    onProgramSelected = { program ->
                        navController.navigate(Screen.ActiveWorkout.routeFor(program.id))
                    },
                    onSkip = { navController.navigate(Screen.ActiveWorkout.routeFor(null)) },
                )
            }
            composable(
                route = Screen.ActiveWorkout.route,
                arguments = listOf(
                    navArgument(Screen.ActiveWorkout.ARG_PROGRAM_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                val context = LocalContext.current
                ActiveWorkoutScreen(
                    onOpenHistory = { navController.navigate(Screen.History.route) },
                    onOpenLog = { LogBus.share(context) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTrainerReport = { navController.navigate(Screen.TrainerReport.route) },
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.TrainerReport.route) {
                TrainerReportScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}