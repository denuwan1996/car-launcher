package com.example.carlauncher
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** ONE shared frame loop for every animated view (instead of one animator each). Frame-rate independent (dt), fps-capped by DeviceTier. */
object Ticker {
    private val h = Handler(Looper.getMainLooper()); private val subs = ArrayList<(Float) -> Unit>()
    private var last = 0L; private var on = false
    private val loop = object : Runnable { override fun run() {
        val n = SystemClock.uptimeMillis(); val dt = ((n - last) / 1000f).coerceAtMost(0.1f); last = n
        for (s in subs.toTypedArray()) s(dt)
        if (subs.isNotEmpty()) h.postDelayed(this, 1000L / DeviceTier.fps) else on = false } }
    fun add(s: (Float) -> Unit) { if (s !in subs) subs.add(s); if (!on) { on = true; last = SystemClock.uptimeMillis(); h.post(loop) } }
    fun remove(s: (Float) -> Unit) { subs.remove(s) }
}
