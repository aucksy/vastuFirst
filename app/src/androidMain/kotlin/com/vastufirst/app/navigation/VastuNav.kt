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
import com.vastufirst.app.ui.report.READING_MILLIS
import com.vastufirst.app.ui.report.ReportScreen
import com.vastufirst.app.ui.scan.PlanReadingConsent
import com.vastufirst.app.ui.scan.ScanConsentScreen
import com.vastufirst.app.ui.scan.ScanRoute
import com.vastufirst.app.ui.scan.ScanStart
import com.vastufirst.app.ui.scan.ScanViewModel
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
 *
 * [onFirstScreenDecided] fires once the launch route has chosen where to land and navigated there.
 * MainActivity holds the opening splash on it, so the splash gives way to the first real screen
 * rather than to a frame of nothing.
 */
@Composable
fun VastuNavHost(onFirstScreenDecided: () -> Unit = {}) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LAUNCH) {

        // First frame decides where to land, so a fresh install never opens on an empty
        // "No plans yet" screen: returning users go to their saved plans, first-timers go straight
        // into the flow. The opening splash (MainActivity) stays up while the DB is read, so no
        // user sees this route draw — its brand mark below is the fallback for the case where the
        // splash has already let go. popUpTo removes LAUNCH so Back from the first real screen
        // exits the app.
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
                    // AFTER navigating, not after deciding: released a frame earlier, the splash
                    // would give way to this route's mark for one frame before the destination.
                    onFirstScreenDecided()
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
                // ⭐ Straight back into the unfinished home — the ONLY route that brings one back.
                // It lands inside the newplan graph without going through Welcome, because the user
                // has already answered those questions once; Back returns here.
                //
                // ⭐⭐ AND IT LANDS ON THE RIGHT SCREEN (owner, 17 Aug 2026: *"'Carry On' option from
                // Home Screen is taking user to manual grid"*). A home read off a PHOTOGRAPH picks
                // up at the North dial and goes on to its report; the grid editor is not part of
                // that flow and never was. A home drawn by hand — and a scan whose rooms could not
                // be placed, which genuinely has to be arranged by hand — still opens the editor,
                // because for those it is the right screen.
                onOpenDraft = { id, fromScan ->
                    nav.go(
                        if (fromScan) Routes.markNorthForDraft(id)
                        else Routes.guidedGridForDraft(id),
                    )
                },
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
                val newHomeHandover = koinInject<com.vastufirst.app.ui.scan.ScanPictureSlot>()
                LaunchedEffect(Unit) {
                    newHomeHandover.data = null
                    // ⭐⭐ AND THE PREVIOUS HOME LETS GO OF THE DRAFT (17 Aug 2026). This graph's
                    // ViewModel outlives Back, so a reader who finished one home and pressed Back to
                    // here was about to write their SECOND plan into the FIRST home's saved row —
                    // and to inherit its paid unlock. A no-op unless a finished home is really
                    // sitting in the draft; see NewPlanViewModel.beginNewHome.
                    vm.beginNewHome()
                }
                AddHomeScreen(
                    // ⭐ House or flat, asked here and nowhere else — see the screen's own note for
                    // why it cannot live on the welcome screen beside "what brings you here".
                    propertyType = vm.propertyType,
                    onPropertyTypeChange = { vm.propertyType = it },
                    onDrawGrid = { nav.go(Routes.GUIDED_GRID) },
                    // ⭐ The consent screen is not optional and not skippable: the scanner is only
                    // ever reached through it, or after it has already been answered once. Each card
                    // names its own picker, which the scan screen opens the moment it appears.
                    onScan = { nav.go(scanDoor(consent.isGranted(), ScanStart.PICK)) },
                    onPhotograph = { nav.go(scanDoor(consent.isGranted(), ScanStart.CAMERA)) },
                    onSample = {
                        val sample = SamplePlans.all.first()
                        vm.updateRooms(sample.rooms)
                        vm.updateDoor(sample.door)
                        vm.updateNorth(sample.north)
                        nav.go(Routes.MARK_NORTH)
                    },
                )
            }

            composable(
                route = Routes.SCAN_CONSENT_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_START) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val consent = koinInject<PlanReadingConsent>()
                // Which picker the reader chose on Add a home — carried through, so agreeing opens it.
                val start = entry.arguments?.getString(Routes.ARG_START) ?: ScanStart.PICK.name
                ScanConsentScreen(
                    onAgree = {
                        consent.set(true)
                        // Replace the gate in the back stack: having agreed, Back from the scanner
                        // should go to "Add home", not back through the consent screen.
                        nav.navigate(Routes.scanStarting(start)) {
                            popUpTo(Routes.SCAN_CONSENT_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onDrawInstead = { nav.go(Routes.GUIDED_GRID) },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(
                route = Routes.SCAN_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_START) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val planVm = sharedVm(nav, entry)
                val scanVm: ScanViewModel = koinViewModel()
                val pictureSlot = koinInject<com.vastufirst.app.ui.scan.ScanPictureSlot>()
                // Which picker "Add a home" asked for — opened the moment this screen appears.
                val start = entry.arguments?.getString(Routes.ARG_START)
                    ?.let { name -> ScanStart.entries.firstOrNull { it.name == name } }
                // ⭐⭐ THE READ IS KEPT THE MOMENT IT SUCCEEDS (owner, 16 Aug 2026: *"I also tried a
                // new home and left it on the screen right after scanning which shows the list of
                // rooms scanned and this one isn't even stored"*). A reader who presses Back from the
                // result still keeps the read: the home has its rooms, so it is on the saved list.
                //
                // A refusal is deliberately NOT accepted: there is nothing in it to keep. "Show me
                // what you read" replaces the state with the alternative reading, which arrives here
                // as an ordinary outcome.
                //
                // ⭐ AND THE PHOTOGRAPH GOES INTO THE SLOT HERE TOO, for a placed read (30 Sep 2026).
                // North is now set on this very screen, on this picture — it used to be written only
                // on the way to the North screen. A read that could not be placed hands over NOTHING
                // and wipes whatever an earlier read left, or the home about to be arranged by hand
                // would inherit the previous plan's photograph.
                val scanState = scanVm.state
                LaunchedEffect(scanState) {
                    val outcome = (scanState as? com.vastufirst.app.ui.scan.ScanUiState.Done)?.outcome
                        ?: return@LaunchedEffect
                    if (outcome is com.vastufirst.shared.scan.ScanOutcome.Refused) return@LaunchedEffect
                    planVm.acceptScan(outcome, scanVm.lastImage?.bytes)
                    pictureSlot.data = (outcome as? com.vastufirst.shared.scan.ScanOutcome.Placed)?.let {
                        com.vastufirst.app.ui.scan.ScanPicture(imageBytes = scanVm.lastImage?.bytes, rooms = it.rooms)
                    }
                }
                // Decoded once per picture: the dial's model compares its image by identity, so a
                // fresh decode per recomposition would restart the drag's measure cache.
                val photo = remember(pictureSlot.data) { pictureSlot.data?.decodeImage() }
                val analysis by planVm.analysis.collectAsStateWithLifecycle()
                ScanRoute(
                    vm = scanVm,
                    startWith = start,
                    // ⭐ North, on the result itself, for a placed read — the same home the report will
                    // show, from the same shared draft the North screen would have used.
                    north = com.vastufirst.app.ui.scan.NorthOnResult(
                        rooms = planVm.rooms,
                        north = planVm.north,
                        analysis = analysis,
                        cols = planVm.gridCols,
                        rows = planVm.gridRows,
                        planImage = photo,
                        onNorthChange = planVm::updateNorth,
                        doorKnown = planVm.door != null,
                        hintPulse = true,
                    ),
                    onUseRooms = { outcome ->
                        // ⚠ The hand-over itself is NewPlanViewModel.acceptScan — one function, called
                        // from here and from the effect above. It is idempotent, so this tap is a no-op
                        // when the effect has already taken this very reading.
                        planVm.acceptScan(outcome, scanVm.lastImage?.bytes)
                        // ⭐⭐ A placed scan NEVER opens the editor (owner, 6 Aug 2026), and since
                        // 30 Sep 2026 its North has just been set on this screen — so the next step is
                        // the front door when the plan did not name its own entrance, and otherwise the
                        // report itself.
                        //
                        // ⚠ The `else` is the one route left from a scan into the editor, and it is not
                        // a preference: it is a read whose rooms could NOT be placed, so somebody has to
                        // supply the geometry.
                        if (outcome is com.vastufirst.shared.scan.ScanOutcome.Placed) {
                            pictureSlot.data = com.vastufirst.app.ui.scan.ScanPicture(
                                imageBytes = scanVm.lastImage?.bytes,
                                rooms = outcome.rooms,
                            )
                            if (planVm.door != null) {
                                // The last step of the flow saves the home and opens its report. The
                                // pop-first guard: a reader who pressed Back from their report and taps
                                // on again returns to the one report rather than stacking a second.
                                planVm.save()
                                if (!nav.popBackStack(Routes.REPORT_ROUTE, inclusive = false)) {
                                    nav.go(Routes.REPORT)
                                }
                            } else {
                                nav.go(Routes.SCAN_DOOR)
                            }
                        } else {
                            pictureSlot.data = null
                            nav.go(Routes.GUIDED_GRID)
                        }
                    },
                    onDrawInstead = { nav.go(Routes.GUIDED_GRID) },
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
                val handover = koinInject<com.vastufirst.app.ui.scan.ScanPictureSlot>()
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
                    // ⭐ In the flow this is the LAST step — North was marked on the scan result just
                    // before it — so it saves the home and opens the report.
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
                val draftHandover = koinInject<com.vastufirst.app.ui.scan.ScanPictureSlot>()
                ResumeDraft(vm = vm, id = draftId, handover = draftHandover)
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
                    // A drawn home's report opens this editor on its door step ("Change front door").
                    startInDoorMode = entry.arguments?.getBoolean(Routes.ARG_DOOR_MODE) ?: false,
                )
            }
            composable(
                route = Routes.MARK_NORTH_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_FROM_REPORT) { type = NavType.BoolType; defaultValue = false },
                    navArgument(Routes.ARG_DRAFT_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val vm = sharedVm(nav, entry)
                // ⭐ Present ONLY when the reader tapped "Carry on" on an unfinished home that came
                // off a photograph — see Routes.markNorthForDraft. Every other way here arrives with
                // no id and the draft already on screen.
                val northDraftId = entry.arguments?.getString(Routes.ARG_DRAFT_ID)
                val pictureSlot = koinInject<com.vastufirst.app.ui.scan.ScanPictureSlot>()
                ResumeDraft(vm = vm, id = northDraftId, handover = pictureSlot)
                val fromReport = entry.arguments?.getBoolean(Routes.ARG_FROM_REPORT) ?: false
                // ⭐⭐ North on the reader's OWN plan whenever this home has one — resumed from the
                // saved-homes list (owner, 16 Aug 2026: *"Do A"*) and, since 30 Sep 2026, opened from
                // that home's report too. See [northShowsPhoto] for why the slot can be trusted.
                //
                // Decoded once and remembered: the dial's model compares its image by identity, so a
                // fresh decode per recomposition would invalidate the measure cache the drag's
                // smoothness depends on.
                val planImage = remember(fromReport, northDraftId, pictureSlot.data) {
                    if (northShowsPhoto(fromReport = fromReport, fromDraft = northDraftId != null)) {
                        pictureSlot.data?.decodeImage()
                    } else {
                        null
                    }
                }
                // ⚠ Which way out, and there are two answers.
                //
                //  · **Drawn by hand, the sample, or a resumed scanned home** — this is the last step,
                //    so it saves and pushes the REPORT (owner, 10 Aug 2026: "After the North is
                //    marked, jump straight to Report screen"). A first scan no longer comes here at
                //    all: its North is set on the scan result.
                //  · **Opened from an already-read home's report** ("Change North") — it goes BACK to
                //    the report it came from, which reads North straight off the shared draft.
                //
                // ⭐ Opened from a report, the dial is an EXPERIMENT until confirmed (audit B5). Every
                // dial move autosaves ~50 ms later, so Back (chevron AND system gesture) puts the entry
                // value back; only the confirm button keeps the new North.
                val entryNorth = rememberSaveable { vm.north }
                val cancelExperiment: () -> Unit = {
                    if (fromReport && vm.north != entryNorth) vm.updateNorth(entryNorth)
                    nav.popBackStack()
                }
                if (fromReport) BackHandler(onBack = cancelExperiment)
                MarkNorthScreen(
                    vm = vm,
                    onRead = {
                        vm.save()
                        if (fromReport) nav.popBackStack() else nav.go(Routes.REPORT)
                    },
                    onBack = cancelExperiment,
                    planImage = planImage,
                    // The button names where it goes: back to the report it came from.
                    returnsToReport = fromReport,
                )
            }
            // The optional extras, offered on the report. "Done" lands back on it, and the report
            // re-reads the live analysis — so an answer given here shows up in the number at once.
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
            // ⭐⭐ WHERE EVERY PATH LANDS: from the scan result or the front-door screen for a read
            // home, from North for a drawn one. There is no free score screen and no checking screen
            // in between (10 Aug and 30 Sep 2026).
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
                val scanHandover = koinInject<com.vastufirst.app.ui.scan.ScanPictureSlot>()
                LaunchedEffect(planId) {
                    if (planId != null) {
                        vm.loadById(planId)
                        // ⭐⭐ A HOME OPENED FROM THE SAVED LIST HAS NO PHOTOGRAPH — ever. The scan's
                        // picture is never written to disk (see ScanPicture), so a saved home can
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
                // ⭐⭐ THE FRONT DOOR, ON THE REPORT'S OWN PICTURE (owner, 18 Aug 2026). Worked out
                // by the same function the front-door screen uses, so the mark lands in the identical
                // place on both and cannot drift. Null for a home drawn by hand — that report shows
                // the zone map, which has never carried a door and is not the place to start.
                val reportDoorAtPage = remember(vm.door, scanned) {
                    val marked = vm.door
                    val rooms = scanned?.rooms
                    if (marked == null || rooms.isNullOrEmpty()) null
                    else com.vastufirst.app.ui.scan.doorMarkerOnPage(marked, rooms)
                }
                // ⭐ WHERE THAT DOOR CAME FROM — the note "Check what we read" used to carry, now
                // behind the i on the report's "Your front door" (30 Sep 2026). Derived on every
                // recomposition, never held as a flag: a reader who moved the door and came back must
                // not be told "we read it from your plan" about a door they placed themselves.
                val doorNote = remember(vm.door, vm.rooms, scanned) {
                    val marked = vm.door
                    if (marked == null || scanned == null) {
                        null
                    } else {
                        val read = com.vastufirst.app.ui.newplan.frontDoorRead(vm.rooms)
                        val ours = read?.door == marked
                        com.vastufirst.app.ui.report.frontDoorProvenance(
                            door = marked,
                            doorFromCaption = read?.fromCaption?.takeIf { ours },
                            doorIsOurs = ours,
                        )
                    }
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
                    doorAtPage = reportDoorAtPage,
                    // ⭐⭐ NO "READING YOUR HOME" WHEN THE READING IS ALREADY DONE (owner, 17 Aug
                    // 2026: *"If I am opening a saved home which has already generated its report,
                    // why is there 'Reading your home' animation"*). He is right, and the honest
                    // answer is that the beat is there to cover the hand-off at the END of the flow
                    // — the engine takes about fifty milliseconds, so without it a reader who taps
                    // "read my home" sees a flash. Reopening a home from the saved list is not that
                    // moment: the home was read days ago, and replaying the bar every time makes
                    // the app feel slower than it is. An id means "opened from the list".
                    introMillis = if (planId != null) 0L else READING_MILLIS,
                    doorNote = doorNote,
                )
            }
        }
    }
}

/**
 * ⭐ MAY THE NORTH DIAL DRAW THIS HOME'S OWN PHOTOGRAPH? — the rule, in one place so a test can hold it.
 *
 * The photograph comes from the one hand-over slot, and every door into a DIFFERENT home empties that
 * slot (starting a home, opening a saved one, resuming an unfinished one), so whatever is in it
 * belongs to the home on screen. This decides only which ways into the dial are allowed to draw it.
 *
 * ⚠ "Change North" on a report was missing from it until 30 Sep 2026: a scanned home's reader who
 * corrected North from their report was handed our redrawn squares instead of their own plan — the one
 * screen the photo flow exists to keep them from. A home drawn by hand has nothing in the slot, so it
 * still gets its zone map.
 */
internal fun northShowsPhoto(fromReport: Boolean, fromDraft: Boolean): Boolean =
    fromReport || fromDraft

/**
 * Where "Upload a plan" and "Photograph your plan" go: the privacy card first, the first time only,
 * then the scan screen, which opens the chosen picker the moment it appears.
 */
private fun scanDoor(consentGiven: Boolean, start: ScanStart): String =
    if (consentGiven) Routes.scanStarting(start.name) else Routes.scanConsentThen(start.name)

/** Navigate, debounced: a fast double-tap can't push two copies of the same destination (§B6). */
private fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }

