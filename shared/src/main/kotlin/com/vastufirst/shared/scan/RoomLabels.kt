// RoomLabels.kt — printed plan captions → the app's 19 `RoomType` values.
//
// ⭐ NO NEW ROOM TYPES. Owner decision D1 (docs/SCAN-PLAN-READING-PLAN.md §3j), confirmed against
// the engine: a genuinely new type would need a Vastu weight plus ideal/acceptable/prohibited zones
// plus provenance — that is an EXPERT RULING (Product PRD §13), not something to invent. A synonym
// table needs none of it: no engine change, no rules change, no score movement. It ships as data
// with tests.
//
// And a sub-area inside a room is invisible to scoring anyway: the only place area enters the engine
// is Brahmasthan encroachment, which measures a room's OWN footprint against the centre, never its
// internal subdivisions. A dressing area inside a bedroom is floor the bedroom already occupies — so
// it is dropped, not typed.
//
// ⚠ REGEX DISCIPLINE (learned the hard way on another app: a `(?U)` flag silently broke digit
// capture on Android for five releases while every JVM test stayed green). Android's regex engine is
// not byte-identical to the desktop JVM for Unicode-aware classes, and this module is tested on the
// JVM but RUNS on Android. So: explicit ASCII classes only — `[0-9]`, `[A-Z]` — never `\d`, never
// `\w`, never `(?U)`. Safe here because owner decision D4 settled that plans are always in English.
package com.vastufirst.shared.scan

import com.vastufirst.shared.RoomType

/** What a printed caption resolved to. */
sealed interface LabelMatch {
    /** A room type, with [loose] true when it matched a substring rather than the whole caption. */
    data class Room(val type: RoomType, val loose: Boolean) : LabelMatch

    /** Deliberately not a room: a sub-area, a duct, a lift. Dropped with [DropReason.NOT_HABITABLE]. */
    data object NotHabitable : LabelMatch

    /** Nothing matched. NOT guessed — a wrong room type moves a score the customer paid for. */
    data object Unknown : LabelMatch
}

object RoomLabels {

    /** Captions that are a whole building unit, not a room — the multi-unit-sheet signature. */
    private val UNIT_WORDS = setOf("UNIT", "FLAT", "APARTMENT", "APT", "BLOCK", "TOWER", "TYPE", "PLOT")

    /**
     * Not habitable, or a sub-area of a room that already covers the floor. Checked as whole-caption
     * matches first, then as substrings — every key here is unambiguous enough to survive that
     * (see [DROP_CONTAINS]).
     */
    private val DROP_EXACT = setOf(
        "DRESS", "DRESSING", "DRESS AREA", "DRESSING AREA", "DRESSING ROOM",
        "WALK IN CLOSET", "WALK IN WARDROBE", "WALK IN ROBE", "WARDROBE", "CLOSET",
        "DUCT", "ELECTRICAL DUCT", "PLUMBING DUCT", "SHAFT", "LIFT", "LIFT LOBBY",
        "ELEVATOR", "AC LEDGE", "LEDGE", "OTS", "VOID", "OPEN TO BELOW",
        // Site features and furniture read off real plans (the corpus audit): a lawn and a pathway
        // are ground outside the home, and a crockery unit is a cabinet drawn on the plan. Naming
        // them here means the drop says "not a room we score" instead of the dishonest "we didn't
        // recognise the name" — we recognised it fine.
        "LAWN", "PATHWAY", "CROCKERY UNIT",
    )

    /**
     * Substrings that condemn a caption wherever they appear. Kept deliberately short and
     * unambiguous: none of these words appears inside the name of a habitable room.
     * ⚠ Consequence, accepted and documented: `LIFT / STAIRCASE` (a building's service core) drops
     * rather than becoming a STAIRCASE. Such sheets are multi-unit and refused upstream anyway.
     */
    private val DROP_CONTAINS = listOf(
        "DRESSING", "DRESS AREA", "WARDROBE", "CLOSET", "DUCT", "SHAFT", "LIFT", "ELEVATOR", "LEDGE",
    )

