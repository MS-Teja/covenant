package io.github.msteja.covenant.core.model

import java.time.Duration
import java.time.Instant

/** A looser change waiting out its cooldown (R24). [proposed] is the whole covenant as it will be. */
data class PendingChange(
    val id: String,
    val proposed: Covenant,
    val requestedAt: Instant,
    val landsAt: Instant,
)

/**
 * The covenant in force, the changes still waiting, and when you sealed it.
 * Before the seal, and during calibration week, every change applies at once (R25).
 */
data class Commitments(
    val current: Covenant,
    val pending: List<PendingChange> = emptyList(),
    val sealedAt: Instant? = null,
    val calibrationEndedEarlyAt: Instant? = null,
) {
    /** The newest version you asked for: what the editors show. */
    val latest: Covenant get() = pending.lastOrNull()?.proposed ?: current
}

val CALIBRATION: Duration = Duration.ofDays(7)

/** A notification from an outside app, kept until [releaseAt], when its app's next block starts (R17). */
data class HeldNotification(
    val app: AppId,
    val title: String,
    val text: String,
    val postedAt: Instant,
    val releaseAt: Instant,
)
