package com.laurio.slamlog.data

import org.json.JSONArray
import java.time.LocalDate
import java.time.Period
import org.json.JSONObject

/** What the next workout looks like. */
data class WorkoutConfig(
    val sets: Int = 5,
    val targetReps: Int = 15,
    val restSec: Int = 60,
    val ballWeightLbs: Int = 20,
    val autoCount: Boolean = true,
    /** Rest until heart rate falls to the recovery target (then 5 s to go), instead of a fixed time. */
    val restUntilTarget: Boolean = false,
    /**
     * Rest-until-target mode: the longest a rest can last, whether or not HR got there (also the rest
     * used when there's no heart rate). 0 = no limit, wait for the target (the baseline test uses this).
     */
    val maxRestSec: Int = 90,
)

/** Personal details (for zones and calories) and app preferences. */
data class UserSettings(
    /** Birthday as yyyy-mm-dd; null until entered. Age is worked out from it every day. */
    val birthDate: String? = null,
    val weightLbs: Int = 180,
    val male: Boolean = true,
    /** 0 means "use 220 minus age". */
    val maxHrOverride: Int = 0,
    /** Slam detection sensitivity, 0 (only very loud slams) to 1 (very sensitive). */
    val sensitivity: Float = 0.5f,
    val voice: Boolean = true,
    /** Call out the rep count every N reps; 0 turns it off. */
    val callEvery: Int = 5,
    /** Short tick sound each time a rep is counted. */
    val repTick: Boolean = true,
    /** Heart rate to recover down to after each set ("time from last slam to 130"). */
    val recoveryTargetHr: Int = 130,
    /** Shockwave burst and shake on screen for every counted slam. */
    val slamEffects: Boolean = true,
    /** The fixed "baseline test" used to measure recovery fairly as training volume grows. */
    val baselineSets: Int = 10,
    val baselineReps: Int = 20,
    val baselineBallLbs: Int = 20,
    val baselineRestUntilTarget: Boolean = true,
) {
    val birthday: LocalDate?
        get() = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Age today, from the birthday. 40 is a placeholder until a birthday is entered. */
    val age: Int
        get() = birthday?.let { Period.between(it, LocalDate.now()).years.coerceIn(10, 110) } ?: DEFAULT_AGE

    val hasBirthday: Boolean get() = birthday != null

    /** Age-predicted max heart rate (220 − age), unless the user entered their real max. */
    val estimatedMaxHr: Int get() = 220 - age

    val maxHr: Int get() = if (maxHrOverride > 0) maxHrOverride else estimatedMaxHr

    companion object {
        const val DEFAULT_AGE = 40
    }
}

data class SetResult(
    val number: Int,
    val reps: Int,
    val durationSec: Int,
    val avgHr: Int,
    val peakHr: Int,
    /** Beats per minute the heart rate dropped after the set; null if unknown. */
    val recoveryBpm: Int? = null,
    /** Over how many seconds the recovery was measured (60 when the rest was a minute or longer). */
    val recoverySec: Int = 0,
    /** The heart rate target that applied to this set. */
    val targetHr: Int = 0,
    /** Seconds from the last slam of the set until HR fell to [targetHr]; null if it didn't get there. */
    val timeToTargetSec: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("number", number)
        .put("reps", reps)
        .put("durationSec", durationSec)
        .put("avgHr", avgHr)
        .put("peakHr", peakHr)
        .put("recoveryBpm", recoveryBpm ?: JSONObject.NULL)
        .put("recoverySec", recoverySec)
        .put("targetHr", targetHr)
        .put("timeToTargetSec", timeToTargetSec ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = SetResult(
            number = o.optInt("number"),
            reps = o.optInt("reps"),
            durationSec = o.optInt("durationSec"),
            avgHr = o.optInt("avgHr"),
            peakHr = o.optInt("peakHr"),
            recoveryBpm = if (o.isNull("recoveryBpm")) null else o.optInt("recoveryBpm"),
            recoverySec = o.optInt("recoverySec"),
            targetHr = o.optInt("targetHr"),
            timeToTargetSec = if (o.isNull("timeToTargetSec")) null else o.optInt("timeToTargetSec"),
        )
    }
}

