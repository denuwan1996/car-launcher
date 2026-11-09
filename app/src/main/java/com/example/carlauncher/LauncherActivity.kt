package com.example.carlauncher
import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.location.*
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class LauncherActivity : AppCompatActivity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var car: CarView; private lateinit var gauge: GaugeView; private lateinit var radio: Radio
    private lateinit var appGrid: RecyclerView
    private var speed = 0; private var tripKm = 0f; private var driveSec = 0; private var radioOn = false
    private val obdActive get() = Vehicle.obdConnected   // set true when OBD is connected so GPS stops overriding speed

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        DeviceTier.init(this)
        setContentView(R.layout.activity_launcher)
        car = findViewById(R.id.car); gauge = findViewById(R.id.gauge); appGrid = findViewById(R.id.appGrid); radio = Radio(this)
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        car.variant = CarView.Variant.values()[prefs.getInt("variant", 0)]
        car.setOnClickListener { val n = (car.variant.ordinal + 1) % CarView.Variant.values().size
            car.variant = CarView.Variant.values()[n]; prefs.edit().putInt("variant", n).apply() }
        car.setOnLongClickListener { setLights(!car.lightsOn); true }
        LightsSync.register(this) { setLights(it) }
        findViewById<View>(R.id.night).setOnClickListener {
            val dark = (resources.configuration.uiMode and 0x30) == 0x20
            AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES) }

        // Top bar: greeting, date, clock; trip/drive stats
        val clock = findViewById<TextView>(R.id.clock); val greet = findViewById<TextView>(R.id.greet); val date = findViewById<TextView>(R.id.date)
        val trip = findViewById<TextView>(R.id.trip); val drive = findViewById<TextView>(R.id.drive)
        val tf = SimpleDateFormat("hh:mm a", Locale.getDefault()); val df = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        var weather = ""
        ui.post(object : Runnable { override fun run() {
            val now = Date(); val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            clock.text = tf.format(now); date.text = df.format(now) + weather
            greet.text = if (h < 12) "Good morning" else if (h < 18) "Good afternoon" else "Good evening"
            if (speed > 0) { tripKm += speed / 3600f; driveSec++ }
            trip.text = "${tripKm.toInt()} km"; drive.text = "${driveSec / 60} min"
            updateProgress(); ui.postDelayed(this, 1000) } })
        fun loadWeather(): Thread = thread { val t = Weather.fetch(6.9271, 79.8612); ui.post { if (t != null) weather = "  ·  $t" }
            ui.postDelayed({ loadWeather() }, 30 * 60_000L) }
        loadWeather()

        // Music card
        try { MediaListener.attach(this) { ui.post { refreshMusic() } } }
        catch (e: SecurityException) { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
        findViewById<View>(R.id.prev).setOnClickListener { MediaListener.controller?.transportControls?.skipToPrevious() }
        findViewById<View>(R.id.next).setOnClickListener { MediaListener.controller?.transportControls?.skipToNext() }
        findViewById<View>(R.id.play).setOnClickListener { val c = MediaListener.controller ?: return@setOnClickListener
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play() }
        findViewById<View>(R.id.turn).setOnClickListener { nav() }

        // Bottom navigation
        val items = listOf<Triple<String, String, () -> Unit>>(
            Triple("🧭", "Navigation", { nav() }),
            Triple("🎵", "Music", { open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)) }),
            Triple("▶️", "Video", { open(packageManager.getLaunchIntentForPackage("com.google.android.youtube")) }),
            Triple("📞", "Phone", { open(Intent(Intent.ACTION_DIAL)) }),
            Triple("📻", "Radio", { if (radioOn) radio.stop() else radio.play(0); radioOn = !radioOn }),
            Triple("🌐", "Connectivity", { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }),
            Triple("⚙️", "Settings", { open(Intent(Settings.ACTION_SETTINGS)) }),
            Triple("▦", "Apps", { appGrid.visibility = View.VISIBLE }))
        val nav = findViewById<LinearLayout>(R.id.nav); val labels = ArrayList<TextView>()
        items.forEachIndexed { i, (ic, lb, act) ->
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f) }
            col.addView(TextView(this).apply { text = ic; textSize = 22f; gravity = Gravity.CENTER })
            val t = TextView(this).apply { text = lb; textSize = 12f; gravity = Gravity.CENTER; setTextColor(getColor(if (i == 0) R.color.accent else R.color.text)) }
            labels.add(t); col.addView(t)
            col.setOnClickListener { labels.forEachIndexed { j, l -> l.setTextColor(getColor(if (j == i) R.color.accent else R.color.text)) }; act() }
            nav.addView(col)
        }

        // App drawer overlay
        appGrid.layoutManager = GridLayoutManager(this, 6)
        val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { Triple(it.loadLabel(packageManager).toString(), it.loadIcon(packageManager), it.activityInfo.packageName) }.sortedBy { it.first }
        appGrid.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = apps.size
            override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
                val v = LinearLayout(p.context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(8, 16, 8, 16)
                    addView(ImageView(p.context).apply { layoutParams = LinearLayout.LayoutParams(112, 112) })
                    addView(TextView(p.context).apply { gravity = Gravity.CENTER; singleLine(); setTextColor(p.context.getColor(R.color.text)) }) }
                v.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                return object : RecyclerView.ViewHolder(v) {} }
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
                val g = h.itemView as LinearLayout
                (g.getChildAt(0) as ImageView).setImageDrawable(apps[i].second); (g.getChildAt(1) as TextView).text = apps[i].first
                g.setOnClickListener { appGrid.visibility = View.GONE; open(packageManager.getLaunchIntentForPackage(apps[i].third)) } }
        }
        val rpmT = findViewById<TextView>(R.id.rpm); val tempT = findViewById<TextView>(R.id.temp); val voltT = findViewById<TextView>(R.id.volt); val alertT = findViewById<TextView>(R.id.alert)
        Vehicle.onChange = { ui.post {
            val on = Vehicle.obdConnected
            if (on) onSpeed(Vehicle.speed)
            rpmT.text = if (on) "${Vehicle.rpm}" else "--"
            tempT.text = if (on && Vehicle.coolant > -40) "${Vehicle.coolant}°C" else "--"
            voltT.text = if (on && Vehicle.volts > 0) "%.1fV".format(Vehicle.volts) else "--"
            alertT.visibility = if (Vehicle.dtc.isEmpty()) View.GONE else View.VISIBLE
            alertT.text = "⚠ Fault codes: " + Vehicle.dtc.joinToString(", ") } }
        findViewById<View>(R.id.telem).setOnClickListener { pickObd() }
        if (prefs.getString("obd", null) != null) startObd()
        startGps()
    }

    private fun startObd() { val i = Intent(this, ObdService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i) }
    /** Tap the RPM/COOLANT/BATTERY strip to choose the ELM327 adapter (paired Bluetooth or Wi-Fi). */
    private fun pickObd() {
        val names = ArrayList<String>(); val addrs = ArrayList<String>()
        try { BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.forEach { names.add(it.name ?: it.address); addrs.add(it.address) } }
        catch (e: SecurityException) { requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 2); return }
        names.add("Wi-Fi OBD (192.168.0.10:35000)"); addrs.add("wifi:192.168.0.10:35000")
        names.add("Emulator test (10.0.2.2:35000)"); addrs.add("wifi:10.0.2.2:35000")
        AlertDialog.Builder(this).setTitle("OBD adapter").setItems(names.toTypedArray()) { _, i ->
            getSharedPreferences("p", MODE_PRIVATE).edit().putString("obd", addrs[i]).apply()
            stopService(Intent(this, ObdService::class.java)); startObd() }.show()
    }

    private fun TextView.singleLine() { setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END }
    private fun open(i: Intent?) { try { if (i != null) startActivity(i) else Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show() } catch (e: Exception) {} }
    private fun nav() = open(packageManager.getLaunchIntentForPackage("com.google.android.apps.maps") ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")))

    /** Call from OBD polling thread via runOnUiThread, or from GPS. */
    fun onSpeed(kmh: Int) { speed = kmh; gauge.setSpeed(kmh); car.speed = kmh }

    private fun setLights(on: Boolean) { car.lightsOn = on
        AppCompatDelegate.setDefaultNightMode(if (on) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO) }

    private fun refreshMusic() {
        val c = MediaListener.controller; val m = c?.metadata
        findViewById<TextView>(R.id.song).text = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Not playing"
        findViewById<TextView>(R.id.artist).text = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        findViewById<ImageView>(R.id.art).setImageBitmap(m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART))
        findViewById<TextView>(R.id.play).text = if (c?.playbackState?.state == PlaybackState.STATE_PLAYING) "⏸" else "▶"
    }
    private fun updateProgress() {
        val c = MediaListener.controller ?: return; val d = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val p = c.playbackState?.position ?: 0L
        if (d > 0) findViewById<ProgressBar>(R.id.prog).progress = (p * 1000 / d).toInt()
    }

    private fun startGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1); return }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, object : LocationListener {
            override fun onLocationChanged(l: Location) {
                if (!obdActive) onSpeed((l.speed * 3.6f).toInt())
                if (l.hasBearing()) findViewById<TextView>(R.id.heading).text = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((l.bearing + 22.5f) / 45f).toInt() % 8] }
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }) } catch (e: SecurityException) {}
    }
    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r); if (r.firstOrNull() == PackageManager.PERMISSION_GRANTED) { if (rc == 1) startGps() else if (rc == 2) pickObd() } }
    override fun onNewIntent(i: Intent?) { super.onNewIntent(i); appGrid.visibility = View.GONE }
    @Deprecated("") override fun onBackPressed() { if (appGrid.visibility == View.VISIBLE) appGrid.visibility = View.GONE }
    override fun onDestroy() { Vehicle.onChange = null; radio.release(); super.onDestroy() }
}
