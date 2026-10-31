package io.github.msteja.covenant.guard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import io.github.msteja.covenant.Graph
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.rules.Access
import io.github.msteja.covenant.core.rules.Moment
import io.github.msteja.covenant.core.rules.Route
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Parts of Android Covenant never blocks and whose notifications it never holds (Vocabulary: system allowlist). */
object SystemAllowlist {
    private val packages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.gms",
        // Updates and in-app update screens for every app, essentials included.
        "com.android.vending",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.android.providers.media.module",
        "com.google.android.providers.media.module",
        "com.android.contacts",
        "com.google.android.contacts",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
        "com.android.cellbroadcastreceiver",
        "com.google.android.cellbroadcastreceiver",
        "com.android.intentresolver",
        "com.android.credentialmanager",
        // The system launcher draws recents even when Covenant is home.
        "com.google.android.apps.nexuslauncher",
        "com.motorola.launcher3",
        "com.android.launcher3",
    )

    fun contains(context: Context, packageName: String): Boolean =
        packageName == context.packageName ||
            packageName in packages ||
            packageName == currentKeyboard(context) ||
            packageName == defaultHome(context) ||
            packageName == context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage

    private var home: String? = null
    private var homeCheckedAt = 0L

    /** The home app Android opens on Home, rechecked every few seconds. Null when none is chosen. */
    fun defaultHome(context: Context): String? {
        val now = SystemClock.uptimeMillis()
        if (homeCheckedAt == 0L || now - homeCheckedAt > HOME_RECHECK_MILLIS) {
            home = context.packageManager
                .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.takeIf { it != "android" }
            homeCheckedAt = now
        }
        return home
    }

    private const val HOME_RECHECK_MILLIS = 10_000L

    private fun currentKeyboard(context: Context): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
}

/** The bridge between Android services and the rule engine. */
object Guard {
    /** Apps with an ongoing navigation notification right now (R15). */
    val navigating: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun inCall(context: Context): Boolean = when (context.getSystemService(AudioManager::class.java).mode) {
        AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_CALL_SCREENING -> true
        else -> false
    }

    fun moment(context: Context, app: AppId, now: Instant = Graph.now()) = Moment(
        now = now,
        systemAllowlisted = SystemAllowlist.contains(context, app.packageName),
        inCallOrNavigating = inCall(context) || app.packageName in navigating,
    )

    fun decide(context: Context, app: AppId, now: Instant = Graph.now()): Access {
        val state = Graph.store.current
        // Nothing is enforced until setup is finished: a fresh install never blocks the phone it lands on.
        if (!state.setupDone) return Access.Open
        return Graph.rules(state.commitments).decide(app, moment(context, app, now), state.activity, state.usageMap)
    }

    fun route(context: Context, app: AppId, isCall: Boolean, now: Instant = Graph.now()): Route {
        val state = Graph.store.current
        if (!state.setupDone) return Route.Deliver
        val moment = moment(context, app, now).copy(inCallOrNavigating = app.packageName in navigating)
        return Graph.rules(state.commitments).route(app, isCall, moment, state.activity, state.usageMap)
    }
}
