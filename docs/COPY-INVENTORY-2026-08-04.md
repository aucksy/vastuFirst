# VastuFirst user-visible string inventory (read-only audit, 4 Aug 2026)

Format: `- "string" | file:line | role | flags`
Flags: HONESTY = consent / refusal / payment honesty / accuracy caveat — may get SHORTER, never WEAKER.
DYNAMIC = contains runtime values. A11Y = spoken by screen reader only (not read visually).
Paths are relative to D:\Apps\VastuFirst\app\src\androidMain\kotlin\com\vastufirst\app\ unless noted.
DS = D:\Apps\VastuFirst\designsystem\src\commonMain\kotlin\com\vastufirst\designsystem\components\

---

## 1. Welcome (ui/welcome/WelcomeScreen.kt)

- "No sign-up · No phone number" | WelcomeScreen.kt:78 | caption | HONESTY (privacy claim)
- "See your home's Vastu — and exactly what to do about it." | WelcomeScreen.kt:86 | title
- "Upload your plan or draw it. Mark which way North is. Get your score and what to do — whether you're buying, building, or already living there." | WelcomeScreen.kt:92 | body
- ~~"Language" | section label~~ · ~~"English" | chip~~ · ~~"More languages are coming soon — the app
  is in English for now." | caption~~ — **all three DELETED 9 Aug 2026. This screen has no language
  copy at all and must not gain any: English only, permanently (`CLAUDE.md` §2e).**
- "What brings you here?" | WelcomeScreen.kt:110 | section label
- "I am buying a home" | WelcomeScreen.kt:118 | card title
- "Deciding between options" | WelcomeScreen.kt:118 | card caption
- "I am building a home" | WelcomeScreen.kt:119 | card title
- "The plan is not final yet" | WelcomeScreen.kt:119 | card caption
- "I already live here" | WelcomeScreen.kt:120 | card title
- "Looking for remedies" | WelcomeScreen.kt:120 | card caption
- "Continue" | WelcomeScreen.kt:124 | button
- "$text — coming soon" | WelcomeScreen.kt:149 | A11Y

## 2. Add home (ui/addhome/AddHomeScreen.kt)

- "Step 1 of 3" | AddHomeScreen.kt:44 | caption
- "Add your home" | AddHomeScreen.kt:46 | title
- "Place your rooms on a simple grid — or try a sample to see the whole flow first." | AddHomeScreen.kt:49 | body
- "Upload a plan" | AddHomeScreen.kt:61 | card title
- "Photo or PDF · we read the room names, you place them" | AddHomeScreen.kt:62 | card caption | HONESTY (claims names only, not placement)
- "Draw it on a grid" | AddHomeScreen.kt:66 | card title
- "Place room blocks by hand · stays on your phone" | AddHomeScreen.kt:67 | card caption | HONESTY (privacy claim)
- "Try a sample plan" | AddHomeScreen.kt:72 | card title
- "See the whole flow in ten seconds" | AddHomeScreen.kt:73 | card caption
- "You confirm every room yourself — nothing is scored until you say it's right." | AddHomeScreen.kt:86 | caption | HONESTY (no-auto-score claim)
- "SOON" | AddHomeScreen.kt:125 | tag (currently unreached — MethodCard soon defaults false)

## 3. Saved plans / Home (ui/home/HomeScreen.kt + ScoreChange.kt)

- "Your plans" | HomeScreen.kt:124 | title
- "Settings" | HomeScreen.kt:129 | A11Y
- "1 home couldn't be opened by this version. It's still saved — an update should bring it back." | HomeScreen.kt:141 | body | HONESTY (data-safety claim) DYNAMIC (plural variant :142)
- "No plans yet" | HomeScreen.kt:151 | empty title
- "Add your first floor plan to see its Vastu score." | HomeScreen.kt:152 | empty body
- "Still to finish" | HomeScreen.kt:170 | section label
- "Finished" | HomeScreen.kt:178 | section label
- "Add a home" | HomeScreen.kt:187 | button
- "Unfinished home" | HomeScreen.kt:223 | row title fallback
- "$rooms rooms so far · Updated …" | HomeScreen.kt:224 | row caption | DYNAMIC
- "Throw away this unfinished home" | HomeScreen.kt:235 | A11Y
- "Carry on" | HomeScreen.kt:241 | row label
- "Throw away this unfinished home?" | HomeScreen.kt:268 | dialog title
- "The $roomCount rooms you placed will be gone, and we can't bring them back. Your finished homes aren't affected." | HomeScreen.kt:270 | dialog body | DYNAMIC
- "Keep it" | HomeScreen.kt:276 | button
- "Throw it away" | HomeScreen.kt:283 | button
- "Got it" | HomeScreen.kt:323 | button
- "$intentLabel · Updated …" | HomeScreen.kt:337 | row caption | DYNAMIC
- "Rename ${plan.name}" | HomeScreen.kt:342 | A11Y
- "/10" | HomeScreen.kt:349 | caption
- "Rename this home" | HomeScreen.kt:375 | dialog title
- "Home name" | HomeScreen.kt:379 | field label
- "e.g. Dwarka flat" | HomeScreen.kt:380 | placeholder
- "Cancel" | HomeScreen.kt:384 | button
- "Save" | HomeScreen.kt:391 | button
- "${name}: X → Y" / "${name}: Y — unchanged" | ScoreChange.kt:74-75 | body | HONESTY (score-change disclosure) DYNAMIC
- "We changed a rule — your score is unchanged" | ScoreChange.kt:80 | card title | HONESTY
- "Your score has changed" | ScoreChange.kt:81 | card title | HONESTY
- "We changed a rule — some scores moved" | ScoreChange.kt:82 | card title | HONESTY
- (card reason comes from rule data, shown verbatim | HomeScreen.kt:310) | body | HONESTY DYNAMIC
- "Updated today" / "Updated yesterday" / "Updated N days ago" / "Updated on 3 Jul" | ui/common/RelativeTime.kt:32-37 | caption | DYNAMIC

