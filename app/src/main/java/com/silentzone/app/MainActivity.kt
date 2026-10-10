
package com.silentzone.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var zoneList: TextView

    private val apiUrl =
        "https://silent-zone.onrender.com/api/zones"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 48, 28, 24)
        }

        val heading = TextView(this).apply {
            text = "Silent Zone"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
        }

        status = TextView(this).apply {
            text = "Ready to connect"
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }

        val syncButton = Button(this).apply {
            text = "Sync Zones"
            setOnClickListener { loadZones() }
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
        layout.addView(locationButton)
        layout.addView(zoneList)

        setContentView(layout)
        loadZones()
    }

    private fun loadZones() {
        status.text = "Loading zones..."

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

                val display = StringBuilder()
                var count = 0

                if (zones != null) {
                    for (i in 0 until zones.length()) {
                        val zone = zones.getJSONObject(i)

                        if (!zone.optBoolean("is_active", true)) {
                            continue
                        }

                        count++
                        display.append("\n$count. ")
                            .append(zone.optString("name", "Unnamed zone"))
                            .append("\n   Action: ")
                            .append(zone.optString("sound_action", "SILENT"))
                            .append("\n")
                    }
                }

                runOnUiThread {
                    status.text = "Connected • $count active zone(s)"
                    zoneList.text =
                        if (count == 0) "No active zones found."
                        else display.toString()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text =
                        "Connection failed: ${e.localizedMessage ?: "Please retry"}"
                }
            }
        }
    }

    private fun checkLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                100
            )
            return
        }

        status.text = "Checking location..."

        val client = LocationServices.getFusedLocationProviderClient(this)
        val token = CancellationTokenSource()

        try {
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                token.token
            ).addOnSuccessListener { location: Location? ->
                if (location == null) {
                    status.text = "Location unavailable. Try again outdoors."
                } else {
                    status.text = String.format(
                        java.util.Locale.US,
                        "Location received • Accuracy %.0f m",
                        location.accuracy
                    )
                }
            }.addOnFailureListener {
                status.text = "Location failed. Check phone settings."
            }
        } catch (e: SecurityException) {
            status.text = "Please allow location permission."
        }
    }
}
