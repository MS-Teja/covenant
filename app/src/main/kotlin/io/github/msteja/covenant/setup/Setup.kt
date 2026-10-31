package io.github.msteja.covenant.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.msteja.covenant.Graph
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.MAX_PASSES_PER_WEEK
import io.github.msteja.covenant.core.rules.ChangeRules
import io.github.msteja.covenant.data.AppState
import io.github.msteja.covenant.data.Starter
import io.github.msteja.covenant.design.Ink
import io.github.msteja.covenant.design.QuietWord
import io.github.msteja.covenant.design.RunningHead
import io.github.msteja.covenant.design.Type
import io.github.msteja.covenant.design.WaveOrnament
import io.github.msteja.covenant.design.WordButton
import io.github.msteja.covenant.design.inkClick
import io.github.msteja.covenant.editor.TimelineEditor
import java.time.format.TextStyle
import java.util.Locale

private enum class Step(val head: String) {
    Welcome("Covenant"),
    Essentials("Essentials"),
    Timeline("Your day"),
    Calls("Ring-through"),
    Exits("Passes"),
    Permissions("Permissions"),
    Seal("Your covenant"),
}

/**
 * The setup wizard (Setup). Before the seal every change is instant (R25), so each step saves as
 * you go. The seal can wait until v0.1 passes its checklist.
 */
@Composable
fun SetupWizard(state: AppState) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(Step.Welcome) }
    var timelineOpen by remember { mutableStateOf(false) }
    val installed by Graph.catalog.apps.collectAsState()
    val covenant = state.commitments.latest

    fun save(next: Covenant) = Graph.store.launchUpdate {
        it.copy(commitments = ChangeRules().propose(it.commitments, next, Graph.now()))
    }
    fun finish(seal: Boolean) = Graph.store.launchUpdate {
        val commitments = if (seal) ChangeRules().seal(it.commitments, Graph.now()) else it.commitments
        it.copy(setupDone = true, commitments = commitments)
    }

    if (timelineOpen) {
        TimelineEditor(state, onBack = { timelineOpen = false })
        return
    }

    var suggested by remember { mutableStateOf(false) }
    if (!suggested && covenant.essentials.isEmpty() && installed.isNotEmpty()) {
        suggested = true
        save(covenant.copy(essentials = Starter.suggestedEssentials(context, installed)))
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { RunningHead(step.head) }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
            when (step) {
                Step.Welcome -> Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText("A different phone at each time of day.", style = Type.display(30.sp, 340))
                    BasicText(
                        "You split the day into blocks, each with its own apps. Anything that gives an app more time waits ${covenant.cooldown.toHours()} hours.",
                        Modifier.padding(top = 18.dp),
                        style = Type.text(16.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center),
                    )
                    WaveOrnament(Modifier.padding(top = 28.dp))
                }
                Step.Essentials -> AppChooser(
                    note = "Essentials open in every block. Covenant suggests your phone, messages, maps, camera, clock, UPI apps and authenticator.",
                    chosen = covenant.essentials,
                ) { save(covenant.copy(essentials = it)) }
                Step.Timeline -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText("Weekdays", style = Type.smallCaps(14.sp))
                    BasicText(vow(covenant.weekday), Modifier.padding(top = 6.dp), style = Type.display(22.sp, 340))
                    BasicText("Weekends", Modifier.padding(top = 22.dp), style = Type.smallCaps(14.sp))
                    BasicText(vow(covenant.weekend), Modifier.padding(top = 6.dp), style = Type.display(22.sp, 340))
                    BasicText(
                        "Weekend days: " + covenant.weekendDays.sorted().joinToString(", ") { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) },
                        Modifier.padding(top = 10.dp),
                        style = Type.text(14.sp, Ink.mist, italic = true),
                    )
                    WordButton("Edit blocks and their apps", Modifier.padding(top = 24.dp), note = "up to 5 blocks · drop apps in · time limits · grayscale") {
                        timelineOpen = true
                    }
                }
                Step.Calls -> AppChooser(
                    note = "Calls from these apps ring even outside their block. Their messages still wait.",
                    chosen = covenant.ringThrough,
                ) { save(covenant.copy(ringThrough = it)) }
                Step.Exits -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(
                        "A pass opens one hidden app for 10 minutes, after a 1-minute wait. Each comes back 7 days after you use it.",
                        style = Type.text(17.sp).copy(textAlign = TextAlign.Center),
                    )
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.Center) {
                        for (count in 0..MAX_PASSES_PER_WEEK) {
                            WordButton("$count", style = Type.display(24.sp, 360, if (covenant.passesPerWeek == count) Ink.bone else Ink.slate)) {
                                save(covenant.copy(passesPerWeek = count))
                            }
                        }
                    }
                    BasicText(
                        "a week. One is enough for most weeks; more waits ${covenant.cooldown.toHours()} hours later.",
                        Modifier.padding(top = 6.dp),
                        style = Type.text(14.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center),
                    )
                }
                Step.Permissions -> PermissionRows()
                Step.Seal -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SealCeremony(covenant) { finish(seal = true) }
                    QuietWord("Not yet", Modifier.padding(top = 18.dp)) { finish(seal = false) }
                    BasicText(
                        "Until you seal, Covenant runs unguarded and every change is instant.",
                        style = Type.text(13.sp, Ink.dim, italic = true).copy(textAlign = TextAlign.Center),
                    )
                }
            }
        }
        if (step != Step.Seal) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (step != Step.Welcome) QuietWord("Back") { step = Step.entries[step.ordinal - 1] } else Box(Modifier)
                WordButton(if (step == Step.Welcome) "Begin" else "Next") { step = Step.entries[step.ordinal + 1] }
            }
        }
    }
}

@Composable
private fun AppChooser(note: String, chosen: Set<AppId>, onChange: (Set<AppId>) -> Unit) {
    val apps by Graph.catalog.apps.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        BasicText(note, Modifier.fillMaxWidth().padding(bottom = 12.dp), style = Type.text(14.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center))
        for (app in apps.sortedBy { if (it.id in chosen) 0 else 1 }) {
            val on = app.id in chosen
            BasicText(
                app.label,
                Modifier.fillMaxWidth().inkClick(app.label) { onChange(if (on) chosen - app.id else chosen + app.id) }.padding(vertical = 10.dp),
                style = Type.text(18.sp, if (on) Ink.bone else Ink.slate),
            )
        }
    }
}