    /**
     * Whole-caption synonyms. Everything here is a real caption style seen on Indian plans —
     * `SER ROOM`, `ATT. TOILET`, `PUJA SPACE`, `SIT OUT` — read verbatim off the 30-plan corpus
     * (§3h) rather than imagined.
     */
    private val EXACT: Map<String, RoomType> = buildMap {
        fun put(type: RoomType, vararg names: String) = names.forEach { put(it, type) }

        put(
            RoomType.ENTRANCE,
            "ENTRANCE", "ENTRY", "FOYER", "VESTIBULE", "PORCH ENTRY", "MAIN ENTRANCE",
            // ⚠ Must be matched EXACTLY, ahead of the plan-aware LOBBY rule — an entrance lobby is a
            // foyer whatever else the plan contains. `ENT. LOBBY` is how the corpus prints it.
            "ENTRANCE LOBBY", "ENTRY LOBBY", "ENT LOBBY",
        )
        put(RoomType.KITCHEN, "KITCHEN", "KIT", "MODULAR KITCHEN", "OPEN KITCHEN", "KITCHEN AREA")
        put(
            RoomType.MASTER_BEDROOM,
            "MASTER BEDROOM", "MASTER BED ROOM", "MASTER BED", "MBR", "M BEDROOM", "MASTER",
            // `Masterbed 360X370` — a hand-drawn plan's caption, read verbatim by the model.
            "MASTERBED",
        )
        put(
            RoomType.BEDROOM,
            "BEDROOM", "BED ROOM", "BED", "SER ROOM", "SERVANT ROOM", "SERVANT", "MAID ROOM",
            "KIDS ROOM", "KIDS BEDROOM", "CHILDREN ROOM", "CHILD BEDROOM",
            // Read off real plans in the corpus audit: `BED RM.-01`, a bare `KIDS`, `SERV. RM`.
            // Each cleans to the form here (dots deleted, the index a measurement token).
            "BED RM", "KIDS", "SERV RM", "SERV ROOM",
            // `MAIDS RM.` — the client's Tower E&F sheet (plan doc §3o): every reader transcribed
            // it faithfully and this table discarded it, deleting a scored bedroom from a paid
            // report. The plural was the whole gap — `MAID ROOM` was already here.
            "MAIDS RM", "MAIDS ROOM", "MAID RM", "MAIDS",
        )
        put(RoomType.GUEST_BEDROOM, "GUEST BEDROOM", "GUEST ROOM", "GUEST BED ROOM")
        put(
            RoomType.POOJA,
            "POOJA", "PUJA", "POOJA ROOM", "PUJA ROOM", "PUJA SPACE", "POOJA SPACE", "PRAYER ROOM",
            "MANDIR", "TEMPLE",
            // `Pojo 150X100` — the reader's faithful transcription of a hand-lettered pooja niche.
            // Whole-caption only, and it collides with nothing else a plan prints.
            "POJO",
        )
        put(
            RoomType.TOILET,
            "TOILET", "WC", "WATER CLOSET", "POWDER ROOM", "ATT TOILET", "ATTACHED TOILET",
            "COMMON TOILET", "WASHROOM", "WASH ROOM",
            // ⭐ `PWD` is how a luxury sheet abbreviates the guest powder room, printed with its own
            // size beside the entrance. We already knew POWDER ROOM and not this, so on the owner's
            // 19-room sheet the one toilet nearest the front door was dropped as an unreadable word
            // and never scored — and a toilet's position is among the heaviest things the engine
            // weighs. Exact-match only, so it cannot swallow a longer caption.
            "PWD",
            // `TOIL` — a caption truncated by the sheet itself, read verbatim (corpus audit).
            "TOIL",
        )
        put(RoomType.BATHROOM, "BATH", "BATHROOM", "BATH ROOM", "BATH TOILET")
        put(
            RoomType.LIVING,
            "LIVING", "LIVING ROOM", "HALL", "DRAWING", "DRAWING ROOM", "LOUNGE", "FAMILY ROOM",
            "LIVING DINING", "LIVING AND DINING", "LIVING CUM DINING", "DRAWING DINING",
            // ⭐ LOBBY. On an Indian flat plan this is the main room, not a passage — see [AMBIGUOUS].
            "LOBBY",
        )
        put(RoomType.DINING, "DINING", "DINING ROOM", "DINING SPACE", "DINING AREA")
        put(RoomType.STAIRCASE, "STAIR", "STAIRS", "STAIRCASE", "STAIR CASE", "STEPS")
        put(RoomType.STUDY, "STUDY", "STUDY ROOM", "OFFICE", "HOME OFFICE", "AV ROOM", "LIBRARY")
        put(RoomType.STORE, "STORE", "STORAGE", "STORE ROOM", "PANTRY")
        put(RoomType.GARAGE, "GARAGE", "CAR PARK", "CAR PARKING", "PARKING", "CAR PORCH")
        put(
            RoomType.BALCONY,
            "BALCONY", "BALC", "TERRACE", "OPEN TERRACE", "SIT OUT", "SITOUT", "PORCH",
            "VERANDAH", "VERANDA", "DECK", "UTILITY BALCONY",
            // `DRY BALC.` and `C Bal` — a dry (service) balcony and a common balcony, both read
            // verbatim off real sheets in the corpus audit.
            "DRY BALC", "DRY BALCONY", "C BAL",
        )
        put(RoomType.BASEMENT, "BASEMENT", "CELLAR")
        put(RoomType.COURTYARD, "COURTYARD", "COURT YARD", "ATRIUM", "OPEN TO SKY")
        put(
            RoomType.UTILITY,
            "UTILITY", "WASH AREA", "WASH", "LAUNDRY", "SERVICE AREA", "UTILITY AREA",
            // `W area 224x270` and `W/area` — how hand-drawn plans abbreviate the wash area.
            "W AREA",
        )
        put(RoomType.CORRIDOR, "CORRIDOR", "PASSAGE", "HALLWAY", "GALLERY", "CIRCULATION")
    }

