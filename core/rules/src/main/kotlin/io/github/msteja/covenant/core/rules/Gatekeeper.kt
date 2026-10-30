package io.github.msteja.covenant.core.rules

import io.github.msteja.covenant.core.model.Activity
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.Limit
import io.github.msteja.covenant.core.model.UsageKey
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** Facts about the moment that only the phone knows. */
data class Moment(
    val now: Instant,
    /** On the system allowlist: Android's own screens, the keyboard, Covenant itself. */
    val systemAllowlisted: Boolean = false,
    /** The app is in a call or navigating. Calls and navigation are never cut (R15). */
    val inCallOrNavigating: Boolean = false,
)

sealed interface Access {
    /** Opens with no pause: essentials, unlimited block apps, borrows and emergencies. */
    data object Open : Access

    /** Opens after the pause, with this much screen time left in [block] (R12). */
    data class Limited(val left: Duration, val block: Occurrence) : Access

    /** Outside the current block, or held back by focus or quiet now. [opensAt] is null when no block holds it. */
    data class Blocked(val reason: Reason, val opensAt: ZonedDateTime?) : Access

    /** Its minutes are spent; hidden until [until], its next block (R13). */
    data class Hidden(val until: ZonedDateTime?) : Access

    enum class Reason { NOT_IN_BLOCK, FOCUS_OR_QUIET }
}

/**
 * Decides whether an app may come to the front, using the checks in the spec's "Opening an app"
 * section, in order. The first one that matches wins.
 */
class Gatekeeper(
    private val covenant: Covenant,
    private val schedule: Schedule,
    private val exits: Exits = Exits(covenant, schedule),
) {
    fun decide(app: AppId, moment: Moment, activity: Activity, usage: Map<UsageKey, Duration>): Access {
        val now = moment.now
        if (moment.systemAllowlisted || moment.inCallOrNavigating) return Access.Open
        if (app in covenant.essentials) return Access.Open
        if (exits.emergencyRunning(activity, now) != null) return Access.Open
        if (exits.essentialsOnlyAt(activity, now)) {
            val freeAt = listOfNotNull(activity.quietUntil, activity.focus?.endsAt).filter { it.isAfter(now) }.max()
            return Access.Blocked(Access.Reason.FOCUS_OR_QUIET, nextOpening(app, freeAt))
        }
        if (exits.borrowRunningFor(app, activity, now) != null) return Access.Open

        val running = schedule.activeAt(now)
        val limit = running?.block?.apps?.get(app)
            ?: return Access.Blocked(Access.Reason.NOT_IN_BLOCK, nextOpening(app, now))
        if (limit == Limit.Unlimited) return Access.Open

        val left = minutesLeft(app, running, limit as Limit.Minutes, usage)
        if (left <= Duration.ZERO) return Access.Hidden(nextOpening(app, running.end.toInstant()))
        return Access.Limited(left, running)
    }

    /** R11, R14: a fresh allowance in every run of a block; unused minutes don't carry over. */
    fun minutesLeft(app: AppId, run: Occurrence, limit: Limit.Minutes, usage: Map<UsageKey, Duration>): Duration {
        val used = usage[UsageKey(app, run.block.id, run.day)] ?: Duration.ZERO
        return Duration.ofMinutes(limit.minutes.toLong()).minus(used)
    }

    /** The next moment at or after [after] when [app] is in a running block. */
    fun nextOpening(app: AppId, after: Instant): ZonedDateTime? =
        schedule.upcomingFrom(after)
            .firstOrNull { app in it.block.apps }
            ?.let { if (it.start.toInstant().isBefore(after)) after.atZone(it.start.zone) else it.start }
}

/** R13: how much more screen time until the 5- and 1-minute warnings, given what's [left]. */
fun warningsAfter(left: Duration): List<Duration> =
    listOf(5L, 1L).map { left.minusMinutes(it) }.filter { !it.isNegative }
