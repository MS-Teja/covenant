package io.github.msteja.covenant.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.msteja.covenant.Graph
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.core.rules.Access
import io.github.msteja.covenant.core.rules.BorrowResult
import io.github.msteja.covenant.core.rules.EmergencyResult
import io.github.msteja.covenant.core.rules.startsAt
import io.github.msteja.covenant.data.AppState
import io.github.msteja.covenant.data.InstalledApp
import io.github.msteja.covenant.design.Ink
import io.github.msteja.covenant.design.QuietWord
import io.github.msteja.covenant.design.RunningHead
import io.github.msteja.covenant.design.Type
import io.github.msteja.covenant.design.WaveOrnament
import io.github.msteja.covenant.design.WordButton
import io.github.msteja.covenant.design.inkClick
import io.github.msteja.covenant.guard.Guard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

/** R47: swipe up to search. Only apps you can open right now are found. */
@Composable
fun SearchScreen(view: HomeView, chapter: String, onOpen: (InstalledApp) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val candidates = (view.apps + view.essentials).distinctBy { it.id }
    val matches = if (query.isBlank()) emptyList() else candidates.filter { it.label.contains(query.trim(), ignoreCase = true) }
        .sortedBy { if (it.label.startsWith(query.trim(), ignoreCase = true)) 0 else 1 }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RunningHead("Search")
        BasicText("$chapter and essentials", style = Type.text(13.sp, Ink.dim, italic = true))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = Type.display(32.sp, 340),
            cursorBrush = SolidColor(Ink.bone),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { matches.firstOrNull()?.let(onOpen) }),
            modifier = Modifier
                .padding(top = 22.dp)
                .width(250.dp)
                .focusRequester(focus)
                .drawBehind {
                    drawLine(Ink.hair, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                }
                .padding(bottom = 12.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            items(matches, key = { it.id.packageName + it.user.hashCode() }) { app ->
                val start = app.label.indexOf(query.trim(), ignoreCase = true).coerceAtLeast(0)
                val end = (start + query.trim().length).coerceAtMost(app.label.length)
                BasicText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Ink.slate)) { append(app.label.substring(0, start)) }
                        withStyle(SpanStyle(color = Ink.brightBone)) { append(app.label.substring(start, end)) }
                        withStyle(SpanStyle(color = Ink.slate)) { append(app.label.substring(end)) }
                    },
                    Modifier.inkClick(app.label) { onOpen(app) }.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = Type.display(32.sp, 340),
                )
            }
        }
    }
}

/** The blocked screen: why, when it opens, and the ways out (R29, R31). */
@Composable
fun BlockedScreen(app: AppId, state: AppState, onOpened: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val rules = Graph.rules(state.commitments)
    val label = Graph.catalog.label(app)
    val running = rules.schedule.activeAt(now)
    var waiting by remember { mutableStateOf<Duration?>(null) }

    val access = Guard.decide(context, app, now)
    if (access == Access.Open || access is Access.Limited) {
        LaunchedEffect(app) {
            Graph.catalog.launch(app)
            onOpened()
        }
    }
    val (headline, note) = when (access) {
        is Access.Hidden -> "$label's time is used up." to (access.until?.let { "It opens again at ${fullTime(it, now)}." } ?: "")
        is Access.Blocked -> {
            val opens = access.opensAt
            val home = opens?.let { at -> rules.schedule.activeAt(at.toInstant())?.block?.name }
            if (opens == null) "$label isn't in any block." to "Add it to a block; that waits the cooldown."
            else "$label opens at ${fullTime(opens, now)}." to (home?.let { "It belongs to $it." } ?: "")
        }
        else -> "Opening $label…" to ""
    }

    val exits = rules.exits
    val borrowsLeft = exits.borrowsLeft(state.activity, now)
    val emergencyAt = exits.emergencyAvailableAt(state.activity)

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 32.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // A used-up app still belongs to this block; only a blocked one is outside it.
        RunningHead(running?.let { if (access is Access.Hidden) it.block.name else "Outside ${it.block.name}" } ?: "Essentials only")
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BasicText(headline, style = Type.display(31.sp, 340))
            if (note.isNotBlank()) BasicText(note, Modifier.padding(top = 12.dp), style = Type.text(16.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center))
            WaveOrnament(Modifier.padding(top = 26.dp))
        }
        val wait = waiting
        if (wait != null) {
            BasicText("Opens in 0:${"%02d".format(wait.seconds.coerceAtLeast(0))}", style = Type.text(20.sp, weight = 380).copy(fontFeatureSettings = Type.TABULAR))
            BasicText("A minute to be sure.", Modifier.padding(top = 4.dp, bottom = 20.dp), style = Type.text(14.sp, Ink.mist, italic = true))
        } else {
            WordButton(
                "Borrow ten minutes",
                Modifier.fillMaxWidth(),
                note = if (borrowsLeft > 0) "$borrowsLeft left today · after a 1-minute wait" else "none left today · back at 4 am",
                enabled = borrowsLeft > 0,
            ) {
                val result = exits.requestBorrow(app, state.activity, Graph.now())
                if (result is BorrowResult.Granted) {
                    Graph.store.launchUpdate { it.copy(activity = it.activity.copy(borrows = it.activity.borrows + result.borrow)) }
                    scope.launch {
                        while (true) {
                            val left = Duration.between(Graph.now(), result.borrow.startsAt)
                            waiting = left
                            if (left.isNegative || left.isZero) break
                            delay(250)
                        }
                        Graph.catalog.launch(app)
                        onOpened()
                    }
                }
            }
            if (state.commitments.current.weeklyEmergencyPass) {
                val available = emergencyAt == null || !emergencyAt.isAfter(now)
                WordButton(
                    "Emergency pass",
                    Modifier.fillMaxWidth(),
                    note = if (available) "everything for 30 minutes · after a 10-minute wait"
                    else "back ${fullTime(emergencyAt!!.atZone(Graph.zone), now)}",
                    enabled = available,
                ) {
                    val result = exits.requestEmergency(state.activity, Graph.now())
                    if (result is EmergencyResult.Granted) {
                        Graph.store.launchUpdate { it.copy(activity = it.activity.copy(emergencyPasses = it.activity.emergencyPasses + result.pass)) }
                    }
                }
            }
        }
        QuietWord("Back", Modifier.padding(top = 8.dp), onClick = onBack)
    }
}

/** Long-press on home: Covenant's menu, in words. */
@Composable
fun MenuScreen(pendingCount: Int, canQuiet: Boolean, onPick: (String) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RunningHead("Covenant")
            Column(Modifier.padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WordButton("Timeline", style = Type.display(28.sp, 340)) { onPick("timeline") }
                WordButton("Essentials and calls", style = Type.display(28.sp, 340)) { onPick("lists") }
                WordButton(
                    "Pending changes",
                    note = if (pendingCount == 0) "none waiting" else "$pendingCount waiting",
                    style = Type.display(28.sp, 340),
                ) { onPick("pending") }
                if (canQuiet) WordButton("Quiet now", note = "essentials only until the next block", style = Type.display(28.sp, 340)) { onPick("quiet") }
                WordButton("Settings", style = Type.display(28.sp, 340)) { onPick("settings") }
            }
            QuietWord("Close", Modifier.padding(top = 20.dp), onClick = onClose)
        }
    }
}
