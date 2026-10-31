package io.github.msteja.covenant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.msteja.covenant.core.model.AppId
import io.github.msteja.covenant.data.AppState
import io.github.msteja.covenant.design.InkScreen
import io.github.msteja.covenant.design.hueAt
import io.github.msteja.covenant.editor.ListsEditor
import io.github.msteja.covenant.editor.PendingScreen
import io.github.msteja.covenant.editor.SettingsScreen
import io.github.msteja.covenant.editor.TimelineEditor
import io.github.msteja.covenant.guard.CovenantAccessibilityService
import io.github.msteja.covenant.guard.Notifier
import io.github.msteja.covenant.guard.Scheduler
import io.github.msteja.covenant.home.BlockedScreen
import io.github.msteja.covenant.home.HomeScreen
import io.github.msteja.covenant.home.MenuScreen
import io.github.msteja.covenant.home.SearchScreen
import io.github.msteja.covenant.home.TodayScreen
import io.github.msteja.covenant.home.homeView
import io.github.msteja.covenant.home.rememberNow
import io.github.msteja.covenant.setup.SetupWizard
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Covenant's home: the launcher activity every screen lives in. */
class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf<Screen>(Screen.Home)

    /** When the blocked screen last appeared; Android's Home intents that follow it are echoes. */
    private var blockedShownAt = 0L

    private fun showBlocked(app: AppId) {
        screen = Screen.Blocked(app)
        blockedShownAt = android.os.SystemClock.uptimeMillis()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        Notifier.createChannels(this)
        handle(intent)
        setContent {
            val state by Graph.store.state.collectAsState()
            state?.let { Root(it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while already home returns to the first page, unless the guard just sent you here.
        val echo = android.os.SystemClock.uptimeMillis() - blockedShownAt < 1500
        if (intent.hasCategory(Intent.CATEGORY_HOME) && Graph.blocked.value == null && !echo) screen = Screen.Home
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        Graph.catalog.refresh()
        Graph.scope.launch { Scheduler.wake(applicationContext) }
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_BLOCKED)?.let { showBlocked(AppId(it)) }
        Graph.blocked.value?.let {
            Graph.blocked.value = null
            showBlocked(it)
        }
    }

    @Composable
    private fun Root(state: AppState) {
        val context = LocalContext.current
        val now = rememberNow()
        val apps by Graph.catalog.apps.collectAsState()
        val hue = hueAt(LocalTime.now())
        val blocked by Graph.blocked.collectAsState()
        LaunchedEffect(blocked) {
            blocked?.let {
                Graph.blocked.value = null
                showBlocked(it)
            }
        }
        InkScreen(hue = hue) {
            if (!state.setupDone) {
                SetupWizard(state)
                return@InkScreen
            }
            val view = homeView(context, state, apps, now)
            val back = { screen = Screen.Home }
            when (val current = screen) {
                Screen.Home -> {
                    val pager = rememberPagerState { 2 }
                    val scope = rememberCoroutineScope()
                    BackHandler(pager.currentPage == 1) { scope.launch { pager.animateScrollToPage(0) } }
                    LaunchedEffect(screen) { if (pager.currentPage != 0) pager.scrollToPage(0) }
                    HorizontalPager(pager) { page ->
                        if (page == 0) {
                            HomeScreen(
                                view = view,
                                onOpen = { Graph.catalog.launch(it) },
                                onSearch = { screen = Screen.Search },
                                onNotifications = { CovenantAccessibilityService.instance?.openNotifications() },
                                onLock = { CovenantAccessibilityService.instance?.lockScreen() },
                                onMenu = { screen = Screen.Menu },
                            )
                        } else {
                            TodayScreen(state, now)
                        }
                    }
                }
                Screen.Search -> SearchScreen(view, view.chapter, onOpen = { app ->
                    screen = Screen.Home
                    Graph.catalog.launch(app)
                }, onClose = back)
                is Screen.Blocked -> BlockedScreen(current.app, state, onOpened = back, onBack = back)
                Screen.Menu -> MenuScreen(
                    pendingCount = state.commitments.pending.size,
                    canQuiet = Graph.rules(state.commitments).schedule.activeAt(now) != null,
                    onPick = { pick ->
                        screen = when (pick) {
                            "timeline" -> Screen.Timeline
                            "lists" -> Screen.Lists
                            "pending" -> Screen.Pending
                            "settings" -> Screen.Settings
                            "quiet" -> {
                                Graph.store.launchUpdate { s ->
                                    Graph.rules(s.commitments).exits.quietNow(s.activity, Graph.now())?.let { s.copy(activity = it) } ?: s
                                }
                                Screen.Home
                            }
                            else -> Screen.Home
                        }
                    },
                    onClose = back,
                )
                Screen.Timeline -> TimelineEditor(state, onBack = back)
                Screen.Lists -> ListsEditor(state, onBack = back)
                Screen.Pending -> PendingScreen(state, onBack = back)
                Screen.Settings -> SettingsScreen(state, onBack = back)
            }
        }
    }

    private sealed interface Screen {
        data object Home : Screen
        data object Search : Screen
        data object Menu : Screen
        data object Timeline : Screen
        data object Lists : Screen
        data object Pending : Screen
        data object Settings : Screen
        data class Blocked(val app: AppId) : Screen
    }

    companion object {
        const val EXTRA_BLOCKED = "io.github.msteja.covenant.BLOCKED"
    }
}
