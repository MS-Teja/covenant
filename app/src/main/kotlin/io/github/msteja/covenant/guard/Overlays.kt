package io.github.msteja.covenant.guard

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.msteja.covenant.design.Grain
import io.github.msteja.covenant.design.Ink
import io.github.msteja.covenant.design.QuietWord
import io.github.msteja.covenant.design.Type
import io.github.msteja.covenant.design.hueAt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Lets Compose run inside a window that no activity owns. */
private class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun start() {
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/** Draws Covenant's moments over other apps, from the accessibility service's own overlay layer. */
class OverlayHost(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val shown = mutableMapOf<String, Pair<View, OverlayOwner>>()

    val showing: Boolean get() = shown.isNotEmpty()

    fun show(tag: String, fullScreen: Boolean, content: @Composable (dismiss: () -> Unit) -> Unit) {
        dismiss(tag)
        val owner = OverlayOwner().apply { start() }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { content { dismiss(tag) } }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                (if (fullScreen) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        windows.addView(view, params)
        shown[tag] = view to owner
    }

    fun dismiss(tag: String) {
        shown.remove(tag)?.let { (view, owner) ->
            runCatching { windows.removeView(view) }
            owner.stop()
        }
    }

    fun dismissAll() = shown.keys.toList().forEach(::dismiss)
}

private val bloomEasing = CubicBezierEasing(0.22f, 0.7f, 0.2f, 1f)

/** R12: the 3-second ink bloom before a time-limited app, with the minutes left. It can't be skipped. */
@Composable
fun PauseOverlay(minutesLeft: Long, blockName: String, onDone: () -> Unit) {
    val bloom = remember { Animatable(0.03f) }
    val words = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { bloom.animateTo(1f, tween(2600, easing = bloomEasing)) }
            launch {
                delay(450)
                words.animateTo(1f, tween(700))
            }
        }
        delay(3000 - 2600L)
        onDone()
    }
    val hue = hueAt(LocalTime.now())
    Box(Modifier.fillMaxSize().background(Ink.ground), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = bloom.value; scaleY = bloom.value }) {
            val radius = size.maxDimension * 0.62f
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFF22344E),
                    0.5f to Color(0xFF192940).copy(alpha = 0.94f),
                    0.82f to hue.copy(alpha = 0.16f),
                    1f to Color.Transparent,
                    radius = radius,
                ),
                radius = radius,
            )
        }
        Grain(Modifier.fillMaxSize())
        Column(Modifier.alpha(words.value), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("$minutesLeft", style = Type.display(76.sp, 300).copy(fontFeatureSettings = Type.TABULAR))
                BasicText(
                    "min",
                    Modifier.padding(bottom = 10.dp),
                    style = Type.text(26.sp, Ink.mist, italic = true, weight = 340),
                )
            }
            BasicText("left in $blockName", style = Type.text(17.sp, Ink.mist, italic = true))
        }
    }
}

/** R13, R15: a sheet that rises like the tide with a short, factual warning. */
@Composable
fun WarningSheet(title: String, note: String, onOk: () -> Unit) {
    val hue = hueAt(LocalTime.now().plusMinutes(1))
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Ink.ground, Ink.ground)))
            .navigationBarsPadding()
            .padding(start = 28.dp, end = 18.dp, top = 22.dp, bottom = 12.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().padding(end = 10.dp, bottom = 16.dp)) {
            drawLine(hue.copy(alpha = 0.6f), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.2.dp.toPx())
        }
        BasicText(title, style = Type.display(25.sp, 380).copy(textAlign = TextAlign.Start))
        BasicText(note, Modifier.padding(top = 6.dp), style = Type.text(16.sp, Ink.mist, italic = true))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { QuietWord("OK", onClick = onOk) }
    }
}
