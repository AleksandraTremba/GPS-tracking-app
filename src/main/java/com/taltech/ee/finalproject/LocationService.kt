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

    private var distanceOverallDirect = 0f
    private var distanceOverallTotal = 0f
    private var locationStart: Location? = null

    private var distanceCPDirect = 0f
    private var distanceCPTotal = 0f
    private var locationCP: Location? = null

    private var distanceWPDirect = 0f
    private var distanceWPTotal = 0f
    private var locationWP: Location? = null



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
                locationResult.lastLocation?.let { onNewLocation(it) }
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
        Log.i(TAG, "New location: $location")
        if (currentLocation == null){
            locationStart = location
            locationCP = location
            locationWP = location
        } else {
            distanceOverallDirect = locationStart?.let { location.distanceTo(it) }!!
            distanceOverallTotal += location.distanceTo(currentLocation!!)

            distanceCPDirect = location()?.let { location.distanceTo(it) }!!
            distanceCPTotal += location.distanceTo(currentLocation!!)

            distanceWPDirect = locationWP?.let { location.distanceTo(it) }!!
            distanceWPTotal += location.distanceTo(currentLocation!!)
        }
        // save the location for calculations
        currentLocation = location

        showNotification()

        // broadcast new location to UI
        val intent = Intent(C.LOCATION_UPDATE_ACTION)
        intent.putExtra(C.LOCATION_UPDATE_ACTION_LATITUDE, location.latitude)
        intent.putExtra(C.LOCATION_UPDATE_ACTION_LONGITUDE, location.longitude)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

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
        locationStart = null
        locationCP = null
        locationWP = null

        distanceOverallDirect = 0f
        distanceOverallTotal = 0f
        distanceCPDirect = 0f
        distanceCPTotal = 0f
        distanceWPDirect = 0f
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


        notifyview.setTextViewText(R.id.textViewOverallDirect, "%.2f".format(distanceOverallDirect))
        notifyview.setTextViewText(R.id.textViewOverallTotal, "%.2f".format(distanceOverallTotal))

        notifyview.setTextViewText(R.id.textViewWPDirect, "%.2f".format(distanceWPDirect))
        notifyview.setTextViewText(R.id.textViewWPTotal, "%.2f".format(distanceWPTotal))

        notifyview.setTextViewText(R.id.textViewCPDirect, "%.2f".format(distanceCPDirect))
        notifyview.setTextViewText(R.id.textViewCPTotal, "%.2f".format(distanceCPTotal))

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
                C.NOTIFICATION_ACTION_WP -> {
                    locationWP = currentLocation
                    distanceWPDirect = 0f
                    distanceWPTotal = 0f
                    showNotification()
                }
                C.NOTIFICATION_ACTION_CP -> {
                    locationCP = currentLocation
                    distanceCPDirect = 0f
                    distanceCPTotal = 0f
                    showNotification()
                }
            }
        }

    }

    private fun LocationService.location() = locationCP

}