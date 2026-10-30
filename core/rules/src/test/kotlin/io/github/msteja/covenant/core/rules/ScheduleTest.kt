package io.github.msteja.covenant.core.rules

import io.github.msteja.covenant.core.model.Block
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.Timeline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val KOLKATA = ZoneId.of("Asia/Kolkata")
private val NEW_YORK = ZoneId.of("America/New_York")

private fun block(id: String, start: String, end: String) =
    Block(id = id, name = id.replaceFirstChar { it.uppercase() }, start = LocalTime.parse(start), end = LocalTime.parse(end))

private val weekday = Timeline(
    listOf(block("work", "08:00", "16:00"), block("evening", "16:00", "22:00"), block("night", "22:00", "08:00")),
)

private fun at(dateTime: String, zone: ZoneId = KOLKATA): Instant = LocalDateTime.parse(dateTime).atZone(zone).toInstant()

class ScheduleTest {
    private val schedule = Schedule(
        Covenant(weekday = weekday, weekend = Timeline(listOf(block("morning", "07:00", "12:00")))),
        KOLKATA,
    )

    @Test
    fun `R1 time outside every block is essentials-only`() {
        // Saturday's weekend timeline only has Morning, 7 am to noon.
        assertNull(schedule.activeAt(at("2026-10-24T15:00")))
    }

    @Test
    fun `R3 weekend days use the weekend timeline`() {
        assertEquals("work", schedule.activeAt(at("2026-10-23T10:00"))?.block?.id)
        assertEquals("morning", schedule.activeAt(at("2026-10-24T10:00"))?.block?.id)
    }

    @Test
    fun `R4 the day starts at 4 am`() {
        assertEquals(LocalDate.parse("2026-10-23"), schedule.dayOf(at("2026-10-24T03:59")))
        assertEquals(LocalDate.parse("2026-10-24"), schedule.dayOf(at("2026-10-24T04:00")))
    }

    @Test
    fun `R4 a block crossing midnight belongs to the day it starts`() {
        val run = schedule.activeAt(at("2026-10-24T02:00"))
        assertEquals("night", run?.block?.id)
        assertEquals(LocalDate.parse("2026-10-23"), run?.day)
    }

    @Test
    fun `R4 a running block finishes before the next day's blocks start`() {
        // Friday's Night runs to 8 am Saturday, so Saturday's Morning starts at 8, not 7.
        assertEquals("night", schedule.activeAt(at("2026-10-24T07:30"))?.block?.id)
        val morning = schedule.activeAt(at("2026-10-24T08:00"))
        assertEquals("morning", morning?.block?.id)
        assertEquals(LocalTime.of(8, 0), morning?.start?.toLocalTime())
    }

    @Test
    fun `the next boundary is when the current block ends`() {
        assertEquals(at("2026-10-23T16:00"), schedule.nextBoundaryAfter(at("2026-10-23T15:59")))
    }

    @Test
    fun `the next day start is the coming 4 am`() {
        assertEquals(at("2026-10-24T04:00"), schedule.nextDayStartAfter(at("2026-10-23T23:00")))
        assertEquals(at("2026-10-24T04:00"), schedule.nextDayStartAfter(at("2026-10-24T03:00")))
    }
}

class DaylightSavingTest {
    private val schedule = Schedule(Covenant(weekday = weekday, weekend = weekday), NEW_YORK)

    @Test
    fun `a block keeps its wall-clock end when clocks spring forward`() {
        val night = schedule.activeAt(at("2026-03-08T01:00", NEW_YORK))!!
        assertEquals(LocalTime.of(8, 0), night.end.toLocalTime())
        assertEquals(Duration.ofHours(9), Duration.between(night.start, night.end))
    }

    @Test
    fun `a block keeps its wall-clock end when clocks fall back`() {
        val night = schedule.activeAt(at("2026-11-01T01:30", NEW_YORK))!!
        assertEquals(LocalTime.of(8, 0), night.end.toLocalTime())
        assertEquals(Duration.ofHours(11), Duration.between(night.start, night.end))
    }

    @Test
    fun `a block that falls inside the skipped hour is dropped, not crashed on`() {
        val skipped = Timeline(listOf(block("late", "02:30", "03:30"), block("day", "09:00", "17:00")))
        val schedule = Schedule(Covenant(weekday = skipped, weekend = skipped), NEW_YORK)
        assertNull(schedule.activeAt(at("2026-03-08T03:45", NEW_YORK)))
        assertEquals("day", schedule.activeAt(at("2026-03-08T10:00", NEW_YORK))?.block?.id)
    }
}

class TimelineRulesTest {
    @Test
    fun `R2 a valid timeline has no problems`() {
        assertEquals(emptyList<TimelineProblem>(), weekday.problems())
    }

    @Test
    fun `R2 at most 5 blocks`() {
        val six = Timeline((0 until 6).map { block("b$it", "%02d:00".format(it * 2), "%02d:00".format(it * 2 + 1)) })
        assertEquals(listOf(TimelineProblem.TooManyBlocks(6)), six.problems())
    }

    @Test
    fun `R2 blocks last at least 30 minutes`() {
        val short = Timeline(listOf(block("tea", "16:00", "16:20")))
        assertEquals(listOf(TimelineProblem.TooShort("tea")), short.problems())
    }

    @Test
    fun `R2 blocks can't overlap, including across midnight`() {
        val clash = Timeline(listOf(block("night", "22:00", "08:00"), block("early", "07:00", "09:00")))
        assertEquals(listOf(TimelineProblem.Overlap("night", "early")), clash.problems())
    }
}
