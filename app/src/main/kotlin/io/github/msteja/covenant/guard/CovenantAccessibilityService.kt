package io.github.msteja.covenant.guard

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.msteja.covenant.Graph
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.UsageKey
import io.github.msteja.covenant.core.rules.Access
import io.github.msteja.covenant.core.rules.warningsAfter
import io.github.msteja.covenant.design.InkScreen
import io.github.msteja.covenant.design.hueAt
import io.github.msteja.covenant.home.BlockedScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * Watches which app comes to the front and asks the rule engine about it, so a notification tap or
 * a link meets the same wall as the home screen (R6, R7). It reads app names only, never screen content.
 */
class CovenantAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlays: OverlayHost
    private var foreground: String? = null
    /** The foreground app when it's a browser tab an allowed app opened, which rides on that app's visit (R60). */
    private var handedOff: String? = null
    private var since: Instant? = null
    private val windowKinds = mutableMapOf<String, Boolean>()
    private val debuggable by lazy { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
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
        val className = event.className?.toString()
        if (packageName == foreground) return
        // Dialogs, pop-ups, the keyboard and overlays (Covenant's own included) sit over the app rather than replace it.
        if (!isActivityWindow(packageName, className)) return
        val cameFrom = foreground
        val handOff = className.isBrowserTab() && cameFrom != null && isOpenNow(cameFrom)
        leave(cameFrom)
        foreground = packageName
        handedOff = if (handOff) packageName else null
        since = Graph.now()
        // A different app, other than the home screen the guard just sent you to, ends the blocked moment.
        if (packageName != SystemAllowlist.defaultHome(this)) overlays.dismiss(BLOCKED)
        check(packageName, className, arriving = true)
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
        // Judge only once the time spent so far is saved, or a limit that just ran out still looks open.
        val saved = flushUsage(app)
        Graph.scope.launch {
            saved?.join()
            handler.post { if (foreground == app) judge(app) }
        }
    }

    private fun judge(app: String) {
        if (SystemAllowlist.contains(this, app) || app == handedOff) return
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

    private fun check(app: String, className: String?, arriving: Boolean) {
        cancelTimers()
        if (SystemAllowlist.contains(this, app)) {
            log(app, className, "allowlisted")
            overlays.dismiss(PAUSE)
            return
        }
        if (app == handedOff) {
            log(app, className, "hand-off from an allowed app")
            return
        }
        val access = Guard.decide(this, AppId(app))
        log(app, className, access.toString())
        when (access) {
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

    /**
     * "Closes" means sent home with re-entry blocked, and the blocked screen says why (R7). It's drawn
     * over whatever the home screen is, so it explains itself even before Covenant is the home app.
     */
    private fun sendHome(app: String) {
        cancelTimers()
        overlays.dismissAll()
        releaseAudio()
        performGlobalAction(GLOBAL_ACTION_HOME)
        showBlocked(AppId(app))
    }

    private fun showBlocked(app: AppId) {
        overlays.show(BLOCKED, fullScreen = true, onBack = { overlays.dismiss(BLOCKED) }) { dismiss ->
            val state by Graph.store.state.collectAsState()
            InkScreen(hue = hueAt(LocalTime.now())) {
                state?.let { BlockedScreen(app, it, onOpened = dismiss, onBack = dismiss) }
            }
        }
    }

    /** True for a window that is one of [pkg]'s activities: a real switch to that app. */
    private fun isActivityWindow(pkg: String, cls: String?): Boolean {
        if (cls == null) return true
        return windowKinds.getOrPut("$pkg/$cls") {
            try {
                packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                // Not one of its activities. If Covenant can't see the package at all, it can't tell, so it counts.
                runCatching { packageManager.getPackageInfo(pkg, 0) }.isFailure
            }
        }
    }

    /** Whether [app] may be on screen right now, so a browser tab it opens can ride on its visit. */
    private fun isOpenNow(app: String): Boolean =
        SystemAllowlist.contains(this, app) || app == handedOff || !Guard.decide(this, AppId(app)).closes()

    /** Browser tabs other apps open for sign-ins, payments and help pages (Custom Tabs, Auth Tabs, Firefox's equivalent). */
    private fun String?.isBrowserTab(): Boolean =
        this != null && BROWSER_TABS.any { endsWith(it) }

    private fun log(app: String, className: String?, decision: String) {
        if (debuggable) Log.i(TAG, "$app ($className): $decision")
    }

    private fun leave(app: String?) {
        app ?: return
        flushUsage(app)
        lastLeft[app] = Graph.now()
        cancelTimers()
        overlays.dismiss(PAUSE)
    }

    /** Records on-screen time against the block it was spent in (R11). */
    private fun flushUsage(app: String): Job? {
        val start = since ?: return null
        val now = Graph.now()
        since = now
        if (SystemAllowlist.contains(this, app) || app == handedOff) return null
        val run = Graph.rules().schedule.activeAt(start) ?: return null
        val spent = Duration.between(start, now)
        return Graph.store.launchUpdate { it.addUsage(UsageKey(AppId(app), run.block.id, run.day), spent) }
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

        private const val TAG = "CovenantGuard"
        private const val PAUSE = "pause"
        private const val WARNING = "warning"
        private const val BLOCKED = "blocked"
        private val BROWSER_TABS = listOf("CustomTabActivity", "AuthTabActivity", "ExternalAppBrowserActivity")
        private val VISIT_GAP: Duration = Duration.ofSeconds(30)
        private val WARNING_SHOWN: Duration = Duration.ofSeconds(8)
    }
}
