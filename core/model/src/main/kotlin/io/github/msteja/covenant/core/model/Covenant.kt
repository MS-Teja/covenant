@file:UseSerializers(LocalTimeSerializer::class, DurationSerializer::class)

package io.github.msteja.covenant.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** The day starts at 4 am for timelines, today's page and stats (R4). */
val DAY_START: LocalTime = LocalTime.of(4, 0)

/** An installed app, identified by its package name. */
@Serializable
@JvmInline
value class AppId(val packageName: String)

/** How long an app may be on screen inside one block (R11). */
@Serializable
sealed interface Limit {
    @Serializable
    @SerialName("unlimited")
    data object Unlimited : Limit

    @Serializable
    @SerialName("minutes")
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
@Serializable
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
@Serializable
data class Timeline(val blocks: List<Block>)

/** Everything you promised: both timelines, essentials, the apps that pull you in and the ring-through list. */
@Serializable
data class Covenant(
    val weekday: Timeline,
    val weekend: Timeline,
    val weekendDays: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    val essentials: Set<AppId> = emptySet(),
    /**
     * Apps that pull you in. Only these are held to blocks; every other app is a tool and opens any
     * time from search. In a block, a tool is only pinned to home (R6).
     */
    val tempting: Set<AppId> = emptySet(),
    val ringThrough: Set<AppId> = emptySet(),
    /** Passes a week, from 0 to [MAX_PASSES_PER_WEEK]. Raising it waits the cooldown (R55). */
    val passesPerWeek: Int = 1,
    /** How long a looser change waits (R22). Lengthening it applies at once; shortening waits (R26). */
    val cooldown: Duration = Duration.ofHours(72),
)

const val MAX_PASSES_PER_WEEK = 2

const val MINUTES_PER_DAY = 24 * 60

fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
