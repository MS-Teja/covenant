package io.github.msteja.covenant.core.rules

import io.github.msteja.covenant.core.model.Block
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.DAY_START
import io.github.msteja.covenant.core.model.Timeline
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One run of a block on a real day. [day] is the day it belongs to: the day it starts, counting
 * days from 4 am (R4). [start] may be later than the block's own start time when the previous
 * day's block was still running.
 */
data class Occurrence(
    val block: Block,
    val day: LocalDate,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
) {
    operator fun contains(instant: Instant): Boolean =
        !instant.isBefore(start.toInstant()) && instant.isBefore(end.toInstant())
}

/** Turns a [Covenant] into real block runs in a time zone. Wall-clock times win across DST changes. */
class Schedule(
    private val covenant: Covenant,
    private val zone: ZoneId,
) {
    /** The day [instant] belongs to: its date, or the day before when it's earlier than 4 am. */
    fun dayOf(instant: Instant): LocalDate {
        val local = instant.atZone(zone)
        return if (local.toLocalTime().isBefore(DAY_START)) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    /** R3: weekend days use the weekend timeline. */
    fun timelineFor(day: LocalDate): Timeline =
        if (day.dayOfWeek in covenant.weekendDays) covenant.weekend else covenant.weekday

    /** The block running at [instant], or null for essentials-only time (R1). */
    fun activeAt(instant: Instant): Occurrence? = occurrencesAround(instant).firstOrNull { instant in it }

    /** The next moment a block starts or ends after [instant], looking up to a week ahead. */
    fun nextBoundaryAfter(instant: Instant): Instant? {
        val firstDay = dayOf(instant).minusDays(1)
        return resolved((0L..8L).map { firstDay.plusDays(it) })
            .flatMap { listOf(it.start.toInstant(), it.end.toInstant()) }
            .filter { it.isAfter(instant) }
            .minOrNull()
    }

    /** The next 4 am after [instant], when the page turns and borrows reset. */
    fun nextDayStartAfter(instant: Instant): Instant =
        ZonedDateTime.of(dayOf(instant).plusDays(1), DAY_START, zone).toInstant()

    /** Resolved runs for the day of [instant] and its neighbours. */
    fun occurrencesAround(instant: Instant): List<Occurrence> {
        val day = dayOf(instant)
        return resolved(listOf(day.minusDays(1), day, day.plusDays(1)))
    }

    /**
     * R4: a block still running when the next day's blocks begin finishes first. Later runs are
     * clipped to start when it ends, and dropped if nothing is left of them.
     */
    private fun resolved(days: List<LocalDate>): List<Occurrence> {
        val raw = days.flatMap { day -> timelineFor(day).blocks.map { occurrence(it, day) } }
            .sortedWith(compareBy<Occurrence> { it.start.toInstant() }.thenBy { it.day })
        val result = mutableListOf<Occurrence>()
        var coveredUntil: ZonedDateTime? = null
        for (run in raw) {
            val start = coveredUntil?.takeIf { it.isAfter(run.start) } ?: run.start
            if (start.isBefore(run.end)) {
                result += run.copy(start = start)
                coveredUntil = run.end
            }
        }
        return result
    }

    private fun occurrence(block: Block, day: LocalDate): Occurrence {
        val startDate = if (block.start.isBefore(DAY_START)) day.plusDays(1) else day
        val start = ZonedDateTime.of(startDate, block.start, zone)
        val endDate = startDate.plusDays(if (block.end.isAfter(block.start)) 0 else 1)
        return Occurrence(block, day, start, ZonedDateTime.of(endDate, block.end, zone))
    }
}
