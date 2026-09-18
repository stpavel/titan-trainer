package com.svensson.titan.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object CourseSelection : Screen("course_selection?forceShow={forceShow}") {
        const val ARG_FORCE_SHOW = "forceShow"
        fun routeFor(forceShow: Boolean = false): String = "course_selection?forceShow=$forceShow"
    }
    data object ProgramSelection : Screen("program_selection")

    data object ActiveWorkout : Screen("active_workout?programId={programId}") {
        const val ARG_PROGRAM_ID = "programId"
        fun routeFor(programId: String?): String =
            if (programId != null) "active_workout?programId=$programId" else "active_workout"
    }

    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object TrainerReport : Screen("trainer_report")
}

data class BottomNavItem(val screen: Screen, val label: String, val icon: ImageVector)

val bottomNavItems = listOf(
    BottomNavItem(Screen.ProgramSelection, "Программы", Icons.Filled.List),
    BottomNavItem(Screen.Settings, "Настройки", Icons.Filled.Settings),
    BottomNavItem(Screen.History, "История", Icons.Filled.DateRange),
)