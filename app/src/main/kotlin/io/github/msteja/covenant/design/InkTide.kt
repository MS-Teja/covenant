package io.github.msteja.covenant.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.msteja.covenant.R
import io.github.msteja.covenant.core.model.DAY_START
import io.github.msteja.covenant.core.model.MINUTES_PER_DAY
import io.github.msteja.covenant.core.model.toMinuteOfDay
import java.time.LocalTime

/** Ink Tide: blue-black ink, bone text, and a tide-blue used only for ripples. */
object Ink {
    val ground = Color(0xFF0A0E14)
    val lift = Color(0xFF101722)
    val sheet = Color(0xFF0D121A)
    val bone = Color(0xFFD8DEE6)
    val brightBone = Color(0xFFE6EBF1)
    val mist = Color(0xFFA9B4C2)
    val slate = Color(0xFF8B96A5)
    val dim = Color(0xFF748091)
    val ornament = Color(0xFF4A5566)
    val hair = Color(0xFF1E2735)
    val tide = Color(0xFF6FA8C9)
}

/** Hue follows the hour: night indigo, dawn blue, pale midday, warm afternoon, dusk amber. */
private val hourStops = listOf(
    0 to Color(0xFF3A3F86),
    180 to Color(0xFF5B7FB0),
    480 to Color(0xFF9FB6CC),
    720 to Color(0xFFB9966E),
    900 to Color(0xFFC08A4E),
    1080 to Color(0xFF4A4F9A),
    MINUTES_PER_DAY to Color(0xFF22265C),
)

/** The block hue for a wall-clock time, counted from the 4 am day start. */
fun hueAt(time: LocalTime): Color {
    val minute = Math.floorMod(time.toMinuteOfDay() - DAY_START.toMinuteOfDay(), MINUTES_PER_DAY)
    val (fromAt, from) = hourStops.last { it.first <= minute }
    val (toAt, to) = hourStops.first { it.first > minute || it.first == MINUTES_PER_DAY }
    val span = (toAt - fromAt).coerceAtLeast(1)
    return lerp(from, to, (minute - fromAt).toFloat() / span)
}

@OptIn(ExperimentalTextApi::class)
private fun literata(weight: Int, italic: Boolean, opticalSize: Float) = Font(
    resId = if (italic) R.font.literata_italic else R.font.literata,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

private val weights = listOf(300, 320, 340, 360, 380, 400, 420)

private fun literataFamily(opticalSize: Float) =
    FontFamily(weights.flatMap { listOf(literata(it, false, opticalSize), literata(it, true, opticalSize)) })

/** Literata cut for large type: app names, the chapter, numerals. */
val LiterataDisplay = literataFamily(36f)

/** Literata cut for reading sizes: labels, notes, buttons. */
val LiterataText = literataFamily(12f)

object Type {
    fun display(size: TextUnit = 34.sp, weight: Int = 340, color: Color = Ink.bone) = TextStyle(
        fontFamily = LiterataDisplay,
        fontSize = size,
        fontWeight = FontWeight(weight),
        letterSpacing = (-0.01).em,
        color = color,
        textAlign = TextAlign.Center,
    )

    fun text(size: TextUnit = 16.sp, color: Color = Ink.bone, italic: Boolean = false, weight: Int = 380) = TextStyle(
        fontFamily = LiterataText,
        fontSize = size,
        fontWeight = FontWeight(weight),
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        color = color,
    )

    /** Small caps, letter-spaced, for running heads and labels. */
    fun smallCaps(size: TextUnit = 14.sp, color: Color = Ink.slate) = TextStyle(
        fontFamily = LiterataText,
        fontSize = size,
        fontFeatureSettings = "c2sc, smcp",
        letterSpacing = 0.14.em,
        color = color,
    )

    /** Fixed-width digits so countdowns don't jitter. */
    const val TABULAR = "tnum"
}