    /**
     * Captions that map to a real type but are genuinely ambiguous, so the user is asked to confirm
     * them. They come back [LabelMatch.Room] with `loose = true`, which puts a "CHECK" against that
     * room on the confirmation screen — the same treatment as a caption we only half-recognised.
     *
     * ⭐ **LOBBY is why this exists, and it is a scoring decision, not a wording one** — the engine
     * weights LIVING 1.5 against CORRIDOR 0.8.
     *
     * The owner scanned a Gurgaon 2BHK where `LOBBY 10'-3½"X14'-10½"` is the largest room in the flat,
     * drawn with sofas and a dining table, on a sheet whose own legend calls the unit *"2 Bedroom +
     * Drawing cum Dining Room"*. Calling that a corridor was plainly wrong.
     *
     * ⚠ **But the obvious fix — LOBBY means LIVING — was measured against the 30-plan corpus and is
     * wrong more often than the bug was.** Six of those plans print a lobby, and **four of the six also
     * print a separate living room**: `LOBBY 5100X1800` next to `LIVING 3925X5000` is a 5.1 m × 1.8 m
     * passage, exactly what the word usually means. Mapping every lobby to LIVING would have invented
     * a second living room in four plans out of six.
     *
     * ⭐ **What actually separates them is the rest of the plan**, which is why this needs
     * [LabelContext]: a lobby is the living room when the plan names no other one, and circulation
     * when it does. On the corpus that reads six of seven cases correctly, and the seventh — a plan
     * with both a living room and an unusually large lobby — is genuinely ambiguous to a human too,
     * and arrives flagged for the user either way.
     */
    private val AMBIGUOUS = setOf("LOBBY")

