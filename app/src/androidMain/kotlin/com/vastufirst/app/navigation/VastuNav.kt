package com.vastufirst.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vastufirst.app.ui.home.HomeScreen
import com.vastufirst.app.ui.welcome.WelcomeScreen

/**
 * The app's navigation host. Screens are folders (Impl PRD §3.2); routes grow as Block C lands
 * the guided-grid path. Home is the start destination so saved plans are the first thing seen.
 */
@Composable
fun VastuNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onAddHome = { nav.navigate(Routes.WELCOME) })
        }
        composable(Routes.WELCOME) {
            WelcomeScreen()
        }
    }
}
