package com.silentzone.app

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.location.CancellationTokenSource
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceBroadcastReceiver
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var zoneProtection: TextView
    private lateinit var zoneList: LinearLayout
    private lateinit var locationInfo: TextView
    private lateinit var connectionInfo: TextView
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

        buildPremiumUi()
        loadZones()
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    private fun text(
        value: String,
        size: Float,
        bold: Boolean = false,
        color: Int = Color.rgb(30, 35, 45)
    ): TextView {

        return TextView(this).apply {

            text = value
            textSize = size
            setTextColor(color)

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Float = 18f
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius =
                dp(radius.toInt()).toFloat()
        }
    }

    private fun card(): LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(18)
            )

            background =
                roundedBackground(
                    Color.WHITE,
                    18f
                )

            elevation =
                dp(3).toFloat()

            val params =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

            params.setMargins(
                0,
                dp(8),
                0,
                dp(8)
            )

            layoutParams = params
        }
    }

    private fun buildPremiumUi() {

        val scroll =
            ScrollView(this)

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(24),
                    dp(18),
                    dp(30)
                )

                setBackgroundColor(
                    Color.rgb(241, 244, 249)
                )
            }

        // --------------------------------
        // HEADER
        // --------------------------------

        val header =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(22),
                    dp(20),
                    dp(22)
                )

                background =
                    roundedBackground(
                        Color.rgb(25, 45, 75),
                        22f
                    )
            }

        header.addView(
            text(
                "Silent Zone",
                30f,
                true,
                Color.WHITE
            )
        )

        header.addView(
            text(
                "Smart location-based sound control",
                14f,
                false,
                Color.rgb(215, 225, 240)
            )
        )

        root.addView(header)

        // --------------------------------
        // ZONE PROTECTION
        // --------------------------------

        val protectionCard =
            card()

        protectionCard.addView(
            text(
                "ZONE PROTECTION",
                12f,
                true,
                Color.rgb(90, 100, 115)
            )
        )

        status =
            text(
                "Checking protection...",
                22f,
                true
            )

        status.setPadding(
            0,
            dp(8),
            0,
            0
        )

        protectionCard.addView(status)

        zoneProtection =
            text(
                "Checking your location against active zones...",
                15f
            )

        zoneProtection.setPadding(
            0,
            dp(8),
            0,
            0
        )

        protectionCard.addView(
            zoneProtection
        )

        root.addView(
            protectionCard
        )

        // --------------------------------
        // CONNECTION
        // --------------------------------

        val connectionCard =
            card()

        connectionCard.addView(
            text(
                "CONNECTION",
                12f,
                true,
                Color.rgb(90, 100, 115)
            )
        )

        connectionInfo =
            text(
                "Connecting to Silent Zone server...",
                15f
            )

        connectionInfo.setPadding(
            0,
            dp(8),
            0,
            0
        )

        connectionCard.addView(
            connectionInfo
        )

        root.addView(
            connectionCard
        )

        // --------------------------------
        // CURRENT LOCATION
        // --------------------------------

        val locationCard =
            card()

        locationCard.addView(
            text(
                "CURRENT LOCATION",
                12f,
                true,
                Color.rgb(90, 100, 115)
            )
        )

        locationInfo =
            text(
                "Location not checked yet",
                15f
            )

        locationInfo.setPadding(
            0,
            dp(8),
            0,
            0
        )

        locationCard.addView(
            locationInfo
        )

        val locationButton =
            Button(this).apply {

                text =
                    "Refresh Location"

                setOnClickListener {
                    checkLocation()
                }
            }

        locationCard.addView(
            locationButton
        )

        root.addView(
            locationCard
        )

        // --------------------------------
        // CONTROLS
        // --------------------------------

        val actionsCard =
            card()

        actionsCard.addView(
            text(
                "CONTROLS",
                12f,
                true,
                Color.rgb(90, 100, 115)
            )
        )

        val syncButton =
            Button(this).apply {

                text =
                    "Sync Zones"

                setOnClickListener {
                    loadZones()
                }
            }

        actionsCard.addView(
            syncButton
        )

        val permissionButton =
            Button(this).apply {

                text =
                    "Background Location Settings"

                setOnClickListener {
                    openLocationSettings()
                }
            }

        actionsCard.addView(
            permissionButton
        )

        root.addView(
            actionsCard
        )

        // --------------------------------
        // ACTIVE ZONES
        // --------------------------------

        val zonesCard =
            card()

        zonesCard.addView(
            text(
                "ACTIVE ZONES",
                12f,
                true,
                Color.rgb(90, 100, 115)
            )
        )

        zoneList =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    0,
                    dp(10),
                    0,
                    0
                )
            }

        zonesCard.addView(
            zoneList
        )

        root.addView(
            zonesCard
        )

        // --------------------------------
        // FOOTER
        // --------------------------------

        val footer =
            text(
                "Silent Zone • Quieter spaces through smart technology",
                12f,
                false,
                Color.rgb(110, 120, 135)
            )

        footer.gravity =
            Gravity.CENTER

        footer.setPadding(
            0,
            dp(18),
            0,
            0
        )

        root.addView(
            footer
        )

        scroll.addView(root)

        setContentView(scroll)
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
                "Background Location Required"

            val intent =
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse(
                        "package:$packageName"
                    )
                )

            startActivity(intent)

        } else {

            status.text =
                "Background Location Enabled"
        }
    }

    // --------------------------------
    // CURRENT PHONE MODE
    // --------------------------------

    private fun getCurrentPhoneMode(): String {

        val audio =
            getSystemService(
                Context.AUDIO_SERVICE
            ) as AudioManager

        return when (
            audio.ringerMode
        ) {

            AudioManager.RINGER_MODE_SILENT ->
                "SILENT"

            AudioManager.RINGER_MODE_VIBRATE ->
                "VIBRATE"

            else ->
                "NORMAL"
        }
    }

    // --------------------------------
    // LOCATION
    // --------------------------------

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

        status.text =
            "Getting precise location..."

        val client =
            LocationServices
                .getFusedLocationProviderClient(this)

        try {

            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->

                if (location == null) {

                    status.text =
                        "Location unavailable"

                    locationInfo.text =
                        "GPS location could not be determined."

                    zoneProtection.text =
                        "Unable to determine zone status."

                    return@addOnSuccessListener
                }

                val accuracy =
                    location.accuracy

                locationInfo.text =
                    "Latitude: %.6f\n" +
                    "Longitude: %.6f\n" +
                    "Accuracy: %.0f metres\n" +
                    "Phone mode: %s"
                        .format(
                            Locale.US,
                            location.latitude,
                            location.longitude,
                            accuracy,
                            getCurrentPhoneMode()
                        )

                status.text =
                    "Location Connected"

                updateZoneProtection(
                    location
                )

            }.addOnFailureListener {

                status.text =
                    "Location Failed"

                locationInfo.text =
                    "Please check GPS and location permission."

                zoneProtection.text =
                    "Unable to determine zone status."
            }

        } catch (e: SecurityException) {

            status.text =
                "Location Permission Required"
        }
    }

    // --------------------------------
    // INSIDE / OUTSIDE CALCULATION
    // --------------------------------

    private fun updateZoneProtection(
        location: Location
    ) {

        if (!::zoneList.isInitialized) {
            return
        }

        val zones =
            getCurrentZoneData()

        if (zones.isEmpty()) {

            zoneProtection.text =
                "No active Silent Zone available."

            status.text =
                "No Active Zones"

            return
        }

        var nearestZone: JSONObject? =
            null

        var nearestDistance =
            Double.MAX_VALUE

        for (zone in zones) {

            val latitude =
                zone.optDouble(
                    "latitude",
                    Double.NaN
                )

            val longitude =
                zone.optDouble(
                    "longitude",
                    Double.NaN
                )

            if (
                !latitude.isFinite() ||
                !longitude.isFinite()
            ) {
                continue
            }

            val result =
                FloatArray(1)

            Location.distanceBetween(
                location.latitude,
                location.longitude,
                latitude,
                longitude,
                result
            )

            val distance =
                result[0].toDouble()

            if (
                distance < nearestDistance
            ) {

                nearestDistance =
                    distance

                nearestZone =
                    zone
            }
        }

        val zone =
            nearestZone

        if (zone == null) {

            zoneProtection.text =
                "Unable to calculate zone distance."

            return
        }

        val radius =
            zone.optDouble(
                "radius_m",
                zone.optDouble(
                    "radius_meters",
                    0.0
                )
            )

        val name =
            zone.optString(
                "name",
                "Silent Zone"
            )

        val action =
            zone.optString(
                "sound_action",
                "SILENT"
            ).uppercase()

        val inside =
            nearestDistance <= radius

        val distanceText =
            if (nearestDistance < 1000) {
                "%.0f m".format(
                    Locale.US,
                    nearestDistance
                )
            } else {
                "%.2f km".format(
                    Locale.US,
                    nearestDistance / 1000.0
                )
            }

        val currentMode =
            getCurrentPhoneMode()

        if (inside) {

            status.text =
                "INSIDE SILENT ZONE"

            status.setTextColor(
                Color.rgb(20, 130, 75)
            )

            zoneProtection.text =
                "● $name\n\n" +
                "Distance: $distanceText / " +
                "%.0f m radius\n".format(
                    Locale.US,
                    radius
                ) +
                "Required mode: $action\n" +
                "Current phone mode: $currentMode"

        } else {

            status.text =
                "OUTSIDE SILENT ZONE"

            status.setTextColor(
                Color.rgb(70, 80, 95)
            )

            zoneProtection.text =
                "○ Nearest zone: $name\n\n" +
                "Distance: $distanceText / " +
                "%.0f m radius\n".format(
                    Locale.US,
                    radius
                ) +
                "Zone action: $action\n" +
                "Current phone mode: $currentMode"
        }
    }

    // --------------------------------
    // STORE / READ ZONE DATA
    // --------------------------------

    private val currentZones =
        mutableListOf<JSONObject>()

    private fun getCurrentZoneData():
        List<JSONObject> {

        return currentZones.toList()
    }

    // --------------------------------
    // LOAD ZONES
    // --------------------------------

    private fun loadZones() {

        status.text =
            "Syncing zones..."

        connectionInfo.text =
            "Connecting to Silent Zone server..."

        thread {

            try {

                val connection =
                    URL(apiUrl)
                        .openConnection()
                            as HttpURLConnection

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    15000

                val response =
                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                connection.disconnect()

                val json =
                    JSONObject(response)

                val zones =
                    json.optJSONArray(
  
