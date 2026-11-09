package com.example.carlauncher
import android.content.*

/** No standard Android API for vehicle lights. Each head-unit brand sends its own broadcast.
 *  Add the action/extra your unit uses (check with a broadcast logger app). Fallback: manual toggle / time of day. */
object LightsSync {
    private val ACTIONS = listOf("com.microntek.light" /* example, verify for your unit */, "com.example.HEADLIGHT")
    fun register(ctx: Context, cb: (Boolean) -> Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { cb(i.getBooleanExtra("on", i.getIntExtra("state", 0) == 1)) }
        }
        val f = IntentFilter().apply { ACTIONS.forEach(::addAction) }
        androidx.core.content.ContextCompat.registerReceiver(ctx, r, f, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
    }
}
