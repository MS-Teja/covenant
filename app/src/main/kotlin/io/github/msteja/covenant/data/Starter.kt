package io.github.msteja.covenant.data

import android.content.Context
import android.provider.Telephony
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.Block
import io.github.msteja.covenant.core.model.Commitments
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.Timeline
import java.time.LocalTime

/** The starting point the setup wizard offers: Work, Evening and Night (setup step 4). */
object Starter {
    fun timeline() = Timeline(
        listOf(
            Block("work", "Work", LocalTime.of(8, 0), LocalTime.of(16, 0)),
            Block("evening", "Evening", LocalTime.of(16, 0), LocalTime.of(22, 0)),
            Block("night", "Night", LocalTime.of(22, 0), LocalTime.of(8, 0), grayscale = true),
        ),
    )

    fun commitments() = Commitments(Covenant(weekday = timeline(), weekend = timeline()))

    /** Packages that commonly belong in essentials: maps, camera, clock, UPI apps, authenticators. */
    private val knownEssentials = listOf(
        "com.google.android.apps.maps",
        "com.google.android.GoogleCamera",
        "com.motorola.camera3",
        "com.motorola.camera2",
        "com.android.camera",
        "com.google.android.deskclock",
        "com.android.deskclock",
        "net.one97.paytm",
        "com.phonepe.app",
        "com.google.android.apps.nbu.paisa.user",
        "com.naviapp",
        "com.navi.pay",
        "com.sliceit.app",
        "indwin.c3.shareapp",
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
        "com.x8bit.bitwarden",
        "com.google.android.contacts",
    )

    /** Setup step 3: the phone and messages apps you use, plus anything above that's installed. */
    fun suggestedEssentials(context: Context, installed: List<InstalledApp>): Set<AppId> {
        val dialer = context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage
        val sms = Telephony.Sms.getDefaultSmsPackage(context)
        val wanted = buildSet {
            dialer?.let(::add)
            sms?.let(::add)
            addAll(knownEssentials)
        }
        return installed.map { it.id }.filter { it.packageName in wanted }.toSet()
    }
}
