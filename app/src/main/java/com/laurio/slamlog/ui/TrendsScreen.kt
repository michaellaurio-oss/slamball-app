package com.laurio.slamlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laurio.slamlog.data.Session
import com.laurio.slamlog.data.UserSettings
import com.laurio.slamlog.workout.Progress
import com.laurio.slamlog.workout.mmss
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val shortDate = SimpleDateFormat("MMM d", Locale.US)

/** Progress across workouts, led by time from last slam to the target heart rate. */
@Composable
fun TrendsScreen(sessions: List<Session>, settings: UserSettings, target: Int, onOpen: (Session) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    // null = all ball weights
    var weight by remember { mutableStateOf<Int?>(null) }

    val chronological = sessions.sortedBy { it.startMillis }
    val weights = chronological.map { it.ballWeightLbs }.distinct().sorted()
    val shown = chronological.filter { weight == null || it.ballWeightLbs == weight }
    val withTarget = shown.filter { it.avgTimeToTarget(target) != null }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TRENDS", fontSize = 26.sp, fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }

        if (weights.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("All balls", weight == null) { weight = null }
                weights.forEach { w -> Choice("$w lb", weight == w) { weight = w } }
            }
        }

        // ---- Is my recovery improving? (baseline tests only) ----
        val baselines = Progress.baselines(sessions, settings)
        val verdict = Progress.verdict(baselines, settings.baselineTargetHr)
        SectionCard {
            Label("Is my recovery improving?")
            Text(
                verdict.headline,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = when (verdict.improving) { true -> Mint; false -> Amber; null -> MaterialTheme.colorScheme.onSurface },
            )
            Text(verdict.detail, style = MaterialTheme.typography.bodyMedium)
            val baselineSetTimes = baselines.mapNotNull { it.avgSetSec }
            if (baselineSetTimes.size >= 2) {
                Text(
                    "Time per set: ${secs(baselineSetTimes.first())} in your first test, ${secs(baselineSetTimes.last())} in your latest.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (baselines.size >= 2) {
                TrendChart(
                    points = baselines.map { it.startMillis to it.avgTimeToTarget(settings.baselineTargetHr)!!.toFloat() },
                    color = Sky,
                    format = { mmss(it.roundToInt()) },
                )
            }
            Text(
                "Uses baseline tests only (${settings.baselineSets} × ${settings.baselineReps} with a " +
                    "${settings.baselineBallLbs} lb ball, every set finished), so bigger training days don't skew it. " +
                    "Lower is better.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            baselines.reversed().forEach { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(shortDate.format(Date(s.startMillis)), Modifier.weight(1f))
                    s.rpe?.let { Text("felt $it/10  ", color = effortColor(it), style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { onOpen(s) }) {
                        Text(mmss(s.avgTimeToTarget(settings.baselineTargetHr)!!.roundToInt()), fontWeight = FontWeight.Bold, color = Sky)
                    }
                }
            }
        }

        // ---- Training workouts, compared only at the same reps per set ----
        val levels = Progress.byReps(shown, target)
        if (levels.isNotEmpty()) {
            SectionCard {
                Label("By reps per set")
                Text(
                    "Time from last slam to $target, comparing each workout only with others at the same reps per set.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    Text("Reps", Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium)
                    Text("Workouts", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("First", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("Latest", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("Best", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                }
                levels.forEach { l ->
                    val better = l.workouts > 1 && l.latest < l.first - 2
                    Row {
                        Text("${l.reps}", Modifier.weight(0.8f), fontWeight = FontWeight.Bold)
                        Text("${l.workouts}", Modifier.weight(1f))
                        Text(mmss(l.first.roundToInt()), Modifier.weight(1f))
                        Text(mmss(l.latest.roundToInt()), Modifier.weight(1f), color = if (better) Mint else MaterialTheme.colorScheme.onSurface)
                        Text(mmss(l.best.roundToInt()), Modifier.weight(1f))
                    }
                }
            }
        }

        // ---- All workouts: last slam → target ----
        SectionCard {
            Label("All workouts · last slam → $target bpm")
            if (withTarget.isEmpty()) {
                Text(
                    "No workouts measured against $target bpm yet. Each set's time shows up here once your " +
                        "heart rate gets down to $target during a rest.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val avgs = withTarget.map { it.avgTimeToTarget(target)!! }
                val best = withTarget.minBy { it.avgTimeToTarget(target)!! }
                Row {
                    Stat("latest", mmss(avgs.last().roundToInt()), Modifier.weight(1f), color = Sky)
                    Stat("best workout", mmss(best.avgTimeToTarget(target)!!.roundToInt()), Modifier.weight(1f), color = Mint)
                    val fastestSet = withTarget.flatMap { s -> s.setsWithTarget(target).mapNotNull { it.timeToTargetSec } }.min()
                    Stat("fastest set", mmss(fastestSet), Modifier.weight(1f))
                }
                if (avgs.size >= 2) {
                    val n = minOf(3, avgs.size / 2).coerceAtLeast(1)
                    val early = avgs.take(n).average()
                    val recent = avgs.takeLast(n).average()
                    val diff = (early - recent).roundToInt()
                    val msg = when {
                        diff > 0 -> "Your last $n ${if (n == 1) "workout is" else "workouts average"} ${abs(diff)} s faster than your first $n."
                        diff < 0 -> "Your last $n ${if (n == 1) "workout is" else "workouts average"} ${abs(diff)} s slower than your first $n."
                        else -> "About the same as when you started."
                    }
                    Text(msg, color = if (diff > 0) Mint else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TrendChart(
                    points = withTarget.map { it.startMillis to it.avgTimeToTarget(target)!!.toFloat() },
                    secondary = withTarget.map { s ->
                        s.startMillis to s.setsWithTarget(target).mapNotNull { it.timeToTargetSec }.min().toFloat()
                    },
                    color = Sky,
                    secondaryColor = Mint,
                    format = { mmss(it.roundToInt()) },
                )
                Legend(listOf(Sky to "Average per workout", Mint to "Fastest set"))
                Text("Lower is better. Compare like with like: reps per set, ball weight and rest length all change the time.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---- Time per set ----
        val timed = shown.filter { it.avgSetSec != null }
        if (timed.isNotEmpty()) {
            SectionCard {
                Label("Time per set")
                val latest = timed.last()
                val fastest = timed.flatMap { it.timedSets }.minBy { it.durationSec }
                Row {
                    Stat("latest avg", secs(latest.avgSetSec!!), Modifier.weight(1f), color = Sky)
                    Stat("fastest set", secs(fastest.durationSec.toDouble()), Modifier.weight(1f), color = Mint)
                    Stat("per rep", String.format(Locale.US, "%.1f s", latest.avgSetSec!! / latest.targetReps.coerceAtLeast(1)), Modifier.weight(1f))
                }
                val setLevels = Progress.setTimeByReps(shown)
                Row {
                    Text("Reps", Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium)
                    Text("Workouts", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("First", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("Latest", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("Best", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                }
                setLevels.forEach { l ->
                    val faster = l.workouts > 1 && l.latest < l.first - 1
                    Row {
                        Text("${l.reps}", Modifier.weight(0.8f), fontWeight = FontWeight.Bold)
                        Text("${l.workouts}", Modifier.weight(1f))
                        Text(secs(l.first), Modifier.weight(1f))
                        Text(secs(l.latest), Modifier.weight(1f), color = if (faster) Mint else MaterialTheme.colorScheme.onSurface)
                        Text(secs(l.best), Modifier.weight(1f))
                    }
                }
                if (timed.size >= 2) {
                    TrendChart(
                        points = timed.map { it.startMillis to (it.avgSetSec!! / it.targetReps.coerceAtLeast(1)).toFloat() },
                        color = Orange,
                        format = { String.format(Locale.US, "%.1f s", it) },
                    )
                    Legend(listOf(Orange to "Seconds per rep (average set ÷ reps)"))
                }
                Text(
                    "From the start beep to the last rep, finished sets only. Compared only at the same reps per set; " +
                        "the chart uses seconds per rep so different set sizes can share it. Lower is faster.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- How hard it felt ----
        val rated = shown.filter { it.rpe != null }
        if (rated.size >= 2) {
            SectionCard {
                Label("How hard it felt (1–10)")
                TrendChart(
                    points = rated.map { it.startMillis to it.rpe!!.toFloat() },
                    color = Amber,
                    format = { "${it.roundToInt()}" },
                )
                Text(
                    "Same workout feeling easier over time, or a faster time to $target at the same effort, means you're getting fitter.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- 1-minute recovery ----
        val withRec = shown.filter { it.avgRecoveryBpm != null }
        if (withRec.size >= 2) {
            SectionCard {
                Label("Heart rate drop in the first minute")
                TrendChart(
                    points = withRec.map { it.startMillis to it.avgRecoveryBpm!!.toFloat() },
                    color = Mint,
                    format = { "${it.roundToInt()} bpm" },
                )
                Text("Higher is better.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---- Volume ----
        if (shown.size >= 2) {
            SectionCard {
                Label("Slams per workout")
                TrendChart(
                    points = shown.map { it.startMillis to it.totalReps.toFloat() },
                    color = Orange,
                    format = { "${it.roundToInt()}" },
                )
            }
        }

        // ---- Table ----
        if (withTarget.isNotEmpty()) {
            SectionCard {
                Label("Workout by workout")
                withTarget.reversed().forEach { s ->
                    val times = s.setsWithTarget(target)
                    val hit = times.count { it.timeToTargetSec != null }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(shortDate.format(Date(s.startMillis)), fontWeight = FontWeight.SemiBold)
                            Text("${s.sets.size}×${s.targetReps} · ${s.ballWeightLbs} lb · hit $target in $hit of ${times.size} sets",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onOpen(s) }) {
                            Text(mmss(s.avgTimeToTarget(target)!!.roundToInt()), fontSize = 18.sp,
                                fontWeight = FontWeight.Bold, color = Sky)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(text) } else OutlinedButton(onClick = onClick) { Text(text) }
}

@Composable
private fun Legend(items: List<Pair<Color, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEach { (c, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(14.dp).height(10.dp)) { drawCircle(c, radius = size.height / 2) }
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A simple line-and-dots chart of one value per workout, spaced by date. */
@Composable
private fun TrendChart(
    points: List<Pair<Long, Float>>,
    color: Color,
    format: (Float) -> String,
    secondary: List<Pair<Long, Float>> = emptyList(),
    secondaryColor: Color = color,
) {
    if (points.isEmpty()) return
    val all = points.map { it.second } + secondary.map { it.second }
    val rawLo = all.min()
    val rawHi = all.max()
    val pad = ((rawHi - rawLo) * 0.15f).coerceAtLeast(1f)
    val lo = (rawLo - pad).coerceAtLeast(0f)
    val hi = rawHi + pad
    val t0 = points.minOf { it.first }
    val t1 = points.maxOf { it.first }
    val grid = MaterialTheme.colorScheme.surfaceVariant

    Column {
        Row(Modifier.height(160.dp)) {
            Column(Modifier.width(52.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(format(hi), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(format(lo), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                fun x(t: Long) = if (t1 == t0) size.width / 2 else (t - t0).toFloat() / (t1 - t0) * size.width
                fun y(v: Float) = size.height * (1f - (v - lo) / (hi - lo))
                drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f))
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height))

                secondary.forEach { (t, v) -> drawCircle(secondaryColor, 3.5.dp.toPx(), Offset(x(t), y(v))) }

                val path = Path()
                points.forEachIndexed { i, (t, v) ->
                    if (i == 0) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
                }
                if (points.size > 1) drawPath(path, color, style = Stroke(2.5.dp.toPx()))
                points.forEach { (t, v) -> drawCircle(color, 4.5.dp.toPx(), Offset(x(t), y(v))) }
            }
        }
        Row(Modifier.padding(start = 52.dp)) {
            Text(shortDate.format(Date(t0)), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (t1 != t0) {
                Text(shortDate.format(Date(t1)), Modifier.weight(1f), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Seconds as "45 s" under a minute, else m:ss. */
private fun secs(v: Double): String { val r = v.roundToInt(); return if (r < 60) "$r s" else mmss(r) }
