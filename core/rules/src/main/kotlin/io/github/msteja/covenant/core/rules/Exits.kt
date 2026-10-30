package io.github.msteja.covenant.core.rules

import io.github.msteja.covenant.core.model.Activity
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.Borrow
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.EmergencyPass
import java.time.Duration
import java.time.Instant

val BORROW_WAIT: Duration = Duration.ofMinutes(1)
val BORROW_LENGTH: Duration = Duration.ofMinutes(10)
const val BORROWS_PER_DAY = 2

val EMERGENCY_WAIT: Duration = Duration.ofMinutes(10)
val EMERGENCY_LENGTH: Duration = Duration.ofMinutes(30)
val EMERGENCY_EVERY: Duration = Duration.ofDays(7)

val Borrow.startsAt: Instant get() = requestedAt.plus(BORROW_WAIT)
val Borrow.endsAt: Instant get() = startsAt.plus(BORROW_LENGTH)
val EmergencyPass.startsAt: Instant get() = requestedAt.plus(EMERGENCY_WAIT)
val EmergencyPass.endsAt: Instant get() = startsAt.plus(EMERGENCY_LENGTH)

sealed interface BorrowResult {
    data class Granted(val borrow: Borrow) : BorrowResult
    data object NoneLeftToday : BorrowResult
    data class AlreadyRunning(val borrow: Borrow) : BorrowResult
    data object FocusOrQuiet : BorrowResult
}

sealed interface EmergencyResult {
    data class Granted(val pass: EmergencyPass) : EmergencyResult
    data object TurnedOff : EmergencyResult
    data class NotYet(val availableAt: Instant) : EmergencyResult
    data class AlreadyRunning(val pass: EmergencyPass) : EmergencyResult
}

/** Borrows (R29), quiet now (R27) and the weekly emergency pass (R31). */
class Exits(
    private val covenant: Covenant,
    private val schedule: Schedule,
) {
    /** A borrow that is waiting or running at [now]. Only one at a time. */
    fun borrowInProgress(activity: Activity, now: Instant): Borrow? =
        activity.borrows.firstOrNull { !now.isBefore(it.requestedAt) && now.isBefore(it.endsAt) }

    /** The borrow giving [app] its screen at [now], once its wait is over. */
    fun borrowRunningFor(app: AppId, activity: Activity, now: Instant): Borrow? =
        activity.borrows.firstOrNull { it.app == app && !now.isBefore(it.startsAt) && now.isBefore(it.endsAt) }

    /** Borrows left on the day of [now]. The count resets at 4 am. */
    fun borrowsLeft(activity: Activity, now: Instant): Int {
        val today = schedule.dayOf(now)
        return BORROWS_PER_DAY - activity.borrows.count { schedule.dayOf(it.requestedAt) == today }
    }

    fun requestBorrow(app: AppId, activity: Activity, now: Instant): BorrowResult {
        borrowInProgress(activity, now)?.let { return BorrowResult.AlreadyRunning(it) }
        if (essentialsOnlyAt(activity, now)) return BorrowResult.FocusOrQuiet
        if (borrowsLeft(activity, now) <= 0) return BorrowResult.NoneLeftToday
        return BorrowResult.Granted(Borrow(app, now))
    }

    fun emergencyRunning(activity: Activity, now: Instant): EmergencyPass? =
        activity.emergencyPasses.firstOrNull { !now.isBefore(it.startsAt) && now.isBefore(it.endsAt) }

    /** The pass comes back 7 days after the last one was asked for. */
    fun emergencyAvailableAt(activity: Activity): Instant? =
        activity.emergencyPasses.maxOfOrNull { it.requestedAt }?.plus(EMERGENCY_EVERY)

    fun requestEmergency(activity: Activity, now: Instant): EmergencyResult {
        if (!covenant.weeklyEmergencyPass) return EmergencyResult.TurnedOff
        activity.emergencyPasses.firstOrNull { !now.isBefore(it.requestedAt) && now.isBefore(it.endsAt) }
            ?.let { return EmergencyResult.AlreadyRunning(it) }
        emergencyAvailableAt(activity)?.takeIf { now.isBefore(it) }?.let { return EmergencyResult.NotYet(it) }
        return EmergencyResult.Granted(EmergencyPass(now))
    }

    /** R27: quiet now lasts until the current block would have ended. Null when no block is running. */
    fun quietNow(activity: Activity, now: Instant): Activity? {
        val running = schedule.activeAt(now) ?: return null
        return activity.copy(quietUntil = running.end.toInstant())
    }

    /** Focus timer or quiet now: only essentials open (R27, R28). */
    fun essentialsOnlyAt(activity: Activity, now: Instant): Boolean =
        activity.quietUntil?.let { now.isBefore(it) } == true ||
            activity.focus?.let { !now.isBefore(it.startedAt) && now.isBefore(it.endsAt) } == true
}