## 4. Guided grid editor (ui/grid/GuidedGridScreen.kt)

Headings (:469-480):
- "Mark your front door" | GuidedGridScreen.kt:471 | title
- "These rooms aren't placed yet" | GuidedGridScreen.kt:477 | title | HONESTY (scan refusal follow-through)
- "Place your rooms" | GuidedGridScreen.kt:478 | title

Subtitles (:483-495):
- "Your home is outlined below. Tap the wall where your main entrance is." | GuidedGridScreen.kt:487 | body
- "Drag the room to move it, or pull a corner to resize." | GuidedGridScreen.kt:488 | body
- "Press the plan where this room goes. Slide to adjust, lift to place." | GuidedGridScreen.kt:489 | body
- "Pick a room below, then press the plan to place it." | GuidedGridScreen.kt:490 | body
- "We read these off your plan but couldn't tell where they go, so they're waiting in a row. Drag each one to where it really is." | GuidedGridScreen.kt:491-492 | body | HONESTY (refusal — could not place)
- "Touch a room and slide to move it, or add another below." | GuidedGridScreen.kt:493 | body

Restored-draft card:
- "Carrying on with your unfinished home" | GuidedGridScreen.kt:510 | card title
- "This is the home you started earlier, exactly as you left it. Carry on from here, or clear it and start this home again." | GuidedGridScreen.kt:514-515 | card body
- "Start this home again" | GuidedGridScreen.kt:519 | button

Drag chip:
- "Rooms can’t overlap" | GuidedGridScreen.kt:538 | overlay chip
- "${type} · WxH · Zone" (describe) | GuidedGridScreen.kt:226 | overlay chip | DYNAMIC

Compass labels:
- "NORTH" | GuidedGridScreen.kt:559 | caption
- "WEST" "SOUTH" "EAST" | GuidedGridScreen.kt:896-898 | caption

Plot size / palette:
- "Plot size" | GuidedGridScreen.kt:967 | section label
- "Narrower plot" / "Wider plot" / "Shallower plot" / "Deeper plot" | GuidedGridScreen.kt:973-986 | A11Y
- "$cols wide" / "$rows deep" | GuidedGridScreen.kt:975/983 | value | DYNAMIC
- "Add a room" | GuidedGridScreen.kt:990 | section label
- room chips = RoomType.label() (see §16)
- "Set the front door" / "Move the front door" | GuidedGridScreen.kt:1016 | button
- "Done placing door" | GuidedGridScreen.kt:957 | button
- "Placing: ${type}" | GuidedGridScreen.kt:1281 | mode bar | DYNAMIC
- "Stop placing ${type}" | GuidedGridScreen.kt:1290 | A11Y
- "Next — mark North" | GuidedGridScreen.kt:1026 | button

Shape section (:1076-1167):
- "Once your rooms are where they belong, we'll ask about any missing corners." | GuidedGridScreen.kt:1081 | caption
- "Is this part of your home?" | GuidedGridScreen.kt:1086 | card title
- "The $n squares marked on the plan are in the $zone and have no room on them yet. If your home is cut off there — an L-shape, or a corner that isn't yours — say so and we'll score the real shape instead of a full rectangle." | GuidedGridScreen.kt:1091-1096 | card body | HONESTY (accuracy — real shape vs rectangle) DYNAMIC
- "Yes — it's part of my home" | GuidedGridScreen.kt:1101 | button
- "No — my home is cut off there" | GuidedGridScreen.kt:1108 | button
- "Your home is cut off in the $cutZones" | GuidedGridScreen.kt:1118 | card title | DYNAMIC
- "We're scoring that real shape, so the missing-corner checks can run." | GuidedGridScreen.kt:1123 | card body | HONESTY (accuracy)
- "Start the shape again" | GuidedGridScreen.kt:1128 | button
- "There's an empty space inside your home" | GuidedGridScreen.kt:1137 | card title
- "It's surrounded by rooms, so it's part of your home either way. If it's an open courtyard, add it as a Courtyard — the centre of a home matters in Vastu." | GuidedGridScreen.kt:1142-1144 | card body
- "The empty squares between your rooms count as part of your home — usually right, since homes have passages between rooms." | GuidedGridScreen.kt:1157-1158 | caption | HONESTY (accuracy — what is being scored)
- "We're treating your home as a full rectangle. If a corner of your home is missing, leave that part of the grid empty and we'll ask about it." | GuidedGridScreen.kt:1163-1164 | caption | HONESTY (accuracy)

Selected-room panel (:1357-1441):
- "$w × $h · Zone, close to X and Y" | GuidedGridScreen.kt:1372-1378 | caption | HONESTY (on-the-line accuracy caveat) DYNAMIC
- "Room type" | GuidedGridScreen.kt:1388 | section label
- "Remove" / "Done" | GuidedGridScreen.kt:1398/1401 | buttons
- "Move" | GuidedGridScreen.kt:1404 | section label
- "Move left" / "Move up" / "Move down" / "Move right" | GuidedGridScreen.kt:1406-1409 | A11Y
- "Size" | GuidedGridScreen.kt:1415 | section label
- "Narrower" / "Wider" / "Shorter" / "Taller" | GuidedGridScreen.kt:1424-1440 | A11Y
- "W $w" / "H $h" | GuidedGridScreen.kt:1426/1437 | value | DYNAMIC

Tiles / door:
- "${type}, W by H cells, Zone" | GuidedGridScreen.kt:1195 | A11Y
- "Selected" / "Not selected" | GuidedGridScreen.kt:1223 | A11Y
- "D" | GuidedGridScreen.kt:1320 | door glyph
- "Front door on the ${side} wall" | GuidedGridScreen.kt:1313 | A11Y
- shortLabel: "WC" "Bath" "Bed" "Entry" "Court" | ui/grid/RoomTileLabel.kt:42-50 | tile label

## 5. Mark North (ui/marknorth/MarkNorthScreen.kt + NorthCheck.kt + Compass.kt)