/** Leave the guided-grid flow for the saved-plans list, clearing the flow so Back doesn't re-enter
 *  it. A first-time user (sent straight into the flow by LAUNCH) otherwise has no path to Home (§A2). */
private fun NavHostController.goHome() = navigate(Routes.HOME) {
    popUpTo(Routes.NEWPLAN_GRAPH) { inclusive = true }
    launchSingleTop = true
}

/**
 * ⭐⭐ BRING ONE UNFINISHED HOME BACK — the one piece both doors into a resumed home use.
 *
 * There are exactly two: the North dial (a home read off a photograph) and the grid editor
 * (a home drawn by hand, or a read whose rooms could not be placed). They used to hold a copy each
 * of "empty the picture slot, then resume", and a copy each is how the two came to disagree about
 * what a resumed home is allowed to show. One function cannot drift.
 *
 * It does two things, in this order, and the order is the safety:
 *
 *  1. **Empty the slot, then ask for the home.** Whatever picture is in there belongs to the last
 *     plan that was read, and every door into a DIFFERENT home has to close it (the rule v0.12.0
 *     established after one property's photograph appeared under another's heading, with "change
 *     where the front door is" inviting a tap on somebody else's flat).
 *  2. **Publish this home's own picture when it arrives.** [NewPlanViewModel.resumeDraft] reads from
 *     disk, so it lands a few frames later. Publishing is gated on the ViewModel confirming it now
 *     holds THIS id — not merely on "a resume was asked for" — so the window in which the previous
 *     home's photograph could be published under this one's name does not exist.
 *
 * A home with no stored photograph (every hand-drawn one) publishes nothing, and every screen falls
 * back to the zone map exactly as it did before photographs were kept at all.
 */
@Composable
private fun ResumeDraft(
    vm: NewPlanViewModel,
    id: String?,
    handover: com.vastufirst.app.ui.scan.ScanPictureSlot,
) {
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        handover.data = null
        vm.resumeDraft(id)
    }
    LaunchedEffect(id, vm.draftId, vm.scanPhoto, vm.scanRooms) {
        if (id == null || vm.draftId != id) return@LaunchedEffect
        val photo = vm.scanPhoto
        val rooms = vm.scanRooms
        handover.data = if (photo != null || rooms.isNotEmpty()) {
            com.vastufirst.app.ui.scan.ScanPicture(imageBytes = photo, rooms = rooms)
        } else {
            null
        }
    }
}

/** The NewPlanViewModel scoped to the whole "newplan" graph, so every step shares one draft. */
@Composable
private fun sharedVm(nav: NavHostController, entry: NavBackStackEntry): NewPlanViewModel {
    val parent = remember(entry) { nav.getBackStackEntry(Routes.NEWPLAN_GRAPH) }
    return koinViewModel(viewModelStoreOwner = parent)
}
