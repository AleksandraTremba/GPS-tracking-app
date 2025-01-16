package com.taltech.ee.finalproject.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
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
import com.taltech.ee.finalproject.location.C
import com.taltech.ee.finalproject.location.LocationService
import com.taltech.ee.finalproject.R
import com.taltech.ee.finalproject.database.SessionsDatabaseHelper


class MainActivity : AppCompatActivity(), OnMapReadyCallback, SensorEventListener {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }


    private val broadcastReceiver = InnerBroadcastReceiver()
    private val broadcastReceiverIntentFilter: IntentFilter = IntentFilter()

    private lateinit var sharedPreferences: SharedPreferences


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
    private var checkpointDirectDistance = 0f
    private var checkpointPace = 0.0

    private var waypointDistance: Float = 0f
    private var waypointDirectDistance = 0f
    private var waypointPace = 0.0

    private var elapsedTime = 0

    //compass
    private lateinit var compassImage: ImageView
    private lateinit var sensorManager: SensorManager
    private lateinit var magnetometer: Sensor
    private lateinit var accelerometer: Sensor

    private val lastAccelerometer = FloatArray(3)
    private val lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var isCompassOn = true

    private var sessionIDBackend: String = "Empty"

    private var updateInterval: Long = 2000
    private var savedStateBundle: Bundle? = null



    // ============================================== MAIN ENTRY - ONCREATE =============================================
    @SuppressLint("MissingInflatedId")
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
        broadcastReceiverIntentFilter.addAction(C.BACKEND_ID_UPDATE)

        elapsedTimeHandler = android.os.Handler(mainLooper)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        isNorthUp = sharedPreferences.getBoolean("isNorthUp", true)
        isCentered = sharedPreferences.getBoolean("isCentered", false)
        isSatelliteView = sharedPreferences.getBoolean("isSatelliteView", true)
        isCompassOn = sharedPreferences.getBoolean("isCompassOn", true)

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

        var sessionButton = findViewById<Button>(R.id.continue_session_button)
        sessionButton.setOnClickListener {
            loadLastSession()
        }

        val isNorthUp = sharedPreferences.getBoolean("isNorthUp", true)

        orientationTextView = findViewById(R.id.orientation)
        orientationTextView.text = if (isNorthUp) "North-Up" else "User-Chosen"

        //compass
        compassImage = findViewById(R.id.compass_arrow)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)!!
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!

        toggleCompass(isCompassOn)


