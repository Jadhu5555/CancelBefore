package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AddEdit : Screen("add_edit?recordId={recordId}&prefillDetectionId={prefillDetectionId}") {
        fun createRoute(recordId: Long = -1L, prefillDetectionId: Long = -1L): String {
            return "add_edit?recordId=$recordId&prefillDetectionId=$prefillDetectionId"
        }
    }
    object Detail : Screen("detail/{recordId}") {
        fun createRoute(recordId: Long): String = "detail/$recordId"
    }
    object Insights : Screen("insights")
    object Settings : Screen("settings")
    object Diagnostics : Screen("diagnostics")
}
