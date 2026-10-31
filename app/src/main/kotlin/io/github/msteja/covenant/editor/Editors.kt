package io.github.msteja.covenant.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.msteja.covenant.Graph
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.Block
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.Limit
import io.github.msteja.covenant.core.model.Timeline
import io.github.msteja.covenant.core.model.toMinuteOfDay
import io.github.msteja.covenant.core.rules.ChangeRules
import io.github.msteja.covenant.core.rules.MAX_BLOCKS
import io.github.msteja.covenant.core.rules.TimelineProblem
import io.github.msteja.covenant.core.rules.isNoLooser
import io.github.msteja.covenant.core.rules.problems
import io.github.msteja.covenant.data.AppState
import io.github.msteja.covenant.data.InstalledApp
import io.github.msteja.covenant.design.Ink
import io.github.msteja.covenant.design.QuietWord
import io.github.msteja.covenant.design.RunningHead
import io.github.msteja.covenant.design.Type
import io.github.msteja.covenant.design.WaveOrnament
import io.github.msteja.covenant.design.WordButton
import io.github.msteja.covenant.design.inkClick
import io.github.msteja.covenant.home.fullTime
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** Saving an edit: stricter parts apply now, looser parts wait the cooldown (R22). */
object Edits {
    fun problemsIn(covenant: Covenant): List<String> =
        covenant.weekday.problems().map { describe(it, covenant.weekday, "Weekdays") } +
            covenant.weekend.problems().map { describe(it, covenant.weekend, "Weekends") }

    fun waits(state: AppState, proposed: Covenant): Boolean =
        !ChangeRules().isCalibrating(state.commitments, Graph.now()) && !isNoLooser(proposed, state.commitments.current)

    fun saveNote(state: AppState, proposed: Covenant): String {
        if (proposed == state.commitments.latest) return "nothing changed"
        val hours = state.commitments.current.cooldown.toHours()
        return if (waits(state, proposed)) "looser parts wait $hours hours · tighter parts apply now" else "applies now"
    }

    fun save(proposed: Covenant) {
        Graph.store.launchUpdate { it.copy(commitments = ChangeRules().propose(it.commitments, proposed, Graph.now())) }
    }

    private fun describe(problem: TimelineProblem, timeline: Timeline, where: String): String {
        fun name(id: String) = timeline.blocks.firstOrNull { it.id == id }?.name ?: "A block"
        return when (problem) {
            is TimelineProblem.TooManyBlocks -> "$where: at most $MAX_BLOCKS blocks."
            is TimelineProblem.TooShort -> "$where: ${name(problem.blockId)} must last at least 30 minutes."
            is TimelineProblem.Overlap -> "$where: ${name(problem.firstId)} and ${name(problem.secondId)} overlap."
            is TimelineProblem.BlankName -> "$where: every block needs a name."
            is TimelineProblem.DuplicateId -> "$where: a block appears twice."
        }
    }
}

/** A page for editing: a running head, the content, and Save with a note saying when it applies. */
@Composable
fun EditorPage(
    title: String,
    onBack: () -> Unit,
    saveNote: String? = null,
    problems: List<String> = emptyList(),
    onSave: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { RunningHead(title) }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 20.dp), content = content)
        for (problem in problems) BasicText(problem, Modifier.fillMaxWidth().padding(bottom = 4.dp), style = Type.text(14.sp, Ink.bone, italic = true).copy(textAlign = TextAlign.Center))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            QuietWord("Back", onClick = onBack)
            if (onSave != null) {
                WordButton("Save", note = saveNote, enabled = problems.isEmpty() && saveNote != "nothing changed", onClick = onSave)
            }
        }
    }
}

private fun clock(time: LocalTime) = "%d:%02d".format(if (time.hour % 12 == 0) 12 else time.hour % 12, time.minute) +
    if (time.hour < 12) " am" else " pm"

