package com.example.carlauncher
import org.json.JSONObject
import java.net.URL

/** Open-Meteo: free, no key. Run off main thread; cache result ~30 min. */
object Weather {
    fun fetch(lat: Double, lon: Double): String? = try {
        val j = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code").readText())
        val cur = j.getJSONObject("current")
        "${icon(cur.getInt("weather_code"))}  ${cur.getDouble("temperature_2m").toInt()}°C"
    } catch (e: Exception) { null }
    private fun icon(c: Int) = when (c) { 0 -> "☀️"; in 1..3 -> "⛅"; in 45..48 -> "🌫️"; in 51..67 -> "🌧️"; in 71..77 -> "❄️"; in 80..99 -> "⛈️"; else -> "🌡️" }
}
