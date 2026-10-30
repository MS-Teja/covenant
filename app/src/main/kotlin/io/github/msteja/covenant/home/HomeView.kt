package io.github.msteja.covenant.home

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import io.github.msteja.covenant.Graph
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.rules.Access
import io.github.msteja.covenant.data.AppState
import io.github.msteja.covenant.data.InstalledApp
import io.github.msteja.covenant.guard.CovenantAccessibilityService
import io.github.msteja.covenant.guard.CovenantNotificationListener
import io.github.msteja.covenant.guard.Guard
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The current time, refreshed every 20 seconds so home turns its page on time. */
@Composable
fun rememberNow(): Instant {
    var now by remember { mutableStateOf(Graph.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = Graph.now()
        }
    }
    return now
}

private val clock = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)
private val clockAmPm = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val weekday = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)

/** "4:00" for home, where the day is obvious. */
fun shortTime(time: ZonedDateTime): String = time.format(clock)

/** "4:00 pm", "4:00 pm tomorrow" or "Monday 4:00 pm": a time someone can act on. */
fun fullTime(time: ZonedDateTime, now: Instant = Graph.now()): String {
    val text = time.format(clockAmPm).lowercase(Locale.ENGLISH)
    val days = ChronoUnit.DAYS.between(now.atZone(time.zone).toLocalDate(), time.toLocalDate())
    return when (days) {
        0L -> text
        1L -> "$text tomorrow"
        else -> "${time.format(weekday)} $text"
    }
}

/** What home shows: a running head, a chapter, the block's apps, the footnote and the next block (R46). */
data class HomeView(
    val runningHead: String,
    val chapter: String,
    val until: String?,
    val apps: List<InstalledApp>,
    val essentials: List<InstalledApp>,
    val next: String?,
    val unguarded: Boolean,
)

fun homeView(context: Context, state: AppState, installed: List<InstalledApp>, now: Instant): HomeView {
    val rules = Graph.rules(state.commitments)
    val zone = Graph.zone
    val day = rules.schedule.dayOf(now)
    val running = rules.schedule.activeAt(now)
    val activity = state.activity
    val essentials = state.commitments.current.essentials

    fun allowed(app: InstalledApp): Boolean = when (Guard.decide(context, app.id, now)) {
        Access.Open, is Access.Limited -> true
        else -> false
    }

    val quiet = activity.quietUntil?.takeIf { it.isAfter(now) }
    val focus = activity.focus?.takeIf { it.endsAt.isAfter(now) }
    val (chapter, until) = when {
        focus != null -> "Focus" to "until ${shortTime(focus.endsAt.atZone(zone))}"
        quiet != null -> "Quiet" to "until ${shortTime(quiet.atZone(zone))}"
        running != null -> running.block.name to "until ${shortTime(running.end)}"
        else -> "Essentials only" to rules.schedule.nextBoundaryAfter(now)?.let { "until ${shortTime(it.atZone(zone))}" }
    }

    val blockApps = running?.block?.apps?.keys.orEmpty()
    val borrowed = rules.exits.borrowInProgress(activity, now)?.app
    val shown = installed.filter { (it.id in blockApps || it.id == borrowed) && it.id !in essentials && allowed(it) }
    val essentialApps = installed.filter { it.id in essentials }

    val after = (quiet ?: running?.end?.toInstant() ?: now)
    val nextRun = rules.schedule.upcomingFrom(after).firstOrNull { it.start.toInstant() >= after && it.block.id != running?.block?.id }
    val next = nextRun?.let { "${it.block.name} at ${shortTime(it.start)}" }

    val head = "${day.format(weekday)} · ${day.dayOfMonth} ${day.month.name.lowercase().replaceFirstChar { it.uppercase() }}"
    val unguarded = state.commitments.sealedAt == null || !guardConnected(context)
    return HomeView(head, chapter, until, shown, essentialApps, next, unguarded)
}

/** Both services Covenant needs are switched on (R39). */
fun guardConnected(context: Context): Boolean = accessibilityEnabled(context) && listenerEnabled(context)

fun accessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val component = ComponentName(context, CovenantAccessibilityService::class.java).flattenToString()
    return enabled.split(':').any { it.equals(component, ignoreCase = true) } || CovenantAccessibilityService.instance != null
}

fun listenerEnabled(context: Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context) ||
        CovenantNotificationListener.instance != null

fun appIdOf(packageName: String) = AppId(packageName)
