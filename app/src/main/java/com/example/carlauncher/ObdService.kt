package com.example.carlauncher
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.*
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.min

/** Foreground service: ELM327 over Bluetooth or Wi-Fi. Auto-reconnect with backoff, prioritised polling, DTC decode + alert. */
class ObdService : Service() {
    private class Link(val i: InputStream, val o: OutputStream, val close: () -> Unit)
    @Volatile private var running = false
    private var known = emptySet<String>()
    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, id: Int): Int {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("obd", "OBD", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel("dtc", "Fault alerts", NotificationManager.IMPORTANCE_HIGH)) }
        startForeground(1, note("obd", "OBD connected service running", false))
        val addr = getSharedPreferences("p", MODE_PRIVATE).getString("obd", null)
        if (addr == null) { stopSelf(); return START_NOT_STICKY }
        if (!running) { running = true; thread(name = "obd") { loop(addr) } }
        return START_STICKY
    }
    override fun onDestroy() { running = false; super.onDestroy() }

    @Suppress("DEPRECATION")
    private fun note(ch: String, text: String, alert: Boolean): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, ch) else Notification.Builder(this)
        return b.setContentTitle(if (alert) "Vehicle fault detected" else "Car Launcher").setContentText(text)
            .setSmallIcon(if (alert) android.R.drawable.stat_sys_warning else android.R.drawable.stat_sys_data_bluetooth).build()
    }

    private fun loop(addr: String) {
        var back = 1000L
        while (running) {
            var link: Link? = null
            try {
                link = open(addr)
                for (c in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATSP0")) send(link, c)
                Vehicle.obdConnected = true; Vehicle.post(); back = 1000L
                var n = 0; val delay = if (DeviceTier.tier == DeviceTier.T.LOW) 250L else 120L
                while (running) {                                   // speed+rpm every cycle, slow PIDs rarely
                    hex(send(link, "010D"), "410D")?.take(2)?.toIntOrNull(16)?.let { Vehicle.speed = it }
                    hex(send(link, "010C"), "410C")?.take(4)?.toIntOrNull(16)?.let { Vehicle.rpm = it / 4 }
                    if (n % 10 == 0) {
                        hex(send(link, "0105"), "4105")?.take(2)?.toIntOrNull(16)?.let { Vehicle.coolant = it - 40 }
                        Regex("[0-9.]+").find(send(link, "ATRV"))?.value?.toFloatOrNull()?.let { Vehicle.volts = it } }
                    if (n % 80 == 0) dtcs(send(link, "03"))
                    Vehicle.post(); n++; Thread.sleep(delay)
                }
            } catch (e: Exception) {
            } finally { Vehicle.obdConnected = false; Vehicle.speed = 0; Vehicle.post(); try { link?.close?.invoke() } catch (e: Exception) {} }
            if (running) Thread.sleep(back)
            back = min(back * 2, 15000L)
        }
    }

    @SuppressLint("MissingPermission")
    private fun open(addr: String): Link {
        if (addr.startsWith("wifi:")) {
            val p = addr.removePrefix("wifi:").split(":"); val s = Socket(); s.connect(InetSocketAddress(p[0], p[1].toInt()), 4000)
            return Link(s.getInputStream(), s.getOutputStream()) { s.close() } }
        val bt = BluetoothAdapter.getDefaultAdapter(); bt.cancelDiscovery()
        val s = bt.getRemoteDevice(addr).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")); s.connect()
        return Link(s.inputStream, s.outputStream) { s.close() }
    }

    private fun send(l: Link, cmd: String): String {
        l.o.write("$cmd\r".toByteArray()); l.o.flush()
        val sb = StringBuilder(); val end = SystemClock.uptimeMillis() + 2000
        while (SystemClock.uptimeMillis() < end) {
            if (l.i.available() > 0) { val c = l.i.read(); if (c == '>'.code) return sb.toString().replace("\\s".toRegex(), ""); sb.append(c.toChar()) }
            else Thread.sleep(5)
        }
        throw IOException("timeout")
    }
    private fun hex(r: String, h: String): String? { val i = r.indexOf(h); return if (i < 0) null else r.substring(i + h.length) }

    /** Mode 03 -> P0301 style codes */
    private fun dtcs(r: String) {
        val d = hex(r, "43") ?: ""; val codes = ArrayList<String>(); var i = 0
        while (i + 4 <= d.length) { val c = d.substring(i, i + 4); i += 4; if (c == "0000") continue
            val a = c[0].digitToIntOrNull(16) ?: continue
            codes.add("${"PCBU"[a shr 2]}${a and 3}${c.substring(1)}") }
        Vehicle.dtc = codes
        val fresh = codes.filter { it !in known }; known = known + codes
        if (fresh.isNotEmpty()) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(2, note("dtc", "Codes: " + fresh.joinToString(", "), true))
    }
}
