package io.github.msteja.covenant.core.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** The day starts at 4 am for timelines, today's page, borrows and stats (R4). */
val DAY_START: LocalTime = LocalTime.of(4, 0)

/** An installed app, identified by its package name. */
@JvmInline
value class AppId(val packageName: String)

/** How long an app may be on screen inside one block (R11). */
sealed interface Limit {
    data object Unlimited : Limit

    data class Minutes(val minutes: Int) : Limit {
        init {
            require(minutes in 1..MINUTES_PER_DAY) { "A limit must be 1–$MINUTES_PER_DAY minutes, was $minutes" }
        }
    }
}

/**
 * A named time range with its own apps, time limits and grayscale setting.
 * A block whose [end] is at or before its [start] crosses midnight.
 */
data class Block(
    val id: String,
    val name: String,
    val start: LocalTime,
    val end: LocalTime,
    val apps: Map<AppId, Limit> = emptyMap(),
    val grayscale: Boolean = false,
) {
    /** Wall-clock length. A block that starts and ends at the same time lasts 24 hours. */
    val length: Duration
        get() {
            val minutes = Math.floorMod(end.toMinuteOfDay() - start.toMinuteOfDay(), MINUTES_PER_DAY)
            return Duration.ofMinutes(if (minutes == 0) MINUTES_PER_DAY.toLong() else minutes.toLong())
        }
}

/** All 24 hours of a day split into blocks. Time outside every block is essentials-only (R1). */
data class Timeline(val blocks: List<Block>)

/** Everything you promised: both timelines, essentials and the ring-through list. */
data class Covenant(
    val weekday: Timeline,
    val weekend: Timeline,
    val weekendDays: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    val essentials: Set<AppId> = emptySet(),
    val ringThrough: Set<AppId> = emptySet(),
    val weeklyEmergencyPass: Boolean = false,
    /** How long a looser change waits (R22). Lengthening it applies at once; shortening waits (R26). */
    val cooldown: Duration = Duration.ofHours(72),
)

const val MINUTES_PER_DAY = 24 * 60

fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
