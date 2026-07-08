package com.flightoverhead.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Route and airline info from the free adsbdb API.
 * https://api.adsbdb.com/v0/callsign/{callsign}
 *
 * Returns null if the callsign is unknown or the request fails.
 */
data class RouteInfo(
    val airlineName: String,   // e.g. "Delta Air Lines"
    val airlineIata: String,   // e.g. "DL"
    val originIata:  String,   // e.g. "ATL"
    val originCity:  String,   // e.g. "Atlanta"
    val originName:  String,   // e.g. "Hartsfield–Jackson Atlanta International Airport"
    val destIata:    String,   // e.g. "LAX"
    val destCity:    String,   // e.g. "Los Angeles"
    val destName:    String    // e.g. "Los Angeles International Airport"
)

object RouteRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getRoute(callsign: String): RouteInfo? = withContext(Dispatchers.IO) {
        try {
            val cs  = callsign.trim().uppercase()
            val url = "https://api.adsbdb.com/v0/callsign/$cs"
            val req = Request.Builder().url(url).build()
            val body = client.newCall(req).execute().use { it.body?.string() }
                ?: return@withContext null

            val root     = JSONObject(body)
            val response = root.optJSONObject("response")    ?: return@withContext null
            val route    = response.optJSONObject("flightroute") ?: return@withContext null

            val airline = route.optJSONObject("airline")
            val origin  = route.optJSONObject("origin")
            val dest    = route.optJSONObject("destination")

            RouteInfo(
                airlineName = airline?.optString("name")         ?: "",
                airlineIata = airline?.optString("iata")         ?: "",
                originIata  = origin?.optString("iata_code")     ?: "???",
                originCity  = origin?.optString("municipality")  ?: "—",
                originName  = origin?.optString("name")          ?: "",
                destIata    = dest?.optString("iata_code")       ?: "???",
                destCity    = dest?.optString("municipality")    ?: "—",
                destName    = dest?.optString("name")            ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }
}
