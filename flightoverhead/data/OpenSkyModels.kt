package com.flightoverhead.data

import com.google.gson.annotations.SerializedName

data class OpenSkyResponse(
    val time: Long,
    val states: List<List<Any?>>?
)

data class Flight(
    val icao24: String,
    val callsign: String,
    val originCountry: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,       // meters
    val velocity: Double,       // m/s
    val heading: Double,        // degrees
    val onGround: Boolean,
    val verticalRate: Double,   // m/s, positive = climbing
    val squawk: String?,
    val spiStatus: Boolean,
    val positionSource: Int
) {
    val altitudeFt: Int get() = (altitude * 3.28084).toInt()
    val speedKnots: Int get() = (velocity * 1.94384).toInt()
    val callsignClean: String get() = callsign.trim().ifEmpty { icao24 }

    val climbDescr: String get() = when {
        verticalRate > 1.0  -> "Climbing"
        verticalRate < -1.0 -> "Descending"
        else                -> "Cruising"
    }
}

/** Parse a raw OpenSky state vector (list of Any?) into a Flight */
fun List<Any?>.toFlight(): Flight? {
    return try {
        Flight(
            icao24        = (this[0] as? String) ?: return null,
            callsign      = (this[1] as? String) ?: "",
            originCountry = (this[2] as? String) ?: "",
            latitude      = (this[6] as? Double) ?: return null,
            longitude     = (this[5] as? Double) ?: return null,
            altitude      = (this[7] as? Double) ?: (this[13] as? Double) ?: 0.0,
            velocity      = (this[9] as? Double) ?: 0.0,
            heading       = (this[10] as? Double) ?: 0.0,
            onGround      = (this[8] as? Boolean) ?: false,
            verticalRate  = (this[11] as? Double) ?: 0.0,
            squawk        = this[14] as? String,
            spiStatus     = (this[15] as? Boolean) ?: false,
            positionSource = ((this[16] as? Double)?.toInt()) ?: 0
        )
    } catch (e: Exception) { null }
}
