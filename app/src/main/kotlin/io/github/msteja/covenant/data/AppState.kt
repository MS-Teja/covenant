@file:UseSerializers(InstantSerializer::class)

package io.github.msteja.covenant.data

import io.github.msteja.covenant.core.model.Activity
import io.github.msteja.covenant.core.model.Commitments
import io.github.msteja.covenant.core.model.HeldNotification
import io.github.msteja.covenant.core.model.InstantSerializer
import io.github.msteja.covenant.core.model.UsageKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Duration
import java.time.Instant

/** Seconds an app spent on screen inside one run of a block. */
@Serializable
data class UsageEntry(val key: UsageKey, val seconds: Long)

/** Everything Covenant keeps on the phone (R48). Nothing here ever leaves it (R50). */
@Serializable
data class AppState(
    val commitments: Commitments,
    val activity: Activity = Activity(),
    val usage: List<UsageEntry> = emptyList(),
    val held: List<HeldNotification> = emptyList(),
    /** Notifications from apps in no block, dismissed and counted in Held (R18). */
    val dismissed: Int = 0,
    val setupDone: Boolean = false,
    /** Covenant mutes notification sounds and replays them only for allowed apps. */
    val ownsAlerts: Boolean = true,
    val createdAt: Instant = Instant.EPOCH,
) {
    val usageMap: Map<UsageKey, Duration>
        get() = usage.associate { it.key to Duration.ofSeconds(it.seconds) }

    fun addUsage(key: UsageKey, time: Duration): AppState {
        val seconds = time.seconds
        if (seconds <= 0) return this
        val existing = usage.firstOrNull { it.key == key }
        val updated = if (existing == null) usage + UsageEntry(key, seconds)
        else usage.map { if (it.key == key) it.copy(seconds = it.seconds + seconds) else it }
        return copy(usage = updated)
    }

    /** Drops what no rule needs any more: older usage, passes and let-gos. */
    fun pruned(now: Instant): AppState {
        val dayAgo = now.minus(Duration.ofDays(2))
        val weekAgo = now.minus(Duration.ofDays(8))
        return copy(
            usage = usage.filter { !it.key.day.isBefore(java.time.LocalDate.now().minusDays(2)) },
            activity = activity.copy(
                passes = activity.passes.filter { it.requestedAt.isAfter(weekAgo) },
                quietUntil = activity.quietUntil?.takeIf { it.isAfter(now) },
                letGos = activity.letGos.filter { it.at.isAfter(dayAgo) },
            ),
        )
    }
}
