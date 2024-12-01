package com.taltech.ee.finalproject

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mLocationCallback: LocationCallback? = null
    private val TAG = "MapsActivity"
    private var mPolyline: Polyline? = null
    private var isTracking = false

    private var startLocation: Location? = null

    // Variables for time tracking
    private var startTime: Long = 0
    private var elapsedTimeHandler = Handler(Looper.getMainLooper())

    // The desired intervals for location updates
    private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 2000
    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2

    // UI elements
    private lateinit var startStopButton: Button
    private lateinit var timeElapsedTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize FusedLocationClient
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize the Start/Stop button and time text view
        startStopButton = findViewById(R.id.start_stop_button)
        timeElapsedTextView = findViewById(R.id.time_elapsed_start)

        startStopButton.setOnClickListener {
            if (isTracking) {
                stopTracking()
            } else {
                startTracking()
            }
        }

        // Create LocationRequest
        createLocationRequest()

        // Set up location callback to receive location updates
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { onNewLocation(it) }
            }
        }

        // Check and request permissions if necessary
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001 // Request code for location permission
            )
        }

        startStopButton.setOnClickListener {
            if (startStopButton.text == "Stop") {
                showPopupWindow()
            } else {
                startTracking()
            }
        }

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



    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.isCompassEnabled = false

        // Move camera to default location (example: Sydney) when map is ready
//        val location = LatLng(59.39487859716227, 24.67152136890696)
        val location = LatLng(0.0,0.0)
        mMap.addMarker(MarkerOptions().position(location).title("Marker in TalTech"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(location))

        // Start location updates when the map is ready
        startLocationUpdates()
    }

    // Create location request for regular updates
    private fun createLocationRequest() {
        val mLocationRequest = LocationRequest.create().apply {
            interval = UPDATE_INTERVAL_IN_MILLISECONDS
            fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    // Start location updates
    private fun startLocationUpdates() {
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            mFusedLocationClient.requestLocationUpdates(
                LocationRequest.create(),
                mLocationCallback!!,
                mainLooper
            )
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission. Could not request updates.", unlikely)
        }
    }

    // Stop location updates when activity is paused or destroyed
    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        if (isTracking) {
            startLocationUpdates()
        }
    }

    private fun stopLocationUpdates() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback!!)
    }

    private fun onNewLocation(location: Location) {
        Log.i(TAG, "New location: $location")

        val latLng = LatLng(location.latitude, location.longitude)

        if (mPolyline == null) {
            // Initialize the polyline if it doesn't exist
            val polylineOptions = PolylineOptions().color(Color.BLUE).width(5f)
            mPolyline = mMap.addPolyline(polylineOptions)
        }

        val points = mPolyline?.points
        points?.add(latLng)
        mPolyline?.points = points!!

        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))

        mMap.addMarker(MarkerOptions().position(latLng).title("You are here"))
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        isTracking = true
        startStopButton.text = "Stop"
        startTime = System.currentTimeMillis()
        elapsedTimeHandler.postDelayed(updateElapsedTimeRunnable, 1000)

        mFusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                startLocation = location
                Log.i(TAG, "Starting location: $startLocation")
            }
        }


        startLocationUpdates()
    }

    private fun stopTracking() {
        isTracking = false
        startStopButton.text = "Start"
        elapsedTimeHandler.removeCallbacks(updateElapsedTimeRunnable)
        stopLocationUpdates()
    }

    private val updateElapsedTimeRunnable = object : Runnable {
        override fun run() {
            if (isTracking) {
                val elapsedMillis = System.currentTimeMillis() - startTime
                val seconds = (elapsedMillis / 1000) % 60
                val minutes = (elapsedMillis / (1000 * 60)) % 60
                val hours = (elapsedMillis / (1000 * 60 * 60)) % 24

                val timeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                timeElapsedTextView.text = timeFormatted

                elapsedTimeHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Permission Denied!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
