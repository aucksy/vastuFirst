package com.vastufirst.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                // ⭐ An UNREADABLE home counts too (audit B6). A user whose every saved home hit a
                // read error used to be routed into first-run onboarding as though they were new —
                // the one screen that says the opposite of "your data is still here" — while the
                // reassuring "still saved" notice sat unreachable on the list they were steered
                // away from. Their rows are on disk; the list is where that truth is told.
                val saved = repo.observePlans().first()
                val hasPlans = saved.plans.isNotEmpty() || saved.unreadable.isNotEmpty()
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
                onOpenPlan = { id -> nav.go(Routes.reportForPlan(id)) },
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
                WelcomeScreen(
                    vm = vm,
                    onContinue = { nav.go(Routes.ADD_HOME) },
                    // The only route to the policy a first-time reader has — see WelcomeContent.
                    onPrivacy = { nav.go(Routes.PRIVACY) },
                )
            }

            composable(Routes.ADD_HOME) { entry ->
                val vm = sharedVm(nav, entry)
                val consent = koinInject<PlanReadingConsent>()
                // ⭐⭐ A NEW HOME STARTS WITH NO PHOTOGRAPH. The scan hands the photo over in a
                // process-wide slot, and nothing used to empty it — so once ANY plan had been
                // scanned, every home opened afterwards drew that picture under "Your home, as we
                // read it", including homes drawn by hand and homes reopened from the saved list.
                // The report gated on "is there a photo lying around" while believing it was gating
                // on "did THIS home arrive by scan". Emptying the slot when a new home begins is
                // half of making those two the same question; the report route does the other half.
                val newHomeHandover = koinInject<com.vastufirst.app.ui.scan.ScanReviewHandover>()
                LaunchedEffect(Unit) { newHomeHandover.data = null }
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
                val reviewHandover = koinInject<com.vastufirst.app.ui.scan.ScanReviewHandover>()
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
                        // ⭐⭐ THE FRONT DOOR, READ OFF THE PLAN (owner, 6 Aug 2026: "cant we do it
                        // ourselves when Entry is clearly marked? we ask only if its not"). Only for
                        // a PLACED scan: an assisted one parks its rooms in a provisional strip, so
                        // "which wall is the foyer on" would be asking about a holding pattern
                        // rather than a home. Null when the plan named no entrance, or named one we
                        // could not pin to a single wall — and null is what makes the next screen
                        // ask instead of tell. See frontDoorFromEntrance for why it refuses often.
                        planVm.updateDoor(
                            if (outcome is com.vastufirst.shared.scan.ScanOutcome.Placed) {
                                com.vastufirst.app.ui.newplan.frontDoorFromEntrance(planVm.rooms)
                            } else {
                                null
                            },
                        )
                        // ⭐⭐ A placed scan NEVER opens the editor (owner, 6 Aug 2026). The grid
                        // rooms above are still populated, because they are what the engine scores
                        // and what the report draws — but the user never sees or touches them
                        // on this path. They set North, check, and mark the door on their own photo.
                        //
                        // ⭐⭐ NORTH COMES FIRST NOW (owner, 11 Aug 2026). A placed scan goes
                        // straight to the North dial, and the check screen follows it — because a
                        // room has no direction and no verdict until North is marked, and the
                        // owner's original list wants both of those ON the check screen's rows.
                        // Ordering the flow the other way round is the only reason they were never
                        // built. The handover is written FIRST either way: North draws the same
                        // photograph the check screen does.
                        //
                        // ⚠ The `else` below is the one route left from a scan into the editor, and
                        // it is not a preference: it is a scan whose rooms could NOT be placed, so
                        // there is no geometry to score and somebody has to supply it. Measured on
                        // the 44 recorded real plans: 24 place, 9 arrive unplaced, 11 are refused.
                        // Removing this branch today would strand about one readable plan in six
                        // with no route to a reading at all. It goes when placing rooms by tapping
                        // the PHOTO exists to replace it — not before.
                        if (outcome is com.vastufirst.shared.scan.ScanOutcome.Placed) {
                            reviewHandover.data = com.vastufirst.app.ui.scan.ScanReviewData(
                                imageBytes = scanVm.lastImage?.bytes,
                                rooms = outcome.rooms,
                            )
                            nav.go(Routes.markNorthFromScan())
                        } else {
                            // ⭐ A read that could not be placed hands over NOTHING — and must also
                            // wipe whatever an earlier read left behind, or the home about to be
                            // arranged by hand inherits the previous plan's photograph.
                            reviewHandover.data = null
                            nav.go(Routes.GUIDED_GRID)
                        }
                    },
                    onDrawInstead = { nav.go(Routes.GUIDED_GRID) },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(Routes.SCAN_REVIEW) { entry ->
                val planVm = sharedVm(nav, entry)
                val handover = koinInject<com.vastufirst.app.ui.scan.ScanReviewHandover>()
                // ⭐ The live reading, so every row can carry the report's own one-word result and
                // the direction the room sits in. It exists by now because North was marked on the
                // screen before this one — which is exactly why that step was moved in front of it.
                val analysis by planVm.analysis.collectAsStateWithLifecycle()
                com.vastufirst.app.ui.scan.ScanReviewScreen(
                    handover = handover,
                    // Already read off the plan's own entrance, or null if the plan named none.
                    door = planVm.door,
                    analysis = analysis,
                    // ⭐ WHAT we read it off, when it was a printed caption rather than a room typed
                    // as an entrance — so the screen can quote the plan's own word back. Recomputed
                    // rather than stored: it is only shown while the door still IS the one we read,
                    // so a user who moves the door never sees a sentence claiming their plan put it
                    // there.
                    doorFromCaption = com.vastufirst.app.ui.newplan.frontDoorRead(planVm.rooms)
                        ?.takeIf { it.door == planVm.door }?.fromCaption,
                    // ⭐ WHERE THIS SCREEN LEADS, and it is now the END of the flow whenever the plan
                    // named its own entrance: North is already marked, so the only thing that can
                    // still be missing is the front door. When the plan answered that (13 of the 24
                    // recorded real plans that place their rooms), the reader goes straight to their
                    // report — the standing rule that the LAST step lands on the report, with no
                    // free score screen in between (owner, 10 Aug 2026), unchanged.
                    //
                    // ⚠ The save moved here with the ending. It used to sit on the North dial's
                    // "read my home" because that was the last step; leaving it there would have
                    // written the home to disk while the reader was still checking it.
                    //
                    // ⚠ The same pop-first guard the door screen uses, and for the same reason: a
                    // reader who presses Back from their report lands HERE now, and tapping on again
                    // would otherwise stack a second report on top of the first. Returning to the one
                    // already on the stack shows the same live reading, because the report reads the
                    // shared draft rather than a snapshot.
                    onContinue = {
                        if (planVm.door != null) {
                            planVm.save()
                            if (!nav.popBackStack(Routes.REPORT_ROUTE, inclusive = false)) {
                                nav.go(Routes.REPORT)
                            }
                        } else {
                            nav.go(Routes.SCAN_DOOR)
                        }
                    },
                    onChangeDoor = { nav.go(Routes.SCAN_DOOR) },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(
                route = Routes.SCAN_DOOR_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_FROM_REPORT) { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val planVm = sharedVm(nav, entry)
                val handover = koinInject<com.vastufirst.app.ui.scan.ScanReviewHandover>()
                com.vastufirst.app.ui.scan.ScanDoorScreen(
                    handover = handover,
                    door = planVm.door,
                    onDoor = planVm::updateDoor,
                    // Opened from a finished report, this screen returns there — so the button says
                    // that, instead of naming a North step it will never open.
                    returnsToReport = entry.arguments?.getBoolean(Routes.ARG_FROM_REPORT) ?: false,
                    // ⚠ Which way out depends on where this was entered from. Reached from the
                    // report's "change where the front door is", the report is already behind us —
                    // so go BACK to it rather than pushing a second report on top of the first.
                    //
                    // ⭐ In the flow this is now the LAST step — North was marked two screens ago
                    // (11 Aug 2026) — so it saves the home and opens the report, which is what
                    // North's own "read my home" used to do.
                    onNext = {
                        if (!nav.popBackStack(Routes.REPORT_ROUTE, inclusive = false)) {
                            planVm.save()
                            nav.go(Routes.REPORT)
                        }
                    },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(
                route = Routes.GUIDED_GRID_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_DRAFT_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(Routes.ARG_DOOR_MODE) { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val vm = sharedVm(nav, entry)
                // Present ONLY when the user tapped an unfinished home on the saved-homes screen.
                // Every other way into this editor arrives with no id and therefore a clean grid.
                val draftId = entry.arguments?.getString(Routes.ARG_DRAFT_ID)
                // ⭐⭐ AND THE PHOTOGRAPH SLOT IS EMPTIED WITH IT — THE THIRD ROUTE, MISSED IN
                // v0.12.0. That release found the scan's picture being drawn under homes it does not
                // belong to and closed two of the three ways in: starting a new home empties the
                // slot, and opening a saved home from the list empties it. Resuming an UNFINISHED
                // home is the third, and it goes straight to this editor without passing either.
                //
                // What that cost, in one session and four taps: scan a plan, read its report, tap
                // "see all my plans", tap a half-drawn home, finish it — and its report opens with
                // the OTHER property's photograph under "Your home, as we read it", the other
                // property's rooms outlined on it, and "change where the front door is" inviting the
                // reader to mark a door on a picture of somebody else's flat. That tap is measured
                // against the wrong plan's geometry and written into THIS home, which is the
                // heaviest single input the engine weighs.
                //
                // ⚠ Emptied here rather than on the way OUT of the scan, because the scan's own
                // screens legitimately need it right up to the report. The rule this restores is the
                // one v0.12.0 stated: a photograph belongs to the home it was taken for, and every
                // door into a DIFFERENT home has to close it.
                val draftHandover = koinInject<com.vastufirst.app.ui.scan.ScanReviewHandover>()
                LaunchedEffect(draftId) {
                    if (draftId != null) {
                        draftHandover.data = null
                        vm.resumeDraft(draftId)
                    }
                }
                GuidedGridScreen(
                    vm = vm,
                    // ⚠ Which way out depends on where this was entered from. In the flow it is
                    // followed by North. Reached from the report's "change where the front door is",
                    // the report is already behind us — so go BACK to it rather than pushing North
                    // and then a second report on top of the first.
                    onNext = {
                        if (!nav.popBackStack(Routes.REPORT_ROUTE, inclusive = false)) {
                            nav.go(Routes.MARK_NORTH)
                        }
                    },
                    // The on-photo review sends the user here to mark their front door (audit B2).
                    startInDoorMode = entry.arguments?.getBoolean(Routes.ARG_DOOR_MODE) ?: false,
                )
            }
            composable(
                route = Routes.MARK_NORTH_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_FROM_REPORT) { type = NavType.BoolType; defaultValue = false },
                    navArgument(Routes.ARG_FROM_SCAN) { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val vm = sharedVm(nav, entry)
                // ⭐ North on the user's OWN plan, when they got here by scanning one. Decoded once
                // and remembered: the dial's model is a data class and an ImageBitmap compares by
                // identity, so a fresh decode per recomposition would invalidate the measure cache
                // the drag's smoothness depends on. Gated on the route flag rather than "is there a
                // photo lying around", so a picture from an earlier scan can never appear under a
                // home that was drawn by hand or reopened from the saved list.
                val scanHandover = koinInject<com.vastufirst.app.ui.scan.ScanReviewHandover>()
                val fromScan = entry.arguments?.getBoolean(Routes.ARG_FROM_SCAN) ?: false
                val planImage = remember(fromScan, scanHandover.data) {
                    if (fromScan) scanHandover.data?.decodeImage() else null
                }
                // ⚠ Which way out, and there are now three answers.
                //
                //  · **Drawn by hand, or the sample home** — this is still the last step, so it saves
                //    and pushes the REPORT. There is no score screen between the two (owner,
                //    10 Aug 2026: "After the North is marked, jump straight to Report screen").
                //  · **⭐ Arrived by SCAN** — North now comes FIRST (11 Aug 2026), so this leads to
                //    "Check what we read", whose rows can finally carry each room's direction and
                //    result because this screen has just supplied them. It does NOT save: the home
                //    is written when the flow actually ends, two screens further on.
                //  · **Opened from an already-read home's report** ("change which way North is") — it
                //    goes BACK to the report it came from. Pushing a second copy would put the report
                //    behind the report, so Back would land on the same screen again. The report reads
                //    North straight off the shared draft, so returning shows the new number and the
                //    re-sorted room list.
                val fromReport = entry.arguments?.getBoolean(Routes.ARG_FROM_REPORT) ?: false
                // ⭐ Opened from an already-read home's report, the dial is an EXPERIMENT until
                // confirmed (audit B5). Every dial move autosaves ~50 ms later — that is what keeps
                // the reading live — so before this, "just seeing" a different North had silently
                // rewritten the saved home by the time Back was pressed. Back (chevron AND system
                // gesture) now puts the entry value back; only the confirm button keeps the new North.
                val entryNorth = rememberSaveable { vm.north }
                val cancelExperiment: () -> Unit = {
                    if (fromReport && vm.north != entryNorth) vm.updateNorth(entryNorth)
                    nav.popBackStack()
                }
                if (fromReport) BackHandler(onBack = cancelExperiment)
                MarkNorthScreen(
                    vm = vm,
                    onRead = {
                        when {
                            fromReport -> { vm.save(); nav.popBackStack() }
                            // A scan checks its rooms next, and the check screen (or the door screen
                            // after it) is what saves and opens the report.
                            fromScan -> nav.go(Routes.SCAN_REVIEW)
                            else -> { vm.save(); nav.go(Routes.REPORT) }
                        }
                    },
                    // ⭐ The button must never promise a screen it does not open. On the scan path
                    // the next screen is the check, not the report.
                    nextIsCheck = fromScan && !fromReport,
                    onBack = cancelExperiment,
                    planImage = planImage,
                )
            }
            // The optional extras. Both ways out land back on the report, which re-reads the live
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
                    // Checkout is now reached FROM the report, so the report is already on the back
                    // stack — pop back to the one the reader was in, which recomposes unlocked.
                    // Pushing a second copy would leave the paywall's report stacked under it and
                    // make Back walk through a screen the reader already finished with.
                    // ⚠ popBackStack matches the DECLARED ROUTE PATTERN, not the address that was
                    // navigated to. The report's destination now carries an optional plan id, so the
                    // pattern is the one with the placeholder in it — passing the bare word would
                    // silently match nothing and push a second report every time.
                    if (!nav.popBackStack(Routes.REPORT_ROUTE, inclusive = false)) {
                        nav.navigate(Routes.REPORT) { popUpTo(Routes.UNLOCK) { inclusive = true }; launchSingleTop = true }
                    }
                })
            }
            // ⭐⭐ WHERE THE FLOW LANDS. North pushes this directly (10 Aug 2026); the free score
            // screen that used to sit between them is gone, and everything only it had came here.
            composable(
                route = Routes.REPORT_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_PLAN_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val vm = sharedVm(nav, entry)
                // Only when the saved-homes list sent an id. Arriving from the flow there is no id,
                // and loading one would pull a different home over the draft already on screen.
                val planId = entry.arguments?.getString(Routes.ARG_PLAN_ID)
                val scanHandover = koinInject<com.vastufirst.app.ui.scan.ScanReviewHandover>()
                LaunchedEffect(planId) {
                    if (planId != null) {
                        vm.loadById(planId)
                        // ⭐⭐ A HOME OPENED FROM THE SAVED LIST HAS NO PHOTOGRAPH — ever. The scan's
                        // picture is never written to disk (see ScanReviewData), so a saved home can
                        // only ever be showing one that is left over from a scan done earlier in the
                        // same session — i.e. somebody else's plan under this home's heading, in the
                        // paid report, with "change where the front door is" letting the reader mark
                        // a door on it. There is no case where keeping it is right, so it goes.
                        scanHandover.data = null
                    }
                }
                // ⭐ The scanned photograph, decoded once, and the rectangles each room was read
                // from. Present only for a home that arrived by scan — a hand-drawn one has no
                // photograph and the report falls back to its zone map. Gated on the handover rather
                // than "is there a picture lying around", for the same reason the North dial is: a
                // photo left over from an earlier scan appearing under someone else's home would be
                // a lie about whose plan is being read.
                val scanned = scanHandover.data
                val planImage = remember(scanned) { scanned?.decodeImage() }
                val planRooms = remember(scanned) {
                    scanned?.rooms?.let { com.vastufirst.app.ui.scan.planRoomsOf(it) }.orEmpty()
                }
                ReportScreen(
                    vm = vm,
                    onDone = { nav.goHome() },
                    // The pay bar on the report is the only route to checkout now.
                    onUnlock = { nav.go(Routes.UNLOCK) },
                    onEditNorth = { nav.go(Routes.markNorthFromReport()) },
                    // ⚠ Two different screens mark the same thing, and which one is right depends on
                    // how this home arrived. A scanned home marks its door ON THE PHOTOGRAPH (owner,
                    // 6 Aug 2026: marking the door "should happen only on this actual floor plan and
                    // not on the floor plan builder"); a home drawn by hand has no photograph, so it
                    // marks the door on the grid it was drawn on. Sending a drawn home to the photo
                    // screen would show it a blank rectangle.
                    onEditEntry = {
                        nav.go(
                            if (scanHandover.data != null) Routes.scanDoorFromReport()
                            else Routes.guidedGridForDoor(),
                        )
                    },
                    onAddDetails = { nav.go(Routes.MORE_DETAILS) },
                    // Recovery when rooms survived a process kill but the intent answer didn't:
                    // back to the first question, on the same shared draft, so nothing redraws.
                    onRestart = { nav.go(Routes.WELCOME) },
                    planImage = planImage,
                    planRooms = planRooms,
                )
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
