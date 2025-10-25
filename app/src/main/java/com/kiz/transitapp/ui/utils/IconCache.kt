package com.kiz.transitapp.ui.utils

import android.content.Context
import android.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.kiz.transitapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class IconCache private constructor(private val context: Context) {

    // Thread-safe cache for bus icons keyed by route ID + color
    private val busIconCache = ConcurrentHashMap<String, BitmapDescriptor>()

    // Cache for preloaded bitmaps (can be loaded before Maps initialization)
    private val preloadedBusIcons = ConcurrentHashMap<String, Bitmap>()

    // Preloaded bitmaps for efficiency
    private var busFiller: Bitmap? = null
    private var busOverlay: Bitmap? = null

    // Flag to track if Google Maps is ready
    private var isMapReady = false

    companion object {
        @Volatile
        private var INSTANCE: IconCache? = null

        fun initialize(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = IconCache(context.applicationContext)
                    }
                }
            }
        }

        fun getInstance(): IconCache {
            return INSTANCE ?: throw IllegalStateException("IconCache must be initialized first")
        }
    }

    // Call this when Google Maps is ready
    fun onMapReady() {
        android.util.Log.d("IconCache", "Google Maps is ready, enabling BitmapDescriptor creation")
        isMapReady = true
    }

    suspend fun preloadBitmaps() = withContext(Dispatchers.IO) {
        try {
            // Preload bus icon resources (these don't require Google Maps)
            busFiller = BitmapFactory.decodeResource(context.resources, R.drawable.bus_marker_filler)
            busOverlay = BitmapFactory.decodeResource(context.resources, R.drawable.bus_marker_overlay)

            android.util.Log.d("IconCache", "Bitmaps preloaded successfully: " +
                "busFiller=${busFiller != null}, busOverlay=${busOverlay != null}")
        } catch (e: Exception) {
            android.util.Log.e("IconCache", "Error preloading bitmaps", e)
        }
    }

    suspend fun preloadBusIconsForRoutes(routeColors: Map<String, Color>) = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        routeColors.forEach { (routeId, color) ->
            try {
                val cacheKey = "${routeId}_${color.toArgb()}"
                if (!preloadedBusIcons.containsKey(cacheKey)) {
                    // Create bitmap but don't convert to BitmapDescriptor yet
                    createBusIconBitmap(routeId, color)?.let { bitmap ->
                        preloadedBusIcons[cacheKey] = bitmap
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("IconCache", "Error preloading bitmap for route $routeId", e)
            }
        }

        val loadTime = System.currentTimeMillis() - startTime
        android.util.Log.d("IconCache", "Preloaded ${preloadedBusIcons.size} bus icon bitmaps in ${loadTime}ms")
    }

    fun getBusIcon(routeId: String, color: Color): BitmapDescriptor? {
        if (!isMapReady) {
            android.util.Log.w("IconCache", "Map not ready, cannot create BitmapDescriptor for route $routeId")
            return null
        }

        val cacheKey = "${routeId}_${color.toArgb()}"

        return busIconCache[cacheKey] ?: run {
            // Try to use preloaded bitmap first
            preloadedBusIcons[cacheKey]?.let { preloadedBitmap ->
                try {
                    val descriptor = BitmapDescriptorFactory.fromBitmap(preloadedBitmap)
                    busIconCache[cacheKey] = descriptor
                    android.util.Log.d("IconCache", "Converted preloaded bitmap to descriptor for route $routeId")
                    return@run descriptor
                } catch (e: Exception) {
                    android.util.Log.e("IconCache", "Error converting preloaded bitmap to descriptor", e)
                    return@run null
                }
            }

            // Fallback: create on demand (should be rare after preloading)
            createBusIcon(routeId, color)?.also { icon ->
                busIconCache[cacheKey] = icon
                android.util.Log.d("IconCache", "Cache miss for route $routeId, created new icon")
            }
        }
    }

    private fun createBusIconBitmap(routeId: String, color: Color, size: Int = 75): Bitmap? {
        return try {
            val filler = busFiller ?: return null
            val overlay = busOverlay ?: return null

            // Create scaled versions
            val scaledFiller = Bitmap.createScaledBitmap(filler, size, size, true)
            val scaledOverlay = Bitmap.createScaledBitmap(overlay, size, size, true)

            // Create tinted filler
            val tintedFiller = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(tintedFiller)
            val paint = Paint().apply {
                colorFilter = PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(scaledFiller, 0f, 0f, paint)

            // Combine tinted filler with overlay
            val finalBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val finalCanvas = Canvas(finalBitmap)
            finalCanvas.drawBitmap(tintedFiller, 0f, 0f, null)
            finalCanvas.drawBitmap(scaledOverlay, 0f, 0f, null)

            // Clean up intermediate bitmaps if they were scaled
            if (scaledFiller != filler) scaledFiller.recycle()
            if (scaledOverlay != overlay) scaledOverlay.recycle()
            tintedFiller.recycle()

            finalBitmap
        } catch (e: Exception) {
            android.util.Log.e("IconCache", "Error creating bus icon bitmap for route $routeId", e)
            null
        }
    }

    private fun createBusIcon(routeId: String, color: Color, size: Int = 75): BitmapDescriptor? {
        return try {
            val bitmap = createBusIconBitmap(routeId, color, size)
            bitmap?.let { BitmapDescriptorFactory.fromBitmap(it) }
        } catch (e: Exception) {
            android.util.Log.e("IconCache", "Error creating bus icon for route $routeId", e)
            null
        }
    }

    fun clearCache() {
        busIconCache.clear()
        preloadedBusIcons.clear()
        isMapReady = false
    }

    fun getCacheStats(): String {
        return "Bus icons cached: ${busIconCache.size}, Preloaded bitmaps: ${preloadedBusIcons.size}, Map ready: $isMapReady"
    }
}
