package com.flightoverhead.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flightoverhead.R
import com.flightoverhead.data.Flight
import com.flightoverhead.databinding.ItemFlightBinding

class FlightAdapter(
    private val onFlightClick: (Flight) -> Unit
) : ListAdapter<Flight, FlightAdapter.FlightVH>(DIFF) {

    var selectedIcao: String? = null
        private set

    fun setSelected(icao24: String?) {
        val oldPos = currentList.indexOfFirst { it.icao24 == selectedIcao }
        val newPos = currentList.indexOfFirst { it.icao24 == icao24 }
        selectedIcao = icao24
        if (oldPos >= 0) notifyItemChanged(oldPos)
        if (newPos >= 0) notifyItemChanged(newPos)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Flight>() {
            override fun areItemsTheSame(a: Flight, b: Flight) = a.icao24 == b.icao24
            override fun areContentsTheSame(a: Flight, b: Flight) =
                a.altitudeFt == b.altitudeFt && a.speedKnots == b.speedKnots
        }
    }

    inner class FlightVH(private val b: ItemFlightBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(flight: Flight, isSelected: Boolean) {
            b.tvCallsign.text = flight.callsignClean
            b.tvCountry.text  = flight.originCountry.uppercase()
            b.tvAltitude.text = "%,d ft".format(flight.altitudeFt)
            b.tvSpeed.text    = "${flight.speedKnots} kts"
            b.tvHeading.text  = "${flight.heading.toInt()}° ${headingToCompass(flight.heading.toInt())}"

            // Selected highlight
            b.root.setBackgroundResource(
                if (isSelected) R.drawable.bg_selected_flight
                else R.drawable.bg_card_gold_border
            )

            // Status pill
            when (flight.climbDescr) {
                "Climbing" -> {
                    b.tvStatusPill.text = "▲ CLIMBING"
                    b.tvStatusPill.setBackgroundResource(R.drawable.bg_pill_climbing)
                    b.tvStatusPill.setTextColor(Color.parseColor("#4CAF7A"))
                }
                "Descending" -> {
                    b.tvStatusPill.text = "▼ DESCENDING"
                    b.tvStatusPill.setBackgroundResource(R.drawable.bg_pill_descending)
                    b.tvStatusPill.setTextColor(Color.parseColor("#F06060"))
                }
                else -> {
                    b.tvStatusPill.text = "— CRUISING"
                    b.tvStatusPill.setBackgroundResource(R.drawable.bg_pill_cruising)
                    b.tvStatusPill.setTextColor(Color.parseColor("#6EB3F0"))
                }
            }

            b.root.setOnClickListener { onFlightClick(flight) }
        }

        private fun headingToCompass(deg: Int): String {
            val dirs = arrayOf("N","NE","E","SE","S","SW","W","NW")
            return dirs[((deg + 22) / 45) % 8]
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlightVH {
        val b = ItemFlightBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FlightVH(b)
    }

    override fun onBindViewHolder(holder: FlightVH, position: Int) {
        val flight = getItem(position)
        holder.bind(flight, flight.icao24 == selectedIcao)

        // Staggered fade-in on first load only
        if (selectedIcao == null) {
            holder.itemView.alpha = 0f
            holder.itemView.animate().alpha(1f)
                .setStartDelay((position * 35L).coerceAtMost(350L))
                .setDuration(250).start()
        }
    }
}
