package com.vastufirst.app.ui.newplan

import com.vastufirst.shared.RoomType

/** A bundled sample home for evaluating the whole flow without drawing anything (§6.2c). */
data class SamplePlan(
    val id: String,
    val name: String,
    val rooms: List<GridRoom>,
    val door: GridDoor,
    val north: Int,
)

/**
 * Two bundled samples. They are valid, plausible homes on the 8×8 guided grid — enough to see
 * the score → report flow end-to-end. (They are not the engine's §15 acceptance fixture, which
 * lives in the engine tests.)
 */
object SamplePlans {
    val all: List<SamplePlan> = listOf(builderDraft2bhk(), compactFlat())

    private fun r(id: String, type: RoomType, col: Int, row: Int, w: Int, h: Int) =
        GridRoom(id, type, col, row, w, h)

    private fun builderDraft2bhk() = SamplePlan(
        id = "sample-2bhk",
        name = "Builder's draft — 2BHK",
        rooms = listOf(
            r("s1", RoomType.POOJA, 0, 0, 2, 2),
            r("s2", RoomType.BEDROOM, 3, 0, 2, 2),
            r("s3", RoomType.TOILET, 6, 0, 2, 2),
            r("s4", RoomType.KITCHEN, 0, 3, 2, 2),
            r("s5", RoomType.STAIRCASE, 3, 3, 2, 2),
            r("s6", RoomType.LIVING, 6, 3, 2, 3),
            r("s7", RoomType.MASTER_BEDROOM, 0, 6, 3, 2),
        ),
        door = GridDoor(DoorSide.S, 6),
        north = 0,
    )

    private fun compactFlat() = SamplePlan(
        id = "sample-flat",
        name = "Compact 2BHK flat",
        rooms = listOf(
            r("f1", RoomType.LIVING, 0, 0, 3, 3),
            r("f2", RoomType.KITCHEN, 5, 0, 3, 2),
            r("f3", RoomType.BEDROOM, 5, 3, 3, 2),
            r("f4", RoomType.TOILET, 3, 3, 2, 2),
            r("f5", RoomType.MASTER_BEDROOM, 0, 5, 3, 3),
        ),
        door = GridDoor(DoorSide.E, 4),
        north = 0,
    )
}
