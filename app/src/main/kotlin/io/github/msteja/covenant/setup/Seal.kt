package io.github.msteja.covenant.setup

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.msteja.covenant.core.model.Covenant
import io.github.msteja.covenant.core.model.Timeline
import io.github.msteja.covenant.design.Ink
import io.github.msteja.covenant.design.Type
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Draws the seal mark: two rings, one tide line, one point for now. [unit] is 1/100 of its width. */
fun DrawScope.sealMark(color: Color, center: Offset, width: Float) {
    val u = width / 100f
    fun at(x: Float, y: Float) = Offset(center.x + (x - 50) * u, center.y + (y - 50) * u)
    drawCircle(color, radius = 46 * u, center = center, style = Stroke(2.4f * u))
    drawCircle(color, radius = 38.5f * u, center = center, style = Stroke(1f * u))
    val wave = Path().apply {
        at(24f, 55f).let { moveTo(it.x, it.y) }
        val c1 = at(31f, 47f); val c2 = at(39f, 47f); val p1 = at(45f, 53f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, p1.x, p1.y)
        val c3 = at(51f, 59f); val c4 = at(59f, 61f); val p2 = at(66f, 53f)
        cubicTo(c3.x, c3.y, c4.x, c4.y, p2.x, p2.y)
        val c5 = at(70f, 48.6f); val c6 = at(73f, 47f); val p3 = at(76f, 49f)
        cubicTo(c5.x, c5.y, c6.x, c6.y, p3.x, p3.y)
    }
    drawPath(wave, color, style = Stroke(3.2f * u, cap = StrokeCap.Round))
    drawCircle(color, radius = 2.2f * u, center = at(50f, 20.5f))
}

/** "On weekdays, Work runs 8 to 4, Evening 4 to 10, and Night 10 to 8." */
fun vow(timeline: Timeline): String {
    fun h(t: LocalTime) = (if (t.hour % 12 == 0) 12 else t.hour % 12).toString() + if (t.minute != 0) ":%02d".format(t.minute) else ""
    val parts = timeline.blocks.sortedBy { Math.floorMod(it.start.hour * 60 + it.start.minute - 240, 1440) }
        .mapIndexed { i, b -> if (i == 0) "${b.name} runs ${h(b.start)} to ${h(b.end)}" else "${b.name} ${h(b.start)} to ${h(b.end)}" }
    return when (parts.size) {
        0 -> "essentials only."
        1 -> parts[0] + "."
        else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last() + "."
    }
}

/** The seal ceremony: your covenant as a vow, then hold for 3 seconds while the seal soaks with ink. */
@Composable
fun SealCeremony(covenant: Covenant, onSealed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var sealed by remember { mutableStateOf(false) }
    val stamp = remember { Animatable(1.3f) }
    LaunchedEffect(sealed) {
        if (sealed) {
            thud(context)
            stamp.animateTo(1f, tween(600))
            kotlinx.coroutines.delay(1400)
            onSealed()
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val vowStyle = Type.text(19.sp, weight = 360).copy(lineHeight = 29.sp)
        BasicText("On weekdays, ${vow(covenant.weekday)}", Modifier.fillMaxWidth(), style = vowStyle)
        BasicText("On weekends, ${vow(covenant.weekend)}", Modifier.fillMaxWidth().padding(top = 14.dp), style = vowStyle)
        BasicText(
            "What loosens this waits ${covenant.cooldown.toHours()} hours.",
            Modifier.fillMaxWidth().padding(top = 18.dp),
            style = Type.text(15.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center),
        )
        Box(
            Modifier
                .padding(top = 36.dp)
                .size(156.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Hold to seal"
                }
                .pointerInput(sealed) {
                    if (sealed) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown()
                        val job = scope.launch {
                            progress.animateTo(1f, tween(((1f - progress.value) * 3000).toInt(), easing = LinearEasing))
                            sealed = true
                        }
                        waitForUpOrCancellation()
                        if (!sealed) {
                            job.cancel()
                            scope.launch { progress.animateTo(0f, tween(300)) }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(156.dp)) {
                if (sealed) {
                    rotate(-6f) {
                        val r = size.minDimension / 2 * stamp.value
                        drawCircle(Ink.bone, radius = r * 0.96f)
                        sealMark(Ink.ground, center, size.minDimension * 0.84f * stamp.value)
                    }
                } else {
                    drawCircle(Ink.hair, radius = size.minDimension / 2 - 2f, style = Stroke(1.5.dp.toPx()))
                    drawCircle(Color(0xFF1B2A40), radius = size.minDimension * 0.37f * progress.value)
                    drawArc(
                        Ink.bone, -90f, 360f * progress.value, false,
                        topLeft = Offset(2f, 2f), size = androidx.compose.ui.geometry.Size(size.width - 4f, size.height - 4f),
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                    sealMark(Ink.bone, center, size.minDimension * 0.62f)
                }
            }
        }
        BasicText(
            when {
                sealed -> "Sealed."
                progress.value > 0f -> "Keep holding"
                else -> "Hold to seal"
            },
            Modifier.padding(top = 14.dp),
            style = if (sealed) Type.display(22.sp, 380) else Type.text(15.sp, Ink.mist, italic = true),
        )
        if (sealed) {
            BasicText(
                "Calibration week begins. For 7 days, every change is instant.",
                Modifier.padding(top = 4.dp),
                style = Type.text(15.sp, Ink.mist, italic = true).copy(textAlign = TextAlign.Center),
            )
        }
    }
}

/** A deep haptic thud when the seal stamps down. */
private fun thud(context: Context) {
    val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
    val effect = if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
        VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f).compose()
    } else {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    }
    vibrator.vibrate(effect)
}