    /**
     * What the *rest* of the plan says, for the handful of captions that cannot be resolved alone.
     *
     * Built once per reply by [contextOf]. Keeping it explicit means [resolve] stays a pure function
     * of its inputs rather than reaching for hidden state — a caption resolves the same way every time
     * it is given the same plan around it.
     */
    data class LabelContext(val hasDedicatedLivingRoom: Boolean = false) {
        companion object {
            /**
             * Nothing known about the rest of the plan. A lone lobby then reads as the living room,
             * which is the right default for a single-room question: a plan that names a lobby and
             * nothing else has named its main room.
             */
            val NONE = LabelContext()
        }
    }

    /** Read the whole caption list once, so an ambiguous caption can be resolved against its plan. */
    fun contextOf(labels: List<String>): LabelContext {
        val hasLiving = labels.any { raw ->
            val cleaned = clean(raw)
            cleaned !in AMBIGUOUS && livingByName(cleaned)
        }
        return LabelContext(hasDedicatedLivingRoom = hasLiving)
    }

    /** True when this caption names a living room outright, by either matching strategy. */
    private fun livingByName(cleaned: String): Boolean {
        EXACT[cleaned]?.let { return it == RoomType.LIVING }
        if (cleaned in DROP_EXACT || DROP_CONTAINS.any { cleaned.contains(it) }) return false
        return CONTAINS.firstOrNull { cleaned.contains(it.first) }?.second == RoomType.LIVING
    }

    /**
     * Abbreviations and modifiers that are only safe as a WHOLE caption. `MASTER` is the one that
     * matters: as a substring it would turn `MASTER TOILET` — an attached bathroom — into a master
     * bedroom, and MASTER_BEDROOM carries weight 3.0 against TOILET's 2.5, so that is a scoring bug,
     * not a cosmetic one. The rest are prefixes of a longer name that is already matched exactly.
     */
    private val LOOSE_EXCLUDED = setOf("MASTER", "BED", "KIT", "MBR", "WC", "BALC", "WASH", "HALL")

    /**
     * Substring fallbacks, tried LONGEST KEY FIRST so `MASTER BEDROOM` can never be eaten by
     * `BEDROOM`, and `ATT TOILET` still lands on TOILET. Derived from [EXACT] so the two can never
     * drift — one table, two matching strategies. Equal-length keys are ordered alphabetically so
     * the result never depends on map iteration order.
     */
    private val CONTAINS: List<Pair<String, RoomType>> = EXACT.entries
        .filter { it.key !in LOOSE_EXCLUDED }
        .map { it.key to it.value }
        .sortedWith(compareByDescending<Pair<String, RoomType>> { it.first.length }.thenBy { it.first })

    /** `X` between two numbers is a dimension separator (`6750X4350`, `12 X 14`), not a letter. */
    private val DIMENSION_X = Regex("(?<=[0-9])[ ]*X[ ]*(?=[0-9])")
    private val FEET_INCH_MARKS = Regex("['\"‘’“”′″]")
    private val PARENTHETICAL = Regex("\\([^)]*\\)")

    /**
     * ⭐ An abbreviation full stop is DELETED, not turned into a space — and that one character was a
     * real bug.
     *
     * A plan prints the second toilet as `W.C`. Replacing the stop with a space made that `W C`, which
     * matches nothing, so the caption resolved to "unrecognised" and the room was dropped. On the
     * owner's Gurgaon 2BHK that silently removed one of the flat's two toilets from a paid score —
     * and a toilet is weighted 2.5, with its zone among the most consequential in Vastu.
     *
     * Deleting the stop instead is strictly better: `W.C` becomes `WC`, which is already in the table,
     * while `ATT. TOILET` still becomes `ATT TOILET` because the space after the stop is its own
     * character and survives.
     */
    private val ABBREVIATION_DOT = Regex("\\.")

