package com.kiz.transitapp.ui.screens

import com.google.android.gms.maps.model.LatLng

data class StopOnRoute(
    val id: String,
    val name: String,
    val location: LatLng,
    val routeId: String
)

