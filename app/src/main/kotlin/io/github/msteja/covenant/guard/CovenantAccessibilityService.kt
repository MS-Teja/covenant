package io.github.msteja.covenant.guard

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import io.github.msteja.covenant.Graph
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.UsageKey
import io.github.msteja.covenant.core.rules.Access
import io.github.msteja.covenant.core.rules.warningsAfter
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Watches which app comes to the front and asks the rule engine about it, so a notification tap or
 * a link meets the same wall as the home screen (R6, R7). It reads app names only, never screen content.
 */
class CovenantAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlays: OverlayHost
    private var foreground: String? = null
    private var since: Instant? = null
    private val lastLeft = mutableMapOf<String, Instant>()
    private val timers = mutableListOf<Runnable>()
    private var focus: AudioFocusRequest? = null

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // R11: time counts only while the app is on screen.
                Intent.ACTION_SCREEN_OFF -> {
                    leave(foreground)
                    foreground = null
                }
                Intent.ACTION_USER_PRESENT -> Graph.scope.launch { Scheduler.wake(applicationContext) }
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
        overlays = OverlayHost(this)
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == foreground) return
        leave(foreground)
        foreground = packageName
        since = Graph.now()
        check(packageName, arriving = true)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(screen) }
        if (::overlays.isInitialized) overlays.dismissAll()
        super.onDestroy()
    }

    /** Called at block boundaries and other wake-ups: re-judges whatever is on screen now. */
    fun recheck() = handler.post {
        val app = foreground ?: return@post
        flushUsage(app)
        if (SystemAllowlist.contains(this, app)) return@post
        val now = Graph.now()
        val access = Guard.decide(this, AppId(app), now)
        val soon = Guard.decide(this, AppId(app), now.plusSeconds(61))
        when {
            access.closes() -> sendHome(app)
            soon.closes() -> warnBeforeBoundary(app)
            access is Access.Limited -> scheduleLimit(app, access.left)
        }
    }

    fun lockScreen() = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    private fun check(app: String, arriving: Boolean) {
        cancelTimers()
        if (SystemAllowlist.contains(this, app)) {
            overlays.dismiss(PAUSE)
            return
        }
        when (val access = Guard.decide(this, AppId(app))) {
            is Access.Blocked, is Access.Hidden -> sendHome(app)
            is Access.Limited -> {
                if (arriving && isNewVisit(app)) showPause(access)
                scheduleLimit(app, access.left)
            }
            Access.Open -> Unit
        }
    }

    /** A visit ends when you've been away for 30 seconds; coming back sooner skips the pause (R12). */
    private fun isNewVisit(app: String): Boolean {
        val left = lastLeft[app] ?: return true
        return Duration.between(left, Graph.now()) > VISIT_GAP
    }

    private fun showPause(access: Access.Limited) {
        holdAudio()
        overlays.show(PAUSE, fullScreen = true) { dismiss ->
            PauseOverlay(access.left.toMinutes().coerceAtLeast(1), access.block.block.name) {
                dismiss()
                releaseAudio()
            }
        }
    }

    /** R13: warnings at 5 minutes and 1 minute left; at zero the app closes. */
    private fun scheduleLimit(app: String, left: Duration) {
        cancelTimers()
        val label = Graph.catalog.label(AppId(app))
        for (after in warningsAfter(left)) {
            val remaining = left.minus(after).toMinutes()
            post(after) {
                warn("$label: $remaining ${if (remaining == 1L) "minute" else "minutes"} left.", "At zero it closes until its next block.")
            }
        }
        post(left.plusSeconds(1)) { if (foreground == app) recheck() }
    }

    /** R15: an app that's leaving gets a 1-minute warning, then closes at the boundary. */
    private fun warnBeforeBoundary(app: String) {
        cancelTimers()
        val label = Graph.catalog.label(AppId(app))
        val next = Graph.rules().schedule.activeAt(Graph.now().plusSeconds(61))?.block?.name
        warn("$label closes in 1 minute.", next?.let { "$it begins." } ?: "Essentials only after this.")
        post(Duration.ofSeconds(61)) { if (foreground == app) recheck() }
    }

    private fun warn(title: String, note: String) {
        overlays.show(WARNING, fullScreen = false) { dismiss -> WarningSheet(title, note, onOk = dismiss) }
        handler.postDelayed({ overlays.dismiss(WARNING) }, WARNING_SHOWN.toMillis())
    }

    /** "Closes" means sent home with re-entry blocked; home explains why (R7, blocked screen). */
    private fun sendHome(app: String) {
        cancelTimers()
        overlays.dismissAll()
        releaseAudio()
        // Home reads the blocked app from here, whichever arrives first: this or the Home intent.
        Graph.blocked.value = AppId(app)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun leave(app: String?) {
        app ?: return
        flushUsage(app)
        lastLeft[app] = Graph.now()
        cancelTimers()
        overlays.dismiss(PAUSE)
    }

    /** Records on-screen time against the block it was spent in (R11). */
    private fun flushUsage(app: String) {
        val start = since ?: return
        val now = Graph.now()
        since = now
        if (SystemAllowlist.contains(this, app)) return
        val run = Graph.rules().schedule.activeAt(start) ?: return
        val spent = Duration.between(start, now)
        Graph.store.launchUpdate { it.addUsage(UsageKey(AppId(app), run.block.id, run.day), spent) }
    }

    private fun post(after: Duration, action: () -> Unit) {
        val runnable = Runnable(action)
        timers += runnable
        handler.postDelayed(runnable, after.toMillis().coerceAtLeast(0))
    }

    private fun cancelTimers() {
        timers.forEach(handler::removeCallbacks)
        timers.clear()
    }

    /** The app is already running under the pause; ask it to stay quiet until the pause ends (R12). */
    private fun holdAudio() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            .build()
        getSystemService(AudioManager::class.java).requestAudioFocus(request)
        focus = request
    }

    private fun releaseAudio() {
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun Access.closes() = this is Access.Blocked || this is Access.Hidden

    companion object {
        @Volatile
        var instance: CovenantAccessibilityService? = null
            private set

        private const val PAUSE = "pause"
        private const val WARNING = "warning"
        private val VISIT_GAP: Duration = Duration.ofSeconds(30)
        private val WARNING_SHOWN: Duration = Duration.ofSeconds(8)
    }
}
