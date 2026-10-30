package io.github.msteja.covenant.guard

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.msteja.covenant.Graph
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.HeldNotification
import io.github.msteja.covenant.core.rules.Route
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds notifications from outside apps until their next block (R17), dismisses those from apps in
 * no block (R18), lets ring-through calls ring (R20), and replays sounds only for allowed apps.
 */
class CovenantNotificationListener : NotificationListenerService() {
    private val alerted: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun onListenerConnected() {
        instance = this
        applyHints()
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    /** Android's own notification sounds go quiet while Covenant replays the ones that should ring. */
    fun applyHints() {
        requestListenerHints(if (Graph.store.current.ownsAlerts) HINT_HOST_DISABLE_NOTIFICATION_EFFECTS else 0)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val packageName = sbn.packageName
        val notification = sbn.notification
        if (sbn.isOngoing && notification.category == Notification.CATEGORY_NAVIGATION) Guard.navigating += packageName

        if (SystemAllowlist.contains(this, packageName)) {
            replay(sbn, rankingMap)
            return
        }
        // Ongoing notifications (music, uploads) can't be held; audio keeps playing (R21).
        if (sbn.isOngoing) return

        val isCall = notification.category == Notification.CATEGORY_CALL
        when (val route = Guard.route(this, AppId(packageName), isCall)) {
            Route.Deliver, Route.RingThrough -> replay(sbn, rankingMap)
            is Route.Hold -> {
                cancelNotification(sbn.key)
                if (notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) hold(sbn, route.releaseAt)
            }
            Route.Dismiss -> {
                cancelNotification(sbn.key)
                if (notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) {
                    Graph.store.launchUpdate { it.copy(dismissed = it.dismissed + 1) }
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.notification.category == Notification.CATEGORY_NAVIGATION) Guard.navigating -= sbn.packageName
        alerted -= sbn.key
    }

    private fun hold(sbn: StatusBarNotification, releaseAt: Instant) {
        val extras = sbn.notification.extras
        val held = HeldNotification(
            app = AppId(sbn.packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            postedAt = Instant.ofEpochMilli(sbn.postTime),
            releaseAt = releaseAt,
        )
        Graph.store.launchUpdate { it.copy(held = it.held + held) }
    }

    /** Plays a delivered notification's sound and vibration the way Android would have. */
    private fun replay(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (!Graph.store.current.ownsAlerts) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && sbn.key in alerted) return
        val isSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (isSummary && notification.groupAlertBehavior == Notification.GROUP_ALERT_CHILDREN) return
        if (!isSummary && notification.group != null && notification.groupAlertBehavior == Notification.GROUP_ALERT_SUMMARY) return

        val ranking = Ranking()
        val ranked = rankingMap?.getRanking(sbn.key, ranking) == true
        if (ranked && (!ranking.matchesInterruptionFilter() || ranking.importance < NotificationManager.IMPORTANCE_DEFAULT)) return
        val channel = if (ranked) ranking.channel else null
        alerted += sbn.key
        Alerts.play(this, channel?.sound ?: if (channel == null) Settings.System.DEFAULT_NOTIFICATION_URI else null, channel?.shouldVibrate() ?: true, channel?.vibrationPattern)
    }

    companion object {
        @Volatile
        var instance: CovenantNotificationListener? = null
            private set
    }
}

/** Notification sound and vibration, respecting silent and vibrate modes. */
object Alerts {
    private val defaultPattern = longArrayOf(0, 220, 140, 220)

    fun play(context: Context, sound: android.net.Uri?, vibrate: Boolean, pattern: LongArray?) {
        val audio = context.getSystemService(AudioManager::class.java)
        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> return
            AudioManager.RINGER_MODE_VIBRATE -> vibrate(context, pattern)
            else -> {
                sound?.let { uri ->
                    RingtoneManager.getRingtone(context, uri)?.apply {
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        play()
                    }
                }
                if (vibrate) vibrate(context, pattern)
            }
        }
    }

    private fun vibrate(context: Context, pattern: LongArray?) {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern ?: defaultPattern, -1))
    }
}
