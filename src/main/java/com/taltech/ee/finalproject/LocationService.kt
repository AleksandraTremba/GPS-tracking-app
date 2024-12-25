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
import com.taltech.ee.finalproject.MainActivity.Companion


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

//    private var distanceOverallTotal = 0f
//    private var paceOverall = 0f
//    private var locationStart: Location? = null
//
//    private var distanceCPTotal = 0f
//    private var paceCP = 0f
//    private var locationCP: Location? = null
//
//    private var distanceWPTotal = 0f
//    private var paceWP = 0f
//    private var locationWP: Location? = null

    private var isTracking = false
    private var totalDistance = 0f
    private var lastUpdateTime = 0L
    private var previousLocation: Location? = null
    private val polylines = mutableListOf<Pair<LatLng, LatLng>>()

    private val checkpointMarkers = mutableListOf<LatLng>()
    private val waypointMarkers = mutableListOf<LatLng>()



    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()

        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_CP)
        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_WP)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION)
        broadcastReceiverIntentFilter.addAction(C.ACTION_UPDATE_TRACKING)


        registerReceiver(broadcastReceiver, broadcastReceiverIntentFilter)


        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let {
                    Log.d(TAG, "Location update: $it")
                    onNewLocation(it) }
            }
        }

        getLastLocation()

        createLocationRequest()
        requestLocationUpdates()
        Log.d(TAG, "LocationService created")

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

    private fun onNewLocation(location: Location) {
        if (!isTracking) return

        val currentTime = System.currentTimeMillis()

        previousLocation?.let { prevLoc ->
            val prevLatLng = LatLng(prevLoc.latitude, prevLoc.longitude)
            val currLatLng = LatLng(location.latitude, location.longitude)

            // Calculate distance and update totals
            val distance = prevLoc.distanceTo(location)
            totalDistance += distance

            // Add polyline
            polylines.add(prevLatLng to currLatLng)

            // Broadcast the new location update
            val intent = Intent(C.LOCATION_UPDATE_ACTION).apply {
                putExtra("totalDistance", totalDistance)
                putExtra("newPolyline", Pair(prevLatLng, currLatLng))
            }
            sendBroadcast(intent)
        }

        previousLocation = location
        lastUpdateTime = currentTime
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

        // Stop location updates
        mLocationCallback?.let { mFusedLocationClient.removeLocationUpdates(it) }

        // Remove notifications
        NotificationManagerCompat.from(this).cancel(C.NOTIFICATION_ID)

        // Unregister broadcast receiver
        unregisterReceiver(broadcastReceiver)

        // Stop the foreground service
        stopForeground(true)

        // Broadcast stop to UI
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
//        locationStart = null
//        locationCP = null
//        locationWP = null
//
//        distanceOverallTotal = 0f
//        distanceCPTotal = 0f
//        distanceWPTotal = 0f


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

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved")
        super.onTaskRemoved(rootIntent)

        // Stop the service
        stopSelf()
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
//        Log.d("NOTIF", "showNotification: overall distance $distanceOverallTotal, " +
//                "cp distance $distanceCPTotal, wp distance $distanceWPTotal")
//
//
//        notifyview.setTextViewText(R.id.overallDistance, "%.2f".format(distanceOverallTotal))
//        notifyview.setTextViewText(R.id.overallPace, "%.2f".format(paceOverall))
//
//        notifyview.setTextViewText(R.id.WPdistance, "%.2f".format(distanceWPTotal))
//        notifyview.setTextViewText(R.id.WPpace, "%.2f".format(paceWP))
//
//        notifyview.setTextViewText(R.id.CPdistance, "%.2f".format(distanceCPTotal))
//        notifyview.setTextViewText(R.id.CPpace, "%.2f".format(paceCP))

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
            when (intent.action) {
                C.ACTION_UPDATE_TRACKING -> {
                    // Update the isTracking flag
                    isTracking = intent.getBooleanExtra(C.EXTRA_IS_TRACKING, false)
                    Log.d(TAG, "isTracking updated to: $isTracking")
                }
                C.LOCATION_UPDATE_ACTION -> {
//                    // Update distances and paces from broadcast
//                    distanceOverallTotal = intent.getFloatExtra("totalDistance", 0f)
//                    distanceCPTotal = intent.getFloatExtra("checkpointDistance", 0f)
//                    distanceWPTotal = intent.getFloatExtra("waypointDistance", 0f)
//                    paceOverall = intent.getFloatExtra("totalPace", 0f)
//                    paceCP = intent.getFloatExtra("checkpointPace", 0f)
//                    paceWP = intent.getFloatExtra("waypointPace", 0f)
//
//                    // Refresh notification
//                    showNotification()
//                }
//                C.NOTIFICATION_ACTION_WP -> {
//                    locationWP = currentLocation
//                    distanceWPTotal = 0f
//                    showNotification()
//                }
//                C.NOTIFICATION_ACTION_CP -> {
//                    locationCP = currentLocation
//                    distanceCPTotal = 0f
//                    showNotification()
                }
            }
        }
    }


//    private fun LocationService.location() = locationCP

}