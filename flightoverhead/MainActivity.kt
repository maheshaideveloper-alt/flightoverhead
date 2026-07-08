package com.flightoverhead

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.location.LocationManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.flightoverhead.data.Flight
import com.flightoverhead.data.FlightRepository
import com.flightoverhead.data.WeatherRepository
import com.flightoverhead.databinding.ActivityMainBinding
import com.flightoverhead.service.FlightMonitorWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // homePoint is mutable — updated by GPS on startup or long-press to pin
    private var homePoint = GeoPoint(FlightRepository.HOME_LAT, FlightRepository.HOME_LON)

    // Flight state
    private val flightData      = mutableMapOf<String, Flight>()
    private val deadReckonedPos = mutableMapOf<String, GeoPoint>()
    private var lastFetchTimeMs = 0L

    // Multi-select: persists across refreshes until plane leaves
    private val selectedIcaos = mutableSetOf<String>()

    private lateinit var chipOverlay: FlightChipOverlay
    private var radiusOverlay: Overlay? = null

    // Animations
    private lateinit var livePulseAnimator: ValueAnimator
    private lateinit var pulseAnimator: ValueAnimator
    private var pulseRadius = 30f

    // Colours
    private val colorGold  = Color.parseColor("#C9A84C")
    private val colorGreen = Color.parseColor("#4ADE80")
    private val colorRed   = Color.parseColor("#FF3B30")
    private val colorPink  = Color.parseColor("#FF69B4")

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val dateFmt = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    private val dayFmt  = SimpleDateFormat("EEEE", Locale.US)

    private val darkTiles = XYTileSource(
        "CartoDarkMatter", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/dark_all/",
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        Configuration.getInstance().userAgentValue = packageName
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        setupHomePulse()
        setupRadiusSlider()
        setupClickListeners()
        requestPermissions()

        // Try GPS first; will also be retried after permission grant
        initLocationFromGps()

        startAutoRefresh()
        startClockTick()
        startDeadReckoning()
        startLivePulse()
        scheduleBackgroundMonitor()
    }

    // ════════════════════════════════════════════════════════════════════
    // LOCATION PINNING
    // ════════════════════════════════════════════════════════════════════

    /**
     * Sets the home point to [point], updates everything that depends on it:
     * home dot, radius circle, coords label, and triggers a fresh flight fetch.
     */
    private fun pinLocation(point: GeoPoint) {
        homePoint = point
        FlightRepository.HOME_LAT = point.latitude
        FlightRepository.HOME_LON = point.longitude

        updateCoordsLabel()
        addRadiusCircle()          // redraws circle at new centre
        binding.map.invalidate()
        lifecycleScope.launch { loadFlights(); loadWeather() }
    }

    /** Tries to get the device's last-known GPS position and pins to it. */
    private fun initLocationFromGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

        if (loc != null) {
            pinLocation(GeoPoint(loc.latitude, loc.longitude))
            binding.map.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
        }
    }

    private fun updateCoordsLabel() {
        binding.tvMapCoords.text =
            "%.4f° N  %.4f° W".format(FlightRepository.HOME_LAT, abs(FlightRepository.HOME_LON))
    }

    // ── Permissions ───────────────────────────────────────────────────────

    private fun requestPermissions() {
        val missing = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty())
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 &&
            grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            // Try GPS now that permission was just granted
            initLocationFromGps()
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ANIMATIONS
    // ════════════════════════════════════════════════════════════════════

    private fun startLivePulse() {
        livePulseAnimator = ValueAnimator.ofFloat(0.35f, 1.0f).apply {
            duration     = 1200
            repeatCount  = ValueAnimator.INFINITE
            repeatMode   = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { binding.tvLiveDot.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun startDeadReckoning() {
        lifecycleScope.launch {
            while (isActive) {
                delay(1_000L)
                if (lastFetchTimeMs == 0L || flightData.isEmpty()) continue
                val dt = ((System.currentTimeMillis() - lastFetchTimeMs) / 1000.0).coerceAtMost(90.0)
                for ((icao, flight) in flightData) {
                    val headingRad = Math.toRadians(flight.heading)
                    val dLat = (flight.velocity * cos(headingRad) * dt) / 111_320.0
                    val dLon = (flight.velocity * sin(headingRad) * dt) /
                               (111_320.0 * cos(Math.toRadians(flight.latitude)))
                    deadReckonedPos[icao] = GeoPoint(flight.latitude + dLat, flight.longitude + dLon)
                }
                binding.map.invalidate()
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // MAP SETUP
    // ════════════════════════════════════════════════════════════════════

    private fun setupMap() {
        binding.map.apply {
            setTileSource(darkTiles)
            setMultiTouchControls(true)
            controller.setZoom(9.5)
            controller.setCenter(homePoint)
            isTilesScaledToDpi = true
        }
        addRadiusCircle()
        addHomeDotOverlay()
        chipOverlay = FlightChipOverlay()
        binding.map.overlays.add(chipOverlay)

        // Long-press anywhere on the map → pin that location
        binding.map.overlays.add(object : Overlay() {
            override fun onLongPress(e: MotionEvent, mapView: MapView): Boolean {
                val pt = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
                val newPoint = GeoPoint(pt.latitude, pt.longitude)
                pinLocation(newPoint)
                Toast.makeText(
                    this@MainActivity,
                    "Location pinned: %.4f° N  %.4f° W".format(
                        newPoint.latitude, abs(newPoint.longitude)),
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }
        })

        updateCoordsLabel()
    }

    private fun setupHomePulse() {
        pulseAnimator = ValueAnimator.ofFloat(16f, 64f).apply {
            duration    = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode  = ValueAnimator.RESTART
            addUpdateListener { pulseRadius = it.animatedValue as Float; binding.map.invalidate() }
            start()
        }
    }

    private fun addHomeDotOverlay() {
        val pulsePaint  = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val dotPaint    = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = colorGreen }
        val ringPaint   = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2.5f; color = colorGreen }
        val shadowPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.argb(60, 0, 0, 0) }

        // The dot draws at the current homePoint — since homePoint is a var it always
        // reflects the latest pinned location when invalidate() is called.
        binding.map.overlays.add(object : Overlay() {
            override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
                if (shadow) return
                val c  = mapView.projection.toPixels(homePoint, null)
                val cx = c.x.toFloat(); val cy = c.y.toFloat()
                val frac = (pulseRadius - 16f) / 48f
                pulsePaint.color = Color.argb(
                    ((1f - frac) * 90f).toInt().coerceIn(0, 90), 74, 222, 128)
                canvas.drawCircle(cx, cy, pulseRadius, pulsePaint)
                canvas.drawCircle(cx, cy + 2f, 11f, shadowPaint)
                canvas.drawCircle(cx, cy, 9f, dotPaint)
                canvas.drawCircle(cx, cy, 9f, ringPaint)
            }
        })
    }

    private fun addRadiusCircle() {
        radiusOverlay?.let { binding.map.overlays.remove(it) }
        val rad    = FlightRepository.RADIUS_DEG
        val fill   = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL;   color = Color.argb(18, 201, 168, 76) }
        val stroke = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; color = Color.argb(90, 201, 168, 76); strokeWidth = 1.5f }
        // Capture the centre at the time the circle is created
        val centre = GeoPoint(homePoint.latitude, homePoint.longitude)
        val ov = object : Overlay() {
            override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
                if (shadow) return
                val proj = mapView.projection
                val c = proj.toPixels(centre, null)
                val e = proj.toPixels(GeoPoint(centre.latitude + rad, centre.longitude), null)
                val r = abs(c.y - e.y).toFloat()
                canvas.drawCircle(c.x.toFloat(), c.y.toFloat(), r, fill)
                canvas.drawCircle(c.x.toFloat(), c.y.toFloat(), r, stroke)
            }
        }
        binding.map.overlays.add(ov)
        radiusOverlay = ov
        binding.map.invalidate()
    }

    // ════════════════════════════════════════════════════════════════════
    // FLIGHT CHIP OVERLAY
    // ════════════════════════════════════════════════════════════════════

    private inner class FlightChipOverlay : Overlay() {

        private var flights: List<Flight> = emptyList()
        private val hitAreas = ArrayList<Pair<RectF, Flight>>(32)

        private val bgPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.argb(192, 3, 11, 26)
        }
        private val bordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f
        }
        private val txtPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE
        }
        private val planePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val planePath  = Path()

        fun setFlights(list: List<Flight>) { flights = list }

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return
            hitAreas.clear()
            for (flight in flights) {
                val pos = deadReckonedPos[flight.icao24]
                    ?: GeoPoint(flight.latitude, flight.longitude)
                val pt = mapView.projection.toPixels(pos, null)
                drawFlight(canvas, pt.x.toFloat(), pt.y.toFloat(), flight)
            }
        }

        private fun drawFlight(canvas: Canvas, cx: Float, cy: Float, flight: Flight) {
            val selected = flight.icao24 in selectedIcaos
            val accent   = if (selected) colorPink else colorRed
            val s = 14f

            planePaint.color = accent
            canvas.save()
            canvas.rotate(flight.heading.toFloat(), cx, cy)
            buildPlanePath(cx, cy, s)
            canvas.drawPath(planePath, planePaint)
            canvas.restore()

            val label = flight.callsignClean
            val tw    = txtPaint.measureText(label)
            val pH = 12f; val pV = 6f
            val chipW = tw + pH * 2f
            val chipH = txtPaint.textSize + pV * 2f
            val left  = cx - chipW / 2f
            val top   = cy + s * 2.2f + 4f
            val rect  = RectF(left, top, left + chipW, top + chipH)

            canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
            bordPaint.color       = accent
            bordPaint.strokeWidth = if (selected) 2.5f else 1.5f
            canvas.drawRoundRect(rect, 8f, 8f, bordPaint)
            canvas.drawText(label, left + pH, top + pV + txtPaint.textSize * 0.85f, txtPaint)

            hitAreas.add(Pair(RectF(cx - s * 2.2f, cy - s * 2.2f, cx + s * 2.2f, top + chipH), flight))
        }

        private fun buildPlanePath(cx: Float, cy: Float, s: Float) {
            planePath.reset()
            planePath.moveTo(cx, cy - s * 2.0f)
            planePath.lineTo(cx + s * 0.22f, cy - s * 0.8f)
            planePath.lineTo(cx + s * 0.22f, cy + s * 1.3f)
            planePath.lineTo(cx, cy + s * 1.6f)
            planePath.lineTo(cx - s * 0.22f, cy + s * 1.3f)
            planePath.lineTo(cx - s * 0.22f, cy - s * 0.8f)
            planePath.close()
            planePath.moveTo(cx, cy - s * 0.4f)
            planePath.lineTo(cx + s * 2.0f, cy + s * 0.7f)
            planePath.lineTo(cx + s * 1.6f, cy + s * 1.0f)
            planePath.lineTo(cx + s * 0.2f, cy + s * 0.5f)
            planePath.lineTo(cx - s * 0.2f, cy + s * 0.5f)
            planePath.lineTo(cx - s * 1.6f, cy + s * 1.0f)
            planePath.lineTo(cx - s * 2.0f, cy + s * 0.7f)
            planePath.close()
            planePath.moveTo(cx, cy + s * 1.0f)
            planePath.lineTo(cx + s * 0.9f, cy + s * 1.5f)
            planePath.lineTo(cx + s * 0.2f, cy + s * 1.6f)
            planePath.lineTo(cx - s * 0.2f, cy + s * 1.6f)
            planePath.lineTo(cx - s * 0.9f, cy + s * 1.5f)
            planePath.close()
        }

        override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
            val x = e.x; val y = e.y
            for (i in hitAreas.indices.reversed()) {
                val (rect, flight) = hitAreas[i]
                if (rect.contains(x, y)) {
                    if (flight.icao24 in selectedIcaos) selectedIcaos.remove(flight.icao24)
                    else selectedIcaos.add(flight.icao24)
                    mapView.invalidate()
                    return true
                }
            }
            return false
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // RADIUS SLIDER
    // ════════════════════════════════════════════════════════════════════

    private fun setupRadiusSlider() {
        binding.seekRadius.progress = radiusToProg(FlightRepository.RADIUS_DEG)
        binding.tvRadiusValue.text  = "${FlightRepository.radiusMiles} mi"
        binding.seekRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                FlightRepository.RADIUS_DEG = progToRadius(p)
                binding.tvRadiusValue.text = "${FlightRepository.radiusMiles} mi"
                addRadiusCircle()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { lifecycleScope.launch { loadFlights() } }
        })
    }

    private fun radiusToProg(deg: Double) = ((deg - 0.1) / 1.8 * 90).toInt().coerceIn(0, 90)
    private fun progToRadius(p: Int)      = 0.1 + p / 90.0 * 1.8

    // ════════════════════════════════════════════════════════════════════
    // REFRESH LOOPS
    // ════════════════════════════════════════════════════════════════════

    private fun startAutoRefresh() {
        lifecycleScope.launch {
            while (isActive) { loadFlights(); loadWeather(); delay(60_000L) }
        }
    }

    private fun startClockTick() {
        lifecycleScope.launch {
            while (isActive) {
                val now = Date()
                binding.tvMapTime.text = timeFmt.format(now)
                binding.tvMapDay.text  = dayFmt.format(now).uppercase()
                binding.tvMapDate.text = dateFmt.format(now)
                delay(1_000L)
            }
        }
    }

    private suspend fun loadFlights() {
        showLoading(true)
        try {
            val flights = FlightRepository.getFlightsOverhead().sortedByDescending { it.altitudeFt }
            flightData.clear()
            flights.forEach { flightData[it.icao24] = it }
            deadReckonedPos.clear()
            flights.forEach { deadReckonedPos[it.icao24] = GeoPoint(it.latitude, it.longitude) }
            lastFetchTimeMs = System.currentTimeMillis()
            selectedIcaos.retainAll(flights.map { it.icao24 }.toSet())
            chipOverlay.setFlights(flights)
            binding.map.invalidate()
            binding.tvFlightCount.text = "${flights.size} flights"
        } catch (e: retrofit2.HttpException) {
            binding.tvFlightCount.text = when (e.code()) {
                429 -> "rate limited"; 401 -> "auth error"; 503 -> "API offline"
                else -> "error ${e.code()}"
            }
        } catch (e: java.net.SocketTimeoutException) { binding.tvFlightCount.text = "timeout" }
        catch (e: java.net.UnknownHostException)     { binding.tvFlightCount.text = "no internet" }
        catch (e: Exception)                          { binding.tvFlightCount.text = "error" }
        finally { showLoading(false) }
    }

    private suspend fun loadWeather() {
        binding.tvWeather.text = WeatherRepository.getWeatherString()
    }

    private fun showLoading(on: Boolean) {
        binding.loadingContainer.visibility =
            if (on && flightData.isEmpty()) View.VISIBLE else View.GONE
    }

    // ════════════════════════════════════════════════════════════════════
    // BUTTONS
    // ════════════════════════════════════════════════════════════════════

    private fun setupClickListeners() {
        binding.btnRefresh.setOnClickListener {
            it.animate().rotation(it.rotation + 360f).setDuration(500).start()
            lifecycleScope.launch { loadFlights(); loadWeather() }
        }
    }

    private fun scheduleBackgroundMonitor() {
        val req = PeriodicWorkRequestBuilder<FlightMonitorWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            FlightMonitorWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req
        )
    }

    override fun onResume()  { super.onResume(); binding.map.onResume()  }
    override fun onPause()   { super.onPause();  binding.map.onPause()   }
    override fun onDestroy() {
        super.onDestroy()
        if (::pulseAnimator.isInitialized)     pulseAnimator.cancel()
        if (::livePulseAnimator.isInitialized) livePulseAnimator.cancel()
    }
}
