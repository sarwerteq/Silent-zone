package com.silentzone.app

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
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
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var status: TextView
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
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun text(
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.rgb(30, 35, 45))
            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(Color.WHITE)

            elevation = dp(4).toFloat()

            val params = LinearLayout.LayoutParams(
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

        val scroll = ScrollView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(20),
                dp(28),
                dp(20),
                dp(30)
            )
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = text(
            "Silent Zone",
            30f,
            true
        )

        val subtitle = text(
            "Smart location-based sound control",
            15f
        )

        header.addView(title)
        header.addView(subtitle)

        root.addView(header)

        // Main status card
        val statusCard = card()

        val statusTitle = text(
            "ZONE PROTECTION",
            13f,
            true
        )

        status = text(
            "Checking connection...",
            22f,
            true
        )

        val statusDescription = text(
            "Automatic sound control based on your active zones.",
            14f
        )

        statusDescription.setPadding(
            0,
            dp(6),
            0,
            0
        )

        statusCard.addView(statusTitle)
        statusCard.addView(status)
        statusCard.addView(statusDescription)

        root.addView(statusCard)

        // Connection card
        val connectionCard = card()

        connectionCard.addView(
            text(
                "CONNECTION",
                13f,
                true
            )
        )

        connectionInfo = text(
            "Connecting to Silent Zone server...",
            16f,
            false
        )

        connectionInfo.setPadding(
            0,
            dp(8),
            0,
            0
        )

        connectionCard.addView(connectionInfo)

        root.addView(connectionCard)

        // Location card
        val locationCard = card()

        locationCard.addView(
            text(
                "CURRENT LOCATION",
                13f,
                true
            )
        )

        locationInfo = text(
            "Location not checked yet",
            16f
        )

        locationInfo.setPadding(
            0,
            dp(8),
            0,
            0
        )

        locationCard.addView(locationInfo)

        val locationButton = Button(this).apply {
            text = "Refresh Location"
            setOnClickListener {
                checkLocation()
            }
        }

        locationCard.addView(locationButton)

        root.addView(locationCard)

        // Actions
        val actionsCard = card()

        actionsCard.addView(
            text(
                "CONTROLS",
                13f,
                true
            )
        )

        val syncButton = Button(this).apply {
            text = "Sync Zones"
            setOnClickListener {
                loadZones()
            }
        }

        actionsCard.addView(syncButton)

        val permissionButton = Button(this).apply {
            text = "Background Location Settings"
            setOnClickListener {
                openLocationSettings()
            }
        }

        actionsCard.addView(permissionButton)

        root.addView(actionsCard)

        // Zones section
        val zonesCard = card()

        zonesCard.addView(
            text(
                "ACTIVE ZONES",
                13f,
                true
            )
        )

        zoneList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                0,
                dp(10),
                0,
                0
            )
        }

        zonesCard.addView(zoneList)

        root.addView(zonesCard)

        val footer = text(
            "Silent Zone • Community safety through quieter spaces",
            12f
        )

        footer.gravity = Gravity.CENTER
        footer.setPadding(
            0,
            dp(18),
            0,
            0
        )

        root.addView(footer)

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

            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )

            startActivity(intent)

        } else {

            status.text =
                "Background Location Enabled"
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

        status.text =
            "Getting precise location..."

        val client =
            LocationServices.getFusedLocationProviderClient(this)

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

                    return@addOnSuccessListener
                }

                val accuracy =
                    location.accuracy

                locationInfo.text =
                    "Latitude: %.6f\nLongitude: %.6f\nAccuracy: %.0f metres"
                        .format(
                            location.latitude,
                            location.longitude,
                            accuracy
                        )

                status.text =
                    "Location Connected"

            }.addOnFailureListener {

                status.text =
                    "Location Failed"

                locationInfo.text =
                    "Please check GPS and location permission."
            }

        } catch (e: SecurityException) {

            status.text =
                "Location Permission Required"
        }
    }

    private fun loadZones() {

        status.text =
            "Syncing zones..."

        connectionInfo.text =
            "Connecting to Silent Zone server..."

        thread {

            try {

                val connection =
                    URL(apiUrl).openConnection()
                            as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val response =
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                connection.disconnect()

                val json =
                    JSONObject(response)

                val zones =
                    json.optJSONArray("zones")
                        ?: throw Exception(
                            "Zones missing in API response"
                        )

                val geofences =
                    mutableListOf<Geofence>()

                val zoneData =
                    mutableListOf<JSONObject>()

                var count = 0

                for (i in 0 until zones.length()) {

                    val zone =
                        zones.getJSONObject(i)

                    if (!zone.optBoolean(
                            "is_active",
                            true
                        )
                    ) {
                        continue
                    }

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

                    val radius =
                        zone.optDouble(
                            "radius_m",
                            zone.optDouble(
                                "radius_meters",
                                0.0
                            )
                        )

                    if (
                        !latitude.isFinite() ||
                        !longitude.isFinite() ||
                        radius < 10 ||
                        radius > 500 ||
                        latitude !in -90.0..90.0 ||
                        longitude !in -180.0..180.0
                    ) {
                        continue
                    }

                    val id =
                        zone.optString(
                            "id",
                            "zone-$i"
                        )

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
                            .setNotificationResponsiveness(
                                60000
                            )
                            .build()
                    )

                    zoneData.add(zone)
                    count++
                }

                runOnUiThread {

                    connectionInfo.text =
                        "● Connected\n${
                            count
                        } active zone(s)"

                    zoneList.removeAllViews()

                    if (count == 0) {

                        zoneList.addView(
                            text(
                                "No active zones available.",
                                15f
                            )
                        )

                    } else {

                        zoneData.forEach { zone ->

                            addZoneCard(zone)
                        }
                    }

                    registerGeofences(
                        geofences.take(100)
                    )
                }

            } catch (e: Exception) {

                runOnUiThread {

                    connectionInfo.text =
                        "● Connection failed"

                    status.text =
                        "Sync Failed"

                    zoneList.removeAllViews()

                    zoneList.addView(
                        text(
                            e.localizedMessage
                                ?: "Unable to load zones.",
                            14f
                        )
                    )
                }
            }
        }
    }

    private fun addZoneCard(zone: JSONObject) {

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
            )
            setBackgroundColor(
                Color.rgb(248, 249, 252)
            )
        }

        val name =
            zone.optString(
                "name",
                "Silent Zone"
            )

        val category =
            zone.optString(
                "category",
                "ZONE"
            )

        val action =
            zone.optString(
                "sound_action",
                "SILENT"
            )

        val radius =
            zone.optDouble(
                "radius_m",
                zone.optDouble(
                    "radius_meters",
                    0.0
                )
            )

        box.addView(
            text(
                name,
                20f,
                true
            )
        )

        box.addView(
            text(
                category.uppercase(),
                12f,
                true
            )
        )

        val details =
            text(
                "Radius: %.0f metres\nAction: %s\nStatus: Active"
                    .format(
                        radius,
                        action.uppercase()
                    ),
                14f
            )

        details.setPadding(
            0,
            dp(8),
            0,
            0
        )

        box.addView(details)

        zoneList.addView(box)

        val spacer =
            View(this)

        spacer.layoutParams =
            LinearLayout.LayoutParams(
                1,
                dp(8)
            )

        zoneList.addView(spacer)
    }

    private fun registerGeofences(
        geofences: List<Geofence>
    ) {

        if (!hasForegroundLocation()) {

            status.text =
                "Location Permission Required"

            return
        }

        if (!hasBackgroundLocation()) {

            status.text =
                "Enable Background Location"

            return
        }

        if (geofences.isEmpty()) {

            geofencingClient
                .removeGeofences(
                    geofencePendingIntent
                )

            status.text =
                "No Active Zones"

            return
        }

        val request =
            GeofencingRequest.Builder()
                .setInitialTrigger(
                    GeofencingRequest.INITIAL_TRIGGER_ENTER
                )
                .addGeofences(geofences)
                .build()

        geofencingClient
            .removeGeofences(
                geofencePendingIntent
            )
            .addOnCompleteListener {

                try {

                    geofencingClient
                        .addGeofences(
                            request,
                            geofencePendingIntent
                        )
                        .addOnSuccessListener {

                            status.text =
                                "Protection Active"

                        }
                        .addOnFailureListener { error ->

                            status.text =
                                "Geofence Setup Failed: ${
                                    error.message
                                        ?: "Unknown error"
                                }"
                        }

                } catch (e: SecurityException) {

                    status.text =
                        "Location Permission Required"
                }
            }
    }
}
