package com.vastufirst.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.vastufirst.app.ui.addhome.AddHomeScreen
import com.vastufirst.app.ui.home.HomeScreen
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.app.ui.welcome.WelcomeScreen
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.theme.VastuTheme
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
            HomeScreen(onAddHome = { nav.navigate(Routes.NEWPLAN_GRAPH) })
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
                        vm.setRooms(sample.rooms)
                        vm.setDoor(sample.door)
                        vm.setNorth(sample.north)
                        nav.navigate(Routes.MARK_NORTH)
                    },
                )
            }

            composable(Routes.GUIDED_GRID) { Stub("Guided grid — coming next") }
            composable(Routes.MARK_NORTH) { Stub("Mark North — coming next") }
            composable(Routes.SCORE) { Stub("Score — coming next") }
            composable(Routes.REPORT) { Stub("Full report — coming next") }
        }
    }
}

/** The NewPlanViewModel scoped to the whole "newplan" graph, so every step shares one draft. */
@Composable
private fun sharedVm(nav: NavHostController, entry: NavBackStackEntry): NewPlanViewModel {
    val parent = remember(entry) { nav.getBackStackEntry(Routes.NEWPLAN_GRAPH) }
    return koinViewModel(viewModelStoreOwner = parent)
}

@Composable
private fun Stub(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().background(VastuTheme.colors.paper).padding(VastuTheme.spacing.s6),
    ) {
        VText(text = text, style = VastuTheme.type.h3, color = VastuTheme.colors.textPrimary)
    }
}