data class Session(
    val id: Long,
    val startMillis: Long,
    val durationSec: Int,
    val ballWeightLbs: Int,
    val targetReps: Int,
    val sets: List<SetResult>,
    val avgHr: Int,
    val maxHr: Int,
    val calories: Double,
    /** Seconds spent in zones 0 (below zone 1) through 5. */
    val zoneSec: List<Int>,
    /** One heart rate reading per second; 0 where no reading was available. */
    val hrSamples: List<Int>,
    /** How hard the whole workout felt, 1 (easy) to 10 (all-out); null if not rated. */
    val rpe: Int? = null,
    /** True for a baseline test (always the same workout, so recovery can be compared fairly). */
    val baseline: Boolean = false,
    /** How many sets were planned; a baseline only counts if they were all done. */
    val plannedSets: Int = 0,
    val restUntilTarget: Boolean = false,
) {
    val totalReps: Int get() = sets.sumOf { it.reps }

    /** Sets measured against this heart rate target. */
    fun setsWithTarget(target: Int) = sets.filter { it.targetHr == target }

    /** Average seconds from last slam to [target], over the sets that got there; null if none did. */
    fun avgTimeToTarget(target: Int): Double? =
        setsWithTarget(target).mapNotNull { it.timeToTargetSec }.takeIf { it.isNotEmpty() }?.average()

    /** Every planned set was done (or the plan is unknown). */
    val completed: Boolean get() = plannedSets == 0 || sets.size >= plannedSets

    /** Short description like "10 × 20 · 20 lb". */
    val shape: String get() = "${if (plannedSets > 0) plannedSets else sets.size} × $targetReps · $ballWeightLbs lb"

    val avgRecoveryBpm: Double?
        get() = sets.mapNotNull { it.recoveryBpm }.takeIf { it.isNotEmpty() }?.average()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("startMillis", startMillis)
        .put("durationSec", durationSec)
        .put("ballWeightLbs", ballWeightLbs)
        .put("targetReps", targetReps)
        .put("sets", JSONArray().apply { sets.forEach { put(it.toJson()) } })
        .put("avgHr", avgHr)
        .put("maxHr", maxHr)
        .put("calories", calories)
        .put("zoneSec", JSONArray(zoneSec))
        .put("hrSamples", JSONArray(hrSamples))
        .put("rpe", rpe ?: JSONObject.NULL)
        .put("baseline", baseline)
        .put("plannedSets", plannedSets)
        .put("restUntilTarget", restUntilTarget)

    companion object {
        fun fromJson(o: JSONObject): Session {
            val setsArr = o.optJSONArray("sets") ?: JSONArray()
            val zones = o.optJSONArray("zoneSec") ?: JSONArray()
            val samples = o.optJSONArray("hrSamples") ?: JSONArray()
            return Session(
                id = o.optLong("id"),
                startMillis = o.optLong("startMillis"),
                durationSec = o.optInt("durationSec"),
                ballWeightLbs = o.optInt("ballWeightLbs"),
                targetReps = o.optInt("targetReps"),
                sets = (0 until setsArr.length()).map { SetResult.fromJson(setsArr.getJSONObject(it)) },
                avgHr = o.optInt("avgHr"),
                maxHr = o.optInt("maxHr"),
                calories = o.optDouble("calories", 0.0),
                zoneSec = List(6) { i -> if (i < zones.length()) zones.optInt(i) else 0 },
                hrSamples = (0 until samples.length()).map { samples.optInt(it) },
                rpe = optIntOrNull(o, "rpe"),
                baseline = o.optBoolean("baseline", false),
                plannedSets = o.optInt("plannedSets", 0),
                restUntilTarget = o.optBoolean("restUntilTarget", false),
            )
        }
    }
}

/** A whole number from JSON, or null when the key is missing or null. */
internal fun optIntOrNull(o: JSONObject, key: String): Int? =
    if (o.has(key) && !o.isNull(key)) o.optInt(key) else null
