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

        put(RoomType.ENTRANCE, "ENTRANCE", "ENTRY", "FOYER", "VESTIBULE", "PORCH ENTRY", "MAIN ENTRANCE")
        put(RoomType.KITCHEN, "KITCHEN", "KIT", "MODULAR KITCHEN", "OPEN KITCHEN", "KITCHEN AREA")
        put(
            RoomType.MASTER_BEDROOM,
            "MASTER BEDROOM", "MASTER BED ROOM", "MASTER BED", "MBR", "M BEDROOM", "MASTER",
        )
        put(
            RoomType.BEDROOM,
            "BEDROOM", "BED ROOM", "BED", "SER ROOM", "SERVANT ROOM", "SERVANT", "MAID ROOM",
            "KIDS ROOM", "KIDS BEDROOM", "CHILDREN ROOM", "CHILD BEDROOM",
        )
        put(RoomType.GUEST_BEDROOM, "GUEST BEDROOM", "GUEST ROOM", "GUEST BED ROOM")
        put(RoomType.POOJA, "POOJA", "PUJA", "POOJA ROOM", "PUJA ROOM", "PUJA SPACE", "POOJA SPACE", "PRAYER ROOM", "MANDIR", "TEMPLE")
        put(RoomType.TOILET, "TOILET", "WC", "WATER CLOSET", "POWDER ROOM", "ATT TOILET", "ATTACHED TOILET", "COMMON TOILET", "WASHROOM", "WASH ROOM")
        put(RoomType.BATHROOM, "BATH", "BATHROOM", "BATH ROOM", "BATH TOILET")
        put(
            RoomType.LIVING,
            "LIVING", "LIVING ROOM", "HALL", "DRAWING", "DRAWING ROOM", "LOUNGE", "FAMILY ROOM",
            "LIVING DINING", "LIVING AND DINING", "LIVING CUM DINING", "DRAWING DINING",
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
        )
        put(RoomType.BASEMENT, "BASEMENT", "CELLAR")
        put(RoomType.COURTYARD, "COURTYARD", "COURT YARD", "ATRIUM", "OPEN TO SKY")
        put(RoomType.UTILITY, "UTILITY", "WASH AREA", "WASH", "LAUNDRY", "SERVICE AREA", "UTILITY AREA")
        put(RoomType.CORRIDOR, "CORRIDOR", "PASSAGE", "LOBBY", "HALLWAY", "GALLERY", "CIRCULATION")
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
    private val NON_ALPHANUMERIC = Regex("[^0-9A-Z]")
    private val ALL_DIGITS = Regex("^[0-9]+$")

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
        s = DIMENSION_X.replace(s, " ")
        s = NON_ALPHANUMERIC.replace(s, " ")
        return s.split(' ')
            .filter { it.isNotEmpty() && !ALL_DIGITS.matches(it) }
            .joinToString(" ")
    }

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
    fun resolve(raw: String): LabelMatch {
        val cleaned = clean(raw)
        if (cleaned.isEmpty()) return LabelMatch.Unknown
        EXACT[cleaned]?.let { return LabelMatch.Room(it, loose = false) }
        if (cleaned in DROP_EXACT) return LabelMatch.NotHabitable
        if (DROP_CONTAINS.any { cleaned.contains(it) }) return LabelMatch.NotHabitable
        CONTAINS.firstOrNull { cleaned.contains(it.first) }
            ?.let { return LabelMatch.Room(it.second, loose = true) }
        return LabelMatch.Unknown
    }
}
