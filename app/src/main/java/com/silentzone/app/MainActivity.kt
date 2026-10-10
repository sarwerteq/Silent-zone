
package com.silentzone.app

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var zoneList: TextView
    private lateinit var geofencingClient: GeofencingClient

    private val apiUrl =
        "https://silent-zone.onrender.com/api/zones"

    private val locationPermissionCode = 100

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(
            this,
            GeofenceBroadcastReceiver::class.java
        )

        var flags = PendingIntent.FLAG_UPDATE_CURRENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }

        PendingIntent.getBroadcast(
            this,
            0,
            intent,
            flags
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        geofencingClient =
            LocationServices.getGeofencingClient(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 40, 28, 24)
        }

        val heading = TextView(this).apply {
            text = "Silent Zone"
            textSize = 28f
        }

        status = TextView(this).apply {
            text = "Ready"
            textSize = 16f
            setPadding(0, 20, 0, 20)
        }

        val syncButton = Button(this).apply {
            text = "Sync Zones"
            setOnClickListener { loadZones() }
        }

        val permissionButton = Button(this).apply {
            text = "Enable Background Location"
            setOnClickListener { openLocationSettings() }
        }

        val locationButton = Button(this).apply {
            text = "Check My Location"
            setOnClickListener { checkLocation() }
        }

        zoneList = TextView(this).apply {
            textSize = 16f
            setPadding(0, 20, 0, 0)
        }

        layout.addView(heading)
        layout.addView(status)
        layout.addView(syncButton)
        layout.addView(permissionButton)
        layout.addView(locationButton)
        layout.addView(zoneList)

        setContentView(layout)
        loadZones()
    }

    private fun hasForegroundLocation(): Boolean {
        return checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocation(): Boolean {
        return Build.VERSION.SDK_INT < 29 ||
            checkSelfPermission(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openLocationSettings() {
        if (!hasForegroundLocation()) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                locationPermissionCode
            )
            return
        }

        if (!hasBackgroundLocation()) {
            status.text =
                "Settings mein Location access ko Allow all the time karo"

            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )

            startActivity(intent)
        } else {
            status.text =
                "Background location already allowed. Tap Sync Zones."
        }
    }

    private fun checkLocation() {
        if (!hasForegroundLocation()) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                locationPermissionCode
            )
            return
        }

        status.text = "Checking location..."

        val client =
            LocationServices.getFusedLocationProviderClient(this)

        try {
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                status.text = if (location == null) {
                    "Location unavailable. Try again."
                } else {
                    "Location received • Accuracy %.0f m"
                        .format(location.accuracy)
                }
            }.addOnFailureListener {
                status.text = "Location failed. Check phone settings."
            }
        } catch (e: SecurityException) {
            status.text = "Please allow location permission."
        }
    }

    private fun loadZones() {
        status.text = "Syncing zones..."

        thread {
            try {
                val connection =
                    URL(apiUrl).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val response = connection.inputStream
                    .bufferedReader().use { it.readText() }

                connection.disconnect()

                val json = JSONObject(response)
                val zones = json.optJSONArray("zones")
                    ?: throw Exception("Zones missing in API response")

                val geofences = mutableListOf<Geofence>()
                val display = StringBuilder()
                var count = 0

                for (i in 0 until zones.length()) {
                    val zone = zones.getJSONObject(i)

                    if (!zone.optBoolean("is_active", true)) {
                        continue
                    }

                    val latitude = zone.optDouble("latitude", Double.NaN)
                    val longitude = zone.optDouble("longitude", Double.NaN)
                    val radius = zone.optDouble(
                        "radius_m",
                        zone.optDouble("radius_meters", 0.0)
                    )

                    if (!latitude.isFinite() ||
                        !longitude.isFinite() ||
                        radius < 50 ||
                        latitude !in -90.0..90.0 ||
                        longitude !in -180.0..180.0
                    ) {
                        continue
                    }

                    val id = zone.optString("id", "zone-$i")
                    val name = zone.optString("name", "Silent Zone")
                    val action = zone.optString("sound_action", "SILENT")

                    geofences.add(
                        Geofence.Builder()
                            .setRequestId(id)
                            .setCircularRegion(
                                latitude,
                                longitude,
                                radius.toFloat()
                            )
                            .setExpirationDuration(
                                Geofence.NEVER_EXPIRE
                            )
                            .setTransitionTypes(
                                Geofence.GEOFENCE_TRANSITION_ENTER or
                                    Geofence.GEOFENCE_TRANSITION_EXIT
                            )
                            .setNotificationResponsiveness(60000)
                            .build()
                    )

                    count++
                    display.append("\n$count. ")
                        .append(name)
                        .append("\n   Action: ")
                        .append(action)
                        .append("\n")
                }

                runOnUiThread {
                    zoneList.text = if (count == 0) {
                        "No valid active zones found."
                    } else {
                        display.toString()
                    }

                    if (count > 100) {
                        status.text =
                            "More than 100 zones. Only the first 100 can be monitored."
                    }

                    registerGeofences(geofences.take(100))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text =
                        "Sync failed: ${e.localizedMessage ?: "Try again"}"
                }
            }
        }
    }

    private fun registerGeofences(geofences: List<Geofence>) {
        if (!hasForegroundLocation()) {
            status.text = "Allow location permission, then Sync Zones."
            return
        }

        if (!hasBackgroundLocation()) {
            status.text =
                "Zones synced. Tap Enable Background Location, allow access, then Sync Zones."
            return
        }

        if (geofences.isEmpty()) {
            geofencingClient.removeGeofences(geofencePendingIntent)
                .addOnSuccessListener {
                    status.text = "No active zones to monitor."
                }
                .addOnFailureListener {
                    status.text = "No zones found. Try Sync Zones again."
                }
            return
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(
                GeofencingRequest.INITIAL_TRIGGER_ENTER
            )
            .addGeofences(geofences)
            .build()

        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnCompleteListener {
                try {
                    geofencingClient.addGeofences(
                        request,
                        geofencePendingIntent
                    ).addOnSuccessListener {
                        status.text =
                            "Monitoring ${geofences.size} zone(s) in background."
                    }.addOnFailureListener { error ->
                        status.text =
                            "Geofence failed: ${error.message ?: "Check permissions"}"
                    }
                } catch (e: SecurityException) {
                    status.text =
                        "Location permission required. Enable it and sync again."
                }
            }
    }
}
