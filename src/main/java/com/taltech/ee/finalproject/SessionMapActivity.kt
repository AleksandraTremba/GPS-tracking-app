package com.taltech.ee.finalproject

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions

class SessionMapActivity : AppCompatActivity(), OnMapReadyCallback {
    private var googleMap: GoogleMap? = null
    private var coordinates: List<LatLng>? = null
    private var checkpointCoordinates: List<LatLng>? = null


    @SuppressLint("WrongViewCast", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_map)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.saved_session_map) as SupportMapFragment
        Log.d("SessionMapActivity", "getMapAsync called")
        mapFragment.getMapAsync(this)

        val sessionId = intent.getLongExtra("SESSION_ID", -1)
        if (sessionId != -1L) {
            loadSessionData(sessionId)
        }

        val sessionText = findViewById<TextView>(R.id.session_id)
        sessionText.text = "Session $sessionId"

    }

    private fun loadSessionData(sessionId: Long) {
        val dbHelper = SessionsDatabaseHelper(this)
        val session = dbHelper.getSessionById(sessionId)

        if (session != null) {
            val track = session.track // Assume track is a String with coordinates
            val distance = session.distance
            val time = session.time
            val pace = session.pace

            // Parse the track string into a list of LatLng coordinates
            coordinates = parseTrack(track)

            val checkpoints = dbHelper.getCheckpointsForSession(sessionId)
            checkpointCoordinates = checkpoints.map { LatLng(it.latitude, it.longitude) }
            Log.d("DB", "checkpoint coordinates: $checkpointCoordinates")


            updateSessionText(distance, time, pace)
        }
    }

    private fun parseTrack(track: String): List<LatLng> {
        val coordinates = mutableListOf<LatLng>()
        val trackParts = track.split(";")

        for (trackPart in trackParts) {
            val points = trackPart.split(",")
            if (points.size == 4) {
                try {
                    val prevLat = points[0].toDouble()
                    val prevLon = points[1].toDouble()
                    val currLat = points[2].toDouble()
                    val currLon = points[3].toDouble()

                    // Add points to the coordinates list
                    coordinates.add(LatLng(prevLat, prevLon))
                    coordinates.add(LatLng(currLat, currLon))
                } catch (e: Exception) {
                    Log.e("SessionMapActivity", "Error parsing track data: ${e.message}")
                }
            }
        }
        return coordinates
    }

    private fun drawPolyline(coordinates: List<LatLng>) {
        Log.d("SessionMapActivity", "Started drawPolylineMethod")

        googleMap?.let {
            Log.d("SessionMapActivity", "Drawing polyline")
            val polylineOptions = PolylineOptions().addAll(coordinates)
            it.addPolyline(polylineOptions)

            if (coordinates.isNotEmpty()) {
                val firstPoint = coordinates[0]
                it.moveCamera(CameraUpdateFactory.newLatLngZoom(firstPoint, 15f))
            }

            if (checkpointCoordinates?.isNotEmpty() == true) {
                for (coordinate in checkpointCoordinates!!) {
                    it.addMarker(MarkerOptions().position(coordinate))
                }
            }
        }
    }

    private fun updateSessionText(distance: Float, time: Int, pace: String) {
        val hours = (time / (1000 * 60 * 60)) % 24
        val minutes = (time / (1000 * 60)) % 60
        val seconds = (time / 1000) % 60

        var savedSessionText = findViewById<TextView>(R.id.saved_sessions_text)
        savedSessionText.text = "Distance: ${"%.2f".format(distance)} km | Time: " +
                "${"%02d:%02d:%02d".format(hours, minutes, seconds)} | Pace: $pace"
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        if (coordinates?.isNotEmpty() == true) {
            Log.d("SessionMapActivity", "Coordinates were not empty: $coordinates")
            drawPolyline(coordinates!!)
        }
    }
}

