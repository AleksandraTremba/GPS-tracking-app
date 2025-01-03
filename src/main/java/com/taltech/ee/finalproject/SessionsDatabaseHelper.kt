package com.taltech.ee.finalproject

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionsDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "sessions.db"
        private const val DATABASE_VERSION = 7

        // Table name and column names
        const val TABLE_NAME = "sessions"
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_TRACK = "track"
        const val COLUMN_DISTANCE = "distance"
        const val COLUMN_TIME = "time"
        const val COLUMN_PACE = "pace"

        // Table name and column names for checkpoints
        const val TABLE_CHECKPOINTS = "checkpoints"
        const val COLUMN_CHECKPOINT_ID = "id"
        const val COLUMN_SESSION_ID = "session_id"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_TIMESTAMP = "timestamp"

        const val TABLE_TRACK_POINTS = "track_points"
        const val COLUMN_TRACK_ID = "id"
        const val COLUMN_TRACK_LATITUDE = "latitude"
        const val COLUMN_TRACK_LONGITUDE = "longitude"
        const val COLUMN_TRACK_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT,
                $COLUMN_TRACK TEXT,
                $COLUMN_DISTANCE REAL,
                $COLUMN_TIME INTEGER,
                $COLUMN_PACE TEXT
            );
        """
        db.execSQL(createTableQuery)

        val createCheckpointsTableQuery = """
            CREATE TABLE $TABLE_CHECKPOINTS (
                $COLUMN_CHECKPOINT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SESSION_ID INTEGER,
                $COLUMN_LATITUDE REAL,
                $COLUMN_LONGITUDE REAL,
                $COLUMN_TIMESTAMP INTEGER,
                FOREIGN KEY($COLUMN_SESSION_ID) REFERENCES $TABLE_NAME($COLUMN_ID)
            );
        """
        db.execSQL(createCheckpointsTableQuery)

        val createTrackPointsTableQuery = """
        CREATE TABLE $TABLE_TRACK_POINTS (
            $COLUMN_TRACK_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_SESSION_ID INTEGER,
            $COLUMN_TRACK_LATITUDE REAL,
            $COLUMN_TRACK_LONGITUDE REAL,
            $COLUMN_TRACK_TIMESTAMP INTEGER,
            FOREIGN KEY($COLUMN_SESSION_ID) REFERENCES $TABLE_NAME($COLUMN_ID)
        );
        """
        db.execSQL(createTrackPointsTableQuery)

    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHECKPOINTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRACK_POINTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertSession(track: String, distance: Float, time: Long, pace: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, "Session")
            put(COLUMN_TRACK, track)
            put(COLUMN_DISTANCE, distance)
            put(COLUMN_TIME, time)
            put(COLUMN_PACE, pace)
        }
        val sessionId = db.insert(TABLE_NAME, null, values)
        db.close()
        return sessionId
    }


    fun insertCheckpoint(sessionId: Long, latitude: Double, longitude: Double, timestamp: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SESSION_ID, sessionId)
            put(COLUMN_LATITUDE, latitude)
            put(COLUMN_LONGITUDE, longitude)
            put(COLUMN_TIMESTAMP, timestamp)
        }
        db.insert(TABLE_CHECKPOINTS, null, values)
        db.close()
    }

    fun insertTrackPoint(sessionId: Long, latitude: Double, longitude: Double, timestamp: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SESSION_ID, sessionId)
            put(COLUMN_TRACK_LATITUDE, latitude)
            put(COLUMN_TRACK_LONGITUDE, longitude)
            put(COLUMN_TRACK_TIMESTAMP, timestamp)
        }
        db.insert(TABLE_TRACK_POINTS, null, values)
        db.close()
    }

    @SuppressLint("Range")
    fun getTrackPointsForSession(sessionId: Long): List<TrackPoint> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TRACK_POINTS,
            arrayOf(COLUMN_TRACK_ID, COLUMN_TRACK_LATITUDE, COLUMN_TRACK_LONGITUDE, COLUMN_TRACK_TIMESTAMP),
            "$COLUMN_SESSION_ID = ?",
            arrayOf(sessionId.toString()),
            null, null, "$COLUMN_TRACK_ID ASC"
        )

        val trackPoints = mutableListOf<TrackPoint>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndex(COLUMN_TRACK_ID))
            val latitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_TRACK_LATITUDE))
            val longitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_TRACK_LONGITUDE))
            val timestamp = cursor.getLong(cursor.getColumnIndex(COLUMN_TRACK_TIMESTAMP))
            trackPoints.add(TrackPoint(id, sessionId, latitude, longitude, timestamp))
        }
        cursor.close()
        db.close()
        return trackPoints
    }



    @SuppressLint("Range")
    fun getCheckpointsForSession(sessionId: Long): List<Checkpoint> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CHECKPOINTS,
            arrayOf(COLUMN_CHECKPOINT_ID, COLUMN_LATITUDE, COLUMN_LONGITUDE, COLUMN_TIMESTAMP),  // Include timestamp
            "$COLUMN_SESSION_ID = ?",
            arrayOf(sessionId.toString()),
            null, null, "$COLUMN_CHECKPOINT_ID DESC"
        )

        val checkpoints = mutableListOf<Checkpoint>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndex(COLUMN_CHECKPOINT_ID))
            val latitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_LATITUDE))
            val longitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_LONGITUDE))
            val timestamp = cursor.getLong(cursor.getColumnIndex(COLUMN_TIMESTAMP))
            checkpoints.add(Checkpoint(id, sessionId, latitude, longitude, timestamp))
        }
        cursor.close()
        db.close()
        return checkpoints
    }


    fun getAllSessions(): Cursor {
        val db = readableDatabase
        return db.query(TABLE_NAME, null, null, null, null, null, "$COLUMN_ID DESC")
    }

    @SuppressLint("Range")
    fun getSessionById(sessionId: Long): Session? {
        val db = this.readableDatabase

        // Prepare query
        val query = "SELECT * FROM $TABLE_NAME WHERE $COLUMN_ID = ?"
        val cursor: Cursor = db.rawQuery(query, arrayOf(sessionId.toString()))

        var session: Session? = null

        // Check if the cursor contains any results
        if (cursor.moveToFirst()) {
            // Access the columns with proper indices
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)) // Use getColumnIndexOrThrow for safety
            val track = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TRACK))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME))
            val distance = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_DISTANCE))
            val time = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TIME))
            val pace = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PACE))

            session = Session(id, track, name, distance, time, pace)
        }

        cursor.close() // Always close the cursor to avoid memory leaks
        db.close() // Close the database

        return session
    }


    fun clearDatabase() {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_CHECKPOINTS")
        db.execSQL("DELETE FROM $TABLE_NAME")
        db.close()
    }


    fun deleteAllSessions() {
        val db = writableDatabase
        db.delete(TABLE_NAME, null, null)
    }

    fun updateSessionName(sessionId: Long, newSessionName: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, newSessionName)  // Correct column used
        }
        db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(sessionId.toString())) // Ensure sessionId is passed
        db.close()
    }


    // Delete session from the database
    fun deleteSession(sessionId: Long) {
        val db = writableDatabase
        db.delete("sessions", "id = ?", arrayOf(sessionId.toString()))
    }

}