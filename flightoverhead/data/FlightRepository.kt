package com.flightoverhead.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object FlightRepository {

    // ── Home coordinates — mutable so the user can pin any location ──────
    var HOME_LAT = 33.0060  // default: North Texas
    var HOME_LON = -96.8453

    var RADIUS_DEG = 0.5
        set(value) { field = value.coerceIn(0.1, 2.0) }

    val radiusMiles: Int get() = (RADIUS_DEG * 69.0).toInt()

    // ── OAuth2 credentials (optional — leave blank for anonymous 400/day) ─
    // Register free at https://opensky-network.org → Account → API Client
    // Authenticated users get 4,000 credits/day (enough for 60s refresh)
    private const val CLIENT_ID     = "mahesh369-api-client"
    private const val CLIENT_SECRET = "xpW2rmEJeYTz2mh3prNblxvPG794lAfC"

    private const val TOKEN_URL =
        "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token"

    // ── Token cache ───────────────────────────────────────────────────────
    private var accessToken: String? = null
    private var tokenExpiresAt: Long = 0L   // epoch millis

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val api: OpenSkyApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val token = accessToken
                val req = if (token != null) {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else chain.request()
                chain.proceed(req)
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        Retrofit.Builder()
            .baseUrl("https://opensky-network.org/api/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenSkyApi::class.java)
    }

    /** Refresh OAuth2 token if credentials are provided and token is expiring soon */
    private suspend fun refreshTokenIfNeeded() {
        if (CLIENT_ID.isBlank() || CLIENT_SECRET.isBlank()) return  // anonymous mode
        val now = System.currentTimeMillis()
        if (accessToken != null && now < tokenExpiresAt - 60_000) return  // still valid

        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build()
            val req = Request.Builder().url(TOKEN_URL).post(body).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body!!.string())
                    accessToken   = json.getString("access_token")
                    val expiresIn = json.optLong("expires_in", 1800)
                    tokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000 - 30_000
                }
            }
        }
    }

    suspend fun getFlightsOverhead(): List<Flight> {
        refreshTokenIfNeeded()
        val response = api.getStates(
            latMin = HOME_LAT - RADIUS_DEG,
            lonMin = HOME_LON - RADIUS_DEG,
            latMax = HOME_LAT + RADIUS_DEG,
            lonMax = HOME_LON + RADIUS_DEG
        )
        return response.states
            ?.mapNotNull { it.toFlight() }
            ?.filter { !it.onGround }
            ?: emptyList()
    }
}
