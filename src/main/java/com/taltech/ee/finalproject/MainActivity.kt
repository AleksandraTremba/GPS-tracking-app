package com.taltech.ee.finalproject

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.snackbar.Snackbar
import com.taltech.ee.finalproject.C
import com.taltech.ee.finalproject.LocationService
import com.taltech.ee.finalproject.R


class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }


    private val broadcastReceiver = InnerBroadcastReceiver()
    private val broadcastReceiverIntentFilter: IntentFilter = IntentFilter()


    private var locationServiceActive = false

    // Google Maps variables
    private lateinit var mMap: GoogleMap
    private var mPolyline: Polyline? = null
    private val polylines = mutableListOf<Polyline>()
    private var isTracking = false

    private var isCentered: Boolean = true
    private var isNorthUp: Boolean = true // Default to North-Up
    private var isSatelliteView: Boolean = false

    private var track: MutableList<String> = mutableListOf()
    private var trackPoints = mutableListOf<List<Any>>()

    private lateinit var optionsButton: ImageButton
    private lateinit var orientationTextView: TextView


    // UI elements
    private lateinit var startStopButton: ImageButton
    private lateinit var timeElapsedTextView: TextView

    private lateinit var timeElapsedCheckpoint: TextView
    private lateinit var timeElapsedWaypoint: TextView

    // Recieved
    private var currentLat = 0.0
    private var currentLong = 0.0


    // Time tracking
    private var startTime: Long = 0
    private lateinit var elapsedTimeHandler: android.os.Handler

    private var totalDistance: Float = 0f
    private var totalPace = 0.0
    private var lastUpdateTime: Long = 0

    private val checkpointMarkers: MutableList<Pair<Marker, Long>> = mutableListOf()
    private var lastCheckpoint: MarkerOptions? = null
    private var lastCheckpointTime: Long = 0

    private var currentWaypointMarker: Marker? = null
    private var lastWaypointTime: Long = 0

    private var checkpointDistance: Float = 0f
    private var checkpointPace = 0.0

    private var waypointDistance: Float = 0f
    private var waypointPace = 0.0

    private var elapsedTime = 0


    // ============================================== MAIN ENTRY - ONCREATE =============================================
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // safe to call every time
        createNotificationChannel()

        if (!checkPermissions()) {
            requestPermissions()
        }

        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION)
        broadcastReceiverIntentFilter.addAction(C.ACTION_UPDATE_TRACKING)
        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_CP)
        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_WP)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION_PACE_OVERALL)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION_PACE_CP)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION_PACE_WP)

        elapsedTimeHandler = android.os.Handler(mainLooper)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        isNorthUp = sharedPreferences.getBoolean("isNorthUp", true)
        isCentered = sharedPreferences.getBoolean("isCentered", false)
        isSatelliteView = sharedPreferences.getBoolean("isSatelliteView", true)

        startStopButton = findViewById(R.id.start_stop_button)
        timeElapsedTextView = findViewById(R.id.time_elapsed_start)
        timeElapsedCheckpoint = findViewById(R.id.time_elapsed_checkpoint)
        timeElapsedWaypoint = findViewById(R.id.time_elapsed_waypoint)

        startStopButton.setOnClickListener {
            if (isTracking) {
                alertStopSession()
            } else {
                startTracking()
            }
        }

        val checkpointButton = findViewById<ImageButton>(R.id.checkpoint_icon)
        checkpointButton.setOnClickListener {
            if (isTracking) {
                val intent = Intent(C.NOTIFICATION_ACTION_CP)
                sendBroadcast(intent)
                Log.d("PRC", "MAIN: checkpoint sent broadcast")
            }
        }
        val waypointButton = findViewById<ImageButton>(R.id.waypoint_icon)
        waypointButton.setOnClickListener {
            if (isTracking) {
                val intent = Intent(C.NOTIFICATION_ACTION_WP)
                sendBroadcast(intent)
                Log.d("PRC", "MAIN: waypoint sent broadcast")
            }
        }
        optionsButton = findViewById(R.id.options_button)
        optionsButton.setOnClickListener {
            val dialogFragment = OptionsDialogFragment()
            dialogFragment.show(supportFragmentManager, "options_dialog")
        }

        val isNorthUp = sharedPreferences.getBoolean("isNorthUp", true)

        orientationTextView = findViewById(R.id.orientation)
        orientationTextView.text = if (isNorthUp) "North-Up" else "Direction-Up"