/** Timeline list editor: add, name and time blocks, then drop apps in (Screens). */
@Composable
fun TimelineEditor(state: AppState, onBack: () -> Unit) {
    var draft by remember { mutableStateOf(state.commitments.latest) }
    var weekends by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    val timeline = if (weekends) draft.weekend else draft.weekday
    fun put(t: Timeline) {
        draft = if (weekends) draft.copy(weekend = t) else draft.copy(weekday = t)
    }

    editing?.let { id ->
        val block = timeline.blocks.firstOrNull { it.id == id }
        if (block != null) {
            BlockDetail(
                block = block,
                onDone = { updated ->
                    put(Timeline(timeline.blocks.map { if (it.id == id) updated else it }))
                    editing = null
                },
                onRemove = {
                    put(Timeline(timeline.blocks.filterNot { it.id == id }))
                    editing = null
                },
            )
            return
        }
    }

    EditorPage(
        title = "Timeline",
        onBack = onBack,
        saveNote = Edits.saveNote(state, draft),
        problems = Edits.problemsIn(draft),
        onSave = {
            Edits.save(draft)
            onBack()
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            WordButton("Weekdays", style = Type.text(18.sp, if (!weekends) Ink.bone else Ink.slate)) { weekends = false }
            WordButton("Weekends", style = Type.text(18.sp, if (weekends) Ink.bone else Ink.slate)) { weekends = true }
        }
        for (block in timeline.blocks.sortedBy { Math.floorMod(it.start.toMinuteOfDay() - 240, 1440) }) {
            Column(Modifier.fillMaxWidth().inkClick(block.name) { editing = block.id }.padding(vertical = 14.dp)) {
                BasicText(block.name, style = Type.display(26.sp, 360).copy(textAlign = TextAlign.Start))
                val apps = block.apps.size
                BasicText(
                    "${clock(block.start)} – ${clock(block.end)} · ${if (apps == 0) "essentials only" else "$apps ${if (apps == 1) "app" else "apps"}"}" +
                        if (block.grayscale) " · grayscale" else "",
                    style = Type.text(14.sp, Ink.mist, italic = true),
                )
            }
        }
        if (timeline.blocks.size < MAX_BLOCKS) {
            WordButton("Add a block", Modifier.fillMaxWidth(), note = "an hour long, in the first free gap") {
                val start = firstFreeHour(timeline)
                put(Timeline(timeline.blocks + Block(UUID.randomUUID().toString(), "New block", start, start.plusHours(1))))
            }
        }
        if (weekends) {
            WaveOrnament(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
            BasicText("Weekend days", Modifier.fillMaxWidth(), style = Type.smallCaps(14.sp).copy(textAlign = TextAlign.Center))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                for (day in DayOfWeek.entries) {
                    val on = day in draft.weekendDays
                    BasicText(
                        day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        Modifier.inkClick(day.name) {
                            draft = draft.copy(weekendDays = if (on) draft.weekendDays - day else draft.weekendDays + day)
                        }.padding(horizontal = 6.dp, vertical = 12.dp),
                        style = Type.text(15.sp, if (on) Ink.bone else Ink.dim),
                    )
                }
            }
            WordButton("Copy the weekday timeline", Modifier.fillMaxWidth()) { draft = draft.copy(weekend = draft.weekday) }
        }
    }
}

private fun firstFreeHour(timeline: Timeline): LocalTime {
    val taken = BooleanArray(24 * 60)
    for (block in timeline.blocks) {
        val from = block.start.toMinuteOfDay()
        for (m in 0 until block.length.toMinutes().toInt()) taken[(from + m) % taken.size] = true
    }
    for (hour in 0 until 24) {
        val from = ((4 + hour) % 24) * 60
        if ((0 until 60).none { taken[(from + it) % taken.size] }) return LocalTime.of((4 + hour) % 24, 0)
    }
    return LocalTime.of(12, 0)
}

private val limitSteps = listOf<Limit>(Limit.Unlimited) + listOf(10, 15, 20, 30, 45, 60, 90, 120).map { Limit.Minutes(it) }

