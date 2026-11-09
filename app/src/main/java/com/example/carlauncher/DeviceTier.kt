package com.example.carlauncher
import android.app.ActivityManager
import android.content.Context

/** Detects how weak the head unit is and scales animation cost. LOW = 20fps, no reflection/pulse, idle = zero redraws. */
object DeviceTier {
    enum class T { LOW, MID, HIGH }
    var tier = T.MID; var fps = 30
    val fancy get() = tier != T.LOW
    fun init(c: Context) {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gb = mi.totalMem / 1073741824.0; val cores = Runtime.getRuntime().availableProcessors()
        tier = when {
            c.getSharedPreferences("p", 0).getString("tier", null) != null -> T.valueOf(c.getSharedPreferences("p", 0).getString("tier", "MID")!!)
            am.isLowRamDevice || gb < 1.5 || cores <= 2 -> T.LOW
            gb < 3.5 || cores <= 4 -> T.MID
            else -> T.HIGH }
        fps = when (tier) { T.LOW -> 20; T.MID -> 30; T.HIGH -> 60 }
    }
}
