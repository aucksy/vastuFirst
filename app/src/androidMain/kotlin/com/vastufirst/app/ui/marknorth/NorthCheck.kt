package com.vastufirst.app.ui.marknorth

import com.vastufirst.app.ui.common.label
import com.vastufirst.app.ui.common.short
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Zone

/**
 * ⭐ THE DOUBLE-CHECK ON NORTH (docs/SCORE-ACCURACY-CAVEATS.md #3).
 *
 * North is the one input the app cannot verify, and every direction in the whole report hangs off
 * it. Asking "are you sure?" is worthless — nobody can answer it. What a person CAN answer, while
 * standing in their own home, is whether their kitchen really is in the south-east.
 *
 * So the confirmation states the CONSEQUENCES of the North they set, in the same words the report
 * will use, and the button that continues is the one that agrees with them. That costs no extra tap
 * — the "Read my home" button simply says what it is agreeing to — and it turns an unanswerable
 * question into three facts a person can check by looking around the room they are standing in.
 *
 * Pure, so the sentences are unit-tested rather than eyeballed.
 */
object NorthCheck {

    /**
     * The rooms worth quoting back, most recognisable first. A person knows where their kitchen and
     * their pooja room are; "corridor" tells them nothing, and a second bedroom is ambiguous.
     */
    private val TELLING_ROOMS = listOf(
        RoomType.KITCHEN, RoomType.POOJA, RoomType.MASTER_BEDROOM, RoomType.TOILET,
        RoomType.LIVING, RoomType.STAIRCASE,
    )

    /** The eight-point zone a compass bearing falls in — the same 45° sectors the engine uses. */
    fun zoneOfBearing(bearingDeg: Double): Zone {
        val b = ((bearingDeg % 360.0) + 360.0) % 360.0
        val sectors = arrayOf(Zone.N, Zone.NE, Zone.E, Zone.SE, Zone.S, Zone.SW, Zone.W, Zone.NW)
        return sectors[((b + 22.5) / 45.0).toInt() % 8]
    }

    /**
     * Up to [limit] plain claims about where things ended up, e.g. "your front door is on the
     * north-east side" and "your kitchen is in the south-east". Empty when there is nothing worth
     * quoting, in which case the caller shows the plain heading instead of an empty list.
     *
     * The front door goes first when there is one: it is the highest-weighted thing the engine
     * scores and the easiest of all to check from where the user is standing.
     */
    fun claims(analysis: Analysis?, limit: Int = 3): List<String> {
        if (analysis == null) return emptyList()
        val out = mutableListOf<String>()
        analysis.doorResult?.let { door ->
            out += "your front door is on the ${zoneOfBearing(door.bearing).short().lowercase()} side"
        }
        // One room per kind, so two bedrooms don't crowd out the kitchen, and never the centre —
        // "in the centre" is not a direction a person can check by turning round.
        val seen = HashSet<RoomType>()
        for (type in TELLING_ROOMS) {
            if (out.size >= limit) break
            val room = analysis.roomResults.firstOrNull { it.type == type && it.zone != Zone.BRAHMASTHAN }
                ?: continue
            if (!seen.add(type)) continue
            out += "your ${type.label().lowercase()} is in the ${room.zone.short().lowercase()}"
        }
        return out.take(limit)
    }

    /** The claims as one sentence: "your front door is on the west side, and your kitchen is in the north". */
    fun sentence(analysis: Analysis?, limit: Int = 3): String {
        val parts = claims(analysis, limit)
        return when (parts.size) {
            0 -> ""
            1 -> parts[0]
            else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
        }
    }
}
