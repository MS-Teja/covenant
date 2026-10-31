package io.github.msteja.covenant.guard

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import io.github.msteja.covenant.Graph
import io.github.msteja.covenant.R
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.HeldNotification
import io.github.msteja.covenant.core.rules.ChangeRules
import io.github.msteja.covenant.core.rules.dueSummaries
import io.github.msteja.covenant.core.rules.endsAt
import io.github.msteja.covenant.core.rules.startsAt
import io.github.msteja.covenant.data.AppState
import kotlinx.coroutines.launch
import java.time.Instant

/** Wakes Covenant exactly at the next moment anything changes: a block boundary, 4 am, a release. */
object Scheduler {
    private const val REQUEST = 41

    fun plan(context: Context, state: AppState) {
        val now = Graph.now()
        val rules = Graph.rules(state.commitments)
        val activity = state.activity
        val next = listOfNotNull(
            rules.schedule.nextBoundaryAfter(now),
            // R15: wake a minute early so a leaving app gets its warning before the boundary.
            rules.schedule.nextBoundaryAfter(now)?.minusSeconds(61),
            rules.schedule.nextDayStartAfter(now),
            state.held.minOfOrNull { it.releaseAt },
            state.commitments.pending.minOfOrNull { it.landsAt },
            activity.quietUntil,
        ).plus(activity.passes.flatMap { listOf(it.startsAt, it.endsAt) })
            .filter { it.isAfter(now) }
            .minOrNull() ?: return

        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, WakeReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), intent)
        }
    }

    /** Runs at every planned wake-up and whenever the screen comes on. */
    suspend fun wake(context: Context) {
        val now = Graph.now()
        val before = Graph.store.update { state ->
            state.copy(commitments = ChangeRules().settle(state.commitments, now)).pruned(now)
        }
        val due = dueSummaries(before.held, now)
        if (due.isNotEmpty()) {
            due.forEach { (app, held) -> Notifier.postSummary(context, app, held) }
            Graph.store.update { state -> state.copy(held = state.held.filter { it.releaseAt.isAfter(now) }) }
        }
        Grayscale.apply(context, Graph.rules().grayscaleAt(now))
        CovenantAccessibilityService.instance?.recheck()
        plan(context, Graph.store.current)
    }
}

class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Graph.scope.launch {
            try {
                Scheduler.wake(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Rebuilds the plan after a reboot or an update (Gaps: reboots). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Graph.scope.launch {
            try {
                Scheduler.wake(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}

/** R16: grayscale through Android's colour correction, when you've granted the secure-settings permission. */
object Grayscale {
    private const val ENABLED = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"

    fun canApply(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun apply(context: Context, on: Boolean) {
        if (!canApply(context)) return
        val resolver = context.contentResolver
        val wanted = if (on) 1 else 0
        runCatching {
            if (Settings.Secure.getInt(resolver, ENABLED, 0) != wanted) {
                if (on) Settings.Secure.putInt(resolver, MODE, 0)
                Settings.Secure.putInt(resolver, ENABLED, wanted)
            }
        }
    }
}

/** Covenant's own notifications: one summary per app when held messages are released (R17). */
object Notifier {
    const val HELD_CHANNEL = "held"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(HELD_CHANNEL, "Held messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Messages that waited for their app's block, one summary per app"
            },
        )
    }

    fun postSummary(context: Context, app: AppId, held: List<HeldNotification>) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannels(context)
        val label = Graph.catalog.label(app)
        val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            PendingIntent.getActivity(context, app.packageName.hashCode(), it, PendingIntent.FLAG_IMMUTABLE)
        }
        val style = NotificationCompat.InboxStyle()
        held.takeLast(6).forEach { style.addLine(listOf(it.title, it.text).filter(String::isNotBlank).joinToString(": ")) }
        val notification = NotificationCompat.Builder(context, HELD_CHANNEL)
            .setSmallIcon(R.drawable.ic_seal_small)
            .setContentTitle(label)
            .setContentText(if (held.size == 1) "1 held message" else "${held.size} held messages")
            .setStyle(style)
            .setContentIntent(launch)
            .setAutoCancel(true)
            .setWhen(held.last().postedAt.toEpochMilli())
            .build()
        context.getSystemService(NotificationManager::class.java).notify(app.packageName.hashCode(), notification)
    }
}

fun Instant.isPast(now: Instant = Graph.now()) = !isAfter(now)
