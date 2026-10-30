package io.github.msteja.covenant.core.rules

import io.github.msteja.covenant.core.model.CALIBRATION
import io.github.msteja.covenant.core.model.Commitments
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.PendingChange
import java.time.Instant
import java.util.UUID

/** Applies edits: stricter ones now, looser ones after the cooldown (R22–R26). */
class ChangeRules(private val newId: () -> String = { UUID.randomUUID().toString() }) {

    /** Before the seal, and for 7 days after it unless ended early, every change is instant (R25). */
    fun isCalibrating(commitments: Commitments, now: Instant): Boolean {
        val sealedAt = commitments.sealedAt ?: return true
        if (commitments.calibrationEndedEarlyAt != null) return false
        return now.isBefore(sealedAt.plus(CALIBRATION))
    }

    /**
     * Saves [proposed], the whole covenant as you want it now. It applies at once when nothing in
     * it is looser than what's in force; otherwise it waits the current cooldown as a pending change.
     */
    fun propose(commitments: Commitments, proposed: Covenant, now: Instant): Commitments {
        val settled = settle(commitments, now)
        if (isCalibrating(settled, now) || isNoLooser(proposed, settled.current)) {
            return settled.copy(current = proposed, pending = emptyList())
        }
        val change = PendingChange(newId(), proposed, now, now.plus(settled.current.cooldown))
        return settled.copy(pending = settled.pending + change)
    }

    /** R24: a pending change can be cancelled any time before it lands. */
    fun cancel(commitments: Commitments, changeId: String): Commitments =
        commitments.copy(pending = commitments.pending.filterNot { it.id == changeId })

    /** Lands every pending change whose cooldown is over, in order. */
    fun settle(commitments: Commitments, now: Instant): Commitments {
        var result = commitments
        for (change in commitments.pending.sortedBy { it.landsAt }) {
            if (change.landsAt.isAfter(now)) break
            result = result.copy(current = change.proposed, pending = result.pending - change)
        }
        return result
    }

    /** Sealing starts calibration week and tamper protection. It happens once. */
    fun seal(commitments: Commitments, now: Instant): Commitments =
        if (commitments.sealedAt != null) commitments else commitments.copy(sealedAt = now)

    /** R25: calibration can end early, and never restarts. */
    fun endCalibration(commitments: Commitments, now: Instant): Commitments =
        if (commitments.sealedAt == null || !isCalibrating(commitments, now)) commitments
        else commitments.copy(calibrationEndedEarlyAt = now)
}
