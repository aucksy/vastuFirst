package com.vastufirst.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.vastufirst.app.ui.addhome.AddHomeScreen
import com.vastufirst.app.ui.grid.GuidedGridScreen
import com.vastufirst.app.ui.home.HomeScreen
import com.vastufirst.app.ui.legal.LegalScreen
import com.vastufirst.app.ui.marknorth.MarkNorthScreen
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.app.ui.report.ReportScreen
import com.vastufirst.app.ui.score.ScoreScreen
import com.vastufirst.app.ui.settings.SettingsScreen
import com.vastufirst.app.ui.unlock.UnlockScreen
import com.vastufirst.app.ui.welcome.WelcomeScreen
import org.koin.androidx.compose.koinViewModel

/**
 * The app's navigation host. The guided-grid path is a nested graph so its screens share one
 * [NewPlanViewModel] (the draft home). Home is the start destination.
 */
@Composable
fun VastuNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onAddHome = { nav.navigate(Routes.NEWPLAN_GRAPH) },
                onOpenPlan = { id -> nav.navigate("${Routes.SCORE}?planId=$id") },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onLegal = { nav.navigate(Routes.LEGAL) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.LEGAL) {
            LegalScreen(onBack = { nav.popBackStack() })
        }

        navigation(startDestination = Routes.WELCOME, route = Routes.NEWPLAN_GRAPH) {

            composable(Routes.WELCOME) { entry ->
                val vm = sharedVm(nav, entry)
                WelcomeScreen(vm = vm, onContinue = { nav.navigate(Routes.ADD_HOME) })
            }

            composable(Routes.ADD_HOME) { entry ->
                val vm = sharedVm(nav, entry)
                AddHomeScreen(
                    onDrawGrid = { nav.navigate(Routes.GUIDED_GRID) },
                    onSample = {
                        val sample = SamplePlans.all.first()
                        vm.updateRooms(sample.rooms)
                        vm.updateDoor(sample.door)
                        vm.updateNorth(sample.north)
                        nav.navigate(Routes.MARK_NORTH)
                    },
                )
            }

            composable(Routes.GUIDED_GRID) { entry ->
                val vm = sharedVm(nav, entry)
                GuidedGridScreen(vm = vm, onNext = { nav.navigate(Routes.MARK_NORTH) })
            }
            composable(Routes.MARK_NORTH) { entry ->
                val vm = sharedVm(nav, entry)
                MarkNorthScreen(
                    vm = vm,
                    onRead = { vm.save(); nav.navigate(Routes.SCORE) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                route = "${Routes.SCORE}?planId={planId}",
                arguments = listOf(navArgument("planId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                val vm = sharedVm(nav, entry)
                val planId = entry.arguments?.getString("planId")
                LaunchedEffect(planId) { if (planId != null) vm.loadById(planId) }
                ScoreScreen(
                    vm = vm,
                    onUnlock = { nav.navigate(Routes.UNLOCK) },
                    onFix = { nav.navigate(Routes.GUIDED_GRID) },
                )
            }
            composable(Routes.UNLOCK) { entry ->
                val vm = sharedVm(nav, entry)
                UnlockScreen(onUnlocked = {
                    vm.unlock()
                    nav.navigate(Routes.REPORT) { popUpTo(Routes.UNLOCK) { inclusive = true } }
                })
            }
            composable(Routes.REPORT) { entry ->
                val vm = sharedVm(nav, entry)
                ReportScreen(vm = vm)
            }
        }
    }
}

/** The NewPlanViewModel scoped to the whole "newplan" graph, so every step shares one draft. */
@Composable
private fun sharedVm(nav: NavHostController, entry: NavBackStackEntry): NewPlanViewModel {
    val parent = remember(entry) { nav.getBackStackEntry(Routes.NEWPLAN_GRAPH) }
    return koinViewModel(viewModelStoreOwner = parent)
}
