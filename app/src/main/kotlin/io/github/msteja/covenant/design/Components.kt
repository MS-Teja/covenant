package io.github.msteja.covenant.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import kotlin.random.Random

/** A press that darkens the ink a little, with no ripple: Covenant's only tap feedback. */
@Composable
fun Modifier.inkClick(label: String? = null, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this
        .alpha(if (pressed) 0.55f else 1f)
        .clickable(interactionSource = source, indication = null, onClickLabel = label, role = Role.Button, onClick = onClick)
}

/** Every Ink Tide screen: lit ink ground, paper grain, and the current block's tide along the bottom. */
@Composable
fun InkScreen(
    modifier: Modifier = Modifier,
    hue: Color? = hueAt(LocalTime.now()),
    tideHeight: Dp = 240.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Ink.lift, Ink.ground), center = Offset(540f, 0f), radius = 1600f)),
    ) {
        if (hue != null) TideWash(hue, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(tideHeight))
        Grain(Modifier.fillMaxSize())
        content()
    }
}

/** A soft wash of the block's hue with a drifting tide line along its top edge. */
@Composable
fun TideWash(hue: Color, modifier: Modifier = Modifier) {
    val drift by rememberInfiniteTransition(label = "tide").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Canvas(modifier) {
        val shift = -drift * 50.dp.toPx()
        val unit = size.width / 390f
        val crest = 60f * unit
        val wave = Path().apply {
            moveTo(shift - 60 * unit, crest + 4 * unit)
            cubicTo(shift + 5 * unit, crest - 14 * unit, shift + 70 * unit, crest - 14 * unit, shift + 135 * unit, crest)
            cubicTo(shift + 200 * unit, crest + 14 * unit, shift + 265 * unit, crest + 20 * unit, shift + 330 * unit, crest - 2 * unit)
            cubicTo(shift + 380 * unit, crest - 18 * unit, shift + 430 * unit, crest - 16 * unit, shift + 470 * unit, crest - 4 * unit)
        }
        val body = Path().apply {
            addPath(wave)
            lineTo(shift + 470 * unit, size.height)
            lineTo(shift - 60 * unit, size.height)
            close()
        }
        drawPath(body, Brush.verticalGradient(listOf(hue.copy(alpha = 0.07f), hue.copy(alpha = 0.16f))))
        drawPath(wave, hue.copy(alpha = 0.34f), style = Stroke(width = 1.dp.toPx()))
    }
}

private val grainBitmap: ImageBitmap by lazy {
    val size = 192
    val random = Random(7)
    val pixels = IntArray(size * size) {
        val v = random.nextInt(256)
        (random.nextInt(18) shl 24) or (v shl 16) or (v shl 8) or v
    }
    android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** A faint paper grain over the ink. */
@Composable
fun Grain(modifier: Modifier = Modifier) {
    val brush = remember { ShaderBrush(ImageShader(grainBitmap, TileMode.Repeated, TileMode.Repeated)) }
    Canvas(modifier) { drawRect(brush) }
}

/** The small tide-wave ornament that separates sections, like a printer's flourish. */
@Composable
fun WaveOrnament(modifier: Modifier = Modifier, color: Color = Ink.ornament) {
    Canvas(modifier.size(30.dp, 8.dp)) {
        val w = size.width / 30f
        val path = Path().apply {
            moveTo(1 * w, 5 * w)
            cubicTo(5 * w, 1.5f * w, 9 * w, 1.5f * w, 13 * w, 4.5f * w)
            cubicTo(17 * w, 7.5f * w, 21 * w, 7.5f * w, 25 * w, 4 * w)
            cubicTo(27 * w, 2.4f * w, 28 * w, 2 * w, 29 * w, 3 * w)
        }
        drawPath(path, color, style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Three hairlines at the right edge: the edge of today's page, one swipe away. */
@Composable
fun PageEdge(modifier: Modifier = Modifier) {
    Canvas(modifier.size(8.dp, 190.dp)) {
        val colors = listOf(Color(0xFF1C2533), Color(0xFF232D3D), Color(0xFF2B3647))
        colors.forEachIndexed { i, c ->
            val x = (2.5f + i * 2f).dp.toPx()
            val inset = (i * 8).dp.toPx()
            drawLine(c, Offset(x, inset), Offset(x, size.height - inset), strokeWidth = 1.dp.toPx())
        }
    }
}

@Composable
fun RunningHead(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = Type.smallCaps())
}

/** The chapter: the current block's name, with when it ends in italics beneath. */
@Composable
fun Chapter(title: String, note: String?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(title, style = Type.display(26.sp, 420))
        if (note != null) BasicText(note, style = Type.text(16.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center))
    }
}

/** A word that acts. Covenant has no icons: every control is a word (Design: Controls). */
@Composable
fun WordButton(
    text: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    style: TextStyle = Type.text(19.sp, weight = 380),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (enabled) Modifier.inkClick(text, onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text, style = style.copy(textAlign = TextAlign.Center))
        if (note != null) BasicText(note, style = Type.text(14.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center))
    }
}

/** A small-caps word for quiet actions such as Back. */
@Composable
fun QuietWord(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    BasicText(
        text,
        modifier.inkClick(text, onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        style = Type.smallCaps(15.sp, Ink.mist),
    )
}