    private val NON_ALPHANUMERIC = Regex("[^0-9A-Z]")

    /**
     * A token that is only a measurement: `1500`, `1500MM`, `12FT`. Dropped so it cannot stop a
     * caption matching by name.
     *
     * The unit suffixes are here because `BALCONY 1500MM WIDE` (seen in the corpus) otherwise cleans
     * to `BALCONY 1500MM`, which matches nothing exactly, resolves through the substring path, and
     * arrives asking the user to check a caption that could not be clearer.
     *
     * ⚠ ASCII classes only, never `\d` and never `(?U)` — this is tested on the JVM and runs on
     * Android, and a Unicode-aware digit class is exactly what silently broke number capture in
     * another app for five releases.
     */
    private val MEASUREMENT_TOKEN = Regex("^[0-9]+(MM|CM|M|FT|SQFT|SQM)?$")

    /**
     * Words that describe a room's caption without naming a room. Only what has actually been seen
     * printed: `BALCONY 5'-0" WIDE` was resolving through the substring path and arriving with a
     * "CHECK" flag against a caption that is perfectly clear.
     *
     * ⚠ Deliberately tiny. `AREA` is NOT here — `WASH AREA` and `DINING AREA` are real room names.
     */
    private val DESCRIPTOR_WORDS = setOf("WIDE")

    /**
     * Strip a printed caption back to its words: drop the parenthetical aside, the feet/inch marks,
     * the printed dimensions and any index suffix, then collapse whitespace.
     *
     * `"ATT. TOILET 1350X2250"` → `"ATT TOILET"` · `"BED ROOM 12'1\"X11'0\""` → `"BED ROOM"` ·
     * `"LIFT 1850X1850 (8 PERSON)"` → `"LIFT"` · `"BEDROOM-1"` → `"BEDROOM"` · `"1"` → `""`.
     *
     * ⚠ A caption that is only a number (a numbered-legend plan, §3j D2) cleans to the empty string
     * and resolves to [LabelMatch.Unknown]. That is correct: the legend key is resolved in the
     * *prompt*, so by the time a caption reaches here it is either a name or it is nothing.
     */
    fun clean(raw: String): String {
        var s = raw.uppercase()
        s = PARENTHETICAL.replace(s, " ")
        s = FEET_INCH_MARKS.replace(s, " ")
        s = ABBREVIATION_DOT.replace(s, "")
        s = DIMENSION_X.replace(s, " ")
        s = NON_ALPHANUMERIC.replace(s, " ")
        return s.split(' ')
            .filter { it.isNotEmpty() && !MEASUREMENT_TOKEN.matches(it) && it !in DESCRIPTOR_WORDS }
            .joinToString(" ")
    }

    /**
     * ⚠ `isFloorPlateLabel` lived here until 16 Aug 2026 — "a caption naming a LIFT means the sheet
     * shows a whole floor, not one home", used to hand the whole plan to the manual grid.
     *
     * It was checked against the four corpus sheets that name a lift and it is wrong on three: each
     * of plan-002, plan-003 and plan-004 is ONE large apartment with the tower's lift core drawn
     * beside it — one kitchen, one living room, one dining room apiece. The claim it rested on
     * ("no single-home plan names one") was simply false. The one genuine multi-home sheet,
     * plan-001, names UNIT-1 to UNIT-4 and is refused by [isUnitLabel] below, which is the test that
     * actually asks the question — is there more than one home on this page?
     *
     * A lift caption is still dropped as not habitable, so no lift is ever scored as a room.
     */
    /** True when this caption names a whole dwelling unit rather than a room (`UNIT-1`, `FLAT B`). */
    fun isUnitLabel(raw: String): Boolean {
        val words = clean(raw).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        // "UNIT", "UNIT A", "TYPE B" — a unit word, optionally followed by a single-letter index.
        if (words[0] !in UNIT_WORDS) return false
        return words.size == 1 || (words.size == 2 && words[1].length <= 2)
    }

