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
    private var locationStart: Location? = null

    private var distanceCPTotal = 0f
    private var locationCP: Location? = null

    private var distanceWPTotal = 0f
    private var locationWP: Location? = null

    private var previousLocation: Location? = null



    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()

        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_CP)
        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_WP)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION)


        registerReceiver(broadcastReceiver, broadcastReceiverIntentFilter)


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

            // Prepare checkpoint data
            val currentTime = System.currentTimeMillis()

            // Broadcast checkpoint info
            val checkpointIntent = Intent(C.NOTIFICATION_ACTION_CP).apply {
                putExtra("lat", latLng.latitude)
                putExtra("lng", latLng.longitude)
                putExtra("timestamp", currentTime)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(checkpointIntent)

            locationCP = location
            distanceCPTotal = 0f
        } ?: Log.e(TAG, "Current location is null. Cannot create checkpoint.")
    }

    private fun addWaypoint() {
        currentLocation?.let { location ->
            val latLng = LatLng(location.latitude, location.longitude)

            // Broadcast waypoint info
            val waypointIntent = Intent(C.NOTIFICATION_ACTION_WP).apply {
                putExtra("lat", latLng.latitude)
                putExtra("lng", latLng.longitude)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(waypointIntent)

            locationWP = location
            distanceWPTotal = 0f
        } ?: Log.e(TAG, "Current location is null. Cannot create waypoint.")
    }


    private fun onNewLocation(location: Location) {
        Log.i(TAG, "New location: $location")
        if (currentLocation == null){
            locationStart = location
            locationCP = location
            locationWP = location
        } else {
            distanceOverallTotal += location.distanceTo(currentLocation!!)

            distanceCPTotal += location.distanceTo(currentLocation!!)

            distanceWPTotal += location.distanceTo(currentLocation!!)
        }

        previousLocation?.let { prevLoc ->
            val prevLatLng = LatLng(prevLoc.latitude, prevLoc.longitude)
            val currLatLng = LatLng(location.latitude, location.longitude)


            // save the location for calculations
            currentLocation = location

            showNotification()

            // broadcast new location to UI
            val intent = Intent(C.LOCATION_UPDATE_ACTION).apply {
                putExtra("newPolyline", Pair(prevLatLng, currLatLng))
            }
            intent.putExtra(C.LOCATION_UPDATE_ACTION_LATITUDE, location.latitude)
            intent.putExtra(C.LOCATION_UPDATE_ACTION_LONGITUDE, location.longitude)
            intent.putExtra(C.LOCATION_UPDATE_ACTION_DISTANCE_OVERALL_TOTAL, distanceOverallTotal)
            intent.putExtra(C.LOCATION_UPDATE_ACTION_DISTANCE_CP_TOTAL, distanceCPTotal)
            intent.putExtra(C.LOCATION_UPDATE_ACTION_DISTANCE_WP_TOTAL, distanceWPTotal)

            Log.d(TAG, "Broadcasting LOCATION_UPDATE_ACTION: Lat=${location.latitude}, Lng=${location.longitude}")

            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            Log.d(TAG, "Broadcast LOCATION_UPDATE_ACTION sent")
        }

        previousLocation = location

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


//        notifyview.setTextViewText(R.id.textViewOverallDirect, "%.2f".format(distanceOverallDirect))
//        notifyview.setTextViewText(R.id.textViewOverallTotal, "%.2f".format(distanceOverallTotal))
//
//        notifyview.setTextViewText(R.id.textViewWPDirect, "%.2f".format(distanceWPDirect))
//        notifyview.setTextViewText(R.id.textViewWPTotal, "%.2f".format(distanceWPTotal))
//
//        notifyview.setTextViewText(R.id.textViewCPDirect, "%.2f".format(distanceCPDirect))
//        notifyview.setTextViewText(R.id.textViewCPTotal, "%.2f".format(distanceCPTotal))

        // construct and show notification
        var builder = NotificationCompat.Builder(applicationContext, C.NOTIFICATION_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        builder.setContent(notifyview)

        // Super important, start as foreground service - ie android considers this as an active app. Need visual reminder - notification.
        // must be called within 5 secs after service starts.
        startForeground(C.NOTIFICATION_ID, builder.build())

    }


    private inner class InnerBroadcastReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent!!.action?.let { Log.d(TAG, it) }
            when(intent!!.action){
                C.NOTIFICATION_ACTION_WP -> addWaypoint()
                C.NOTIFICATION_ACTION_CP -> addCheckpoint()
            }
        }

    }

}