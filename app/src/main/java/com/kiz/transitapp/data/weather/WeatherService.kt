package com.kiz.transitapp.data.weather

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// Data models for OpenWeatherMap API
data class WeatherResponse(
    @SerializedName("main")
    val main: WeatherMain,
    @SerializedName("weather")
    val weather: List<WeatherCondition>,
    @SerializedName("name")
    val cityName: String,
    @SerializedName("dt")
    val timestamp: Long
)

data class WeatherMain(
    @SerializedName("temp")
    val temperature: Double,
    @SerializedName("feels_like")
    val feelsLike: Double,
    @SerializedName("humidity")
    val humidity: Int
)

data class WeatherCondition(
    @SerializedName("main")
    val main: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("icon")
    val icon: String
)

// Weather API service
interface WeatherApiService {
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("q") cityName: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): Response<WeatherResponse>
}

// UI data class
data class WeatherData(
    val temperature: Int,
    val description: String,
    val cityName: String,
    val icon: String,
    val humidity: Int,
    val feelsLike: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)
