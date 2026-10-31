package io.github.msteja.covenant.core.rules

import io.github.msteja.covenant.core.model.Activity
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.Pass
import java.time.Duration
import java.time.Instant

val PASS_WAIT: Duration = Duration.ofMinutes(1)
val PASS_LENGTH: Duration = Duration.ofMinutes(10)
/** A used pass comes back this long after it was asked for. */
val PASS_RETURNS: Duration = Duration.ofDays(7)

/** The lengths Quiet offers besides "until the next block" (R27). */
val QUIET_LENGTHS: List<Duration> = listOf(30L, 60L, 90L).map(Duration::ofMinutes)

val Pass.startsAt: Instant get() = requestedAt.plus(PASS_WAIT)
val Pass.endsAt: Instant get() = startsAt.plus(PASS_LENGTH)

sealed interface PassResult {
    data class Granted(val pass: Pass) : PassResult
    /** No pass left this week; the next one comes back at [backAt], or never when none are allowed. */
    data class NoneLeft(val backAt: Instant?) : PassResult
    data class AlreadyRunning(val pass: Pass) : PassResult
    data object Quiet : PassResult
}

/** The weekly pass (R55) and Quiet (R27). */
class Exits(
    private val covenant: Covenant,
    private val schedule: Schedule,
) {
    /** A pass that is waiting or running at [now]. Only one at a time. */
    fun passInProgress(activity: Activity, now: Instant): Pass? =
        activity.passes.firstOrNull { !now.isBefore(it.requestedAt) && now.isBefore(it.endsAt) }

    /** The pass giving [app] its screen at [now], once its wait is over. */
    fun passRunningFor(app: AppId, activity: Activity, now: Instant): Pass? =
        activity.passes.firstOrNull { it.app == app && !now.isBefore(it.startsAt) && now.isBefore(it.endsAt) }

    /** Passes asked for in the 7 days before [now]; each comes back 7 days after it was used. */
    private fun recent(activity: Activity, now: Instant): List<Pass> =
        activity.passes.filter { !it.requestedAt.isAfter(now) && now.isBefore(it.requestedAt.plus(PASS_RETURNS)) }

    fun passesLeft(activity: Activity, now: Instant): Int =
        (covenant.passesPerWeek - recent(activity, now).size).coerceAtLeast(0)

    /** When the next pass comes back, if none is left at [now]. Null when one is left, or none are allowed. */
    fun nextPassAt(activity: Activity, now: Instant): Instant? {
        if (covenant.passesPerWeek == 0 || passesLeft(activity, now) > 0) return null
        val used = recent(activity, now).map { it.requestedAt }.sorted()
        // With n passes a week, the oldest of the last n comes back first.
        return used[used.size - covenant.passesPerWeek].plus(PASS_RETURNS)
    }

    fun requestPass(app: AppId, activity: Activity, now: Instant): PassResult {
        passInProgress(activity, now)?.let { return PassResult.AlreadyRunning(it) }
        if (quietAt(activity, now)) return PassResult.Quiet
        if (passesLeft(activity, now) <= 0) return PassResult.NoneLeft(nextPassAt(activity, now))
        return PassResult.Granted(Pass(app, now))
    }

    /**
     * R27: Quiet for [length], or until the current block ends when [length] is null. It never shortens
     * a Quiet already running. Null when there's no block to end and no length was given.
     */
    fun quiet(activity: Activity, now: Instant, length: Duration? = null): Activity? {
        val until = length?.let { now.plus(it) } ?: schedule.activeAt(now)?.end?.toInstant() ?: return null
        val kept = activity.quietUntil?.takeIf { it.isAfter(until) } ?: until
        return activity.copy(quietUntil = kept)
    }

    /** Quiet is on: only essentials open (R27). */
    fun quietAt(activity: Activity, now: Instant): Boolean =
        activity.quietUntil?.let { now.isBefore(it) } == true
}
