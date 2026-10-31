package com.laurio.slamlog.workout

import com.laurio.slamlog.data.Session
import com.laurio.slamlog.data.UserSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** The answer to "Is my recovery improving?" */
data class Verdict(val headline: String, val detail: String, val improving: Boolean?)

/** Time to target at one reps-per-set level, first workout vs latest. */
data class RepLevel(val reps: Int, val workouts: Int, val first: Double, val latest: Double, val best: Double)

object Progress {
    private val day = SimpleDateFormat("MMM d", Locale.US)

    /**
     * Finished baseline tests that match today's baseline definition (sets, reps, ball weight,
     * rest mode and minimum rest) and have a time to target, oldest first. Only these are compared with each other.
     */
    fun baselines(sessions: List<Session>, s: UserSettings): List<Session> = sessions
        .filter {
            it.baseline && it.completed &&
                it.targetReps == s.baselineReps &&
                it.ballWeightLbs == s.baselineBallLbs &&
                (it.plannedSets == 0 || it.plannedSets == s.baselineSets) &&
                it.restUntilTarget == s.baselineRestUntilTarget &&
                (!s.baselineRestUntilTarget || it.minRestSec == s.baselineMinRestSec) &&
                it.avgTimeToTarget(s.recoveryTargetHr) != null
        }
        .sortedBy { it.startMillis }

    /**
     * Compares recent baseline tests with early ones. With 4 or more tests it averages the first two
     * and the last two, so one off day (poor sleep, caffeine, heat) can't flip the answer; with 2 or 3
     * it compares the first and the latest. Differences under 3 seconds or 5% count as "about the same".
     */
    fun verdict(baselines: List<Session>, target: Int): Verdict {
        if (baselines.isEmpty()) {
            return Verdict(
                "No baseline test yet",
                "Run a baseline test from the Home screen. It's the same workout every time, so it's a fair " +
                    "yardstick. Repeat it every couple of weeks.",
                null,
            )
        }
        if (baselines.size == 1) {
            val f = baselines.first().avgTimeToTarget(target)!!
            return Verdict(
                "First baseline: ${mmss(f.roundToInt())} to $target",
                "Do another baseline test in a week or two to see how it changes.",
                null,
            )
        }
        val n = if (baselines.size >= 4) 2 else 1
        val early = baselines.take(n)
        val recent = baselines.takeLast(n)
        val f = early.map { it.avgTimeToTarget(target)!! }.average()
        val l = recent.map { it.avgTimeToTarget(target)!! }.average()
        val diff = f - l // positive = faster now
        val pct = if (f > 0) diff / f * 100 else 0.0
        val improving = when {
            diff >= 3 && pct >= 5 -> true
            diff <= -3 && pct <= -5 -> false
            else -> null
        }
        val headline = when (improving) {
            true -> "Yes, your recovery is improving"
            false -> "Recovery is slower than when you started"
            null -> "About the same so far"
        }
        fun dates(list: List<Session>) = list.joinToString(" & ") { day.format(Date(it.startMillis)) }
        val detail = buildString {
            append("Average time from last slam to $target: ")
            if (n == 1) {
                append("${mmss(f.roundToInt())} on ${dates(early)} → ${mmss(l.roundToInt())} on ${dates(recent)}")
            } else {
                append("${mmss(f.roundToInt())} (first two tests, ${dates(early)}) → ")
                append("${mmss(l.roundToInt())} (last two, ${dates(recent)})")
            }
            if (improving != null) {
                append(" (${abs(diff).roundToInt()} s ${if (diff > 0) "faster" else "slower"}, ${abs(pct).roundToInt()}%)")
            }
            append(". ")
            val r1 = early.mapNotNull { it.avgRecoveryBpm }.takeIf { it.isNotEmpty() }?.average()
            val r2 = recent.mapNotNull { it.avgRecoveryBpm }.takeIf { it.isNotEmpty() }?.average()
            if (r1 != null && r2 != null) {
                append("First-minute heart rate drop: ${r1.roundToInt()} → ${r2.roundToInt()} bpm. ")
            }
            append("Based on ${baselines.size} baseline tests.")
        }
        return Verdict(headline, detail, improving)
    }

    /** Time to target grouped by reps per set, so 20-rep workouts are only compared with 20-rep workouts. */
    fun byReps(sessions: List<Session>, target: Int): List<RepLevel> = sessions
        .filter { it.avgTimeToTarget(target) != null }
        .groupBy { it.targetReps }
        .map { (reps, list) ->
            val sorted = list.sortedBy { it.startMillis }
            val times = sorted.map { it.avgTimeToTarget(target)!! }
            RepLevel(reps, sorted.size, times.first(), times.last(), times.min())
        }
        .sortedBy { it.reps }
}

/** All workouts as CSV, one row per set, for a spreadsheet (or to share with Claude). */
fun buildCsv(sessions: List<Session>): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    val sb = StringBuilder()
    sb.append("date,baseline,planned_sets,reps_per_set,ball_lb,rest_until_target,felt_1_to_10,")
    sb.append("set,reps,set_seconds,avg_hr,peak_hr,hr_drop_1min,target_hr,seconds_last_slam_to_target\n")
    sessions.sortedBy { it.startMillis }.forEach { s ->
        s.sets.forEach { set ->
            sb.append(stamp.format(Date(s.startMillis))).append(',')
            sb.append(if (s.baseline) "yes" else "no").append(',')
            sb.append(if (s.plannedSets > 0) s.plannedSets else s.sets.size).append(',')
            sb.append(s.targetReps).append(',')
            sb.append(s.ballWeightLbs).append(',')
            sb.append(if (s.restUntilTarget) "yes" else "no").append(',')
            sb.append(s.rpe ?: "").append(',')
            sb.append(set.number).append(',')
            sb.append(set.reps).append(',')
            sb.append(set.durationSec).append(',')
            sb.append(if (set.avgHr > 0) set.avgHr else "").append(',')
            sb.append(if (set.peakHr > 0) set.peakHr else "").append(',')
            sb.append(set.recoveryBpm ?: "").append(',')
            sb.append(if (set.targetHr > 0) set.targetHr else "").append(',')
            sb.append(set.timeToTargetSec ?: "").append('\n')
        }
    }
    return sb.toString()
}
