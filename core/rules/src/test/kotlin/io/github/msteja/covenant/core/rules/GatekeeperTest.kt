package io.github.msteja.covenant.core.rules

import io.github.msteja.covenant.core.model.Activity
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.Block
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.Limit
import io.github.msteja.covenant.core.model.Pass
import io.github.msteja.covenant.core.model.Timeline
import io.github.msteja.covenant.core.model.UsageKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val ZONE = ZoneId.of("Asia/Kolkata")
private fun t(dateTime: String): Instant = LocalDateTime.parse(dateTime).atZone(ZONE).toInstant()

private val gmail = AppId("com.google.android.gm")
private val instagram = AppId("com.instagram.android")
private val phone = AppId("com.android.dialer")
private val uber = AppId("com.ubercab")

private val day = Timeline(
    listOf(
        Block("work", "Work", LocalTime.of(8, 0), LocalTime.of(16, 0), mapOf(gmail to Limit.Unlimited)),
        Block("evening", "Evening", LocalTime.of(16, 0), LocalTime.of(22, 0), mapOf(instagram to Limit.Minutes(30))),
        Block("night", "Night", LocalTime.of(22, 0), LocalTime.of(8, 0), grayscale = true),
    ),
)
private val covenant = Covenant(weekday = day, weekend = day, essentials = setOf(phone))
private val schedule = Schedule(covenant, ZONE)
private val exits = Exits(covenant, schedule)
private val gate = Gatekeeper(covenant, schedule, exits)
private val none = emptyMap<UsageKey, Duration>()

private fun decide(app: AppId, at: String, activity: Activity = Activity(), usage: Map<UsageKey, Duration> = none) =
    gate.decide(app, Moment(t(at)), activity, usage)

class GatekeeperTest {
    @Test
    fun `R6 essentials open in every block`() {
        assertEquals(Access.Open, decide(phone, "2026-10-23T23:00"))
    }

    @Test
    fun `R6 an outside app is blocked until its next block`() {
        val access = decide(instagram, "2026-10-23T11:20") as Access.Blocked
        assertEquals(Access.Reason.NOT_IN_BLOCK, access.reason)
        assertEquals(t("2026-10-23T16:00"), access.opensAt?.toInstant())
    }

    @Test
    fun `R8 an app in no block is blocked with nowhere to open`() {
        assertEquals(Access.Blocked(Access.Reason.NOT_IN_BLOCK, null), decide(uber, "2026-10-23T13:00"))
    }

    @Test
    fun `R11 a limited app shows the minutes it has left`() {
        val usage = mapOf(UsageKey(instagram, "evening", LocalDate.parse("2026-10-23")) to Duration.ofMinutes(12))
        val access = decide(instagram, "2026-10-23T17:00", usage = usage) as Access.Limited
        assertEquals(Duration.ofMinutes(18), access.left)
    }

    @Test
    fun `R13 at zero the app hides until its next block`() {
        val usage = mapOf(UsageKey(instagram, "evening", LocalDate.parse("2026-10-23")) to Duration.ofMinutes(30))
        val access = decide(instagram, "2026-10-23T17:00", usage = usage) as Access.Hidden
        assertEquals(t("2026-10-24T16:00"), access.until?.toInstant())
    }

    @Test
    fun `R14 unused minutes don't carry over`() {
        val usage = mapOf(UsageKey(instagram, "evening", LocalDate.parse("2026-10-23")) to Duration.ofMinutes(5))
        val access = decide(instagram, "2026-10-24T17:00", usage = usage) as Access.Limited
        assertEquals(Duration.ofMinutes(30), access.left)
    }

    @Test
    fun `R13 warnings come at 5 minutes and 1 minute left`() {
        assertEquals(listOf(Duration.ofMinutes(25), Duration.ofMinutes(29)), warningsAfter(Duration.ofMinutes(30)))
        assertEquals(listOf(Duration.ofMinutes(2)), warningsAfter(Duration.ofMinutes(3)))
    }

    @Test
    fun `R15 calls and navigation are never cut`() {
        assertEquals(Access.Open, gate.decide(uber, Moment(t("2026-10-23T23:00"), inCallOrNavigating = true), Activity(), none))
    }

    @Test
    fun `R27 Quiet keeps you on essentials until the next block`() {
        val quiet = exits.quiet(Activity(), t("2026-10-23T10:00"))!!
        assertEquals(t("2026-10-23T16:00"), quiet.quietUntil)
        val access = decide(gmail, "2026-10-23T10:01", quiet) as Access.Blocked
        assertEquals(Access.Reason.QUIET, access.reason)
        assertEquals(t("2026-10-24T08:00"), access.opensAt?.toInstant())
        assertEquals(Access.Open, decide(phone, "2026-10-23T10:01", quiet))
    }