//        val intentFilter = IntentFilter().apply {
//            addAction(C.LOCATION_UPDATE_ACTION)
//            addAction(C.ACTION_UPDATE_TRACKING)
//        }
//        registerReceiver(broadcastReceiver, intentFilter)

    }

    // ============================================== MAP =============================================


    // ============================================== LIFECYCLE CALLBACKS =============================================
    override fun onStart() {
        Log.d(TAG, "onStart")

        super.onStart()
    }

    override fun onResume() {
        Log.d(TAG, "onResume")
        super.onResume()

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, broadcastReceiverIntentFilter)

    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        super.onStop()

    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    override fun onRestart() {
        Log.d(TAG, "onRestart")
        super.onRestart()
    }

    // ============================================== NOTIFICATION CHANNEL CREATION =============================================
    private fun createNotificationChannel() {
        // when on 8 Oreo or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                C.NOTIFICATION_CHANNEL,
                "Default channel",
                NotificationManager.IMPORTANCE_HIGH
            );


            channel.description = "Default channel"
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    // ============================================== PERMISSION HANDLING =============================================
    // Returns the current state of the permissions needed.
    private fun checkPermissions(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun requestPermissions() {
        val shouldProvideRationale =
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(
                TAG,
                "Displaying permission rationale to provide additional context."
            )
            Snackbar.make(
                findViewById(R.id.map),
                "Hey, i really need to access GPS!",
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction("OK", View.OnClickListener {
                    // Request permission
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        C.REQUEST_PERMISSIONS_REQUEST_CODE
                    )
                })
                .show()
        } else {
            Log.i(TAG, "Requesting permission")
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                C.REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode === C.REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.count() <= 0) { // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.")
                Toast.makeText(this, "User interaction was cancelled.", Toast.LENGTH_SHORT).show()
            } else if (grantResults[0] === PackageManager.PERMISSION_GRANTED) {// Permission was granted.
                Log.i(TAG, "Permission was granted")
                Toast.makeText(this, "Permission was granted", Toast.LENGTH_SHORT).show()
            } else { // Permission denied.
                Snackbar.make(
                    findViewById(R.id.map),
                    "You denied GPS! What can I do?",
                    Snackbar.LENGTH_INDEFINITE
                )
                    .setAction("Settings", View.OnClickListener {
                        // Build intent that displays the App settings screen.
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri: Uri = Uri.fromParts("package", applicationContext.packageName, null)
                        intent.data = uri
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    })
                    .show()
            }
        }

    }



    // ============================================== HANDLERS =============================================
    private fun alertStopSession() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("End the session")
        builder.setMessage("Do you want to end the session?")

        builder.setPositiveButton("End") { _, _ ->
            stopTracking()
        }
        builder.setNegativeButton("Cancel", null)

        builder.show()
    }

    private fun handleCheckpoint(lat: Double, lng: Double, timestamp: Long) {
        val latLng = LatLng(lat, lng)
        val checkpointMarkerOptions = MarkerOptions()
            .position(latLng)
            .title("Checkpoint")
            .snippet("Lat: $lat, Lng: $lng")
        val checkpointMarker = mMap.addMarker(checkpointMarkerOptions)

        checkpointMarker?.let {
            checkpointMarkers.add(Pair(it, timestamp))
        }

        lastCheckpoint = checkpointMarkerOptions
        lastCheckpointTime = timestamp
        Log.d("DB", "Checkpoint added: $latLng at $timestamp")
    }

    private fun handleWaypoint(lat: Double, lng: Double, timestamp: Long) {
        val latLng = LatLng(lat, lng)
        currentWaypointMarker?.remove()
        currentWaypointMarker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Waypoint")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
        )
        lastWaypointTime = timestamp
        Log.d("DB", "Waypoint added: $latLng")
    }



    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val location = LatLng(59.39487859716227, 24.67152136890696)
        mMap.addMarker(MarkerOptions().position(location).title("Marker in TalTech"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(location))

        // Set map orientation according to the saved preferences
        updateOrientation(isNorthUp)

        // If the map should be centered, do so
        if (isCentered) {
            centerMapOnCurrentLocation()
        }
        mapViewState()
    }

    fun updateOrientation(isNorthUp: Boolean) {
        // Update the map orientation and preferences
        val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("isNorthUp", isNorthUp).apply()

        // Update TextView in main activity to reflect the orientation
        orientationTextView.text = if (isNorthUp) "North-Up" else "Direction-Up"

        // Apply map updates
        if (isNorthUp) {
            // Lock the bearing to North (0 degrees) directly
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.builder(mMap.cameraPosition)
                    .bearing(0f)
                    .build()
            ))

            // Disable rotation gestures
            mMap.uiSettings.isRotateGesturesEnabled = false
        } else {
            // Enable rotation gestures
            mMap.uiSettings.isRotateGesturesEnabled = true
        }
    }

    fun toggleSatelliteView(isSatelliteView: Boolean) {
        val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("isSatelliteView", isSatelliteView).apply()

        if (isSatelliteView) {
            mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE)
        } else {
            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL)
        }
    }

    fun mapViewState() {
        if (isSatelliteView) {
            mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE)
        } else {
            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL)
        }
    }


    fun toggleMapCentering(isCentered: Boolean) {
        val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("isCentered", isCentered).apply()

        if (isCentered) {
            centerMapOnCurrentLocation()
        }
    }

    private fun centerMapOnCurrentLocation() {
        if (currentLat != 0.0 && currentLong != 0.0) {
            val latLng = LatLng(currentLat, currentLong)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        } else {
            // Handle the case where currentLat and currentLong are not set
            Toast.makeText(this, "Current location not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDistanceTextView() {
        val distanceTextView = findViewById<TextView>(R.id.distance_start)
        val distanceInKm = totalDistance / 1000
        distanceTextView.text = String.format("%.2f km", distanceInKm)
    }

    private fun updatePaceTextView() {
        val paceTextView = findViewById<TextView>(R.id.pace_start)
        if (totalDistance > 0) {
            val minutes = (totalPace / 60).toInt()
            val seconds = (totalPace % 60).toInt()

            paceTextView.text = String.format("%d:%02d min/km", minutes, seconds)
        } else {
            paceTextView.text = "0:00 min/km"
        }
    }

    private fun updateCheckpointDistanceTextView() {
        val distanceTextView = findViewById<TextView>(R.id.distance_checkpoint)
        val distanceInKm = checkpointDistance / 1000
        distanceTextView.text = String.format("%.2f km", distanceInKm)
    }

    private fun updateCheckpointPaceTextView() {
        val paceTextView = findViewById<TextView>(R.id.pace_checkpoint)

        if (checkpointDistance > 0) {
            val minutes = (checkpointPace / 60).toInt()
            val seconds = (checkpointPace % 60).toInt()

            paceTextView.text = String.format("%d:%02d min/km", minutes, seconds)
        } else {
            paceTextView.text = "0:00 min/km"
        }
    }

    private fun updateWaypointDistanceTextView() {
        val distanceTextView = findViewById<TextView>(R.id.distance_waypoint)
        val distanceInKm = waypointDistance / 1000
        distanceTextView.text = String.format("%.2f km", distanceInKm)
    }

    private fun updateWaypointPaceTextView() {
        val paceTextView = findViewById<TextView>(R.id.pace_waypoint)
        if (waypointDistance > 0) {
            val minutes = (waypointPace / 60).toInt()
            val seconds = (waypointPace % 60).toInt()

            paceTextView.text = String.format("%d:%02d min/km", minutes, seconds)
        } else {
            paceTextView.text = "0:00 min/km"
        }
    }

    private fun updateTrackingState(isTracking: Boolean) {
        val intent = Intent(C.ACTION_UPDATE_TRACKING).apply {
            putExtra(C.EXTRA_IS_TRACKING, isTracking)
            putExtra("startTime", startTime)
        }
        val result = sendBroadcast(intent)
    }

    private fun startTracking() {
        isTracking = true
        startTime = System.currentTimeMillis()
        startstopLocationService()
        Log.d("PRC", "MAIN: started tracking")
        startStopButton.setImageResource(R.drawable.pause)
        elapsedTimeHandler.postDelayed(updateElapsedTimeRunnable, 1000)
        elapsedTimeHandler.postDelayed({
            updateTrackingState(isTracking)
        }, 1000)
    }


    private fun stopTracking() {
        isTracking = false
        updateTrackingState(isTracking)
        startstopLocationService()
        startStopButton.setImageResource(R.drawable.play)
        elapsedTimeHandler.removeCallbacks(updateElapsedTimeRunnable)

        saveSessionData()

        polylines.forEach { polyline ->
            polyline.remove()  // Remove each polyline from the map
        }
        polylines.clear()
        mPolyline?.remove()


        checkpointMarkers.forEach { (marker, _) ->
            marker.remove()
        }

        checkpointMarkers.clear()  // Clear the list of checkpoint markers

        // Remove current waypoint marker (if needed)
        currentWaypointMarker?.remove()

        // Reset the tracking data
        totalDistance = 0f
        checkpointDistance = 0f
        waypointDistance = 0f
        lastUpdateTime = 0
        mPolyline = null
        lastCheckpoint = null
        lastCheckpointTime = 0L
        lastUpdateTime = 0L
        lastWaypointTime = 0L
        currentWaypointMarker = null
        totalPace = 0.0
        checkpointPace = 0.0
        waypointPace = 0.0
    }

    private fun saveSessionData() {
        val savedTrack = track.joinToString(";")

        val distanceInKm = totalDistance / 1000  // Convert to kilometers
        val elapsedTime = System.currentTimeMillis() - startTime  // Time in milliseconds

        val pace = if (totalDistance > 0) {
            val time = elapsedTime / 1000f
            val pace = if (time > 0) (time / (totalDistance / 1000)) else 0f
            val paceInMinutes = pace / 60
            val minutes = paceInMinutes.toInt()
            val seconds = ((paceInMinutes - minutes) * 60).toInt()

            String.format("%d:%02d min/km", minutes, seconds)
        } else {
            "N/A"
        }

        // Insert session into the database
        val dbHelper = SessionsDatabaseHelper(this)
        val sessionId = dbHelper.insertSession(savedTrack, distanceInKm.toFloat(), elapsedTime, pace)

        for ((marker, timestamp) in checkpointMarkers) {
            Log.d("DB", "${timestamp} saved in database")
            val latitude = marker.position.latitude
            val longitude = marker.position.longitude
            dbHelper.insertCheckpoint(sessionId, latitude, longitude, timestamp)
        }

        for (point in trackPoints) {
            val latitude = point[0] as Double
            val longitude = point[1] as Double
            val timestamp = point[2] as Long

            dbHelper.insertTrackPoint(sessionId, latitude, longitude, timestamp)
        }


        Toast.makeText(this, "Session and checkpoints saved.", Toast.LENGTH_SHORT).show()
    }


    private val updateElapsedTimeRunnable = object : Runnable {
        override fun run() {
            if (isTracking) {
                val elapsedMillis = System.currentTimeMillis() - startTime
                val hours = (elapsedMillis / (1000 * 60 * 60)) % 24
                val minutes = (elapsedMillis / (1000 * 60)) % 60
                val seconds = (elapsedMillis / 1000) % 60
                timeElapsedTextView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                if (lastCheckpointTime != 0L) {
                    val elapsedMillisCheckpoint = System.currentTimeMillis() - lastCheckpointTime
                    val hoursCheckpoint = (elapsedMillisCheckpoint / (1000 * 60 * 60)) % 24
                    val minutesCheckpoint = (elapsedMillisCheckpoint / (1000 * 60)) % 60
                    val secondsCheckpoint = (elapsedMillisCheckpoint / 1000) % 60
                    timeElapsedCheckpoint.text = String.format(
                        "%02d:%02d:%02d",
                        hoursCheckpoint,
                        minutesCheckpoint,
                        secondsCheckpoint
                    )
                }

                if (lastWaypointTime != 0L) {
                    val elapsedMillisWaypoint = System.currentTimeMillis() - lastWaypointTime
                    val hoursWaypoint = (elapsedMillisWaypoint / (1000 * 60 * 60)) % 24
                    val minutesWaypoint = (elapsedMillisWaypoint / (1000 * 60)) % 60
                    val secondsWaypoint = (elapsedMillisWaypoint / 1000) % 60
                    timeElapsedWaypoint.text = String.format(
                        "%02d:%02d:%02d",
                        hoursWaypoint,
                        minutesWaypoint,
                        secondsWaypoint
                    )
                }
                elapsedTimeHandler.postDelayed(this, 1000)
            }
        }
    }


    fun startstopLocationService() {
        Log.d(TAG, "StartedLocationService. locationServiceActive: $locationServiceActive")
        // try to start/stop the background service

        if (locationServiceActive) {
            // stopping the service
            stopService(Intent(this, LocationService::class.java))

        } else {
            if (Build.VERSION.SDK_INT >= 26) {
                // starting the FOREGROUND service
                // service has to display non-dismissable notification within 5 secs
                startForegroundService(Intent(this, LocationService::class.java))
                Log.d("pace", "MAIN: started service")
            } else {
                startService(Intent(this, LocationService::class.java))
                Log.d("pace", "MAIN: started service")
            }
        }

        locationServiceActive = !locationServiceActive
    }

    fun buttonWPOnClick(view: View) {
        Log.d(TAG, "buttonWPOnClick")
        sendBroadcast(Intent(C.NOTIFICATION_ACTION_WP))
    }

    fun buttonCPOnClick(view: View) {
        Log.d(TAG, "buttonCPOnClick")
        sendBroadcast(Intent(C.NOTIFICATION_ACTION_CP))
    }

    // ============================================== BROADCAST RECEIVER =============================================
    private inner class InnerBroadcastReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent!!.action?.let { Log.d(TAG, it) }
            when (intent!!.action){
                C.LOCATION_UPDATE_ACTION -> {
                    Log.d(TAG, "LOCATION UPDATE!")
                    val currentTime = System.currentTimeMillis()
                    currentLat = intent.getDoubleExtra(C.LOCATION_UPDATE_ACTION_LATITUDE, 0.0)
                    currentLong = intent.getDoubleExtra(C.LOCATION_UPDATE_ACTION_LONGITUDE, 0.0)
                    totalDistance = intent.getFloatExtra(C.LOCATION_UPDATE_ACTION_DISTANCE_OVERALL_TOTAL, 0f)
                    checkpointDistance = intent.getFloatExtra(C.LOCATION_UPDATE_ACTION_DISTANCE_CP_TOTAL, 0f)
                    waypointDistance = intent.getFloatExtra(C.LOCATION_UPDATE_ACTION_DISTANCE_WP_TOTAL, 0f)
                    totalPace = intent.getDoubleExtra(C.LOCATION_UPDATE_ACTION_PACE_OVERALL, 0.0)
                    checkpointPace = intent.getDoubleExtra(C.LOCATION_UPDATE_ACTION_PACE_CP, 0.0)
                    waypointPace = intent.getDoubleExtra(C.LOCATION_UPDATE_ACTION_PACE_WP, 0.0)

                    Log.d("PRC", "MAIN: broadcast recieved: distance = $totalDistance, " +
                        "pace = $totalPace, CP distance = $checkpointDistance, CP pace = $checkpointPace")


                    val newPolyline = intent.getSerializableExtra("newPolyline") as? Pair<LatLng, LatLng>

                    updateDistanceTextView()
                    updatePaceTextView()
                    updateCheckpointDistanceTextView()
                    updateCheckpointPaceTextView()
                    updateWaypointDistanceTextView()
                    updateWaypointPaceTextView()
                    if (isCentered) {
                        centerMapOnCurrentLocation()
                    }

                    trackPoints.add(listOf(currentLat, currentLong, currentTime))

                    // Draw new polyline on the map
                    newPolyline?.let { (start, end) ->
                        val polyline = mMap.addPolyline(
                            PolylineOptions().add(start, end).color(Color.BLUE).width(5f)
                        )
                        polylines.add(polyline)
                        track.add("${start.latitude},${start.longitude},${end.latitude},${end.longitude}")
                        Log.d("DB", "start: $start, end: $end")
                    }
                }
                C.NOTIFICATION_ACTION_CP -> {
                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lng = intent.getDoubleExtra("lng", 0.0)
                    val timestamp = intent.getLongExtra("timestamp", 0L)

                    // Handle checkpoint data
                    handleCheckpoint(lat, lng, timestamp)

                }
                C.NOTIFICATION_ACTION_WP -> {
                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lng = intent.getDoubleExtra("lng", 0.0)
                    val timestamp = intent.getLongExtra("timestamp", 0L)

                    // Handle waypoint data
                    handleWaypoint(lat, lng, timestamp)
                }
            }
        }
    }
}