- "Step 2 of 3" | MarkNorthScreen.kt:112 | caption
- "Which way is North?" | MarkNorthScreen.kt:114 | title
- "Drag the N dial, or use the slider. Everything else follows from this." | MarkNorthScreen.kt:116 | body
- "Use my phone's compass" | MarkNorthScreen.kt:238 | button
- "Point your phone" | MarkNorthScreen.kt:248 | card title
- "Hold the phone flat, screen up. Stand in your home and turn until the TOP of the phone points the same way as the TOP of your plan — the wall you drew along the top edge." | MarkNorthScreen.kt:251-252 | card body | DO-NOT-REWORD (the maths is written against this exact sentence — file comment :222-226)
- "Finding the compass…" | MarkNorthScreen.kt:259 | status
- "Pointing" | MarkNorthScreen.kt:271 | label
- "Your phone can't read the compass here — move away from anything metal or magnetic, or set North by hand with the dial below." | MarkNorthScreen.kt:283-284 | warning
- "The compass needs settling. Wave the phone slowly in a figure of eight, then read it again." | MarkNorthScreen.kt:286 | warning
- "Set North from this" | MarkNorthScreen.kt:293 | button
- "Close the compass" | MarkNorthScreen.kt:299 | button
- "This is your phone's magnetic compass. In India that is within about a degree of true North." | MarkNorthScreen.kt:305 | caption | HONESTY (accuracy caveat)
- "North is at" | MarkNorthScreen.kt:157 | label
- "Live score" | MarkNorthScreen.kt:161 | label
- "Check this before we score" | MarkNorthScreen.kt:191 | card title
- "With North where you have put it, $check." | MarkNorthScreen.kt:194 | card body | HONESTY (accuracy double-check) DYNAMIC
- "If that is not how your home really is, turn the dial above until it is. Every direction in your report depends on this one answer." | MarkNorthScreen.kt:199-200 | card body | HONESTY (accuracy)
- "Back" | MarkNorthScreen.kt:208 | button
- "Yes — read my home" / "Read my home" | MarkNorthScreen.kt:210 | button
- Legend: "Ideal" "Fine" "Not ideal" "Defect" | MarkNorthScreen.kt:322-325 | legend
- "Floor plan compass. North at $north degrees. … Drag to set North." | MarkNorthScreen.kt:137 | A11Y
- "North bearing, $north degrees" | MarkNorthScreen.kt:147 | A11Y
- NorthCheck claims: "your front door is on the ${zone} side" / "your ${room} is in the ${zone}" | NorthCheck.kt:53/63 | card body fragments | HONESTY DYNAMIC
- compassWord: "North" … "North-West" | Compass.kt:87 | value
- "Turn North one degree anticlockwise" / "…clockwise" | DS Fields.kt:123-124 | A11Y

## 6. Score — free result (ui/score/ScoreScreen.kt)

- "Reading your home…" | ScoreScreen.kt:139 | loading
- "Let's pick up where you left off" | ScoreScreen.kt:151 | guidance title
- "We couldn't find this plan on screen — it may have closed in the background. Head back to your saved plans to reopen it." | ScoreScreen.kt:152 | guidance body
- "Go to my plans" | ScoreScreen.kt:153 | button
- "Let's finish your plan" | ScoreScreen.kt:161 | guidance title
- "Add a few rooms and your front door, and we'll read your home." | ScoreScreen.kt:163 | guidance body (fallback; usually engine note)
- "Add my rooms" | ScoreScreen.kt:164 | button
- "Your result · free" | ScoreScreen.kt:191 | section label
- verdictLine (:336-339):
  - "Strong — a few refinements left." | ScoreScreen.kt:336 | body
  - "Workable, with real problems worth addressing — remedies can help." | ScoreScreen.kt:337 | body
  - "Workable, with real problems to fix while it is still on paper." | ScoreScreen.kt:338 | body
  - "Several core placements work against the tradition — remedies can help." | ScoreScreen.kt:339 | body
  - "Several core placements work against you — worth fixing now, on paper." | ScoreScreen.kt:340 | body
- "Zone map" | ScoreScreen.kt:204 | section label
- "Your plan with Vastu zones, North at $north degrees, Score X out of 10." | ScoreScreen.kt:211 | A11Y
- "Not quite your home? You can change it — the score follows straight away." | ScoreScreen.kt:223 | body
- "Change the rooms or the front door" | ScoreScreen.kt:229 | button
- "Change which way North is" | ScoreScreen.kt:237 | button
- "What this covers" | ScoreScreen.kt:249 | section label | HONESTY
- coverageLine (ui/details/MoreDetailsScreen.kt:183-185) | body | HONESTY (accuracy — what the number looked at) DYNAMIC:
  - "Your rooms, your front door and your home's shape. We haven't looked at your water tank, trees or the road outside."
  - "Your rooms, your front door, your home's shape — and the $total extra things you told us about."
  - "Your rooms, your front door, your home's shape, and $done of $total extra things you told us about."
- "Answer a few more and check more" | ScoreScreen.kt:255 | button
- "Biggest problems" | ScoreScreen.kt:263 | section label
- "No major defects found — a strong start." | ScoreScreen.kt:268 | body
- "See the full report" | ScoreScreen.kt:303/379 | button
- "Unlock the full report & remedies" | ScoreScreen.kt:364 | card title
- "ONE-TIME" | ScoreScreen.kt:371 | caption | HONESTY (payment)
- "The score out of 10 is VastuFirst's own way of summarising the report — a summary, not a measurement, and not part of the tradition. Vastu is traditional guidance for your own decisions, not a guaranteed outcome." | ScoreScreen.kt:314 | caption | HONESTY (accuracy caveat + disclaimer)
- "See all my plans" | ScoreScreen.kt:321 | button

## 7. Full report (ui/report/ReportScreen.kt + ReportText.kt)

