package com.flightoverhead.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object WeatherRepository {

    private val client = OkHttpClient()

    suspend fun getWeatherString(): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${FlightRepository.HOME_LAT}" +
                "&longitude=${FlightRepository.HOME_LON}" +
                "&current_weather=true" +
                "&temperature_unit=fahrenheit" +
                "&windspeed_unit=mph"

            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { it.body?.string() } ?: return@withContext "—"

            val cw = JsonParser.parseString(body)
                .asJsonObject.getAsJsonObject("current_weather")
            val temp  = cw.get("temperature").asDouble.toInt()
            val code  = cw.get("weathercode").asInt

            "${temp}°F  ${weatherLabel(code)}"
        } catch (e: Exception) {
            "Weather unavailable"
        }
    }

    private fun weatherLabel(code: Int): String = when (code) {
        0            -> "☀️ Clear"
        1, 2, 3      -> "⛅ Cloudy"
        45, 48       -> "🌫 Foggy"
        in 51..57    -> "🌦 Drizzle"
        in 61..67    -> "🌧 Rain"
        in 71..77    -> "🌨 Snow"
        in 80..82    -> "🌧 Showers"
        95, 96, 99   -> "⛈ Storm"
        else         -> ""
    }
}
