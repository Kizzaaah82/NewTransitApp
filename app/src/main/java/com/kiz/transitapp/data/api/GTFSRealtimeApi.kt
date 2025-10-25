package com.kiz.transitapp.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

interface GTFSRealtimeApi {
    @GET("gtfrealtime_VehiclePositions.bin")
    suspend fun getVehiclePositions(@retrofit2.http.Query("t") timestamp: Long = System.currentTimeMillis()): Response<ResponseBody>

    @GET("gtfrealtime_TripUpdates.bin")
    suspend fun getTripUpdates(@retrofit2.http.Query("t") timestamp: Long = System.currentTimeMillis()): Response<ResponseBody>

    @GET("gtfrealtime_ServiceAlerts.bin")
    suspend fun getServiceAlerts(@retrofit2.http.Query("t") timestamp: Long = System.currentTimeMillis()): Response<ResponseBody>
}

object GTFSRealtimeClient {
    private const val BASE_URL = "https://windsor.mapstrat.com/current/"

    val instance: GTFSRealtimeApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .build()
        retrofit.create(GTFSRealtimeApi::class.java)
    }
}
