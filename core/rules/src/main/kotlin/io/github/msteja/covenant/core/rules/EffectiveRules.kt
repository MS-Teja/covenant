package io.github.msteja.covenant.core.rules

import io.github.msteja.covenant.core.model.Activity
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.Commitments
import io.github.msteja.covenant.core.model.HeldNotification
import io.github.msteja.covenant.core.model.UsageKey
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Where a notification goes when it arrives (R17, R18, R20). */
sealed interface Route {
    data object Deliver : Route
    /** A call from an app on the ring-through list: it rings and its call screen opens. */
    data object RingThrough : Route
    data class Hold(val releaseAt: Instant) : Route
    /** From an app in no block: dismissed and counted in Held. */
    data object Dismiss : Route
}

/**
 * What's in force right now: the current covenant and every pending change, enforced together.
 * An app opens only when all of them let it, with the smallest allowance (R22).
 */
class EffectiveRules(commitments: Commitments, zone: ZoneId) {
    private val versions = listOf(commitments.current) + commitments.pending.map { it.proposed }
    private val gates = versions.map { Gatekeeper(it, Schedule(it, zone)) }
    private val ringThrough = versions.map { it.ringThrough }.reduce { a, b -> a intersect b }

    /** The schedule shown on home and today's page: the covenant in force. */
    val schedule = Schedule(commitments.current, zone)
    val exits = Exits(commitments.current, schedule)

    fun decide(app: AppId, moment: Moment, activity: Activity, usage: Map<UsageKey, Duration>): Access =
        gates.map { it.decide(app, moment, activity, usage) }.reduce(::stricter)

    /** R16: grayscale is on when any version in force asks for it. */
    fun grayscaleAt(now: Instant): Boolean =
        versions.any { Schedule(it, schedule.zone).activeAt(now)?.block?.grayscale == true }

    fun route(app: AppId, isCall: Boolean, moment: Moment, activity: Activity, usage: Map<UsageKey, Duration>): Route =
        when (val access = decide(app, moment, activity, usage)) {
            Access.Open, is Access.Limited -> Route.Deliver
            is Access.Hidden -> access.until?.let { Route.Hold(it.toInstant()) } ?: Route.Dismiss
            is Access.Blocked -> when {
                isCall && app in ringThrough -> Route.RingThrough
                access.opensAt == null -> Route.Dismiss
                else -> Route.Hold(access.opensAt.toInstant())
            }
        }
}

/** The tighter of two decisions. */
fun stricter(a: Access, b: Access): Access {
    fun rank(x: Access) = when (x) {
        is Access.Blocked, is Access.Hidden -> 0
        is Access.Limited -> 1
        Access.Open -> 2
    }
    return when {
        rank(a) != rank(b) -> if (rank(a) < rank(b)) a else b
        a is Access.Limited && b is Access.Limited -> if (a.left <= b.left) a else b
        a is Access.Blocked && b is Access.Blocked -> laterOpening(a, b)
        else -> a
    }
}

private fun laterOpening(a: Access.Blocked, b: Access.Blocked): Access.Blocked = when {
    a.opensAt == null -> a
    b.opensAt == null -> b
    a.opensAt.isAfter(b.opensAt) -> a
    else -> b
}

/** Held notifications due by [now], one summary per app (R17). */
fun dueSummaries(held: List<HeldNotification>, now: Instant): Map<AppId, List<HeldNotification>> =
    held.filter { !it.releaseAt.isAfter(now) }.sortedBy { it.postedAt }.groupBy { it.app }

/**
 * R40: a clean run had no pass and no unguarded time.
 * Hitting a time limit doesn't count against it.
 */
fun isClean(run: Occurrence, activity: Activity, unguarded: List<ClosedRange<Instant>>): Boolean {
    val start = run.start.toInstant()
    val end = run.end.toInstant()
    fun overlaps(from: Instant, until: Instant) = from.isBefore(end) && until.isAfter(start)
    return activity.passes.none { overlaps(it.startsAt, it.endsAt) } &&
        unguarded.none { overlaps(it.start, it.endInclusive) }
}
