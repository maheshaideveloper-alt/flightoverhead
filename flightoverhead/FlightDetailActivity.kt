package com.flightoverhead

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.flightoverhead.data.RouteRepository
import com.flightoverhead.databinding.ActivityFlightDetailBinding
import kotlinx.coroutines.launch

class FlightDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ICAO24        = "icao24"
        const val EXTRA_CALLSIGN      = "callsign"
        const val EXTRA_COUNTRY       = "country"
        const val EXTRA_ALTITUDE      = "altitude"
        const val EXTRA_SPEED         = "speed"
        const val EXTRA_HEADING       = "heading"
        const val EXTRA_VERTICAL_RATE = "verticalRate"
        const val EXTRA_CLIMB_DESCR   = "climbDescr"
        const val EXTRA_SQUAWK        = "squawk"
        const val EXTRA_LAT           = "lat"
        const val EXTRA_LON           = "lon"
    }

    private lateinit var binding: ActivityFlightDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        binding = ActivityFlightDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── Read extras ──────────────────────────────────────────────────
        val callsign     = intent.getStringExtra(EXTRA_CALLSIGN)      ?: "UNKNOWN"
        val icao24       = intent.getStringExtra(EXTRA_ICAO24)        ?: ""
        val country      = intent.getStringExtra(EXTRA_COUNTRY)       ?: "Unknown"
        val altitude     = intent.getIntExtra(EXTRA_ALTITUDE,    0)
        val speed        = intent.getIntExtra(EXTRA_SPEED,       0)
        val heading      = intent.getIntExtra(EXTRA_HEADING,     0)
        val verticalRate = intent.getDoubleExtra(EXTRA_VERTICAL_RATE, 0.0)
        val climbDescr   = intent.getStringExtra(EXTRA_CLIMB_DESCR)   ?: "Cruising"
        val squawk       = intent.getStringExtra(EXTRA_SQUAWK)        ?: "N/A"
        val lat          = intent.getDoubleExtra(EXTRA_LAT,  0.0)
        val lon          = intent.getDoubleExtra(EXTRA_LON,  0.0)

        val vertRateFpm = (verticalRate * 196.85).toInt()
        val vertRateStr = when {
            verticalRate >  1.0 -> "+%,d".format(vertRateFpm)
            verticalRate < -1.0 -> "%,d".format(vertRateFpm)
            else                -> "0"
        }

        // ── Bind static fields ───────────────────────────────────────────
        binding.apply {
            tvCallsign.text     = callsign
            tvIcao.text         = "ICAO  $icao24"
            tvCountry.text      = country
            tvAltitude.text     = "%,d".format(altitude)
            tvSpeed.text        = "%,d".format(speed)
            tvHeading.text      = "${heading}°  ${headingToCompass(heading)}"
            tvVerticalRate.text = vertRateStr
            tvSquawk.text       = squawk
            tvPosition.text     = "%.4f° N\n%.4f° W".format(lat, Math.abs(lon))

            // Airline placeholder until route loads
            tvAirlineName.text  = ""

            // Status pill
            when (climbDescr) {
                "Climbing" -> {
                    tvStatusPill.text = "▲  CLIMBING"
                    tvStatusPill.setBackgroundResource(R.drawable.bg_pill_climbing)
                    tvStatusPill.setTextColor(Color.parseColor("#4CAF7A"))
                }
                "Descending" -> {
                    tvStatusPill.text = "▼  DESCENDING"
                    tvStatusPill.setBackgroundResource(R.drawable.bg_pill_descending)
                    tvStatusPill.setTextColor(Color.parseColor("#F06060"))
                }
                else -> {
                    tvStatusPill.text = "—  CRUISING"
                    tvStatusPill.setBackgroundResource(R.drawable.bg_pill_cruising)
                    tvStatusPill.setTextColor(Color.parseColor("#6EB3F0"))
                }
            }

            btnBack.setOnClickListener { finish() }

            tvFlightaware.setOnClickListener {
                val uri = android.net.Uri.parse("https://flightaware.com/live/flight/$callsign")
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
            }
        }

        // ── Fetch route + airline info ───────────────────────────────────
        lifecycleScope.launch {
            val route = RouteRepository.getRoute(callsign)
            if (route != null) {
                binding.tvAirlineName.text   = route.airlineName
                binding.tvOriginIata.text    = route.originIata
                binding.tvOriginCity.text    = route.originCity
                binding.tvOriginName.text    = route.originName
                binding.tvDestIata.text      = route.destIata
                binding.tvDestCity.text      = route.destCity
                binding.tvDestName.text      = route.destName
                binding.tvRouteLoading.visibility = View.GONE
            } else {
                // Route unknown (private/military/unregistered callsign)
                binding.tvOriginIata.text    = "—"
                binding.tvOriginCity.text    = ""
                binding.tvDestIata.text      = "—"
                binding.tvDestCity.text      = ""
                binding.tvRouteLoading.text  = "Route not available"
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun headingToCompass(deg: Int): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return dirs[((deg + 22) / 45) % 8]
    }
}
