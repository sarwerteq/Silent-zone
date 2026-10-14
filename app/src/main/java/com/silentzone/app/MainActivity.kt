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
import android.location.Location
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

import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource

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

        val intent = Intent().apply {
              setClassName(
               this@MainActivity,
               "com.silentzone.app.GeofenceBroadcastReceiver"
             )
        }

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
    if (!LocationPermissionManager.hasForegroundLocation(this)) {

    LocationPermissionManager.requestForegroundLocation(this)

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


private fun openNotificationPolicySettings() {
    try {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
        )
        startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            this,
            "Notification Policy settings unavailable",
            android.widget.Toast.LENGTH_LONG
        ).show()
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

  val backgroundPermissionButton =
    Button(this).apply {

        text =
            "Allow Background Location"

        setOnClickListener {

            LocationPermissionManager
                .openBackgroundLocationSettings(
                    this@MainActivity
                )
        }
    }

locationCard.addView(
    backgroundPermissionButton
)
val pauseSilentButton =
    Button(this).apply {

        text =
            "Pause Silent Zone"

        setOnClickListener {

            val result =
                SilentZonePauseManager.pause(
                    this@MainActivity
                )

            text =
                "Silent Zone Paused"

            status.text =
                "SILENT ZONE PAUSED"

            zoneProtection.text =
                "Automatic Silent Mode paused until you leave this zone.\n\n$result"
        }
    }

locationCard.addView(
    pauseSilentButton
)
val permissionButton = Button(this).apply {
    text = "Grant Silent Permission"

    setOnClickListener {
        openNotificationPolicySettings()
    }
}

locationCard.addView(permissionButton)

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

      /*  val permissionButton =
            Button(this).apply {

                text =
                    "Background Location Settings"

                setOnClickListener {
                    openLocationSettings()
                }
            } 

        actionsCard.addView(
            permissionButton
        ) */

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

    status.text = "Getting precise location..."

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

            locationInfo.text = String.format(
                Locale.US,
                "Latitude: %.6f\nLongitude: %.6f\nAccuracy: %.0f metres\nPhone mode: %s",
                location.latitude,
                location.longitude,
                accuracy,
                getCurrentPhoneMode()
            )

            status.text =
                "Location Connected"

            updateZoneProtection(location)

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

    
private fun updateZoneProtection(location: Location) {

    if (!::zoneList.isInitialized) return

    val zones = getCurrentZoneData()

    if (zones.isEmpty()) {
        zoneProtection.text = "No active Silent Zone available."
        status.text = "No Active Zones"
        return
    }

    var nearestZone: JSONObject? = null
    var nearestDistance = Double.MAX_VALUE

    for (zone in zones) {
        val latitude = zone.optDouble("latitude", Double.NaN)
        val longitude = zone.optDouble("longitude", Double.NaN)

        if (!latitude.isFinite() || !longitude.isFinite()) continue

        val result = FloatArray(1)

        Location.distanceBetween(
            location.latitude,
            location.longitude,
            latitude,
            longitude,
            result
        )

        val distance = result[0].toDouble()

        if (distance < nearestDistance) {
            nearestDistance = distance
            nearestZone = zone
        }
    }

    val zone = nearestZone

    if (zone == null) {
        zoneProtection.text = "Unable to calculate zone distance."
        return
    }

    val radius = zone.optDouble(
        "radius_m",
        zone.optDouble("radius_meters", 0.0)
    )

    val name = zone.optString("name", "Silent Zone")

    val action = zone.optString(
        "sound_action",
        "SILENT"
    ).uppercase()

    val inside = nearestDistance <= radius

    val distanceText = if (nearestDistance < 1000) {
        String.format(Locale.US, "%.0f m", nearestDistance)
    } else {
        String.format(Locale.US, "%.2f km", nearestDistance / 1000.0)
    }

    // Capture the actual result from SoundModeManager
    val soundResult =
    if (inside) {

        if (
            SilentZonePauseManager
                .isPaused(this)
        ) {

            "Silent Zone temporarily paused"

        } else {

            SoundModeManager.apply(
                this,
                action
            )
        }

    } else {

        SilentZonePauseManager
            .resetOnExit(this)

        SoundModeManager.restore(
            this
        )
    }

    val currentMode = getCurrentPhoneMode()

    if (inside) {
        status.text = "INSIDE SILENT ZONE"
        status.setTextColor(Color.rgb(20, 130, 75))
    } else {
        status.text = "OUTSIDE SILENT ZONE"
        status.setTextColor(Color.rgb(70, 80, 95))
    }

    zoneProtection.text =
        (if (inside) "● $name" else "○ Nearest zone: $name") +
        "\n\nDistance: $distanceText / " +
        String.format(Locale.US, "%.0f m radius", radius) +
        "\nRequired mode: $action" +
        "\nCurrent phone mode: $currentMode" +
        "\n\nSound control: $soundResult"
}
        
    // --------------------------------
    // STORE / READ ZONE DATA
    // --------------------------------

    private val currentZones =
        mutableListOf<JSONObject>()

    private fun getCurrentZoneData(): List<JSONObject> {
        return currentZones.toList()
    }

    // --------------------------------
    // LOAD ZONES
    // --------------------------------

    private fun loadZones() {

    status.text = "Syncing zones..."

    connectionInfo.text =
        "Connecting to Silent Zone server..."

    thread {

        var lastError: Exception? = null
        var success = false

        // Automatically retry 3 times
        for (attempt in 1..3) {

            try {

                if (attempt > 1) {

                    runOnUiThread {

                        status.text =
                            "Retrying zone sync ($attempt/3)..."

                        connectionInfo.text =
                            "Previous sync failed. Retrying..."
                    }

                    // Wait before retry
                    when (attempt) {
                        2 -> Thread.sleep(2000)
                        3 -> Thread.sleep(5000)
                    }
                }

                val connection =
                    URL(apiUrl)
                        .openConnection() as HttpURLConnection

                try {

                    connection.requestMethod = "GET"

                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000

                    connection.setRequestProperty(
                        "Accept",
                        "application/json"
                    )

                    val responseCode =
                        connection.responseCode

                    if (responseCode !in 200..299) {

                        throw Exception(
                            "Server returned HTTP $responseCode"
                        )
                    }

                    val response =
                        connection.inputStream
                            .bufferedReader()
                            .use { it.readText() }

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

                    val actionMap =
                        JSONObject()

                    var count = 0

                    for (i in 0 until zones.length()) {

                        val zone =
                            zones.getJSONObject(i)

                        if (
                            !zone.optBoolean(
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

                        val action =
                            zone.optString(
                                "sound_action",
                                "SILENT"
                            ).uppercase()

                        actionMap.put(
                            id,
                            action
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

                    // Save everything on UI thread
                    runOnUiThread {

                        currentZones.clear()

                        currentZones.addAll(
                            zoneData
                        )

                        connectionInfo.text =
                            "● Connected\n$count active zone(s)"

                        status.text =
                            "Zones Synced"

                        zoneList.removeAllViews()

                        if (count == 0) {

                            zoneList.addView(
                                text(
                                    "No active zones available.",
                                    15f
                                )
                            )

                            zoneProtection.text =
                                "No active Silent Zone available."

                        } else {

                            zoneData.forEach { zone ->

                                addZoneCard(zone)

                            }
                        }

                        // Save zone actions for GeofenceBroadcastReceiver
                        getSharedPreferences(
                            "silent_zone_geofence",
                            Context.MODE_PRIVATE
                        )
                            .edit()
                            .putString(
                                "zone_actions",
                                actionMap.toString()
                            )
                            .apply()

                        // Register background geofences
                        registerGeofences(
                            geofences.take(100)
                        )

                        // Check current location immediately
                        if (hasForegroundLocation()) {

                            checkLocation()

                        }
                    }

                    success = true
                    lastError = null

                    // Sync successful, no more retry
                    break

                } finally {

                    connection.disconnect()

                }

            } catch (e: Exception) {

                lastError = e

                // If this was the last attempt,
                // show final error
                if (attempt == 3) {

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

                        zoneProtection.text =
                            "Unable to load zone information."
                    }
                }
            }
        }

        // Safety check
        if (!success && lastError != null) {

            runOnUiThread {

                status.text =
                    "Sync Failed"

            }
        }
    }
    }
    // --------------------------------
    // ZONE CARD
    // --------------------------------

    private fun addZoneCard(
        zone: JSONObject
    ) {

        val box =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(16),
                    dp(16),
                    dp(16)
                )

                background =
                    roundedBackground(
                        Color.rgb(247, 249, 252),
                        15f
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
                19f,
                true,
                Color.rgb(25, 35, 50)
            )
        )

        val categoryText =
            text(
                category.uppercase(),
                11f,
                true,
                Color.rgb(80, 105, 135)
            )

        categoryText.setPadding(
            0,
            dp(3),
            0,
            0
        )

        box.addView(categoryText)

    val detailsText = String.format(
    Locale.US,
    "Radius: %.0f metres\nAction: %s\nStatus: Active",
    radius,
    action.uppercase()
    )

        val details =
        text(
        detailsText,
        14f,
        false,
        Color.rgb(70, 80, 95)
    )
        details.setPadding(
            0,
            dp(10),
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

    // --------------------------------
    // GEOFENCE
    // --------------------------------

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
                .addGeofences(
                    geofences
                )
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

                            if (
                                currentZones.isNotEmpty()
                            ) {
                                checkLocation()
                            }
                        }
                        .addOnFailureListener { error ->

                            status.text =
                                "Geofence Setup Failed: ${
                                    error.message
                                        ?: "Unknown error"
                                }"
                        }

                } catch (
                    e: SecurityException
                ) {

                    status.text =
                        "Location Permission Required"
                }
            }
    }
}
