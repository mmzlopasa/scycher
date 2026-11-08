package dev.scyde.scycher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class HourForecast(val hour: String, val temp: Double, val code: Int, val isDay: Boolean)

@Serializable
data class DayForecast(val day: String, val min: Double, val max: Double, val code: Int)

@Serializable
data class Weather(
    val city: String,
    val temp: Double,
    val code: Int,
    val isDay: Boolean,
    val min: Double,
    val max: Double,
    val hours: List<HourForecast>,
    val days: List<DayForecast>,
    val fetchedAt: Long,
)

data class GeoResult(val name: String, val region: String, val lat: Double, val lon: Double)

/** Keyless weather using Open-Meteo. */
class WeatherRepository(private val context: Context, private val store: ConfigStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheFile = File(context.cacheDir, "weather.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _weather = MutableStateFlow(loadCache())
    val weather: StateFlow<Weather?> = _weather
    @Volatile private var fetching = false

    private fun loadCache(): Weather? = try {
        if (cacheFile.exists()) json.decodeFromString(Weather.serializer(), cacheFile.readText()) else null
    } catch (_: Throwable) { null }

    fun refreshIfStale(force: Boolean = false) {
        val w = _weather.value
        val cfg = store.value.widgets
        val stale = w == null || w.city != cfg.weatherCity || System.currentTimeMillis() - w.fetchedAt > 30 * 60_000
        if ((stale || force) && !fetching) scope.launch { fetch() }
    }

    private fun fetch() {
        fetching = true
        try {
            val cfg = store.value.widgets
            val unit = if (cfg.weatherFahrenheit) "&temperature_unit=fahrenheit" else ""
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${cfg.weatherLat}&longitude=${cfg.weatherLon}" +
                "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days=6&timezone=auto$unit"
            val o = JSONObject(get(url))
            val cur = o.getJSONObject("current")
            val hourly = o.getJSONObject("hourly")
            val daily = o.getJSONObject("daily")
            val curTime = cur.getString("time")
            val htimes = hourly.getJSONArray("time")
            var start = 0
            for (i in 0 until htimes.length()) if (htimes.getString(i) <= curTime) start = i
            val hours = (start until (start + 6).coerceAtMost(htimes.length())).map { i ->
                HourForecast(
                    htimes.getString(i).substringAfter('T').take(5),
                    hourly.getJSONArray("temperature_2m").getDouble(i),
                    hourly.getJSONArray("weather_code").getInt(i),
                    hourly.getJSONArray("is_day").getInt(i) == 1,
                )
            }
            val dtimes = daily.getJSONArray("time")
            val days = (0 until dtimes.length()).map { i ->
                DayForecast(
                    dtimes.getString(i),
                    daily.getJSONArray("temperature_2m_min").getDouble(i),
                    daily.getJSONArray("temperature_2m_max").getDouble(i),
                    daily.getJSONArray("weather_code").getInt(i),
                )
            }
            val w = Weather(
                city = cfg.weatherCity,
                temp = cur.getDouble("temperature_2m"),
                code = cur.getInt("weather_code"),
                isDay = cur.getInt("is_day") == 1,
                min = days.firstOrNull()?.min ?: 0.0,
                max = days.firstOrNull()?.max ?: 0.0,
                hours = hours, days = days,
                fetchedAt = System.currentTimeMillis(),
            )
            _weather.value = w
            cacheFile.writeText(json.encodeToString(Weather.serializer(), w))
        } catch (_: Throwable) {
        } finally {
            fetching = false
        }
    }

    suspend fun search(query: String): List<GeoResult> = withContext(Dispatchers.IO) {
        try {
            val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&name=" + URLEncoder.encode(query, "UTF-8")))
            val arr = o.optJSONArray("results") ?: return@withContext emptyList()
            (0 until arr.length()).map { i ->
                val r = arr.getJSONObject(i)
                GeoResult(
                    r.getString("name"),
                    listOfNotNull(r.optString("admin1").takeIf { it.isNotBlank() }, r.optString("country").takeIf { it.isNotBlank() }).joinToString(", "),
                    r.getDouble("latitude"), r.getDouble("longitude"),
                )
            }
        } catch (_: Throwable) { emptyList() }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Scycher-Launcher")
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    companion object {
        fun describe(code: Int): String = when (code) {
            0 -> "Clear"
            1 -> "Mostly Clear"
            2 -> "Partly Cloudy"
            3 -> "Cloudy"
            45, 48 -> "Fog"
            51, 53, 55, 56, 57 -> "Drizzle"
            61, 63, 66 -> "Rain"
            65, 67 -> "Heavy Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Showers"
            85, 86 -> "Snow Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "—"
        }
    }
}
