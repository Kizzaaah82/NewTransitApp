package com.kiz.transitapp.data.weather

import android.util.Log
import com.kiz.transitapp.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WeatherRepository {
    private val weatherApi: WeatherApiService

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        weatherApi = retrofit.create(WeatherApiService::class.java)
    }

    suspend fun getCurrentWeather(cityName: String = "Windsor,CA"): WeatherData? {
        return try {
            val response = weatherApi.getCurrentWeather(
                cityName = cityName,
                apiKey = BuildConfig.WEATHER_API_KEY
            )

            if (response.isSuccessful && response.body() != null) {
                val weatherResponse = response.body()!!
                Log.d("WeatherRepository", "Raw API response: $weatherResponse")

                WeatherData(
                    temperature = weatherResponse.main.temperature.toInt(),
                    description = weatherResponse.weather.first().description.replaceFirstChar { it.uppercase() },
                    cityName = weatherResponse.cityName,
                    icon = weatherResponse.weather.first().icon,
                    humidity = weatherResponse.main.humidity,
                    feelsLike = weatherResponse.main.feelsLike.toInt()
                )
            } else {
                Log.e("WeatherRepository", "Weather API error: ${response.code()} - ${response.message()}")
                Log.e("WeatherRepository", "Response body: ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("WeatherRepository", "Failed to fetch weather data", e)
            null
        }
    }
}
