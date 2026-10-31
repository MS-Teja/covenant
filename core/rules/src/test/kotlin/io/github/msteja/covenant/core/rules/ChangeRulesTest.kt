package io.github.msteja.covenant.core.rules

import io.github.msteja.covenant.core.model.Activity
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.Block
import io.github.msteja.covenant.core.model.Commitments
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.HeldNotification
import io.github.msteja.covenant.core.model.Limit
import io.github.msteja.covenant.core.model.Pass
import io.github.msteja.covenant.core.model.Timeline
import io.github.msteja.covenant.core.model.UsageKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val Z = ZoneId.of("Asia/Kolkata")
private fun at(s: String): Instant = LocalDateTime.parse(s).atZone(Z).toInstant()

private val insta = AppId("com.instagram.android")
private val slack = AppId("com.Slack")
private val whatsapp = AppId("com.whatsapp")

private fun covenantWith(evening: Block, extra: List<Block> = emptyList()): Covenant {
    val work = Block("work", "Work", LocalTime.of(8, 0), LocalTime.of(16, 0), mapOf(slack to Limit.Unlimited))
    val night = Block("night", "Night", LocalTime.of(22, 0), LocalTime.of(8, 0), grayscale = true)
    val day = Timeline(listOf(work, evening, night) + extra)
    return Covenant(weekday = day, weekend = day)
}

private fun evening(start: Int = 16, end: Int = 22, minutes: Int = 30) =
    Block("evening", "Evening", LocalTime.of(start, 0), LocalTime.of(end, 0), mapOf(insta to Limit.Minutes(minutes)))

private val base = covenantWith(evening())
private val rules = ChangeRules { "change" }
private val sealed = Commitments(current = base, sealedAt = at("2026-10-01T09:00"))

class LoosenessTest {
    @Test
    fun `R22 lowering a limit is stricter`() {
        assertTrue(isNoLooser(covenantWith(evening(minutes = 20)), base))
    }

    @Test
    fun `R23 raising a limit is looser`() {
        assertFalse(isNoLooser(covenantWith(evening(minutes = 60)), base))
    }

    @Test
    fun `R23 growing a block is looser`() {
        assertFalse(isNoLooser(covenantWith(evening(end = 23)).withNightFrom(23), base))
    }

    @Test
    fun `R23 splitting a limited block into two budgets is looser`() {
        val split = covenantWith(
            evening(end = 19),
            listOf(Block("late", "Late", LocalTime.of(19, 0), LocalTime.of(22, 0), mapOf(insta to Limit.Minutes(30)))),
        )
        assertFalse(isNoLooser(split, base))
    }

    @Test
    fun `R23 adding essentials, turning grayscale off and shortening the cooldown are looser`() {
        assertFalse(isNoLooser(base.copy(essentials = setOf(whatsapp)), base))
        val noGray = base.copy(weekday = Timeline(base.weekday.blocks.map { it.copy(grayscale = false) }))
        assertFalse(isNoLooser(noGray, base))
        assertFalse(isNoLooser(base.copy(cooldown = Duration.ofHours(24)), base))
    }

    @Test
    fun `R26 lengthening the cooldown is stricter`() {
        assertTrue(isNoLooser(base.copy(cooldown = Duration.ofHours(96)), base))
    }

    @Test
    fun `R55 raising passes per week is looser, lowering them is stricter`() {
        assertFalse(isNoLooser(base.copy(passesPerWeek = 2), base))
        assertTrue(isNoLooser(base.copy(passesPerWeek = 0), base))
    }
}

class ChangeRulesTest {
    @Test
    fun `R22 a stricter change applies at once`() {
        val result = rules.propose(sealed, covenantWith(evening(minutes = 20)), at("2026-10-23T21:00"))
        assertEquals(covenantWith(evening(minutes = 20)), result.current)
        assertTrue(result.pending.isEmpty())
    }

    @Test
    fun `R22 a looser change waits 72 hours, then lands`() {
        val raised = covenantWith(evening(minutes = 60))
        val result = rules.propose(sealed, raised, at("2026-10-23T21:00"))
        assertEquals(base, result.current)
        assertEquals(at("2026-10-26T21:00"), result.pending.single().landsAt)
        assertEquals(raised, rules.settle(result, at("2026-10-26T21:00")).current)
    }

