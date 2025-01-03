package com.taltech.ee.finalproject

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private var trackCoordinates: List<LatLng>? = null
    private var checkpointCoordinates: List<LatLng>? = null
    private var sessionId: Long = -1


    @SuppressLint("WrongViewCast", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_map)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.saved_session_map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        sessionId = intent.getLongExtra("SESSION_ID", -1)  // Store the sessionId passed from the previous activity
        if (sessionId != -1L) {
            loadSessionData(sessionId)
        }

        findViewById<Button>(R.id.rename_button).setOnClickListener {
            showRenameDialog()
        }

        findViewById<Button>(R.id.delete_button).setOnClickListener {
            showDeleteConfirmationDialog()
        }

        findViewById<Button>(R.id.export_button).setOnClickListener {
            sendSessionAsEmail()
        }
    }

    private fun loadSessionData(sessionId: Long) {
        val dbHelper = SessionsDatabaseHelper(this)

        // Retrieve track points
        val trackPoints = dbHelper.getTrackPointsForSession(sessionId)
        trackCoordinates = trackPoints.map { LatLng(it.latitude, it.longitude) }

        // Retrieve checkpoints
        val checkpoints = dbHelper.getCheckpointsForSession(sessionId)
        checkpointCoordinates = checkpoints.map { LatLng(it.latitude, it.longitude) }

        val session = dbHelper.getSessionById(sessionId)
        session?.let {
            val distance = it.distance
            val time = it.time
            val pace = it.pace

            val sessionText = findViewById<TextView>(R.id.session_id)
            sessionText.text = if (it.name != "Session") {
                it.name
            } else {
                "Session $sessionId"
            }

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
        googleMap?.let { map ->
            val polylineOptions = PolylineOptions().addAll(coordinates)
            map.addPolyline(polylineOptions)

            if (coordinates.isNotEmpty()) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinates.first(), 15f))
            }

            checkpointCoordinates?.forEach { checkpoint ->
                map.addMarker(MarkerOptions().position(checkpoint))
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

    private fun showRenameDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Rename Session")

        val input = EditText(this)
        input.setHint("Enter new session name")
        builder.setView(input)

        builder.setPositiveButton("Rename") { _, _ ->
            val newSessionName = input.text.toString()
            if (newSessionName.isNotEmpty()) {
                renameSession(newSessionName)
            }
        }
        builder.setNegativeButton("Cancel", null)

        builder.show()
    }

    private fun sendSessionAsEmail() {
        val dbHelper = SessionsDatabaseHelper(this)
        val gpxUri = dbHelper.exportSessionToGpx(this, sessionId)

        if (gpxUri == null) {
            Toast.makeText(this, "Failed to generate GPX file", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_SUBJECT, "Exported GPX File")
            putExtra(Intent.EXTRA_TEXT, "Please find the exported GPX file attached for session $sessionId.")
            putExtra(Intent.EXTRA_STREAM, gpxUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(intent, "Send GPX File"))
        } catch (e: Exception) {
            Toast.makeText(this, "No email client found to send the file", Toast.LENGTH_SHORT).show()
        }
    }



    private fun renameSession(newSessionName: String) {
        val dbHelper = SessionsDatabaseHelper(this)
        dbHelper.updateSessionName(sessionId, newSessionName)
        Toast.makeText(this, "Session renamed to: $newSessionName", Toast.LENGTH_SHORT).show()

        loadSessionData(sessionId)
    }


    private fun showDeleteConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Delete Session")
        builder.setMessage("Are you sure you want to delete this session?")

        builder.setPositiveButton("Delete") { _, _ ->
            deleteSession()
        }
        builder.setNegativeButton("Cancel", null)

        builder.show()
    }

    private fun deleteSession() {
        val dbHelper = SessionsDatabaseHelper(this)
        dbHelper.deleteSession(sessionId)
        Toast.makeText(this, "Session deleted", Toast.LENGTH_SHORT).show()
        finish() // Close activity after deletion
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        trackCoordinates?.let { drawPolyline(it) }
    }
}