Screen chrome:
- "Preparing your report…" | ReportScreen.kt:100 | loading
- "Go to my plans" | ReportScreen.kt:101 | button
- "Full report" | ReportScreen.kt:112 | section label
- "BUILDING" / "BUYING" / "ALREADY LIVING HERE" | ReportScreen.kt:390-392 | badge
- "What to do now" / "What to change" | ReportScreen.kt:116 | title
- "Ranked by how much each matters. Nothing is built yet — every layout change below is still free to make." | ReportScreen.kt:120 | body
- "This home is already built, so everything below is something you can do without moving a wall. Ranked by how much each matters." | ReportScreen.kt:121 | body
- "Walls can't move, so everything below is something you can do in the home as it stands. Ranked by how much each matters." | ReportScreen.kt:122 | body
- "8 zones" / "16 zones · soon" | ReportScreen.kt:139 | segmented control
- "The 16-zone school is a separate reading — coming in a later update." | ReportScreen.kt:145 | caption
- "Your front door" | ReportScreen.kt:151 | section label
- "What to do, most important first" / "What to change, most important first" | ReportScreen.kt:155 | section label
- "No defects to rank — the placements read well." | ReportScreen.kt:158 | body
- "Not ideal — worth knowing" | ReportScreen.kt:170 | section label
- "Already right — leave alone" | ReportScreen.kt:184 | section label
- "Couldn't check these yet" | ReportScreen.kt:197 | section label | HONESTY (refusal to claim coverage)
- "We didn't have the details to check these, so they are neither passed nor failed." | ReportScreen.kt:199 | caption | HONESTY
- "Vastu is a traditional practice. This is guidance for your own decisions — not a guaranteed outcome." | ReportScreen.kt:217 | disclaimer | HONESTY
- "Done — see all my plans" | ReportScreen.kt:225 | button
- "#$rank" | ReportScreen.kt:242 | caption | DYNAMIC
- "✦ Change the layout — free now" | ReportScreen.kt:345 | block heading
- "If it cannot move — remedies" | ReportScreen.kt:359 | block heading
- "Where the schools disagree" | ReportScreen.kt:411 | section label | HONESTY (disputes shown)
- "What your score uses" | ReportScreen.kt:426 | label | HONESTY (which reading the number takes)

ReportText.kt sentence factories (all report prose):
- prose(): "front door" "kitchen" "master bedroom" … "corridor" | ReportText.kt:33-53 | body fragments
- zoneList(): "the North, the East or the North-East" | ReportText.kt:56-63 | body fragment | DYNAMIC
- zoneMeaning(): "$sanskrit, the quarter of $deity, the element of $element. In the tradition: $domain" (also "X's own square" for centre) | ReportText.kt:70-84 | caption | DYNAMIC (content from zones.json)
- whyRight IDEAL: "This is exactly where the tradition puts a $room." | ReportText.kt:94 | body | DYNAMIC
- whyRight ACCEPTABLE (no ideal): "This is a direction the rule accepts for a $room." | ReportText.kt:96 | body
- whyRight ACCEPTABLE: "Not the first choice, but a direction the rule accepts for a $room — the first choice is $zones." | ReportText.kt:97-98 | body
- whyNotIdeal (no preference): "This rule names no preferred direction for a $room, so the $zone is neither right nor wrong for it." | ReportText.kt:115-116 | body | HONESTY (precision claim)
- whyNotIdeal: "The $zone is neither a direction this rule calls right for a $room nor one it rules out. It is simply not where the tradition puts one — that is $zones." | ReportText.kt:118-120 | body | HONESTY (precision claim)
- NOT_IDEAL_INTRO: "None of these is a defect and none of them is prohibited — each is simply not the direction the tradition prefers. They do count towards your score, which is why they are listed here rather than left out." | ReportText.kt:126-129 | body | HONESTY (they count toward score)
- padaStanding: "one the tradition counts favourable" / "one the tradition counts middling" / "one the sources read both ways" / "one the tradition counts unfavourable" | ReportText.kt:135-138 | body fragment
- padaBadge: "Favourable" / "Middling" / "Read both ways" / "Unfavourable" | ReportText.kt:150-153 | pill
- doorTitle: "Front door — $side wall" | ReportText.kt:157 | card title | DYNAMIC
- doorPlaceLine: "Position $n of 32 · $name — $domain" | ReportText.kt:160-165 | body | DYNAMIC
- doorUnnamedNote: "The 32 positions come from the classical sources, and this one is left unnamed in them. We say so rather than invent a name for it; the reading itself is unaffected." | ReportText.kt:174-175 | caption | HONESTY (refusal to invent)
- doorExplanation base: "The tradition lays 32 named positions around the edge of the plan, eight to a wall, and judges the main entrance by which one it stands on. Yours is $standing. The front door carries more weight in this reading than any single room." | ReportText.kt:183-185 | body | DYNAMIC
- doorExplanation (remediesOnly, unfavourable): "Only two or three of the eight positions on any wall are counted favourable, so where a door stands along the same wall matters a great deal — which is why the entrance carries this much weight in your reading." | ReportText.kt:197-199 | body
- doorExplanation (building, unfavourable): "Only two or three of the eight positions on any wall are counted favourable, so a door moved a few feet along the same wall can read quite differently. While the plan is still on paper, that is worth walking through with whoever is drawing it." | ReportText.kt:201-203 | body
- doorExplanation (wide door): "Your doorway is wide enough to stand across two of these positions; it has been read on the one it mostly sits on." | ReportText.kt:207-208 | body | HONESTY (how it was read)
- provenanceWords: "From classical text" / "Traditional practice" / "Modern practice" / "Schools disagree" | ReportText.kt:217-220 | tag words | HONESTY (source tagging)
- remedyLine: "$remedy ($provenanceWords)" | ReportText.kt:234 | body | DYNAMIC
- notCheckedLine: item.label (from rules JSON) | ReportText.kt:239 | body | HONESTY DYNAMIC
- unlockPreviewLines | ReportText.kt:269-287 | paywall bullets | HONESTY (payment — must not oversell) DYNAMIC:
  - "The full reading, with the reasoning behind every placement"
  - "All $n problems — $m you haven't seen — each with the whole reason and remedies for that problem in that direction"
  - "The whole reason behind each problem, and remedies for that problem in that direction"
  - "$n rooms rated not ideal — which, and why"
  - "The $n already right, and why · your front door by name · both readings where the schools disagree"
