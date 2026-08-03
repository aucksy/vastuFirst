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
import com.vastufirst.app.ui.details.MoreDetailsScreen
import com.vastufirst.app.ui.grid.GuidedGridScreen
import com.vastufirst.app.ui.home.HomeScreen
import com.vastufirst.app.ui.legal.LegalScreen
import com.vastufirst.app.ui.legal.PrivacyScreen
import com.vastufirst.app.ui.marknorth.MarkNorthScreen
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.app.ui.report.ReportScreen
import com.vastufirst.app.ui.scan.PlanReadingConsent
import com.vastufirst.app.ui.scan.ScanConsentScreen
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
                val hasPlans = repo.observePlans().first().plans.isNotEmpty()
                // ⭐ An unfinished home counts (v0.6.6). Nothing restores a draft by itself any more,
                // so a user whose only home is half-drawn must land on the list that OFFERS it —
                // otherwise the flow would open on a blank grid and their work, though safely on
                // disk, would look exactly like work the app had thrown away.
                val hasDrafts = repo.observeDrafts().first().isNotEmpty()
                target = if (hasPlans || hasDrafts) Routes.HOME else Routes.NEWPLAN_GRAPH
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
                // ⭐ Straight to the editor with the unfinished home in it — the ONLY route that
                // brings one back. It lands inside the newplan graph without going through Welcome,
                // because the user has already answered those questions once; Back returns here.
                onOpenDraft = { id -> nav.go(Routes.guidedGridForDraft(id)) },
                onSettings = { nav.go(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onLegal = { nav.go(Routes.LEGAL) },
                onPrivacy = { nav.go(Routes.PRIVACY) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.LEGAL) {
            LegalScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { nav.popBackStack() })
        }

        navigation(startDestination = Routes.WELCOME, route = Routes.NEWPLAN_GRAPH) {

            composable(Routes.WELCOME) { entry ->
                val vm = sharedVm(nav, entry)
                WelcomeScreen(vm = vm, onContinue = { nav.go(Routes.ADD_HOME) })
            }

            composable(Routes.ADD_HOME) { entry ->
                val vm = sharedVm(nav, entry)
                val consent = koinInject<PlanReadingConsent>()
                AddHomeScreen(
                    onDrawGrid = { nav.go(Routes.GUIDED_GRID) },
                    // ⭐ The consent screen is not optional and not skippable: the scanner is only
                    // ever reached through it, or after it has already been answered once.
                    onScan = { nav.go(if (consent.isGranted()) Routes.SCAN else Routes.SCAN_CONSENT) },
                    onSample = {
                        val sample = SamplePlans.all.first()
                        vm.updateRooms(sample.rooms)
                        vm.updateDoor(sample.door)
                        vm.updateNorth(sample.north)
                        nav.go(Routes.MARK_NORTH)
                    },
                )
            }

            composable(Routes.SCAN_CONSENT) {
                val consent = koinInject<PlanReadingConsent>()
                ScanConsentScreen(
                    onAgree = {
                        consent.set(true)
                        // Replace the gate in the back stack: having agreed, Back from the scanner
                        // should go to "Add home", not back through the consent screen.
                        nav.navigate(Routes.SCAN) {
                            popUpTo(Routes.SCAN_CONSENT) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onDrawInstead = { nav.go(Routes.GUIDED_GRID) },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(Routes.SCAN) { entry ->
                val planVm = sharedVm(nav, entry)
                val scanVm: ScanViewModel = koinViewModel()
                ScanRoute(
                    vm = scanVm,
                    onUseRooms = { outcome ->
                        // The scan's rooms land in the guided grid — the confirmation surface §6.2b
                        // requires.
                        //
                        // ⚠ CLEAR FIRST, then resize, then add. `updateGrid` re-packs whatever is
                        // already placed and REFUSES a size the existing rooms cannot fit — so if the
                        // user had drawn rooms earlier in this session (grid → back → upload), the
                        // resize could silently decline while the scanned rooms, sized for the grid we
                        // asked for, went in anyway and landed outside the plot. That is the v0.3.7
                        // coordinate-space bug arriving by a new road. An empty plot always resizes.
                        // Clearing is also correct on its own terms: a scan replaces the home, so the
                        // previous rooms and their front door do not belong to it.
                        val (cols, rows) = gridForOutcome(outcome)
                        planVm.updateRooms(emptyList())
                        planVm.updateGrid(cols, rows)
                        planVm.updateRooms(toGridRooms(outcome.scannedRooms(), cols, rows))
                        // ⭐ Tell the editor whether these rooms are a PLAN or a parking row. Without
                        // it the grid says "Place your rooms" over a strip of identical squares and
                        // then asks whether the leftovers of that strip are part of the home — which
                        // is the screen the owner was handed for his own flat.
                        planVm.markRoomsUnplaced(outcome !is com.vastufirst.shared.scan.ScanOutcome.Placed)
                        nav.go(Routes.GUIDED_GRID)
                    },
                    onDrawInstead = { nav.go(Routes.GUIDED_GRID) },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(
                route = Routes.GUIDED_GRID_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_DRAFT_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val vm = sharedVm(nav, entry)
                // Present ONLY when the user tapped an unfinished home on the saved-homes screen.
                // Every other way into this editor arrives with no id and therefore a clean grid.
                val draftId = entry.arguments?.getString(Routes.ARG_DRAFT_ID)
                LaunchedEffect(draftId) { if (draftId != null) vm.resumeDraft(draftId) }
                GuidedGridScreen(vm = vm, onNext = { nav.go(Routes.MARK_NORTH) })
            }
            composable(
                route = Routes.MARK_NORTH_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_FROM_SCORE) { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val vm = sharedVm(nav, entry)
                // ⚠ Which way out. At the END of the drawing flow this screen pushes the score, as it
                // always has. Opened from an ALREADY-SAVED home's score ("change which way North
                // is"), it goes BACK to the score it came from instead — pushing a second copy would
                // put the score behind the score, so Back would land on the same screen again. The
                // score reads North straight off the shared draft, so returning shows the new number.
                val fromScore = entry.arguments?.getBoolean(Routes.ARG_FROM_SCORE) ?: false
                MarkNorthScreen(
                    vm = vm,
                    onRead = { vm.save(); if (fromScore) nav.popBackStack() else nav.go(Routes.SCORE) },
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
                    // No draft id: the home on screen is already loaded into the shared draft, and
                    // an id here would try to pull an unfinished home over the top of it.
                    onFix = { nav.go(Routes.GUIDED_GRID) },
                    onChangeNorth = { nav.go(Routes.markNorthFromScore()) },
                    onDone = { nav.goHome() },
                    onAddDetails = { nav.go(Routes.MORE_DETAILS) },
                )
            }
            // The optional extras. Both ways out land back on the score, which re-reads the live
            // analysis — so an answer given here shows up in the number immediately.
            composable(Routes.MORE_DETAILS) { entry ->
                val vm = sharedVm(nav, entry)
                MoreDetailsScreen(
                    vm = vm,
                    onDone = { nav.popBackStack() },
                    onBack = { nav.popBackStack() },
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