    @Test
    fun `R27 Quiet for an hour ends after an hour`() {
        val quiet = exits.quiet(Activity(), t("2026-10-23T10:00"), Duration.ofMinutes(60))!!
        assertInstanceOf(Access.Blocked::class.java, decide(gmail, "2026-10-23T10:59", quiet))
        assertEquals(Access.Open, decide(gmail, "2026-10-23T11:00", quiet))
    }

    @Test
    fun `R27 Quiet can't be shortened`() {
        val long = exits.quiet(Activity(), t("2026-10-23T10:00"), Duration.ofMinutes(90))!!
        val shorter = exits.quiet(long, t("2026-10-23T10:10"), Duration.ofMinutes(30))!!
        assertEquals(t("2026-10-23T11:30"), shorter.quietUntil)
    }

    @Test
    fun `R55 Quiet beats a running pass`() {
        val activity = Activity(quietUntil = t("2026-10-23T16:00"), passes = listOf(Pass(uber, t("2026-10-23T10:00"))))
        assertInstanceOf(Access.Blocked::class.java, decide(uber, "2026-10-23T10:05", activity))
    }
}

class ExitsTest {
    @Test
    fun `R55 a pass opens after a 1-minute wait, for 10 minutes`() {
        val granted = exits.requestPass(uber, Activity(), t("2026-10-23T13:00")) as PassResult.Granted
        val activity = Activity(passes = listOf(granted.pass))
        assertInstanceOf(Access.Blocked::class.java, decide(uber, "2026-10-23T13:00:30", activity))
        assertEquals(Access.Open, decide(uber, "2026-10-23T13:01", activity))
        assertInstanceOf(Access.Blocked::class.java, decide(uber, "2026-10-23T13:11", activity))
    }

    @Test
    fun `R55 a pass opens an app whose time ran out`() {
        val usage = mapOf(UsageKey(instagram, "evening", LocalDate.parse("2026-10-23")) to Duration.ofMinutes(30))
        val activity = Activity(passes = listOf(Pass(instagram, t("2026-10-23T17:00"))))
        assertEquals(Access.Open, decide(instagram, "2026-10-23T17:05", activity, usage))
    }

    @Test
    fun `R55 a pass outlasts a block change`() {
        val activity = Activity(passes = listOf(Pass(uber, t("2026-10-23T15:58"))))
        assertEquals(Access.Open, decide(uber, "2026-10-23T16:05", activity))
    }

    @Test
    fun `R55 one pass at a time`() {
        val two = Exits(covenant.copy(passesPerWeek = 2), schedule)
        val activity = Activity(passes = listOf(Pass(uber, t("2026-10-23T13:00"))))
        assertInstanceOf(PassResult.AlreadyRunning::class.java, two.requestPass(gmail, activity, t("2026-10-23T13:05")))
    }

    @Test
    fun `R55 one a week, back 7 days after it was used`() {
        val activity = Activity(passes = listOf(Pass(uber, t("2026-10-20T10:00"))))
        assertEquals(0, exits.passesLeft(activity, t("2026-10-23T10:00")))
        assertEquals(PassResult.NoneLeft(t("2026-10-27T10:00")), exits.requestPass(uber, activity, t("2026-10-23T10:00")))
        assertInstanceOf(PassResult.Granted::class.java, exits.requestPass(uber, activity, t("2026-10-27T10:00")))
    }

    @Test
    fun `R55 with two a week, the older one comes back first`() {
        val two = Exits(covenant.copy(passesPerWeek = 2), schedule)
        val activity = Activity(passes = listOf(Pass(uber, t("2026-10-20T10:00")), Pass(uber, t("2026-10-22T10:00"))))
        assertEquals(PassResult.NoneLeft(t("2026-10-27T10:00")), two.requestPass(uber, activity, t("2026-10-23T10:00")))
    }

    @Test
    fun `R55 no passes when none are allowed`() {
        val none = Exits(covenant.copy(passesPerWeek = 0), schedule)
        assertEquals(PassResult.NoneLeft(null), none.requestPass(uber, Activity(), t("2026-10-23T10:00")))
    }

    @Test
    fun `R55 no pass during Quiet`() {
        val activity = Activity(quietUntil = t("2026-10-23T16:00"))
        assertEquals(PassResult.Quiet, exits.requestPass(uber, activity, t("2026-10-23T12:00")))
    }
}
