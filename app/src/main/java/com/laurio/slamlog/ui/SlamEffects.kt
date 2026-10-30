package com.laurio.slamlog.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A slam "impact": a quick flash, three shockwave rings and a spray of debris that
 * falls back down. Fires each time [trigger] goes up. [big] is the set-complete version.
 * Draws past its own bounds on purpose, so the rings spread across the screen.
 */
@Composable
fun SlamBurst(trigger: Int, color: Color, big: Boolean, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    val seeds = remember(trigger) { List(if (big) 28 else 18) { Random.nextFloat() } }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(if (big) 1100 else 700, easing = LinearEasing))
    }

    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val c = center
        val maxR = size.maxDimension * (if (big) 1.1f else 0.65f)
        val fade = 1f - p

        // Flash
        drawRect(color.copy(alpha = (if (big) 0.35f else 0.22f) * fade * fade))

        // Shockwave rings, each starting a beat after the last
        for (i in 0 until 3) {
            val delay = i * 0.14f
            val pi = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (pi <= 0f) continue
            val grow = 1f - (1f - pi).pow(3)
            drawCircle(
                color = color.copy(alpha = (1f - pi) * 0.85f),
                radius = maxR * grow,
                center = c,
                style = Stroke(width = (if (big) 24.dp else 16.dp).toPx() * (1f - pi) + 1.dp.toPx()),
            )
        }

        // Debris: flies out, then gravity pulls it down
        val out = 1f - fade.pow(3)
        val n = seeds.size
        seeds.forEachIndexed { i, s ->
            val angle = (i.toFloat() / n) * 2f * PI.toFloat() + s
            val dist = maxR * (0.3f + 0.6f * s) * out
            val drop = 140.dp.toPx() * p * p
            val pos = Offset(c.x + cos(angle) * dist, c.y + sin(angle) * dist + drop)
            val chip = if (i % 3 == 0) Color.White else color
            drawCircle(chip.copy(alpha = fade), radius = (3f + 5f * s).dp.toPx() * (1f - p * 0.6f), center = pos)
        }
    }
}

/** Pop for the rep number: jumps up in size, then springs back. Returns the current scale. */
@Composable
fun rememberPop(trigger: Int, big: Boolean): Float {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        pop.snapTo(if (big) 1.6f else 1.35f)
        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 400f))
    }
    return pop.value
}

/** Screen shake: a short, fading side-to-side wobble. Returns the offset as a fraction (−1..1). */
@Composable
fun rememberShake(trigger: Int): Float {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        shake.snapTo(1f)
        shake.animateTo(0f, tween(380, easing = LinearEasing))
    }
    val t = shake.value
    return sin(t * 28f) * t
}