/** Block detail: name, times, grayscale, and which apps it holds with their limits. */
@Composable
fun BlockDetail(block: Block, onDone: (Block) -> Unit, onRemove: () -> Unit) {
    var draft by remember { mutableStateOf(block) }
    val apps by Graph.catalog.apps.collectAsState()
    EditorPage(title = "Block", onBack = { onDone(draft) }) {
        BasicTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it.take(24)) },
            singleLine = true,
            textStyle = Type.display(30.sp, 360),
            cursorBrush = SolidColor(Ink.bone),
            modifier = Modifier.fillMaxWidth().drawBehind {
                drawLine(Ink.hair, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }.padding(bottom = 10.dp),
        )
        TimeRow("Starts", draft.start) { draft = draft.copy(start = it) }
        TimeRow("Ends", draft.end) { draft = draft.copy(end = it) }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText("Grayscale", style = Type.text(18.sp))
            WordButton(if (draft.grayscale) "on" else "off", style = Type.text(18.sp, Ink.mist, italic = true)) { draft = draft.copy(grayscale = !draft.grayscale) }
        }
        WaveOrnament(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp))
        BasicText("Apps in this block", Modifier.fillMaxWidth(), style = Type.smallCaps(14.sp).copy(textAlign = TextAlign.Center))
        BasicText("Tap an app to add or remove it. Tap its limit to change it.", Modifier.fillMaxWidth().padding(bottom = 8.dp), style = Type.text(13.sp, Ink.dim, italic = true).copy(textAlign = TextAlign.Center))
        val ordered = apps.sortedBy { if (it.id in draft.apps) 0 else 1 }
        for (app in ordered) AppLimitRow(app, draft.apps[app.id], onToggle = {
            draft = draft.copy(apps = if (app.id in draft.apps) draft.apps - app.id else draft.apps + (app.id to Limit.Unlimited))
        }, onCycle = {
            val current = draft.apps[app.id] ?: return@AppLimitRow
            val next = limitSteps[(limitSteps.indexOf(current) + 1) % limitSteps.size]
            draft = draft.copy(apps = draft.apps + (app.id to next))
        })
        WordButton("Remove this block", Modifier.fillMaxWidth().padding(top = 16.dp), style = Type.text(16.sp, Ink.mist), onClick = onRemove)
    }
}

@Composable
private fun TimeRow(label: String, time: LocalTime, onChange: (LocalTime) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = Type.text(18.sp))
        QuietWord("earlier") { onChange(time.minusMinutes(30)) }
        BasicText(clock(time), style = Type.text(18.sp, weight = 400).copy(fontFeatureSettings = Type.TABULAR))
        QuietWord("later") { onChange(time.plusMinutes(30)) }
    }
}

@Composable
private fun AppLimitRow(app: InstalledApp, limit: Limit?, onToggle: () -> Unit, onCycle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            app.label,
            Modifier.weight(1f).inkClick(app.label, onToggle).padding(vertical = 10.dp),
            style = Type.text(18.sp, if (limit != null) Ink.bone else Ink.slate),
        )
        if (limit != null) {
            BasicText(
                when (limit) {
                    Limit.Unlimited -> "no limit"
                    is Limit.Minutes -> "${limit.minutes} min"
                },
                Modifier.inkClick("Change limit", onCycle).padding(horizontal = 8.dp, vertical = 10.dp),
                style = Type.text(15.sp, Ink.mist, italic = true).copy(fontFeatureSettings = Type.TABULAR),
            )
        }
    }
}

