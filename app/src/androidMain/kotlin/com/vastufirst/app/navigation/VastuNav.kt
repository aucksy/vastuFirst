package com.vastufirst.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.vastufirst.app.ui.scan.ScanRoute
import com.vastufirst.app.ui.scan.ScanViewModel
import com.vastufirst.app.ui.scan.gridForOutcome
import com.vastufirst.app.ui.scan.scannedRooms
import com.vastufirst.app.ui.scan.toGridRooms
import com.vastufirst.app.ui.score.ScoreScreen
import com.vastufirst.app.ui.settings.SettingsScreen
import com.vastufirst.app.ui.unlock.UnlockScreen
import com.vastufirst.app.ui.welcome.WelcomeScreen
import com.vastufirst.data.PlanRepository
import com.vastufirst.designsystem.components.BrandMark
import com.vastufirst.designsystem.theme.VastuTheme
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The app's navigation host. The guided-grid path is a nested graph so its screens share one
 * [NewPlanViewModel] (the draft home). Home is the start destination.
 */
@Composable
fun VastuNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LAUNCH) {

        // First frame decides where to land, so a fresh install never opens on an empty
        // "No plans yet" screen: returning users go to their saved plans, first-timers go straight
        // into the flow. A themed splash (no white flash) shows for the single frame it takes to
        // read the DB. popUpTo removes LAUNCH so Back from the first real screen exits the app.
        composable(Routes.LAUNCH) {
            val repo = koinInject<PlanRepository>()
            var target by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                val hasPlans = repo.observePlans().first().isNotEmpty()
                target = if (hasPlans) Routes.HOME else Routes.NEWPLAN_GRAPH
            }
            LaunchedEffect(target) {
                target?.let { dest ->
                    nav.navigate(dest) { popUpTo(Routes.LAUNCH) { inclusive = true } }
                }
            }
            Box(
                Modifier.fillMaxSize().background(VastuTheme.colors.paper),
                contentAlignment = Alignment.Center,
            ) {
                BrandMark()
            }
        }

        composable(Routes.HOME) {
            HomeScreen(
                onAddHome = { nav.go(Routes.NEWPLAN_GRAPH) },
                onOpenPlan = { id -> nav.go("${Routes.SCORE}?planId=$id") },
                onSettings = { nav.go(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onLegal = { nav.go(Routes.LEGAL) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.LEGAL) {
            LegalScreen(onBack = { nav.popBackStack() })
        }

        navigation(startDestination = Routes.WELCOME, route = Routes.NEWPLAN_GRAPH) {

            composable(Routes.WELCOME) { entry ->
                val vm = sharedVm(nav, entry)
                WelcomeScreen(vm = vm, onContinue = { nav.go(Routes.ADD_HOME) })
            }

            composable(Routes.ADD_HOME) { entry ->
                val vm = sharedVm(nav, entry)
                AddHomeScreen(
                    onDrawGrid = { nav.go(Routes.GUIDED_GRID) },
                    onScan = { nav.go(Routes.SCAN) },
                    onSample = {
                        val sample = SamplePlans.all.first()
                        vm.updateRooms(sample.rooms)
                        vm.updateDoor(sample.door)
                        vm.updateNorth(sample.north)
                        nav.go(Routes.MARK_NORTH)
                    },
                )
            }

            composable(Routes.SCAN) { entry ->
                val planVm = sharedVm(nav, entry)
                val scanVm: ScanViewModel = koinViewModel()
                ScanRoute(
                    vm = scanVm,
                    onUseRooms = { outcome ->
                        // The scan's rooms land in the guided grid — the confirmation surface §6.2b
                        // requires. Grid FIRST, then rooms: resolveGridResize re-packs whatever is
                        // already placed, so setting the plot while it is still empty is a plain
                        // resize and can never disturb the rooms we are about to add.
                        val (cols, rows) = gridForOutcome(outcome)
                        planVm.updateGrid(cols, rows)
                        planVm.updateRooms(toGridRooms(outcome.scannedRooms(), cols, rows))
                        nav.go(Routes.GUIDED_GRID)
                    },
                    onDrawInstead = { nav.go(Routes.GUIDED_GRID) },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(Routes.GUIDED_GRID) { entry ->
                val vm = sharedVm(nav, entry)
                GuidedGridScreen(vm = vm, onNext = { nav.go(Routes.MARK_NORTH) })
            }
            composable(Routes.MARK_NORTH) { entry ->
                val vm = sharedVm(nav, entry)
                MarkNorthScreen(
                    vm = vm,
                    onRead = { vm.save(); nav.go(Routes.SCORE) },
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
                    // Already paid? go straight to the report rather than the paywall again (B11).
                    onUnlock = { nav.go(if (vm.unlocked) Routes.REPORT else Routes.UNLOCK) },
                    onFix = { nav.go(Routes.GUIDED_GRID) },
                    onDone = { nav.goHome() },
                )
            }
            composable(Routes.UNLOCK) { entry ->
                val vm = sharedVm(nav, entry)
                UnlockScreen(onUnlocked = {
                    vm.unlock()
                    nav.navigate(Routes.REPORT) { popUpTo(Routes.UNLOCK) { inclusive = true }; launchSingleTop = true }
                })
            }
            composable(Routes.REPORT) { entry ->
                val vm = sharedVm(nav, entry)
                ReportScreen(vm = vm, onDone = { nav.goHome() })
            }
        }
    }
}

/** Navigate, debounced: a fast double-tap can't push two copies of the same destination (§B6). */
private fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }

/** Leave the guided-grid flow for the saved-plans list, clearing the flow so Back doesn't re-enter
 *  it. A first-time user (sent straight into the flow by LAUNCH) otherwise has no path to Home (§A2). */
private fun NavHostController.goHome() = navigate(Routes.HOME) {
    popUpTo(Routes.NEWPLAN_GRAPH) { inclusive = true }
    launchSingleTop = true
}

/** The NewPlanViewModel scoped to the whole "newplan" graph, so every step shares one draft. */
@Composable
private fun sharedVm(nav: NavHostController, entry: NavBackStackEntry): NewPlanViewModel {
    val parent = remember(entry) { nav.getBackStackEntry(Routes.NEWPLAN_GRAPH) }
    return koinViewModel(viewModelStoreOwner = parent)
}
