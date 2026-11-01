package com.laurio.slamlog.data

import android.content.Context

class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("slamlog", Context.MODE_PRIVATE)

    fun loadSettings() = UserSettings(
        birthDate = p.getString("birthDate", null),
        weightLbs = p.getInt("weightLbs", 180),
        male = p.getBoolean("male", true),
        maxHrOverride = p.getInt("maxHrOverride", 0),
        sensitivity = p.getFloat("sensitivity", 0.5f),
        voice = p.getBoolean("voice", true),
        callEvery = p.getInt("callEvery", 5),
        repTick = p.getBoolean("repTick", true),
        recoveryTargetHr = p.getInt("recoveryTargetHr", 130),
        slamEffects = p.getBoolean("slamEffects", true),
        baselineSets = p.getInt("baselineSets", 10),
        baselineReps = p.getInt("baselineReps", 20),
        baselineBallLbs = p.getInt("baselineBallLbs", p.getInt("ballWeightLbs", 20)),
        baselineRestUntilTarget = p.getBoolean("baselineRestUntilTarget", true),
        baselineMinRestSec = p.getInt("baselineMinRestSec", 60),
        baselineTargetHr = p.getInt("baselineTargetHr", p.getInt("recoveryTargetHr", 130)),
        hrAlarm = p.getBoolean("hrAlarm", false),
        hrAlarmBpm = p.getInt("hrAlarmBpm", 0),
    )

    fun saveSettings(s: UserSettings) {
        p.edit()
            .putString("birthDate", s.birthDate)
            .putInt("weightLbs", s.weightLbs)
            .putBoolean("male", s.male)
            .putInt("maxHrOverride", s.maxHrOverride)
            .putFloat("sensitivity", s.sensitivity)
            .putBoolean("voice", s.voice)
            .putInt("callEvery", s.callEvery)
            .putBoolean("repTick", s.repTick)
            .putInt("recoveryTargetHr", s.recoveryTargetHr)
            .putBoolean("slamEffects", s.slamEffects)
            .putInt("baselineSets", s.baselineSets)
            .putInt("baselineReps", s.baselineReps)
            .putInt("baselineBallLbs", s.baselineBallLbs)
            .putBoolean("baselineRestUntilTarget", s.baselineRestUntilTarget)
            .putInt("baselineMinRestSec", s.baselineMinRestSec)
            .putInt("baselineTargetHr", s.baselineTargetHr)
            .putBoolean("hrAlarm", s.hrAlarm)
            .putInt("hrAlarmBpm", s.hrAlarmBpm)
            .apply()
    }

    fun loadConfig() = WorkoutConfig(
        sets = p.getInt("sets", 5),
        targetReps = p.getInt("targetReps", 15),
        restSec = p.getInt("restSec", 60),
        ballWeightLbs = p.getInt("ballWeightLbs", 20),
        autoCount = p.getBoolean("autoCount", true),
        restUntilTarget = p.getBoolean("restUntilTarget", false),
        minRestSec = p.getInt("minRestSec", 60),
    )

    fun saveConfig(c: WorkoutConfig) {
        p.edit()
            .putInt("sets", c.sets)
            .putInt("targetReps", c.targetReps)
            .putInt("restSec", c.restSec)
            .putInt("ballWeightLbs", c.ballWeightLbs)
            .putBoolean("autoCount", c.autoCount)
            .putBoolean("restUntilTarget", c.restUntilTarget)
            .putInt("minRestSec", c.minRestSec)
            .apply()
    }

    var deviceAddress: String?
        get() = p.getString("deviceAddress", null)
        set(v) = p.edit().putString("deviceAddress", v).apply()

    var deviceName: String?
        get() = p.getString("deviceName", null)
        set(v) = p.edit().putString("deviceName", v).apply()
}
