package com.taltech.ee.finalproject

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
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

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    // Google Maps variables
    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mLocationCallback: LocationCallback? = null
    private var mPolyline: Polyline? = null
    private val polylines = mutableListOf<Polyline>()
    private var isTracking = false
    private var startLocation: Location? = null

    private var isCentered: Boolean = true
    private var isNorthUp: Boolean = true // Default to North-Up
    private var isSatelliteView: Boolean = false

    private var track: MutableList<String> = mutableListOf()

    private lateinit var optionsButton: Button
    private lateinit var orientationTextView: TextView

    private var previousLocation: Location? = null

    // UI elements
    private lateinit var startStopButton: Button
    private lateinit var timeElapsedTextView: TextView

    private lateinit var timeElapsedCheckpoint: TextView
    private lateinit var timeElapsedWaypoint: TextView


    // Time tracking
    private var startTime: Long = 0
    private lateinit var elapsedTimeHandler: android.os.Handler

    // Notification and Broadcast
    private val broadcastReceiver = InnerBroadcastReceiver()
    private val broadcastReceiverIntentFilter = IntentFilter().apply {
        addAction(C.LOCATION_UPDATE_ACTION)
    }
    private var locationServiceActive = false

    private var totalDistance: Float = 0f
    private var lastUpdateTime: Long = 0

    private val checkpointMarkers: MutableList<Marker> = mutableListOf()
    private var lastCheckpoint: MarkerOptions? = null
    private var lastCheckpointTime: Long = 0

    private var currentWaypointMarker: Marker? = null
    private var lastWaypointTime: Long = 0

    private var checkpointDistance: Float = 0f

    private var waypointDistance: Float = 0f



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize elapsedTimeHandler here
        elapsedTimeHandler = android.os.Handler(mainLooper)

        // The rest of your onCreate logic
        createNotificationChannel()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        isNorthUp = sharedPreferences.getBoolean("isNorthUp", true)
        isCentered = sharedPreferences.getBoolean("isCentered", false)
        isSatelliteView = sharedPreferences.getBoolean("isSatelliteView", true)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        startStopButton = findViewById(R.id.start_stop_button)
        timeElapsedTextView = findViewById(R.id.time_elapsed_start)
        timeElapsedCheckpoint = findViewById(R.id.time_elapsed_checkpoint)
        timeElapsedWaypoint = findViewById(R.id.time_elapsed_waypoint)

        startStopButton.setOnClickListener {
            if (startStopButton.text == "Stop") {
                showPopupWindow()
            } else {
                startTracking()
            }
        }

        val checkpointButton = findViewById<ImageButton>(R.id.checkpoint_icon)
        checkpointButton.setOnClickListener {
            if (isTracking) {
                enableCheckpointMode()
            }
        }
        val waypointButton = findViewById<ImageButton>(R.id.waypoint_icon)
        waypointButton.setOnClickListener {
            if (isTracking) {
                addWaypoint()
            }
        }

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let {
                    Log.d(TAG, "Location update: $it")
                    onNewLocation(it)
                }
            }
        }

        if (!checkPermissions()) {
            requestPermissions()
        }

        optionsButton = findViewById(R.id.options_button)
        optionsButton.setOnClickListener {
            val dialogFragment = OptionsDialogFragment()
            dialogFragment.show(supportFragmentManager, "options_dialog")
        }

        orientationTextView = findViewById(R.id.orientation)

        // Load preferences
        val isNorthUp = sharedPreferences.getBoolean("isNorthUp", true)

        // Set initial text based on preferences
        orientationTextView.text = if (isNorthUp) "North-Up" else "Direction-Up"
    }

    private fun showPopupWindow() {
        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.popup_window, null)

        val popupWindow = PopupWindow(
            popupView,
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        val noButton = popupView.findViewById<Button>(R.id.no_button)
        val yesButton = popupView.findViewById<Button>(R.id.yes_button)

        noButton.setOnClickListener {
            popupWindow.dismiss()
        }

        yesButton.setOnClickListener {
            stopTracking()
            popupWindow.dismiss()
        }

        popupWindow.showAtLocation(
            findViewById(R.id.map),  // root layout to anchor the popup
            android.view.Gravity.CENTER,  // Center the popup
            0, 0  // offset position (0, 0 means centered)
        )
    }

    private fun enableCheckpointMode() {

        Toast.makeText(this, "Tap on the map to add a checkpoint.", Toast.LENGTH_SHORT).show()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions()
            return
        }

        mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)

                val checkpointMarkerOptions = MarkerOptions()
                    .position(latLng)
                    .title("Checkpoint")
                    .snippet("Lat: ${latLng.latitude}, Lng: ${latLng.longitude}")
                var checkpointMarker = mMap.addMarker(checkpointMarkerOptions)

                checkpointMarker?.let { checkpointMarkers.add(it) }
                lastCheckpoint = checkpointMarkerOptions
                val currentTime = System.currentTimeMillis()
                lastCheckpointTime = currentTime

                Toast.makeText(this, "Checkpoint added.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Unable to fetch current location.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to get location: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addWaypoint() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions()
            return
        }

        mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                currentWaypointMarker?.remove()

                // Add a new waypoint marker at the current location
                currentWaypointMarker = mMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Waypoint")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
                )

                val currentTime = System.currentTimeMillis()
                lastWaypointTime = currentTime

                Toast.makeText(this, "Waypoint added at your current location.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Unable to fetch current location.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to get location: ${it.message}", Toast.LENGTH_SHORT).show()
        }
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



    fun toggleMapCentering(isCentered: Boolean) {
        val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("isCentered", isCentered).apply()

        if (isCentered) {
            centerMapOnCurrentLocation()
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

    private fun centerMapOnCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }
        }
    }

    private fun simulateMovement() {
        val startLocation = previousLocation ?: Location("").apply {
            latitude = 59.39487859716227
            longitude = 24.67152136890696
        }

        val newLocation = Location("").apply {
            val randomDirection = Math.random() * 2 * Math.PI // Random direction in radians
            val distance = 100.0 / 6371000.0 // 100 meters in radians (Earth radius = 6371 km)

            latitude = startLocation.latitude + Math.toDegrees(distance * Math.cos(randomDirection))
            longitude = startLocation.longitude + Math.toDegrees(distance * Math.sin(randomDirection) / Math.cos(Math.toRadians(startLocation.latitude)))
        }

        // Pass this new location to your `onNewLocation` function
        onNewLocation(newLocation)

        // Schedule the next update in 2 seconds
        elapsedTimeHandler.postDelayed({ simulateMovement() }, 2000)
    }


    private fun onNewLocation(location: Location) {
        if (!isTracking) {
            return // Don't calculate distance or update data if tracking is not active
        }

        // Convert the location to LatLng
        val latLng = LatLng(location.latitude, location.longitude)

        // If we have a previous location, draw a polyline
        previousLocation?.let {
            val previousLatLng = LatLng(it.latitude, it.longitude)

            // Create a polyline from previous location to the new one
            val polyline = mMap.addPolyline(PolylineOptions().add(previousLatLng, latLng).color(android.graphics.Color.BLUE).width(5f))
            polylines.add(polyline)
            track.add("${location.latitude},${location.longitude},${it.latitude},${it.longitude}")
        }

        val currentTime = System.currentTimeMillis()

        // Calculate distance if there is a previous location
        previousLocation?.let {
            val distance = it.distanceTo(location) // Distance in meters
            totalDistance += distance // Add to total distance

            // Calculate elapsed time since last update
            val elapsedTime = (currentTime - lastUpdateTime) / 1000f // in seconds

            // Calculate pace (time per kilometer)
            if (totalDistance > 0) {
                val pace = if (elapsedTime > 0) (elapsedTime / (distance / 1000)) else 0f // seconds/km
                updatePaceTextView(pace)
            }

            // Calculate distances for Checkpoint and Waypoint only if they exist

            // Checkpoint
            var positionToCheckpoint = lastCheckpoint?.position
            val locationToCheckpoint = positionToCheckpoint?.let {
                Location("").apply {
                    latitude = it.latitude
                    longitude = it.longitude
                }
            }

            var distanceToCheckpoint = locationToCheckpoint?.let { it1 -> it.distanceTo(it1) }
            if (distanceToCheckpoint != null) {
                checkpointDistance = distanceToCheckpoint
            }

            val elapsedTimeCheckpoint = (currentTime - lastCheckpointTime) / 1000f // in seconds

            if (distanceToCheckpoint != null && distanceToCheckpoint > 0) {
                val pace = if (elapsedTimeCheckpoint > 0) (elapsedTimeCheckpoint / (distanceToCheckpoint / 1000)) else 0f // seconds/km
                updateCheckpointPaceTextView(pace)
            }

            // Waypoint
            var positionToWaypoint = currentWaypointMarker?.position
            val locationToWaypoint = positionToWaypoint?.let {
                Location("").apply {
                    latitude = it.latitude
                    longitude = it.longitude
                }
            }

            var distanceToWaypoint = locationToWaypoint?.let { it1 -> it.distanceTo(it1) }
            if (distanceToWaypoint != null) {
                waypointDistance += distanceToWaypoint
            }
            val elapsedTimeWaypoint = (currentTime - lastWaypointTime) / 1000f // in seconds

            if (distanceToWaypoint != null && distanceToWaypoint > 0) {
                val pace = if (elapsedTimeWaypoint > 0) (elapsedTimeWaypoint / (distanceToWaypoint / 1000)) else 0f // seconds/km
                updateWaypointPaceTextView(pace)
            }

        }

        lastUpdateTime = currentTime
        previousLocation = location

        updateDistanceTextView()
        updateCheckpointDistanceTextView()
        updateWaypointDistanceTextView()

        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }


    private fun updateDistanceTextView() {
        val distanceTextView = findViewById<TextView>(R.id.distance_start)
        val distanceInKm = totalDistance / 1000
        distanceTextView.text = String.format("%.2f km", distanceInKm)
    }

    private fun updatePaceTextView(pace: Float) {
        val paceTextView = findViewById<TextView>(R.id.pace_start)
        val paceInMinutes = pace / 60
        val minutes = paceInMinutes.toInt()
        val seconds = ((paceInMinutes - minutes) * 60).toInt()
        paceTextView.text = String.format("%d:%02d min/km", minutes, seconds)
    }

    private fun updateCheckpointDistanceTextView() {
        val distanceTextView = findViewById<TextView>(R.id.distance_checkpoint)
        val distanceInKm = checkpointDistance / 1000
        distanceTextView.text = String.format("%.2f km", distanceInKm)
    }

    private fun updateCheckpointPaceTextView(pace: Float) {
        val paceTextView = findViewById<TextView>(R.id.pace_checkpoint)
        val paceInMinutes = pace / 60
        val minutes = paceInMinutes.toInt()
        val seconds = ((paceInMinutes - minutes) * 60).toInt()
        paceTextView.text = String.format("%d:%02d min/km", minutes, seconds)
    }

    private fun updateWaypointDistanceTextView() {
        val distanceTextView = findViewById<TextView>(R.id.distance_waypoint)
        val distanceInKm = waypointDistance / 1000
        distanceTextView.text = String.format("%.2f km", distanceInKm)
    }

    private fun updateWaypointPaceTextView(pace: Float) {
        val paceTextView = findViewById<TextView>(R.id.pace_waypoint)
        val paceInMinutes = pace / 60
        val minutes = paceInMinutes.toInt()
        val seconds = ((paceInMinutes - minutes) * 60).toInt()
        paceTextView.text = String.format("%d:%02d min/km", minutes, seconds)
    }



    private fun startTracking() {
        isTracking = true
        startStopButton.text = "Stop"
        startTime = System.currentTimeMillis()
        elapsedTimeHandler.postDelayed(updateElapsedTimeRunnable, 1000)
        startLocationUpdates()

//        simulateMovement()
    }


    private fun stopTracking() {
        isTracking = false
        startStopButton.text = "Start"
        elapsedTimeHandler.removeCallbacks(updateElapsedTimeRunnable)
        mFusedLocationClient.removeLocationUpdates(mLocationCallback!!) // Stop location updates

        polylines.forEach { polyline ->
            polyline.remove()  // Remove each polyline from the map
        }
        polylines.clear()
        mPolyline?.remove()


        checkpointMarkers.forEach { marker ->
            marker.remove()
        }
        checkpointMarkers.clear()  // Clear the list of checkpoint markers

        // Remove current waypoint marker (if needed)
        currentWaypointMarker?.remove()

        // Save session data
        saveSessionData()

        // Reset the tracking data
        totalDistance = 0f
        lastUpdateTime = 0
        previousLocation = null
        mPolyline = null
        lastCheckpoint = null
        lastCheckpointTime = 0L
        currentWaypointMarker = null
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
        dbHelper.insertSession(savedTrack, distanceInKm.toFloat(), elapsedTime, pace)
        Toast.makeText(this, "Session saved.", Toast.LENGTH_SHORT).show()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                C.NOTIFICATION_CHANNEL,
                "Default channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Default channel" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            C.REQUEST_PERMISSIONS_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == C.REQUEST_PERMISSIONS_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Permission Denied!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        mFusedLocationClient.requestLocationUpdates(
            LocationRequest.create().apply {
                interval = 2000 // Update every 2 seconds
                fastestInterval = 1000 // Fastest update every 1 second
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            },
            mLocationCallback!!,
            mainLooper
        )
    }

    private inner class InnerBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                C.LOCATION_UPDATE_ACTION -> {
                    findViewById<TextView>(R.id.textViewLatitude).text =
                        intent.getDoubleExtra(C.LOCATION_UPDATE_ACTION_LATITUDE, 0.0).toString()
                    findViewById<TextView>(R.id.textViewLongitude).text =
                        intent.getDoubleExtra(C.LOCATION_UPDATE_ACTION_LONGITUDE, 0.0).toString()
                }
            }
        }
    }
}
