package com.taltech.ee.finalproject

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng


class LocationService : Service() {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }


    // The desired intervals for location updates. Inexact. Updates may be more or less frequent.
    private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 2000
    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2

    private val broadcastReceiver = InnerBroadcastReceiver()
    private val broadcastReceiverIntentFilter: IntentFilter = IntentFilter()

    private val mLocationRequest: LocationRequest = LocationRequest()
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mLocationCallback: LocationCallback? = null

    // last received location
    private var currentLocation: Location? = null

    private var distanceOverallTotal = 0f
    private var paceOverall = 0.0
    private var locationStart: Location? = null

    private var distanceCPTotal = 0f
    private var paceCP = 0.0
    private var locationCP: Location? = null

    private var distanceWPTotal = 0f
    private var paceWP = 0.0
    private var locationWP: Location? = null

    private var previousLocation: Location? = null
    private var startTime = 0L
    private var isTracking = false

    private var lastCheckpointTime = 0L
    private var lastWaypointTime = 0L
    private var checkpoint = false
    private var waypoint = false



    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()
        Log.d("pace", "started service!")

        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_CP)
        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_WP)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION)
        broadcastReceiverIntentFilter.addAction(C.ACTION_UPDATE_TRACKING)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION_PACE_OVERALL)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION_PACE_CP)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION_PACE_WP)

        registerReceiver(broadcastReceiver, broadcastReceiverIntentFilter)
        Log.d("pace", "registered broadcast")


        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val location = locationResult.lastLocation
                if (location != null) {
                    Log.d(TAG, "Received location update: Lat=${location.latitude}, Lng=${location.longitude}")
                    onNewLocation(location)
                } else {
                    Log.w(TAG, "Location result is null")
                }
            }
        }

        getLastLocation()

        createLocationRequest()
        requestLocationUpdates()

    }

    fun requestLocationUpdates() {
        Log.i(TAG, "Requesting location updates")

        try {
            mLocationCallback?.let {
                mFusedLocationClient.requestLocationUpdates(
                    mLocationRequest,
                    it, Looper.myLooper()
                )
            }
        } catch (unlikely: SecurityException) {
            Log.e(
                TAG,
                "Lost location permission. Could not request updates. $unlikely"
            )
        }
    }

    private fun addCheckpoint() {
        currentLocation?.let { location ->
            val latLng = LatLng(location.latitude, location.longitude)
            checkpoint = true

            // Prepare checkpoint data
            val currentTime = System.currentTimeMillis()
            lastCheckpointTime = currentTime

            // Broadcast checkpoint info
            val checkpointIntent = Intent(C.NOTIFICATION_ACTION_CP).apply {
                putExtra("lat", latLng.latitude)
                putExtra("lng", latLng.longitude)
                putExtra("timestamp", currentTime)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(checkpointIntent)
            Log.d("PRC", "SERVICE: checkpoint added, current time: $lastCheckpointTime")

            locationCP = location
            distanceCPTotal = 0f
        } ?: Log.e(TAG, "Current location is null. Cannot create checkpoint.")
    }

    private fun addWaypoint() {
        currentLocation?.let { location ->
            val latLng = LatLng(location.latitude, location.longitude)
            waypoint = true

            val currentTime = System.currentTimeMillis()
            lastWaypointTime = currentTime

            // Broadcast waypoint info
            val waypointIntent = Intent(C.NOTIFICATION_ACTION_WP).apply {
                putExtra("lat", latLng.latitude)
                putExtra("lng", latLng.longitude)
                putExtra("timestamp", currentTime)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(waypointIntent)
            Log.d("PRC", "SERVICE: waypoint added, current time: $lastCheckpointTime")

            locationWP = location
            distanceWPTotal = 0f
        } ?: Log.e(TAG, "Current location is null. Cannot create waypoint.")
    }


    private fun onNewLocation(location: Location) {
        Log.i(TAG, "New location: $location")

        val maxAllowedDistance = 500.0f

        if (currentLocation != null) {
            val distance = location.distanceTo(currentLocation!!)
            Log.d(TAG, "Distance from previous location: $distance meters")

            if (distance > maxAllowedDistance) {
                Log.w(TAG, "Location is too far away (${distance}m). Ignoring update.")
                return
            }
        }

        if (currentLocation == null){
            locationStart = location
            locationCP = location
            locationWP = location
        } else {
            distanceOverallTotal += location.distanceTo(currentLocation!!)
            paceOverall = countPace(distanceOverallTotal, startTime)
            Log.d("PRC", "SERVICE: overall distance: $distanceOverallTotal")
            Log.d("PRC", "SERVICE: overall pace: $paceOverall")


            if (checkpoint) {
                distanceCPTotal += location.distanceTo(currentLocation!!)
                paceCP = countPace(distanceCPTotal, lastCheckpointTime)
                Log.d("PRC", "SERVICE: CP distance: $distanceCPTotal")
                Log.d("PRC", "SERVICE: CP pace: $paceCP")
            }

            if (waypoint) {
                distanceWPTotal += location.distanceTo(currentLocation!!)
                paceWP = countPace(distanceWPTotal, lastWaypointTime)
                Log.d("PRC", "SERVICE: WP distance: $distanceWPTotal")
                Log.d("PRC", "SERVICE: WP pace: $paceWP")
            }
        }

        previousLocation?.let { prevLoc ->
            val prevLatLng = LatLng(prevLoc.latitude, prevLoc.longitude)
            val currLatLng = LatLng(location.latitude, location.longitude)


            // save the location for calculations
            currentLocation = location

            showNotification()

            // broadcast new location to UI
            val intent = Intent(C.LOCATION_UPDATE_ACTION).apply {
                putExtra("newPolyline", Pair(
                    LatLng(previousLocation?.latitude ?: 0.0, previousLocation?.longitude ?: 0.0),
                    LatLng(location.latitude, location.longitude)
                ))
                putExtra(C.LOCATION_UPDATE_ACTION_LATITUDE, location.latitude)
                putExtra(C.LOCATION_UPDATE_ACTION_LONGITUDE, location.longitude)
                putExtra(C.LOCATION_UPDATE_ACTION_DISTANCE_OVERALL_TOTAL, distanceOverallTotal)
                putExtra(C.LOCATION_UPDATE_ACTION_DISTANCE_CP_TOTAL, distanceCPTotal)
                putExtra(C.LOCATION_UPDATE_ACTION_DISTANCE_WP_TOTAL, distanceWPTotal)
                putExtra(C.LOCATION_UPDATE_ACTION_PACE_OVERALL, paceOverall)
                putExtra(C.LOCATION_UPDATE_ACTION_PACE_CP, paceCP)
                putExtra(C.LOCATION_UPDATE_ACTION_PACE_WP, paceWP)
            }

            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            Log.d("PRC", "SERVICE: broadcast on new location sent to main")
            Log.d(TAG, "Broadcast LOCATION_UPDATE_ACTION sent")
        }

        previousLocation = location

    }

    private fun countPace(countDistance: Float, timestamp: Long): Double {
        val distance = countDistance / 1000.0
        Log.d("pace", "distance: $distance, start time: $startTime")
        val paceInSeconds = if (countDistance > 0) {
            val elapsedMillis = System.currentTimeMillis() - timestamp
            (elapsedMillis / 1000.0) / distance
        } else {
            0.0
        }
        return paceInSeconds
    }

    private fun createLocationRequest() {
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS)
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        mLocationRequest.setMaxWaitTime(UPDATE_INTERVAL_IN_MILLISECONDS)
    }


    private fun getLastLocation() {
        try {
            mFusedLocationClient.lastLocation
                .addOnCompleteListener { task -> if (task.isSuccessful) {
                    Log.w(TAG, "task successfull");
                    if (task.result != null){
                        onNewLocation(task.result!!)
                    }
                } else {

                    Log.w(TAG, "Failed to get location." + task.exception)
                }}
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission.$unlikely")
        }
    }


    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()

        //stop location updates
        mLocationCallback?.let { mFusedLocationClient.removeLocationUpdates(it) }

        // remove notifications
        NotificationManagerCompat.from(this).cancelAll()


        // don't forget to unregister brodcast receiver!!!!
        unregisterReceiver(broadcastReceiver)


        // broadcast stop to UI
        val intent = Intent(C.LOCATION_UPDATE_ACTION)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

    }

    override fun onLowMemory() {
        Log.d(TAG, "onLowMemory")
        super.onLowMemory()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        // set counters and locations to 0/null
        currentLocation = null
        locationStart = null
        locationCP = null
        locationWP = null

        distanceOverallTotal = 0f
        distanceCPTotal = 0f
        distanceWPTotal = 0f


        showNotification()

        return START_STICKY
        //return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind")
        TODO("not implemented")
    }

    override fun onRebind(intent: Intent?) {
        Log.d(TAG, "onRebind")
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        return super.onUnbind(intent)

    }

    @SuppressLint("ForegroundServiceType")
    fun showNotification(){
        val intentCp = Intent(C.NOTIFICATION_ACTION_CP)
        val intentWp = Intent(C.NOTIFICATION_ACTION_WP)

        val pendingIntentCp = PendingIntent.getBroadcast(this, 0, intentCp,
            PendingIntent.FLAG_IMMUTABLE)
        val pendingIntentWp = PendingIntent.getBroadcast(this, 0, intentWp,
            PendingIntent.FLAG_IMMUTABLE)

        val notifyview = RemoteViews(packageName, R.layout.notification_layout)

        notifyview.setOnClickPendingIntent(R.id.imageButtonCP, pendingIntentCp)
        notifyview.setOnClickPendingIntent(R.id.imageButtonWP, pendingIntentWp)

        val overallMinutes = (paceOverall / 60).toInt()
        val overallSeconds = (paceOverall % 60).toInt()

        val CPminutes = (paceCP / 60).toInt()
        val CPseconds = (paceCP % 60).toInt()

        val WPminutes = (paceWP / 60).toInt()
        val WPseconds = (paceWP % 60).toInt()

        Log.d("PRC", "SERVICE: notification distance: $distanceOverallTotal")

        notifyview.setTextViewText(R.id.overallDistance, "%.2f".format(distanceOverallTotal / 1000))
        notifyview.setTextViewText(R.id.overallPace, "%d:%02d".format(overallMinutes, overallSeconds))

        if (distanceCPTotal > 0) {
            Log.d("PRC", "SERVICE: CP distance: $distanceCPTotal")
            notifyview.setTextViewText(R.id.CPdistance, "%.2f".format(distanceCPTotal / 1000))
            notifyview.setTextViewText(R.id.CPpace, "%d:%02d".format(CPminutes, CPseconds))
        }

        if (distanceWPTotal > 0) {
            Log.d("PRC", "SERVICE: WP distance: $distanceWPTotal")
            notifyview.setTextViewText(R.id.WPdistance, "%.2f".format(distanceCPTotal / 1000))
            notifyview.setTextViewText(R.id.WPpace, "%d:%02d".format(WPminutes, WPseconds))
        }

        // construct and show notification
        val builder = NotificationCompat.Builder(applicationContext, C.NOTIFICATION_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContent(notifyview)

        builder.setContent(notifyview)

        // Super important, start as foreground service - ie android considers this as an active app. Need visual reminder - notification.
        // must be called within 5 secs after service starts.
        startForeground(C.NOTIFICATION_ID, builder.build())

    }

    private fun startTracking() {
        isTracking = true
    }

    private fun stopTracking() {
        isTracking = false
    }


    private inner class InnerBroadcastReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent!!.action?.let { Log.d(TAG, it) }
            when(intent!!.action){
                C.NOTIFICATION_ACTION_WP -> addWaypoint()
                C.NOTIFICATION_ACTION_CP -> {
                    addCheckpoint()
                }
                C.ACTION_UPDATE_TRACKING -> {
                    isTracking = intent.getBooleanExtra(C.EXTRA_IS_TRACKING, false)
                    startTime = intent.getLongExtra("startTime", startTime)
                    if (isTracking) {
                        startTracking()
                    } else {
                        stopTracking()
                    }
                } else -> Log.d("pace", "Unknown action: ${intent.action}")
            }
        }

    }

}