    @Test
    fun `R22 a mixed edit tightens now and loosens later`() {
        // Evening moves from 4-10 pm to 5-11 pm: 4-5 pm closes at once, 10-11 pm opens in 72 hours.
        val moved = rules.propose(sealed, covenantWith(evening(start = 17, end = 23)).withNightFrom(23), at("2026-10-23T09:00"))
        val effective = EffectiveRules(moved, Z)
        val none = emptyMap<UsageKey, Duration>()
        assertInstanceOf(Access.Blocked::class.java, effective.decide(insta, Moment(at("2026-10-23T16:30")), Activity(), none))
        assertInstanceOf(Access.Limited::class.java, effective.decide(insta, Moment(at("2026-10-23T17:30")), Activity(), none))
        assertInstanceOf(Access.Blocked::class.java, effective.decide(insta, Moment(at("2026-10-23T22:30")), Activity(), none))
    }

    @Test
    fun `R24 a pending change can be cancelled`() {
        val result = rules.propose(sealed, covenantWith(evening(minutes = 60)), at("2026-10-23T21:00"))
        assertTrue(rules.cancel(result, "change").pending.isEmpty())
    }

    @Test
    fun `R25 every change is instant before the seal and during calibration week`() {
        val looser = covenantWith(evening(minutes = 60))
        assertEquals(looser, rules.propose(Commitments(base), looser, at("2026-10-23T21:00")).current)
        val justSealed = Commitments(base, sealedAt = at("2026-10-20T09:00"))
        assertEquals(looser, rules.propose(justSealed, looser, at("2026-10-23T21:00")).current)
    }

    @Test
    fun `R25 calibration can end early and never restarts`() {
        val justSealed = Commitments(base, sealedAt = at("2026-10-20T09:00"))
        val ended = rules.endCalibration(justSealed, at("2026-10-21T09:00"))
        assertFalse(rules.isCalibrating(ended, at("2026-10-21T10:00")))
        assertEquals(ended, rules.seal(ended, at("2026-10-22T09:00")))
    }

    @Test
    fun `R16 grayscale switches with the block`() {
        val effective = EffectiveRules(sealed, Z)
        assertTrue(effective.grayscaleAt(at("2026-10-23T23:00")))
        assertFalse(effective.grayscaleAt(at("2026-10-23T12:00")))
    }
}

class NotificationRouteTest {
    private val effective = EffectiveRules(sealed.copy(current = base.copy(ringThrough = setOf(whatsapp))), Z)
    private val none = emptyMap<UsageKey, Duration>()

    @Test
    fun `R17 an outside app's notification is held until its next block`() {
        assertEquals(Route.Hold(at("2026-10-23T16:00")), effective.route(insta, false, Moment(at("2026-10-23T11:20")), Activity(), none))
    }

    @Test
    fun `R18 an app in no block is dismissed`() {
        assertEquals(Route.Dismiss, effective.route(whatsapp, false, Moment(at("2026-10-23T11:20")), Activity(), none))
    }

    @Test
    fun `R20 calls from ring-through apps ring outside their block`() {
        assertEquals(Route.RingThrough, effective.route(whatsapp, true, Moment(at("2026-10-23T23:00")), Activity(), none))
        // Not on the list: the call is silenced and held for Instagram's next block.
        assertEquals(Route.Hold(at("2026-10-24T16:00")), effective.route(insta, true, Moment(at("2026-10-24T02:00")), Activity(), none))
    }

    @Test
    fun `R17 held notifications come out as one summary per app`() {
        val held = listOf(
            HeldNotification(insta, "a", "1", at("2026-10-23T11:00"), at("2026-10-23T16:00")),
            HeldNotification(insta, "b", "2", at("2026-10-23T12:00"), at("2026-10-23T16:00")),
            HeldNotification(slack, "c", "3", at("2026-10-23T23:00"), at("2026-10-24T08:00")),
        )
        assertEquals(setOf(insta), dueSummaries(held, at("2026-10-23T16:00")).keys)
        assertEquals(2, dueSummaries(held, at("2026-10-23T16:00")).getValue(insta).size)
    }

    @Test
    fun `R40 a pass costs the block its clean record`() {
        val run = Schedule(base, Z).activeAt(at("2026-10-23T12:00"))!!
        assertTrue(isClean(run, Activity(), emptyList()))
        assertFalse(isClean(run, Activity(passes = listOf(Pass(insta, at("2026-10-23T13:00")))), emptyList()))
    }
}

private fun Covenant.withNightFrom(hour: Int): Covenant {
    fun Timeline.shift() = Timeline(blocks.map { if (it.id == "night") it.copy(start = LocalTime.of(hour, 0)) else it })
    return copy(weekday = weekday.shift(), weekend = weekend.shift())
}