- remainingLine: "$m more problems · $n rooms rated not ideal · every reason and remedy in full" | ReportText.kt:296-299 | paywall caption | HONESTY DYNAMIC

## 8. Unlock / payment (ui/unlock/UnlockScreen.kt + billing/Billing.kt + billing/PlayBilling.kt)

- "Unlock the full report" | UnlockScreen.kt:99 | title
- "one-time · this home, forever" | UnlockScreen.kt:107 | caption | HONESTY (payment)
- "What you get" | UnlockScreen.kt:114 | section label
- "Every problem ranked, with the whole reason behind it" | UnlockScreen.kt:123 | bullet | HONESTY (payment claim)
- "Remedies for that problem in that direction — and where none exists, we say so" | UnlockScreen.kt:124 | bullet | HONESTY
- "The rooms rated not ideal, which the free score only counts" | UnlockScreen.kt:125 | bullet | HONESTY
- "Your front door by name, and the source behind every rule" | UnlockScreen.kt:126 | bullet | HONESTY
- "I already paid — restore it" | UnlockScreen.kt:150 | button
- "We couldn't find a purchase on this Google account." | UnlockScreen.kt:77 | error | HONESTY (payment)
- billingNotice | Billing.kt:75-84 | caption | HONESTY (payment — pinned by tests):
  - "You already have this report. Nothing further will be charged."
  - "No payment is taken in this version — the report unlocks on this device, free. Paid checkout arrives in a later update."
  - "We can't reach Google Play right now, so we can't take a payment. Check your connection and try again — nothing has been charged."
  - "One payment through Google Play. No subscription, and nothing renews."
- billingActionLabel | Billing.kt:87-92 | button | HONESTY (payment):
  - "See the full report" / "Unlock on this device — free" / "Try again" / "Pay $price and unlock"
- "₹699" (FALLBACK_PRICE) | Billing.kt:68 | price | HONESTY (payment)
- PlayBilling errors | PlayBilling.kt:136-199 | error | HONESTY (payment — every one asserts "nothing has been charged"):
  - "We couldn't reach Google Play to take a payment. Nothing has been charged — please try again."
  - "We couldn't open the payment screen. Nothing has been charged."
  - "We can't reach Google Play right now, so we can't check your purchases."
  - "Google Play couldn't take a payment on this device. Nothing has been charged."
  - "This report isn't on sale yet. Nothing has been charged."
  - "We couldn't reach Google Play. Check your connection and try again — nothing has been charged."
  - "The payment didn't go through, and nothing has been charged. Please try again."

## 9. Scan consent (ui/scan/ScanConsentScreen.kt) — ALL HONESTY (DPDP consent)

- "Before we start" | ScanConsentScreen.kt:48 | section label
- "Your plan leaves this phone" | ScanConsentScreen.kt:51 | title | HONESTY
- "Everything else in this app happens on your phone. Reading a plan is the one thing that doesn't, so we'd rather tell you exactly what happens than bury it." | ScanConsentScreen.kt:54-55 | body | HONESTY
- "What we send" / "The one picture or PDF you choose. Nothing else — no name, no phone number, no location." | ScanConsentScreen.kt:61 | fact pair | HONESTY
- "Who reads it" / "A plan-reading service called Groq, on computers in the United States." | ScanConsentScreen.kt:62 | fact pair | HONESTY
- "What we ask it" / "Only to read the room names printed on your plan. It is never asked anything about Vastu." | ScanConsentScreen.kt:63 | fact pair | HONESTY
- "What we keep" / "Nothing. Your plan is not stored by us, and it stays in your phone's own storage." | ScanConsentScreen.kt:64 | fact pair | HONESTY
- "Who works out your score" / "Your phone does, on its own, exactly as it does for a home you draw by hand." | ScanConsentScreen.kt:65 | fact pair | HONESTY
- "You can turn this off again at any time in Settings." | ScanConsentScreen.kt:70 | caption | HONESTY (withdrawable)
- "I agree — read my plan" | ScanConsentScreen.kt:75 | button | HONESTY
- "Rather not?" | ScanConsentScreen.kt:84 | card title
- "Draw your home on the grid instead. It takes a few minutes, nothing leaves your phone, and the score is exactly the same." | ScanConsentScreen.kt:87-88 | card body | HONESTY (real alternative claim)
- "Draw it on a grid instead" | ScanConsentScreen.kt:93 | button
- "Back" | ScanConsentScreen.kt:101 | button

## 10. Scan (ui/scan/ScanScreen.kt)