//        val intentFilter = IntentFilter().apply {
//            addAction(C.LOCATION_UPDATE_ACTION)
//            addAction(C.ACTION_UPDATE_TRACKING)
//        }
//        registerReceiver(broadcastReceiver, intentFilter)

    }

    // ============================================== MAP =============================================
    private fun loadLastSession() {

        val sessionIDBackend = getSavedSessionID()
        if (sessionIDBackend == "Empty") {
            return
        }
        val distance = getDistancesFromStorage()

        Log.d("FUCK", "MAIN: Loaded session with id: $sessionIDBackend and distance: $distance")

        if (sessionIDBackend != null && distance != null) {
            val intent = Intent(this, LocationService::class.java).apply {
                putExtra("sessionIDBackend", sessionIDBackend)
                putExtra("totalDistance", distance)
            }

            startService(intent)
            locationServiceActive = true
            Log.d(TAG, "LocationService started to resume session with ID: $sessionIDBackend")
        } else {
            Log.e(TAG, "Failed to load last session. Missing data.")
        }

        val dbHelper = SessionsDatabaseHelper(this)

        // Retrieve trackpoints without a session ID
        val unsavedTrackPoints = dbHelper.getUnsavedTrackPoints()
        val unsavedCheckpoints = dbHelper.getUnsavedCheckpoints()

        Log.d("FUCK", "Unsaved checkpoints: ${unsavedCheckpoints.size}")
        Log.d("FUCK", "Unsaved trackpoints: ${unsavedTrackPoints.size}")


        // Clear any existing map data before loading
        polylines.forEach { it.remove() }
        polylines.clear()

        checkpointMarkers.forEach { (marker, _) -> marker.remove() }
        checkpointMarkers.clear()

        // Plot trackpoints on the map
        var previousLatLng: LatLng? = null
        for (point in unsavedTrackPoints) {
            val latLng = LatLng(point.latitude, point.longitude)
            trackPoints.add(listOf(point.latitude, point.longitude, point.timestamp))

            if (previousLatLng != null) {
                val polyline = mMap.addPolyline(
                    PolylineOptions().add(previousLatLng, latLng).color(Color.BLUE).width(5f)
                )
                polylines.add(polyline)
                track.add("${previousLatLng.latitude},${previousLatLng.longitude},${latLng.latitude},${latLng.longitude}")
            }

            previousLatLng = latLng
        }

        for (checkpoint in unsavedCheckpoints) {
            Log.d("FUCK", "Checkpoint: ${checkpoint.latitude}, ${checkpoint.longitude}, ${checkpoint.timestamp}")
            val latLng = LatLng(checkpoint.latitude, checkpoint.longitude)
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title("Checkpoint")
                .snippet("Lat: ${checkpoint.latitude}, Lng: ${checkpoint.longitude}")
            val marker = mMap.addMarker(markerOptions)

            marker?.let {
                checkpointMarkers.add(Pair(it, checkpoint.timestamp))
            }
        }

        // Update app state to reflect loaded session
        isTracking = true
        startTime = unsavedTrackPoints.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
        startStopButton.setImageResource(R.drawable.pause)

        // Resume tracking
        elapsedTimeHandler.postDelayed(updateElapsedTimeRunnable, 1000)

        Toast.makeText(this, "Previous session loaded successfully.", Toast.LENGTH_SHORT).show()
    }

    private fun getSavedSessionID(): String? {
        return sharedPreferences.getString("SESSION_ID", null)
    }

    private fun getDistancesFromStorage(): Float {
        return sharedPreferences.getFloat("DISTANCE_OVERALL", 0f)
    }

    private fun getStartTimeFromStorage(): Long {
        return sharedPreferences.getLong("START_TIME", 0L)
    }


    // ============================================== LIFECYCLE CALLBACKS =============================================
    override fun onStart() {
        Log.d(TAG, "onStart")

        super.onStart()
    }

    override fun onResume() {
        Log.d(TAG, "onResume")
        super.onResume()

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, broadcastReceiverIntentFilter)
        isCompassOn.let {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        super.onPause()
        isCompassOn.let {sensorManager.unregisterListener(this)}
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Handle orientation-specific UI updates here, if needed
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
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(it.values, 0, lastMagnetometer, 0, it.values.size)
                lastMagnetometerSet = true
            } else if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(it.values, 0, lastAccelerometer, 0, it.values.size)
                lastAccelerometerSet = true
            }

            if (lastAccelerometerSet && lastMagnetometerSet) {
                // Calculate rotation matrix and orientation
                SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)
                SensorManager.getOrientation(rotationMatrix, orientation)

                // Calculate azimuth angle
                val azimuthInRadians = orientation[0]
                val azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()

                // Rotate the compass arrow to point north
                compassImage.rotation = -azimuthInDegrees // Negative to make it point north
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    fun toggleCompass(isEnabled: Boolean) {
        isCompassOn = isEnabled
        if (isEnabled) {
            compassImage.visibility = View.VISIBLE
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        } else {
            compassImage.visibility = View.GONE
            sensorManager.unregisterListener(this)
        }
    }

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

        val dbHelper = SessionsDatabaseHelper(this)
        dbHelper.insertCheckpoint(null, currentLat, currentLong, lastCheckpointTime)
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

        savedStateBundle?.let {
            restoreMapState(it)
            savedStateBundle = null
        }

    }

    fun updateOrientation(isNorthUp: Boolean) {
        // Update the map orientation and preferences
        val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("isNorthUp", isNorthUp).apply()

        // Update TextView in main activity to reflect the orientation
        orientationTextView.text = if (isNorthUp) "North-Up" else "User-Chosen"

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


    fun toggleMapCentering(newCentered: Boolean) {
        isCentered = newCentered
        val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("isCentered", isCentered).apply()

        Log.d("DEBUG", "toggleMapCentering called with newCentered: $newCentered")

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
        val directDistanceTextView = findViewById<TextView>(R.id.distance_checkpoint_direct)
        val distanceInKm = checkpointDistance / 1000
        val directDistanceInKm = checkpointDirectDistance / 1000

        if (distanceTextView != null) {
            distanceTextView.text = String.format("%.2f km", distanceInKm)
        } else {
            Log.e("MainActivity", "distance_checkpoint TextView not found")
        }

        if (directDistanceTextView != null) {
            directDistanceTextView.text = String.format("%.2f km", directDistanceInKm)
        } else {
            Log.e("MainActivity", "distance_checkpoint_direct TextView not found")
        }
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
        val directDistanceTextView = findViewById<TextView>(R.id.distance_waypoint_direct)
        val distanceInKm = waypointDistance / 1000
        val directDistanceInKm = waypointDirectDistance /1000
        distanceTextView.text = String.format("%.2f km", distanceInKm)
        directDistanceTextView.text = String.format("%.2f km", directDistanceInKm)
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isTracking", isTracking)
        outState.putLong("startTime", startTime)
        outState.putLong("lastCheckpointTime", lastCheckpointTime)
        outState.putLong("lastWaypointTime", lastWaypointTime)

        // Save checkpoint markers
        val checkpointData = checkpointMarkers.map { pair ->
            val position = pair.first.position
            arrayOf(position.latitude, position.longitude, pair.second)
        }
        outState.putSerializable("checkpointMarkers", checkpointData.toTypedArray())

        // Save waypoint data
        currentWaypointMarker?.let { marker ->
            val position = marker.position
            outState.putDouble("currentWaypointLat", position.latitude)
            outState.putDouble("currentWaypointLng", position.longitude)
            outState.putLong("currentWaypointTime", lastWaypointTime)
        }
        // Save polylines
        outState.putStringArrayList("track", ArrayList(track))

        // Save tracking data
        outState.putFloat("totalDistance", totalDistance)
        outState.putFloat("checkpointDistance", checkpointDistance)
        outState.putFloat("waypointDistance", waypointDistance)

        // Save direct distances
        outState.putFloat("checkpointDirectDistance", checkpointDirectDistance)
        outState.putFloat("waypointDirectDistance", waypointDirectDistance)

        // Save pace data
        outState.putDouble("totalPace", totalPace)
        outState.putDouble("checkpointPace", checkpointPace)
        outState.putDouble("waypointPace", waypointPace)

    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        isTracking = savedInstanceState.getBoolean("isTracking", false)
        updateStartStopButton()

        // Save the bundle for deferred restoration
        savedStateBundle = savedInstanceState

        startTime = savedInstanceState.getLong("startTime", 0L)
        lastCheckpointTime = savedInstanceState.getLong("lastCheckpointTime", 0L)
        lastWaypointTime = savedInstanceState.getLong("lastWaypointTime", 0L)
        if (isTracking) {
            elapsedTimeHandler.post(updateElapsedTimeRunnable)
        }

        // Restore tracking data
        totalDistance = savedInstanceState.getFloat("totalDistance", 0f)
        checkpointDistance = savedInstanceState.getFloat("checkpointDistance", 0f)
        waypointDistance = savedInstanceState.getFloat("waypointDistance", 0f)

        // Restore direct distances
        checkpointDirectDistance = savedInstanceState.getFloat("checkpointDirectDistance", 0f)
        waypointDirectDistance = savedInstanceState.getFloat("waypointDirectDistance", 0f)

        // Restore pace data
        totalPace = savedInstanceState.getDouble("totalPace", 0.0)
        checkpointPace = savedInstanceState.getDouble("checkpointPace", 0.0)
        waypointPace = savedInstanceState.getDouble("waypointPace", 0.0)

        // Update the UI with restored data
        updateUI()
    }


    private fun updateUI() {
        updateDistanceTextView()
        updateCheckpointDistanceTextView()
        updateWaypointDistanceTextView()
        updatePaceTextView()
        updateCheckpointPaceTextView()
        updateWaypointPaceTextView()
    }

    private fun restoreMapState(savedInstanceState: Bundle) {
        // Restore checkpoint markers
        val checkpointData = savedInstanceState.getSerializable("checkpointMarkers") as? Array<Array<Any>>
        checkpointData?.forEach { data ->
            val lat = data[0] as Double
            val lng = data[1] as Double
            val timestamp = data[2] as Long
            val position = LatLng(lat, lng)
            val marker = mMap.addMarker(MarkerOptions().position(position).title("Checkpoint"))
            marker?.let { checkpointMarkers.add(Pair(it, timestamp)) }
        }

        // Restore waypoint data
        val waypointLat = savedInstanceState.getDouble("currentWaypointLat", 0.0)
        val waypointLng = savedInstanceState.getDouble("currentWaypointLng", 0.0)
        if (waypointLat != 0.0 && waypointLng != 0.0) {
            val position = LatLng(waypointLat, waypointLng)
            currentWaypointMarker = mMap.addMarker(
                MarkerOptions().position(position).title("Waypoint")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            )
            lastWaypointTime = savedInstanceState.getLong("currentWaypointTime", 0L)
        }

        // Restore polylines
        val savedTrack = savedInstanceState.getStringArrayList("track")
        savedTrack?.forEach { segment ->
            val coordinates = segment.split(",")
            if (coordinates.size == 4) {
                val start = LatLng(coordinates[0].toDouble(), coordinates[1].toDouble())
                val end = LatLng(coordinates[2].toDouble(), coordinates[3].toDouble())
                val polyline = mMap.addPolyline(PolylineOptions().add(start, end).color(Color.BLUE).width(5f))
                polylines.add(polyline)
            }
        }
    }



    private fun updateStartStopButton() {
        if (isTracking) {
            startStopButton.setImageResource(R.drawable.pause)
        } else {
            startStopButton.setImageResource(R.drawable.play)
        }
    }



    private fun updateTrackingState(isTracking: Boolean) {
        val intent = Intent(C.ACTION_UPDATE_TRACKING).apply {
            putExtra(C.EXTRA_IS_TRACKING, isTracking)
            putExtra("startTime", startTime)
            putExtra("interval", updateInterval)
        }
        val result = sendBroadcast(intent)
    }

    private fun startTracking() {
        isTracking = true
        startTime = System.currentTimeMillis()
        sharedPreferences.edit().putLong("START_TIME", startTime).apply()
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

        Log.d("FUCK", "Cleared existing map data. Polylines: ${polylines.size}, Checkpoints: ${checkpointMarkers.size}")


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
        sessionIDBackend = "Empty"
    }

    private fun saveSessionData() {
        val savedTrack = track.joinToString(";")

        val starttime = sharedPreferences.getLong("START_TIME", 0L)

        val distanceInKm = totalDistance / 1000  // Convert to kilometers
        val elapsedTime = System.currentTimeMillis() - starttime  // Time in milliseconds

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
        val sessionId = dbHelper.insertSession(savedTrack, distanceInKm.toFloat(), elapsedTime, pace, sessionIDBackend)

        dbHelper.updateSessionIdForTrackPointsAndCheckpoints(-1, sessionId)

//        for ((marker, timestamp) in checkpointMarkers) {
//            Log.d("DB", "$marker, ${timestamp} saved in database")
//            if (marker.position.latitude != 0.0 && marker.position.longitude != 0.0) {
//                val latitude = marker.position.latitude
//                val longitude = marker.position.longitude
//                dbHelper.insertCheckpoint(sessionId, latitude, longitude, timestamp)
//            }
//        }
//
//        for (point in trackPoints) {
//            val latitude = point[0] as Double
//            val longitude = point[1] as Double
//            val timestamp = point[2] as Long
//
//            if (latitude != 0.0 || longitude != 0.0) {
//                dbHelper.insertTrackPoint(sessionId, latitude, longitude, timestamp)
//            }
//        }

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

    fun changeUpdateInterval(newInterval: Long) {
        updateInterval = newInterval
        Log.d(TAG, "Interval is now: $updateInterval")
    }

    fun insertTrackpoint(lat: Double, long: Double, currentTime: Long) {
        if (lat == 0.0 && long == 0.0) return
        val dbHelper = SessionsDatabaseHelper(this)
        dbHelper.insertTrackPoint(null, lat, long, currentTime)
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
                    checkpointDirectDistance = intent.getFloatExtra(C.LOCATION_UPDATE_ACTION_DISTANCE_CP_DIRECT, 0f)
                    waypointDirectDistance = intent.getFloatExtra(C.LOCATION_UPDATE_ACTION_DISTANCE_WP_DIRECT, 0f)
                    sessionIDBackend = intent.getStringExtra(C.BACKEND_ID_UPDATE).toString()

                    sharedPreferences.edit().putString("SESSION_ID", sessionIDBackend).apply()

                    sharedPreferences.edit().putFloat("DISTANCE_OVERALL", totalDistance).apply()


                    Log.d("PRC", "MAIN: broadcast recieved: distance = $totalDistance, " +
                        "CP distance = $checkpointDistance, CP distance direct = $checkpointDirectDistance")

                    insertTrackpoint(currentLat, currentLong, currentTime)

                    val newPolyline = intent.getSerializableExtra("newPolyline") as? Pair<LatLng, LatLng>

                    updateDistanceTextView()
                    updatePaceTextView()
                    updateCheckpointDistanceTextView()
                    updateCheckpointPaceTextView()
                    updateWaypointDistanceTextView()
                    updateWaypointPaceTextView()
                    if (isCentered) {
                        Log.d("CENTER", "is centered: $isCentered")
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