/** Essentials and ring-through: the two always-on lists (Screens). */
@Composable
fun ListsEditor(state: AppState, onBack: () -> Unit) {
    var draft by remember { mutableStateOf(state.commitments.latest) }
    var calls by remember { mutableStateOf(false) }
    val apps by Graph.catalog.apps.collectAsState()
    EditorPage(
        title = "Essentials and calls",
        onBack = onBack,
        saveNote = Edits.saveNote(state, draft),
        onSave = {
            Edits.save(draft)
            onBack()
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            WordButton("Essentials", style = Type.text(18.sp, if (!calls) Ink.bone else Ink.slate)) { calls = false }
            WordButton("Ring-through", style = Type.text(18.sp, if (calls) Ink.bone else Ink.slate)) { calls = true }
        }
        BasicText(
            if (calls) "Calls from these apps ring in every block. Their messages still wait."
            else "Essentials open in every block, with no pause and no limit.",
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            style = Type.text(14.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center),
        )
        val chosen: Set<AppId> = if (calls) draft.ringThrough else draft.essentials
        for (app in apps.sortedBy { if (it.id in chosen) 0 else 1 }) {
            val on = app.id in chosen
            BasicText(
                app.label,
                Modifier.fillMaxWidth().inkClick(app.label) {
                    val next = if (on) chosen - app.id else chosen + app.id
                    draft = if (calls) draft.copy(ringThrough = next) else draft.copy(essentials = next)
                }.padding(vertical = 10.dp),
                style = Type.text(18.sp, if (on) Ink.bone else Ink.slate),
            )
        }
    }
}

/** Pending changes: countdowns, with cancel (R24). */
@Composable
fun PendingScreen(state: AppState, onBack: () -> Unit) {
    val now = Graph.now()
    EditorPage(title = "Pending changes", onBack = onBack) {
        if (state.commitments.pending.isEmpty()) {
            BasicText("Nothing is waiting.", Modifier.fillMaxWidth().padding(top = 40.dp), style = Type.display(24.sp, 340))
        }
        var before = state.commitments.current
        for (change in state.commitments.pending) {
            val left = Duration.between(now, change.landsAt)
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                for (line in describeChange(before, change.proposed)) {
                    BasicText(line, style = Type.text(18.sp).copy(textAlign = TextAlign.Center))
                }
                BasicText(
                    "lands ${fullTime(change.landsAt.atZone(Graph.zone), now)} · in ${left.toHours()} h ${left.toMinutesPart()} min",
                    Modifier.padding(top = 4.dp),
                    style = Type.text(14.sp, Ink.mist, italic = true).copy(fontFeatureSettings = Type.TABULAR),
                )
                QuietWord("Cancel") {
                    Graph.store.launchUpdate { it.copy(commitments = ChangeRules().cancel(it.commitments, change.id)) }
                }
            }
            before = change.proposed
        }
    }
}

/** Plain sentences for what a change loosens, so a countdown always says what it's counting to. */
fun describeChange(old: Covenant, new: Covenant): List<String> {
    val lines = mutableListOf<String>()
    fun label(id: AppId) = Graph.catalog.label(id)
    (new.essentials - old.essentials).forEach { lines += "${label(it)} becomes essential." }
    (new.ringThrough - old.ringThrough).forEach { lines += "${label(it)} calls ring through." }
    if (new.weeklyEmergencyPass && !old.weeklyEmergencyPass) lines += "The weekly emergency pass turns on."
    if (new.weekendDays != old.weekendDays) lines += "Weekend days change."
    if (new.cooldown < old.cooldown) lines += "The cooldown shortens to ${new.cooldown.toHours()} hours."
    for ((name, before, after) in listOf(Triple("Weekdays", old.weekday, new.weekday), Triple("Weekends", old.weekend, new.weekend))) {
        for (block in after.blocks) {
            val was = before.blocks.firstOrNull { it.id == block.id }
            if (was == null) {
                lines += "$name: ${block.name} is added."
                continue
            }
            if (was.start != block.start || was.end != block.end) lines += "$name: ${block.name} moves to ${clock(block.start)} – ${clock(block.end)}."
            if (was.grayscale && !block.grayscale) lines += "$name: ${block.name} loses grayscale."
            for ((app, limit) in block.apps) {
                val prior = was.apps[app]
                when {
                    prior == null -> lines += "$name: ${label(app)} joins ${block.name}."
                    prior != limit -> lines += "$name: ${label(app)} gets ${if (limit is Limit.Minutes) "${limit.minutes} min" else "no limit"} in ${block.name}."
                }
            }
        }
    }
    return lines.ifEmpty { listOf("A looser change.") }
}
