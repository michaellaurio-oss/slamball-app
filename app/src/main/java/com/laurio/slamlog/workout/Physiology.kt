package com.laurio.slamlog.workout

import com.laurio.slamlog.data.UserSettings
import kotlin.math.max

object Physiology {
    val zoneNames = listOf("Below Z1", "Z1 Warm-up", "Z2 Easy", "Z3 Aerobic", "Z4 Threshold", "Z5 Max")

    /** ARGB colors for zones 0..5. */
    val zoneColors = listOf(
        0xFF6B7280, 0xFF60A5FA, 0xFF34D399, 0xFFFBBF24, 0xFFF97316, 0xFFEF4444,
    )

    /** Zone by percent of max heart rate: Z1 50–60%, Z2 60–70%, Z3 70–80%, Z4 80–90%, Z5 90%+. */
    fun zone(bpm: Int, maxHr: Int): Int {
        if (bpm <= 0 || maxHr <= 0) return 0
        val pct = bpm.toDouble() / maxHr
        return when {
            pct < 0.5 -> 0
            pct < 0.6 -> 1
            pct < 0.7 -> 2
            pct < 0.8 -> 3
            pct < 0.9 -> 4
            else -> 5
        }
    }

    /**
     * Calories burned per second at a given heart rate, using the Keytel et al. (2005)
     * heart-rate equation. It's an estimate, like every chest-strap calorie number.
     */
    fun kcalPerSecond(bpm: Int, s: UserSettings): Double {
        val kg = s.weightLbs * 0.453592
        val perMinute = if (s.male) {
            (-55.0969 + 0.6309 * bpm + 0.1988 * kg + 0.2017 * s.age) / 4.184
        } else {
            (-20.4022 + 0.4472 * bpm - 0.1263 * kg + 0.074 * s.age) / 4.184
        }
        return max(0.0, perMinute) / 60.0
    }
}

fun mmss(totalSec: Int): String {
    val s = max(0, totalSec)
    return "%d:%02d".format(s / 60, s % 60)
}
