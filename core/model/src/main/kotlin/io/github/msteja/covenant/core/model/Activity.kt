@file:UseSerializers(InstantSerializer::class, DurationSerializer::class, LocalDateSerializer::class)

package io.github.msteja.covenant.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.LocalDate

/** A pass opening one outside app for a while, asked for at [requestedAt] (R55). */
@Serializable
data class Pass(val app: AppId, val requestedAt: Instant)

/** Choosing not to open [app] at the pause (R12, R57). */
@Serializable
data class LetGo(val app: AppId, val at: Instant)

/** Everything you did that changes what's open right now. */
@Serializable
data class Activity(
    val passes: List<Pass> = emptyList(),
    /** Quiet: essentials only until this moment (R27). */
    val quietUntil: Instant? = null,
    val letGos: List<LetGo> = emptyList(),
)

/** Time an app spent on screen inside one run of a block. */
@Serializable
data class UsageKey(val app: AppId, val blockId: String, val day: LocalDate)
