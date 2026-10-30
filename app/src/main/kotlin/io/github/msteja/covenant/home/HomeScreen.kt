package io.github.msteja.covenant.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.msteja.covenant.data.InstalledApp
import io.github.msteja.covenant.design.Chapter
import io.github.msteja.covenant.design.Ink
import io.github.msteja.covenant.design.PageEdge
import io.github.msteja.covenant.design.RunningHead
import io.github.msteja.covenant.design.Type
import io.github.msteja.covenant.design.WaveOrnament
import io.github.msteja.covenant.design.inkClick

/** Home reads like a book page: running head, chapter, the block's apps, a footnote of essentials (R46). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    view: HomeView,
    onOpen: (InstalledApp) -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onLock: () -> Unit,
    onMenu: () -> Unit,
) {
    val swipe = remember { floatArrayOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            // R47: swipe up to search, swipe down for notifications, double-tap to lock, long-press for the menu.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { swipe[0] = 0f },
                    onDragEnd = {
                        if (swipe[0] < -120f) onSearch() else if (swipe[0] > 120f) onNotifications()
                    },
                ) { _, dy -> swipe[0] += dy }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onLock() }, onLongPress = { onMenu() }) },
    ) {
        PageEdge(Modifier.align(Alignment.CenterEnd))
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 32.dp).padding(top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RunningHead(view.runningHead)
            if (view.unguarded) BasicText("unguarded", Modifier.padding(top = 2.dp), style = Type.smallCaps(12.sp, Ink.dim))
            Chapter(view.chapter, view.until, Modifier.padding(top = 40.dp))

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.height(24.dp))
                val big = view.apps.ifEmpty { if (view.chapter == "Essentials only" || view.chapter == "Quiet") view.essentials else emptyList() }
                for (app in big) {
                    BasicText(
                        app.label,
                        Modifier.inkClick(app.label) { onOpen(app) }.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = Type.display(34.sp, 340),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            if (view.apps.isNotEmpty() && view.essentials.isNotEmpty()) {
                WaveOrnament(Modifier.padding(bottom = 10.dp))
                FlowRow(Modifier.widthIn(max = 320.dp), horizontalArrangement = Arrangement.Center) {
                    for (app in view.essentials) {
                        BasicText(
                            app.label.lowercase(),
                            Modifier.inkClick(app.label) { onOpen(app) }.padding(horizontal = 6.dp, vertical = 10.dp),
                            style = Type.smallCaps(16.sp, Ink.mist),
                        )
                    }
                }
            }
            if (view.next != null) {
                BasicText(view.next, Modifier.padding(top = 18.dp), style = Type.text(15.sp, Ink.mist, italic = true))
            }
        }
    }
}
