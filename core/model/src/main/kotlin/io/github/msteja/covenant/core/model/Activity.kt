package io.github.msteja.covenant.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Bringing one outside app into the current block for a while, asked for at [requestedAt] (R29). */
data class Borrow(val app: AppId, val requestedAt: Instant)

/** A weekly emergency pass asked for at [requestedAt] (R31). */
data class EmergencyPass(val requestedAt: Instant)

/** Essentials only from [startedAt] for [length] (R28). */
data class FocusSession(val startedAt: Instant, val length: Duration) {
    val endsAt: Instant get() = startedAt.plus(length)
}

/** Everything you did that changes what's open right now. */
data class Activity(
    val borrows: List<Borrow> = emptyList(),
    val emergencyPasses: List<EmergencyPass> = emptyList(),
    /** Quiet now: essentials only until this moment, the start of the next block (R27). */
    val quietUntil: Instant? = null,
    val focus: FocusSession? = null,
)

/** Time an app spent on screen inside one run of a block. */
data class UsageKey(val app: AppId, val blockId: String, val day: LocalDate)
