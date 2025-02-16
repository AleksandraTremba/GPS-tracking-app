package com.taltech.ee.finalproject.location

class C {
    companion object {
        val NOTIFICATION_CHANNEL = "default_channel"
        val NOTIFICATION_ACTION_WP = "com.taltech.ee.finalproject.wp"
        val NOTIFICATION_ACTION_CP = "com.taltech.ee.finalproject.cp"
        val ACTION_UPDATE_TRACKING = "com.taltech.ee.finalproject.UPDATE_TRACKING"
        val EXTRA_IS_TRACKING = "isTracking"

        val LOCATION_UPDATE_ACTION = "com.taltech.ee.finalproject.location_update"

        val LOCATION_UPDATE_ACTION_LATITUDE = "com.taltech.ee.finalproject.location_update.latitude"
        val LOCATION_UPDATE_ACTION_LONGITUDE = "com.taltech.ee.finalproject.location_update.longitude"
        const val LOCATION_UPDATE_ACTION_DISTANCE_OVERALL_TOTAL = "com.taltech.ee.finalproject.location_update.distanceOverallTotal"
        const val LOCATION_UPDATE_ACTION_DISTANCE_CP_TOTAL = "com.taltech.ee.finalproject.location_update.distanceCPTotal"
        const val LOCATION_UPDATE_ACTION_DISTANCE_WP_TOTAL = "com.taltech.ee.finalproject.location_update.distanceWPTotal"

        const val LOCATION_UPDATE_ACTION_DISTANCE_CP_DIRECT = "com.taltech.ee.finalproject.location_update.distanceCPDirect"
        const val LOCATION_UPDATE_ACTION_DISTANCE_WP_DIRECT = "com.taltech.ee.finalproject.location_update.distanceWPDirect"

        const val LOCATION_UPDATE_ACTION_PACE_OVERALL = "com.taltech.ee.finalproject.location_update.paceOverall"
        const val LOCATION_UPDATE_ACTION_PACE_CP = "com.taltech.ee.finalproject.location_update.paceCP"
        const val LOCATION_UPDATE_ACTION_PACE_WP = "com.taltech.ee.finalproject.location_update.paceWP"

        const val BACKEND_ID_UPDATE = "com.taltech.ee.finalproject.id_update"


        const val LOCATION_UPDATE_ACTION_POLYLINE = "com.taltech.ee.finalproject.location_update.polyline"

        val NOTIFICATION_ID = 4321
        val REQUEST_PERMISSIONS_REQUEST_CODE = 34;

    }
}