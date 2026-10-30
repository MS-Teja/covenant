package io.github.msteja.covenant.core.rules

import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.Limit
import java.time.Instant
import java.time.ZoneOffset

/** A week with no daylight-saving change, Monday 4 am to Monday 4 am, used to compare two covenants. */
private val REFERENCE_WEEK_START: Instant = Instant.parse("2026-01-05T04:00:00Z")
private const val MINUTES_PER_WEEK = 7 * 24 * 60

/**
 * True when [proposed] gives no app more time than [current] anywhere in the week (R22, R23).
 * It errs on the side of "looser": a change it can't prove stricter waits the cooldown, which is
 * always safe, because the old and new rules are enforced together while it waits.
 */
fun isNoLooser(proposed: Covenant, current: Covenant): Boolean {
    if (!current.essentials.containsAll(proposed.essentials)) return false
    if (!current.ringThrough.containsAll(proposed.ringThrough)) return false
    if (proposed.weeklyEmergencyPass && !current.weeklyEmergencyPass) return false
    if (proposed.weekendDays != current.weekendDays) return false
    if (proposed.cooldown < current.cooldown) return false
    return timelinesNoLooser(proposed, current)
}

private fun timelinesNoLooser(proposed: Covenant, current: Covenant): Boolean {
    val newRuns = runsInReferenceWeek(proposed)
    val oldRuns = runsInReferenceWeek(current)
    val newAt = minuteIndex(newRuns)
    val oldAt = minuteIndex(oldRuns)

    for (minute in 0 until MINUTES_PER_WEEK) {
        val newRun = newAt[minute]
        val oldRun = oldAt[minute]
        // Turning grayscale off is looser.
        if (oldRun?.block?.grayscale == true && newRun?.block?.grayscale != true) return false
        // An app open in the new rules must be open in the old ones at the same minute.
        for (app in newRun?.block?.apps?.keys.orEmpty()) {
            if (app !in current.essentials && oldRun?.block?.apps?.containsKey(app) != true) return false
        }
    }

    // Budgets: inside each old run, the new rules may not offer an app more minutes than the old limit.
    for (oldRun in oldRuns) {
        for ((app, oldLimit) in oldRun.block.apps) {
            if (oldLimit !is Limit.Minutes || app in proposed.essentials) continue
            val offered = newRuns.sumOf { newRun -> offeredInside(app, newRun, oldRun) }
            if (offered > oldLimit.minutes) return false
        }
    }
    return true
}

/** Minutes [newRun] lets [app] use inside [oldRun]: its overlap, capped by the new limit. */
private fun offeredInside(app: AppId, newRun: Occurrence, oldRun: Occurrence): Long {
    val limit = newRun.block.apps[app] ?: return 0
    val overlap = overlapMinutes(newRun, oldRun)
    return when (limit) {
        Limit.Unlimited -> overlap
        is Limit.Minutes -> minOf(overlap, limit.minutes.toLong())
    }
}

private fun overlapMinutes(a: Occurrence, b: Occurrence): Long {
    val start = maxOf(a.start.toInstant(), b.start.toInstant())
    val end = minOf(a.end.toInstant(), b.end.toInstant())
    return if (end.isAfter(start)) java.time.Duration.between(start, end).toMinutes() else 0
}

private fun runsInReferenceWeek(covenant: Covenant): List<Occurrence> {
    val schedule = Schedule(covenant, ZoneOffset.UTC)
    val weekEnd = REFERENCE_WEEK_START.plus(java.time.Duration.ofDays(7))
    return schedule.upcomingFrom(REFERENCE_WEEK_START).filter { it.start.toInstant().isBefore(weekEnd) }
}

private fun minuteIndex(runs: List<Occurrence>): Array<Occurrence?> {
    val index = arrayOfNulls<Occurrence>(MINUTES_PER_WEEK)
    for (run in runs) {
        val from = java.time.Duration.between(REFERENCE_WEEK_START, run.start.toInstant()).toMinutes().coerceAtLeast(0)
        val until = java.time.Duration.between(REFERENCE_WEEK_START, run.end.toInstant()).toMinutes().coerceAtMost(MINUTES_PER_WEEK.toLong())
        for (minute in from until until) index[minute.toInt()] = run
    }
    return index
}