    /**
     * Resolve one printed caption.
     *
     * Order matters and is deliberate: an EXACT room name wins outright (so a plan that literally
     * says `STAIRCASE` is a staircase), then an exact drop, then a substring drop, then a substring
     * room match. Nothing is ever guessed — an unmatched caption comes back [LabelMatch.Unknown] and
     * is surfaced to the user rather than typed at random.
     */
    fun resolve(raw: String, context: LabelContext = LabelContext.NONE): LabelMatch {
        val cleaned = clean(raw)
        if (cleaned.isEmpty()) return LabelMatch.Unknown

        // Resolved against the plan around it, and always flagged so the user has the last word.
        if (cleaned in AMBIGUOUS) return LabelMatch.Room(ambiguousType(context), loose = true)

        EXACT[cleaned]?.let { return LabelMatch.Room(it, loose = false) }
        if (cleaned in DROP_EXACT) return LabelMatch.NotHabitable
        if (DROP_CONTAINS.any { cleaned.contains(it) }) return LabelMatch.NotHabitable
        CONTAINS.firstOrNull { cleaned.contains(it.first) }?.let { (key, type) ->
            // A caption that only half-matched an ambiguous word ("LOBBY AREA") gets the same
            // plan-aware treatment as the bare word, so the two cannot disagree with each other.
            return LabelMatch.Room(if (key in AMBIGUOUS) ambiguousType(context) else type, loose = true)
        }
        return LabelMatch.Unknown
    }

    private fun ambiguousType(context: LabelContext): RoomType =
        if (context.hasDedicatedLivingRoom) RoomType.CORRIDOR else RoomType.LIVING

    /**
     * ⭐⭐ CAPTIONS THAT NAME A WAY IN — read to decide which wall the front door is on, and for
     * nothing else. **This never changes a room's [RoomType] and therefore never changes a score
     * by itself**: a porch stays a balcony, and is scored as one.
     *
     * ⚠ IT EXISTS BECAUSE THE FEATURE WAS MEASURED (11 Aug 2026, `tools/scan-eval/audit-entry.mjs`).
     * "Skip the question when the plan already says where the entrance is" fired on **7 of the 24**
     * recorded real plans that place their rooms. Every one of the seventeen refusals was the same
     * reason — *no room was typed as an entrance* — and **not one** was the geometry: the wall-reach
     * test and the corner tie-break refused **zero** plans between them, so loosening either would
     * have bought nothing. What the plans actually print is `PORCH`, `Porch 160x450`,
     * `VERANDAH 9'5"X4'6"` — the covered way in, which our table types as a balcony because that is
     * what it is. Reading those captions takes it to **13 of 24**.
     *
     * ⚠ **LOBBY IS DELIBERATELY ABSENT, and the number is why.** Adding it reaches 15 of 24 — and
     * then Green Court 336, read twice (clean sheet and branded sheet), puts the front door on two
     * DIFFERENT walls. A front door is the heaviest single input the engine weighs, so a reading
     * that cannot agree with itself about one home is worse than asking. `LOBBY` is already the
     * caption this file treats as ambiguous ([AMBIGUOUS]); it stays a question.
     *
     * ⚠ `CAR PORCH` / `CAR PARKING` is where the car lives, not where the person walks in — and it
     * is often on a different wall entirely. Excluded by name.
     */
    private val WAY_IN_WORDS = listOf(
        "ENTRANCE", "ENTRY", "FOYER", "VESTIBULE", "PORCH", "VERANDAH", "VERANDA",
    )

    /** True when this printed caption names the way into the home — see [WAY_IN_WORDS]. */
    fun namesAWayIn(raw: String): Boolean {
        val cleaned = clean(raw)
        if (cleaned.isEmpty()) return false
        if (cleaned.contains("CAR")) return false
        return WAY_IN_WORDS.any { cleaned.contains(it) }
    }
}