- "Step 1 of 3" | ScanScreen.kt:88 | caption
- "Back" | ScanScreen.kt:103 | button
Idle:
- "Upload your plan" | ScanScreen.kt:115 | title
- "We'll read the room names off your plan so you don't have to type them. You then place them on the grid and check everything before anything is scored." | ScanScreen.kt:118-119 | body | HONESTY (claims names only; nothing scored till checked)
- "Choose a PDF or picture" | ScanScreen.kt:127 | button
- "Take a photo of it now" | ScanScreen.kt:132 | button
- "This phone doesn't have a camera app we can open. Choose a picture or PDF above instead — it reads better anyway." | ScanScreen.kt:136-137 | error
- "What works best" | ScanScreen.kt:144 | card title
- "The PDF your architect or builder sent, or a clear screenshot of it." | ScanScreen.kt:146 | bullet
- "A flat, top-down plan — not a 3D picture of the finished home." | ScanScreen.kt:147 | bullet
- "Room names printed on the plan, like KITCHEN or BEDROOM." | ScanScreen.kt:148 | bullet
- "One home per picture. If the sheet shows several flats, crop to yours." | ScanScreen.kt:149 | bullet
- "If you photograph a printed plan, hold the phone flat above it. A picture taken at an angle is the one thing we struggle with." | ScanScreen.kt:152-153 | caption | HONESTY (limitation admission)
Reading:
- "Reading your plan…" | ScanScreen.kt:166 | loading
- "This usually takes a few seconds." | ScanScreen.kt:169 | caption
Done (Placed):
- "We read $n rooms" | ScanScreen.kt:186 | title | DYNAMIC
- "We've put them on the grid roughly where they appear on your plan. Check each one and move anything that isn't right — nothing is scored until you do." | ScanScreen.kt:187-188 | body | HONESTY ("roughly"; nothing scored till checked)
- "Check them on the grid" | ScanScreen.kt:191 | button
Done (Assisted) — ALL HONESTY (refusal to guess placement):
- "We found $n rooms" | ScanScreen.kt:202 | title | DYNAMIC
- TOO_MANY_ROOMS: "We read every room name clearly. But this plan has a lot of rooms and doesn't print their sizes, so we can't tell where each one sits. We haven't guessed. They're waiting on the grid in a row: drag each one to where it really is." | ScanScreen.kt:204-208 | body | HONESTY
- FLOOR_PLATE: "We read every room name clearly. But this sheet has a lift on it, so it shows a whole floor of the building rather than one home. Drag the rooms that are yours to where they belong." | ScanScreen.kt:213-216 | body | HONESTY
- TOO_FEW_PLACED: "We could read the room names clearly, but not where they sit on the plan — so we haven't guessed. They're waiting on the grid in a row: drag each one to where it really is." | ScanScreen.kt:217-220 | body | HONESTY
- UNIFORM_BOXES: "We could read the room names clearly, but the shapes we got back all came out identical, which means they weren't really measured. They're waiting on the grid in a row: drag each one to where it really is." | ScanScreen.kt:221-224 | body | HONESTY
- "Place them on the grid" | ScanScreen.kt:228 | button
Room list:
- "Tap any room to change what kind of room it is." | ScanScreen.kt:286 | caption
- "We read \"$label\" as $type, $size. Please check this one." | ScanScreen.kt:364-367 | A11Y
- "Change" | ScanScreen.kt:402 | row label
- "CHECK" | ScanScreen.kt:432 | pill
- "We also saw, but didn't add" | ScanScreen.kt:308 | card title | HONESTY (nothing silently dropped)
- "• $label — $reason" | ScanScreen.kt:311 | bullet | DYNAMIC
- "If any of these is a real room, add it yourself on the next screen." | ScanScreen.kt:315 | caption
- DropReason.plain | ScanScreen.kt:584-590 | body fragments | HONESTY:
  - "it isn't a room we score" / "we didn't recognise the name" / "it was too small to place on the grid" / "we couldn't make sense of its shape" / "it overlapped another room"
- "Try a different picture" | ScanScreen.kt:324 | button
Refusals — ALL HONESTY:
- "That looks like a 3D picture" / "It's a picture of the finished home rather than a plan. Please upload the flat, top-down floor plan — the one with the rooms drawn as boxes." | ScanScreen.kt:447-449 | guidance
- "That doesn't look like a floor plan" / "We couldn't find a floor plan in that picture. It might be an elevation, a brochure page or a site map. Please upload the flat, top-down plan." | ScanScreen.kt:450-452 | guidance
- "We can't see the room names" / "The rooms on this plan aren't named, or the names are too small to read. Please upload a plan with the rooms named — or draw your home instead, which takes a few minutes." | ScanScreen.kt:453-455 | guidance
- "There's more than one home on this sheet" / "This looks like a floor plate with several flats on it. Please crop the picture to just your own home and try again." | ScanScreen.kt:456-458 | guidance
- "We couldn't pick out any rooms" / "The plan looks right, but nothing on it came through as a room we recognise. A clearer picture often fixes it." | ScanScreen.kt:459-461 | guidance
- "Draw it on a grid instead" | ScanScreen.kt:467 etc. | button (repeated on every state)
Busy / offline / bad file / unconfigured:
- "Please try again in a minute." / "Please try again in about $n seconds." / "Please try again in about $n minutes." | ScanScreen.kt:478-480 | body | DYNAMIC
- "We're reading a lot of plans right now" / "Your plan is fine — we just need a moment. $wait You can also draw your home on the grid, which works straight away and needs no internet." | ScanScreen.kt:483-485 | guidance
- "Try again" | ScanScreen.kt:488 | button
- "We couldn't read your plan just now" / "Reading a plan needs an internet connection. Check you're online and try again — or draw your home on the grid, which works completely offline." | ScanScreen.kt:498-500 | guidance
- "We couldn't open that file" / "The picture or PDF wouldn't open. If it's a PDF, check it isn't password-protected, or take a screenshot of the page and upload that instead." | ScanScreen.kt:513-515 | guidance
- "This copy of the app can't read plans" / "Plan reading was left switched off when this version was built, so there's nothing behind the upload button. It isn't your plan or your phone. Drawing your home on the grid works normally and gives exactly the same score." | ScanScreen.kt:531-534 | guidance | HONESTY (stub admission)
- "Choose another file" | ScanScreen.kt:518 | button
Offline alternative (every state):
- "Rather not upload anything?" | ScanScreen.kt:559 | card title
- "Drawing your home on the grid takes a few minutes, stays entirely on your phone, and gives exactly the same score." | ScanScreen.kt:562-563 | card body | HONESTY (privacy + same-score claim)

## 11. Scan review on photo (ui/scan/ScanReviewScreen.kt)

