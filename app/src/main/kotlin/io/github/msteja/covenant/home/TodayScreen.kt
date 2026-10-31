package io.github.msteja.covenant.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.msteja.covenant.Graph
import io.github.msteja.covenant.core.model.DAY_START
import io.github.msteja.covenant.core.rules.Occurrence
import io.github.msteja.covenant.core.rules.isClean
import io.github.msteja.covenant.core.rules.startsAt
import io.github.msteja.covenant.data.AppState
import io.github.msteja.covenant.design.Ink
import io.github.msteja.covenant.design.RunningHead
import io.github.msteja.covenant.design.Type
import io.github.msteja.covenant.design.hueAt
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DAY_MINUTES = 24f * 60f

/** Today's page (R41): one ink ribbon for the day. Lived hours are inked, the rest stays faint. */
@Composable
fun TodayScreen(state: AppState, now: Instant) {
    val rules = Graph.rules(state.commitments)
    val day = rules.schedule.dayOf(now)
    val dayStart = ZonedDateTime.of(day, DAY_START, Graph.zone)
    val dayEnd = dayStart.plusDays(1)
    val runs = rules.schedule.occurrencesAround(now).filter { it.day == day || (it.end.isAfter(dayStart) && it.start.isBefore(dayStart)) }
    val unclean = runs.filter { !it.start.toInstant().isAfter(now) && !isClean(it, state.activity, emptyList()) }.toSet()
    val ripple by rememberInfiniteTransition(label = "ripple").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "ring",
    )

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(start = 16.dp, end = 24.dp, top = 28.dp, bottom = 20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RunningHead("Today")
            BasicText(
                day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                style = Type.display(24.sp, 380),
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 28.dp, bottom = 20.dp)) {
            val height = maxHeight
            fun yOf(time: ZonedDateTime): Float =
                (Duration.between(dayStart, time).toMinutes().coerceIn(0, DAY_MINUTES.toLong()) / DAY_MINUTES)
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(46.dp).fillMaxSize()) {
                    for (hour in listOf(0, 4, 8, 12, 16, 20, 24)) {
                        val label = when (val h = (4 + hour) % 24) { 0 -> "12 am"; 12 -> "noon"; in 1..11 -> "$h am"; else -> "${h - 12} pm" }
                        BasicText(
                            label,
                            Modifier.align(Alignment.TopEnd).offset(y = height * (hour / 24f) - 7.dp),
                            style = Type.text(11.sp, Ink.dim, italic = true).copy(textAlign = TextAlign.End),
                        )
                    }
                    BasicText(
                        "now",
                        Modifier.align(Alignment.TopEnd).offset(y = height * yOf(now.atZone(Graph.zone)) - 8.dp),
                        style = Type.smallCaps(13.sp, Ink.bone),
                    )
                }
                Box(Modifier.weight(1f).fillMaxSize().padding(start = 10.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val gap = 3.dp.toPx()
                        val nowY = size.height * yOf(now.atZone(Graph.zone))
                        for (run in runs) {
                            val top = size.height * yOf(maxOf(run.start, dayStart)) + gap / 2
                            val bottom = size.height * yOf(minOf(run.end, dayEnd)) - gap / 2
                            if (bottom <= top) continue
                            val colors = (0..6).map { i ->
                                val t = run.start.plusMinutes(Duration.between(run.start, run.end).toMinutes() * i / 6)
                                hueAt(t.toLocalTime()).copy(alpha = 0.46f)
                            }
                            drawRoundRect(
                                Brush.verticalGradient(colors, startY = top, endY = bottom),
                                topLeft = Offset(0f, top), size = Size(size.width, bottom - top),
                                cornerRadius = CornerRadius(14.dp.toPx()),
                            )
                            // The future stays faint: only lived hours are inked.
                            val faintFrom = maxOf(top, nowY)
                            if (faintFrom < bottom) {
                                clipRect(top = faintFrom, bottom = bottom) {
                                    drawRoundRect(
                                        Ink.ground.copy(alpha = 0.55f), topLeft = Offset(0f, top), size = Size(size.width, bottom - top),
                                        cornerRadius = CornerRadius(14.dp.toPx()),
                                    )
                                }
                            }
                            if (run in unclean) {
                                val pass = state.activity.passes.firstOrNull { it.startsAt.isAfter(run.start.toInstant()) && it.startsAt.isBefore(run.end.toInstant()) }
                                val at = pass?.startsAt?.atZone(Graph.zone) ?: run.start.plusMinutes(Duration.between(run.start, run.end).toMinutes() / 2)
                                val cy = size.height * yOf(at)
                                for (k in 0 until 3) {
                                    val p = (ripple + k / 3f) % 1f
                                    drawOval(
                                        Ink.tide.copy(alpha = 0.9f * (1 - p)),
                                        topLeft = Offset(size.width * 0.68f - 56.dp.toPx() * (0.2f + 1.6f * p), cy - 18.dp.toPx() * (0.2f + 1.6f * p)),
                                        size = Size(112.dp.toPx() * (0.2f + 1.6f * p), 36.dp.toPx() * (0.2f + 1.6f * p)),
                                        style = Stroke(1.2.dp.toPx()),
                                    )
                                }
                            }
                        }
                        drawLine(Ink.bone.copy(alpha = 0.75f), Offset(0f, nowY), Offset(size.width, nowY), strokeWidth = 1.dp.toPx())
                    }
                    for (run in runs) {
                        val top = height * yOf(maxOf(run.start, dayStart))
                        val lived = !run.start.toInstant().isAfter(now)
                        Row(Modifier.fillMaxWidth().offset(y = top).padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Column(Modifier.weight(1f)) {
                                BasicText(run.block.name, style = Type.text(17.sp, if (lived) Ink.bone else Ink.mist))
                                BasicText(
                                    "${fullTime(run.start, now).substringBefore(" tomorrow")} – ${fullTime(run.end, now).substringBefore(" tomorrow")}",
                                    style = Type.text(12.sp, if (lived) Color(0xFFD0D7E0) else Ink.slate, italic = true),
                                )
                            }
                            if (lived) BasicText(if (run in unclean) "rippled" else "still", style = Type.smallCaps(13.sp, Color(0xFFD0D7E0)))
                        }
                    }
                }
            }
        }
        BasicText(summary(runs, unclean, now), Modifier.fillMaxWidth(), style = Type.text(15.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center))
    }
}

/** One written line for the day so far: "Night lay still. Work rippled once." */
private fun summary(runs: List<Occurrence>, unclean: Set<Occurrence>, now: Instant): String {
    val lived = runs.filter { !it.start.toInstant().isAfter(now) }
    if (lived.isEmpty()) return "The page turns at 4 am."
    return lived.joinToString(" ") { run ->
        val ended = !run.end.toInstant().isAfter(now)
        when {
            run in unclean -> "${run.block.name} rippled."
            ended -> "${run.block.name} lay still."
            else -> "${run.block.name} is still so far."
        }
    }
}
