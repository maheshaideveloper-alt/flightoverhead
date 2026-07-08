package com.flightoverhead.ui

import android.graphics.Color
import android.widget.TextView
import com.flightoverhead.R
import com.flightoverhead.data.Flight
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.infowindow.InfoWindow

class FlightInfoWindow(
    private val flight: Flight,
    mapView: MapView
) : InfoWindow(R.layout.popup_flight, mapView) {

    override fun onOpen(item: Any?) {
        val v = mView

        v.findViewById<TextView>(R.id.popCallsign).text = flight.callsignClean
        v.findViewById<TextView>(R.id.popCountry).text  = flight.originCountry.uppercase()
        v.findViewById<TextView>(R.id.popAltitude).text = "%,d ft".format(flight.altitudeFt)
        v.findViewById<TextView>(R.id.popSpeed).text    = "${flight.speedKnots} kts"
        v.findViewById<TextView>(R.id.popHeading).text  = "${flight.heading.toInt()}°"

        val vertFpm = (flight.verticalRate * 196.85).toInt()
        v.findViewById<TextView>(R.id.popVertRate).text = when {
            flight.verticalRate > 1.0  -> "▲ Climbing  +%,d fpm".format(vertFpm)
            flight.verticalRate < -1.0 -> "▼ Descending  %,d fpm".format(vertFpm)
            else                       -> "— Cruising"
        }

        // Status pill
        val pill = v.findViewById<TextView>(R.id.popStatus)
        when (flight.climbDescr) {
            "Climbing"   -> { pill.text = "▲ CLIMBING";   pill.setBackgroundResource(R.drawable.bg_pill_climbing);   pill.setTextColor(Color.parseColor("#4CAF7A")) }
            "Descending" -> { pill.text = "▼ DESCENDING"; pill.setBackgroundResource(R.drawable.bg_pill_descending); pill.setTextColor(Color.parseColor("#F06060")) }
            else         -> { pill.text = "— CRUISING";   pill.setBackgroundResource(R.drawable.bg_pill_cruising);   pill.setTextColor(Color.parseColor("#6EB3F0")) }
        }

        // Close button
        v.findViewById<TextView>(R.id.popClose).setOnClickListener { close() }
    }

    override fun onClose() {}
}