- "Check what we read" | ScanReviewScreen.kt:120 | title
- "Your plan stays as you scanned it. Tap a room below — the tint shows roughly where it was read." | ScanReviewScreen.kt:124 | body | HONESTY ("roughly" — approximate tint)
- "The photo could not be shown" / "Every room we read is still listed below, and the grid can show the layout." | ScanReviewScreen.kt:173-174 | guidance
- "$n rooms read from your plan" | ScanReviewScreen.kt:180 | section label | DYNAMIC
- " · no size printed" | ScanReviewScreen.kt:189 | caption fragment | HONESTY (no invented size)
- "Shown" / "Check" / "›" | ScanReviewScreen.kt:195 | row trailing
- "These are my rooms — set North" | ScanReviewScreen.kt:207 | button
- "Something is wrong — fix on the grid" | ScanReviewScreen.kt:209 | button
- "Your plan, showing roughly where $type was read" / "Your scanned plan" | ScanReviewScreen.kt:141-142 | A11Y
- "show this room on the plan" | ScanReviewScreen.kt:190 | A11Y

## 12. Settings (ui/settings/SettingsScreen.kt)

- "Settings" | SettingsScreen.kt:140 | title
- "Preferences" | SettingsScreen.kt:144 | section label
- ~~"Language" / "English" | SettingsScreen.kt:147 | row~~ — **DELETED (4 Aug 2026), and permanent
  since 9 Aug 2026: English only (`CLAUDE.md` §2e). Never reinstate this row.**
- "Vastu reading" / "8 zones" | SettingsScreen.kt:151 | row
- "Check a scan" / "On the photo" / "On the grid" | SettingsScreen.kt:156-157 | row
- "Data & privacy" | SettingsScreen.kt:163 | section label
- "Your plans stay on this device" / "On" | SettingsScreen.kt:166 | row | HONESTY
- "Reading uploaded plans online" / "Allowed" / "Off" | SettingsScreen.kt:170-171 | row | HONESTY (withdrawable consent)
- "Privacy" | SettingsScreen.kt:175 | row
- "Honesty & sources" | SettingsScreen.kt:176 | row
- "Delete all my data" | SettingsScreen.kt:177 | row
- "No account, no phone number. Only a plan you upload ever leaves your phone." | SettingsScreen.kt:189 | caption | HONESTY
- "Something went wrong" | SettingsScreen.kt:199 | section label
- "The app closed unexpectedly last time you used it. Sending us what happened helps us fix it. It opens your email app so you can read it first, and it contains nothing about you or your homes." | SettingsScreen.kt:204-206 | body | HONESTY (privacy of crash report)
- "Send what went wrong" | SettingsScreen.kt:210 | button
- "No thanks" | SettingsScreen.kt:213 | button
- "Delete all your data?" | SettingsScreen.kt:232 | dialog title
- "This permanently removes every saved home from this device. It can't be undone." | SettingsScreen.kt:234 | dialog body
- "Keep my data" | SettingsScreen.kt:239 | button
- "Delete everything" | SettingsScreen.kt:245 | button
- crash email: "VastuFirst — something went wrong" / "Anything you were doing when it happened (optional):" / "--- technical detail, nothing personal ---" | SettingsScreen.kt:104-108 | email text | HONESTY

## 13. Honesty & sources (ui/legal/LegalScreen.kt) — ALL HONESTY

- "Honesty & sources" | LegalScreen.kt:40 | title
- "Vastu is a traditional practice. This is guidance for your own decisions — not a guaranteed outcome." | LegalScreen.kt:48 | disclaimer | HONESTY
- "How we tag every rule" | LegalScreen.kt:54 | section label
- "Traceable to a named source, with citation." | LegalScreen.kt:57 | tag row | HONESTY
- "Reasoned from the mandala; widely taught." | LegalScreen.kt:58 | tag row | HONESTY (contains jargon: "mandala")
- "20th-century. Honesty about age, not a warning." | LegalScreen.kt:59 | tag row | HONESTY
- "Two readings shown, neither chosen for you." | LegalScreen.kt:60 | tag row | HONESTY
- "We never use fear, urgency, or promises of wealth, health or fortune. Where the tradition contradicts itself, we show you both sides." | LegalScreen.kt:65 | body | HONESTY

## 14. Privacy (ui/legal/PrivacyScreen.kt) — ALL HONESTY (published policy; generated to docs/PRIVACY-POLICY.md)

- "Privacy" | PrivacyScreen.kt:43 | title
- "Your homes never leave your phone." | PrivacyScreen.kt:49 | card title | HONESTY
- "Everything you draw, every score and every report is worked out on this device and saved only on this device. There is no account, no sign-up and no phone number." | PrivacyScreen.kt:54-55 | card body | HONESTY
- "What we collect" + PRIVACY_COLLECT (49 words) | PrivacyScreen.kt:60/90-93 | section | HONESTY
- "The one thing that leaves your phone" + PRIVACY_UPLOAD (61 words) | PrivacyScreen.kt:61/95-100 | section | HONESTY
- "When the app crashes" + PRIVACY_CRASH (56 words) | PrivacyScreen.kt:62/102-106 | section | HONESTY
- "If you buy the full report" + PRIVACY_PAYMENT (38 words) | PrivacyScreen.kt:63/108-111 | section | HONESTY
- "Deleting everything" + PRIVACY_DELETE (43 words) | PrivacyScreen.kt:64/113-116 | section | HONESTY
- "Children" + PRIVACY_CHILDREN (14 words) | PrivacyScreen.kt:65/118-119 | section | HONESTY
- "Contact" + PRIVACY_CONTACT | PrivacyScreen.kt:66/121-122 | section | HONESTY
- "Last updated 1 August 2026." | PrivacyScreen.kt:70 | caption

## 15. A few more things (ui/details/MoreDetailsScreen.kt + SiteDetails.kt)

