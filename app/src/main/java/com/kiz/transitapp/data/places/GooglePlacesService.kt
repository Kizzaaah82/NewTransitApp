package com.kiz.transitapp.data.places

import android.content.Context
import android.util.Log
import com.kiz.transitapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PlacePrediction(
    val description: String,
    val placeId: String,
    val types: List<String>
)

class GooglePlacesService(private val context: Context) {

    // Use the API key from BuildConfig (loaded from your local.properties)
    private val apiKey = BuildConfig.PLACES_API_KEY

    suspend fun getAddressPredictions(query: String): List<PlacePrediction> {
        return withContext(Dispatchers.IO) {
            if (query.length < 2) return@withContext emptyList()

            // Check if API key is available
            if (apiKey.isEmpty()) {
                Log.w("GooglePlaces", "Places API key not configured, using fallback")
                return@withContext getFallbackSuggestions(query)
            }

            Log.d("GooglePlaces", "Searching for: '$query' with API key: ${apiKey.take(10)}...")

            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")

                // More flexible Windsor, Ontario area search
                val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?" +
                        "input=$encodedQuery" +
                        "&location=42.3149,-83.0364" + // Windsor coordinates
                        "&radius=100000" + // Increased to 100km radius
                        "&components=country:ca|administrative_area:ON" + // Canada, Ontario
                        "&types=address|establishment" + // Include both addresses and places
                        "&key=$apiKey"

                Log.d("GooglePlaces", "Calling URL: ${url.replace(apiKey, "***")}")

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                Log.d("GooglePlaces", "Response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    Log.d("GooglePlaces", "Response received, length: ${response.length}")
                    val results = parseAddressPredictions(response)
                    Log.d("GooglePlaces", "Parsed ${results.size} predictions")

                    return@withContext results
                } else {
                    // Try to read error response
                    val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                    val errorResponse = errorReader.readText()
                    errorReader.close()
                    Log.e("GooglePlaces", "HTTP Error: $responseCode, Response: $errorResponse")
                    return@withContext getFallbackSuggestions(query)
                }
            } catch (e: Exception) {
                Log.e("GooglePlaces", "Error fetching predictions: ${e.message}", e)
                return@withContext getFallbackSuggestions(query)
            }
        }
    }

    private fun parseAddressPredictions(jsonResponse: String): List<PlacePrediction> {
        try {
            Log.d("GooglePlaces", "Raw JSON Response: $jsonResponse")

            val jsonObject = JSONObject(jsonResponse)

            // Check if there's an error in the response
            if (jsonObject.has("error_message")) {
                val errorMessage = jsonObject.getString("error_message")
                Log.e("GooglePlaces", "API Error: $errorMessage")
                return emptyList()
            }

            val status = jsonObject.optString("status", "UNKNOWN")
            Log.d("GooglePlaces", "API Status: $status")

            if (status != "OK" && status != "ZERO_RESULTS") {
                Log.e("GooglePlaces", "API returned status: $status")
                return emptyList()
            }

            val predictions = jsonObject.getJSONArray("predictions")
            val results = mutableListOf<PlacePrediction>()

            for (i in 0 until predictions.length()) {
                val prediction = predictions.getJSONObject(i)
                val description = prediction.getString("description")
                val placeId = prediction.getString("place_id")

                val typesArray = prediction.getJSONArray("types")
                val types = mutableListOf<String>()
                for (j in 0 until typesArray.length()) {
                    types.add(typesArray.getString(j))
                }

                results.add(PlacePrediction(description, placeId, types))
            }

            Log.d("GooglePlaces", "Successfully parsed ${results.size} predictions")
            return results.take(8) // Limit to 8 suggestions
        } catch (e: Exception) {
            Log.e("GooglePlaces", "Error parsing predictions: ${e.message}", e)
            return emptyList()
        }
    }

    // Fallback suggestions if Google Places API fails
    private fun getFallbackSuggestions(query: String): List<PlacePrediction> {
        val fallbackAddresses = listOf(
            "University of Windsor, Windsor, ON, Canada",
            "St. Clair College, Windsor, ON, Canada",
            "Devonshire Mall, Windsor, ON, Canada",
            "Windsor Regional Hospital, Windsor, ON, Canada",
            "Downtown Windsor, Windsor, ON, Canada",
            "Tecumseh Road East, Windsor, ON, Canada",
            "Howard Avenue, Windsor, ON, Canada",
            "Walker Road, Windsor, ON, Canada"
        )

        return fallbackAddresses
            .filter { it.contains(query, ignoreCase = true) }
            .map { PlacePrediction(it, "", listOf("establishment")) }
            .take(5)
    }
}
