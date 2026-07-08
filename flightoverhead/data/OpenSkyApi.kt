package com.flightoverhead.data

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenSkyApi {
    @GET("states/all")
    suspend fun getStates(
        @Query("lamin") latMin: Double,
        @Query("lomin") lonMin: Double,
        @Query("lamax") latMax: Double,
        @Query("lomax") lonMax: Double
    ): OpenSkyResponse
}