- "Optional" | MoreDetailsScreen.kt:67 | section label
- "A few more things" | MoreDetailsScreen.kt:69 | title
- "Your score so far comes from your rooms, your front door and your home's shape. Answer any of these and we can check more. Skip anything you don't know — we'll say it wasn't checked rather than guess." | MoreDetailsScreen.kt:72-74 | body | HONESTY (accuracy + refusal to guess)
- "Water tank on the roof" / "The overhead tank. Best in the south-west or west; the north-east is the one to avoid." | SiteDetails.kt:36-38 | question + help
- "Underground tank, sump or borewell" / "Water kept below ground. Best in the north-east; the south-west is the one to avoid." | SiteDetails.kt:41-43 | question + help
- "A big, heavy tree" / "A large tree close to the home. The north-east and the centre are the places it weighs on." | SiteDetails.kt:46-48 | question + help
- "A road pointing straight at your home" / "A road that ends at your home rather than passing it — a T-junction or a dead end." | SiteDetails.kt:51-53 | question + help
- "It's in the ${zone}." / "There isn't one." / "Which direction is it in?" | MoreDetailsScreen.kt:116-118 | state line | DYNAMIC
- "There isn't one" | MoreDetailsScreen.kt:138 | chip
- "Back" | MoreDetailsScreen.kt:92 | button
- "Save and see my score" | MoreDetailsScreen.kt:93 | button
- coverageLine → listed under Score (§6)

## 16. Shared vocabulary (ui/common/UiMappers.kt + design system)

- Zone.short(): "North" "North-East" "East" "South-East" "South" "South-West" "West" "North-West" "centre" | UiMappers.kt:39-43 | vocabulary
- RoomType.label(): "Entrance" "Kitchen" "Master" "Bedroom" "Pooja" "Toilet" "Bathroom" "Living" "Dining" "Stairs" "Study" "Store" "Guest" "Garage" "Balcony" "Basement" "Courtyard" "Utility" "Corridor" | UiMappers.kt:61-69 | vocabulary
- defectTitle: "$room — $zone" / "$zone — structure" | UiMappers.kt:72-75 | card title | DYNAMIC
- DoorSide.spoken(): "north" "east" "south" "west" | UiMappers.kt:57-59 | A11Y
- Verdict pills: "Ideal" "Fine" "Not ideal" "Defect" "Not assessed" | DS Pills.kt:56-60 | pill
- Provenance pills: "From classical text" "Traditional practice" "Modern practice" "Schools disagree" | DS Pills.kt:84-87 | pill | HONESTY
- spokenScore: "Score $x out of 10" | DS Score.kt:59 | A11Y
- "Change room type" / "Change room type. Currently $type." | ui/common/RoomTypePicker.kt:54/59 | button + A11Y
- "$type, the current room type" / "Change to $type" | RoomTypePicker.kt:84-85 | A11Y
- "N" | DS NorthDial.kt:151 | dial glyph

## 17. Engine notes shown verbatim in NotesStrip (engine\src\main\kotlin\com\vastufirst\engine\)

- "Add your home's layout to see its score." | PlanSanitizer.kt:43 | note
- "Skipped $droppedRooms room(s) we couldn't read clearly." | PlanSanitizer.kt:56 | note | HONESTY (dropped data disclosed) — "room(s)" is a programmer plural
- "We estimated the home's outline from your rooms — add the real outline for accurate corner and shape checks." | PlanSanitizer.kt:70 | note | HONESTY (accuracy)
- "Add your home's outline to see its score." | PlanSanitizer.kt:74 | note
- "The outline crossed itself, so we simplified it — please recheck it for accurate corner and shape checks." | PlanSanitizer.kt:86 | note | HONESTY
- "Mark your main entrance to include it in the score." | PlanSanitizer.kt:97 | note
- "More than one main entrance was marked — using the first." | PlanSanitizer.kt:100 | note | HONESTY (what was used)
- "Showing the classic 8-direction reading; other schools are coming soon." | VastuEngine.kt:55 | note
- "We couldn't fully read this plan — add or adjust your layout to try again." | VastuEngine.kt:64 | note | HONESTY (refusal)
- "This home sits about $n° off the compass; the score accounts for that." | VastuEngine.kt:125 | note | HONESTY (accuracy) DYNAMIC
- "Your home isn't a regular rectangle. A squarer, more regular shape is traditionally preferred; we've scored the rooms and entrance, and the corner checks need a clearer outline." | VastuEngine.kt:134 | note | HONESTY (accuracy — what was and wasn't scored)
- "This home is a bit long and narrow; a squarer shape is considered more balanced." | VastuEngine.kt:152 | note

## 18. Rules JSON prose (reaches the paid report verbatim via ReportText/ReportScreen)

Source: D:\Apps\VastuFirst\rules\src\main\resources\ruleset\
Not inline Kotlin, but it is most of what a paying reader actually reads:
- defects.json — 109 prose strings, ~3,239 words (explanation, layoutFix, remedyNote, notCheckedLabel per defect). Reading level well above first grade: "Ishanya — Shiva's quarter … the Purusha's head". | body | HONESTY-adjacent (remedyNote lines that say "no classical text prescribes a cure" are HONESTY)
- disputes.json — 50 prose strings, ~536 words (title, both readings, howWeScore) | body | HONESTY (both sides shown)
- rooms.json — 22 prose strings, ~804 words (rule rationales) | body
- remedies.json — 28 prose strings, ~677 words (remedy texts) | body
- zones.json — 4 prose strings, ~31 words (domains) | caption
Total ≈ 5,290 words of report prose living in data files.

---

## Rough totals (Kotlin UI strings, excluding rules JSON)

- Distinct user-visible Kotlin strings inventoried above: 312 quoted entries (measured) + ~20 grouped
  entries (privacy-policy constants, dynamic templates) ≈ 330 strings (≈290 visual + ≈40 screen-reader-only)
- Total words across them: 2,975 measured in quoted entries + ~285 in the grouped privacy constants
  ≈ 3,260 words (visual ≈2,960; A11Y ≈300)
- Rules JSON adds ≈5,290 more words to the paid report.
- Honesty-flagged strings: ≈120 (consent screen, privacy screen, legal screen, all billing copy, all scan refusal/assist copy, accuracy caveats, engine notes) — roughly 1,500 of the 3,450 words.
