package io.github.msteja.covenant.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.msteja.covenant.Graph
import io.github.msteja.covenant.core.model.CALIBRATION
import io.github.msteja.covenant.core.rules.ChangeRules
import io.github.msteja.covenant.data.AppState
import io.github.msteja.covenant.design.Ink
import io.github.msteja.covenant.design.QuietWord
import io.github.msteja.covenant.design.Type
import io.github.msteja.covenant.design.WaveOrnament
import io.github.msteja.covenant.design.WordButton
import io.github.msteja.covenant.guard.CovenantNotificationListener
import io.github.msteja.covenant.home.fullTime
import io.github.msteja.covenant.setup.PermissionRows
import io.github.msteja.covenant.setup.SealCeremony
import java.time.Duration

private val MIN_COOLDOWN: Duration = Duration.ofHours(72)
private val MAX_COOLDOWN: Duration = Duration.ofDays(7)

/** Settings: cooldown, exits, alert sounds, protection health and the seal. */
@Composable
fun SettingsScreen(state: AppState, onBack: () -> Unit) {
    var sealing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(state.commitments.latest) }
    val now = Graph.now()
    val rules = ChangeRules()

    if (sealing) {
        EditorPage(title = "Your covenant", onBack = { sealing = false }) {
            SealCeremony(state.commitments.current) {
                Graph.store.launchUpdate { it.copy(commitments = rules.seal(it.commitments, Graph.now())) }
                sealing = false
            }
        }
        return
    }

    EditorPage(
        title = "Settings",
        onBack = onBack,
        saveNote = Edits.saveNote(state, draft),
        onSave = { Edits.save(draft) },
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("Cooldown", style = Type.text(18.sp))
                BasicText("how long a looser change waits", style = Type.text(13.sp, Ink.mist, italic = true))
            }
            QuietWord("shorter") { draft = draft.copy(cooldown = maxOf(MIN_COOLDOWN, draft.cooldown.minusHours(24))) }
            BasicText("${draft.cooldown.toHours()} h", style = Type.text(18.sp, weight = 400).copy(fontFeatureSettings = Type.TABULAR))
            QuietWord("longer") { draft = draft.copy(cooldown = minOf(MAX_COOLDOWN, draft.cooldown.plusHours(24))) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("Weekly emergency pass", style = Type.text(18.sp))
                BasicText("30 minutes of everything, once a week", style = Type.text(13.sp, Ink.mist, italic = true))
            }
            QuietWord(if (draft.weeklyEmergencyPass) "on" else "off") { draft = draft.copy(weeklyEmergencyPass = !draft.weeklyEmergencyPass) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("Covenant plays alert sounds", style = Type.text(18.sp))
                BasicText("held apps stay silent; allowed apps still ring", style = Type.text(13.sp, Ink.mist, italic = true))
            }
            QuietWord(if (state.ownsAlerts) "on" else "off") {
                Graph.store.launchUpdate { it.copy(ownsAlerts = !it.ownsAlerts) }
                // The listener reads the new setting once the store has saved it.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ CovenantNotificationListener.instance?.applyHints() }, 300)
            }
        }

        WaveOrnament(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
        BasicText("Protections", Modifier.fillMaxWidth(), style = Type.smallCaps(14.sp).copy(textAlign = TextAlign.Center))
        PermissionRows()

        WaveOrnament(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
        val sealedAt = state.commitments.sealedAt
        when {
            sealedAt == null -> {
                BasicText(
                    "Not sealed. Covenant runs unguarded and every change is instant until you seal.",
                    Modifier.fillMaxWidth(),
                    style = Type.text(15.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center),
                )
                WordButton("Seal your covenant", Modifier.fillMaxWidth()) { sealing = true }
            }
            rules.isCalibrating(state.commitments, now) -> {
                BasicText(
                    "Calibration week ends ${fullTime(sealedAt.plus(CALIBRATION).atZone(Graph.zone), now)}. Until then every change is instant.",
                    Modifier.fillMaxWidth(),
                    style = Type.text(15.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center),
                )
                WordButton("End calibration now", Modifier.fillMaxWidth(), note = "it never restarts") {
                    Graph.store.launchUpdate { it.copy(commitments = rules.endCalibration(it.commitments, Graph.now())) }
                }
            }
            else -> BasicText(
                "Sealed. Looser changes wait ${state.commitments.current.cooldown.toHours()} hours.",
                Modifier.fillMaxWidth(),
                style = Type.text(15.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center),
            )
        }
    }
}
