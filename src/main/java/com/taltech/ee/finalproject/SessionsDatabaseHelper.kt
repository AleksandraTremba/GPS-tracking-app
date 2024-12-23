package com.taltech.ee.finalproject

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SessionsDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "sessions.db"
        private const val DATABASE_VERSION = 2

        // Table name and column names
        const val TABLE_NAME = "sessions"
        const val COLUMN_ID = "id"
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

    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
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
                FOREIGN KEY($COLUMN_SESSION_ID) REFERENCES $TABLE_NAME($COLUMN_ID)
            );
        """
        db.execSQL(createCheckpointsTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHECKPOINTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertSession(track: String, distance: Float, time: Long, pace: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TRACK, track)
            put(COLUMN_DISTANCE, distance)
            put(COLUMN_TIME, time)
            put(COLUMN_PACE, pace)
        }
        val sessionId = db.insert(TABLE_NAME, null, values)
        db.close()
        return sessionId
    }


    fun insertCheckpoint(sessionId: Long, latitude: Double, longitude: Double) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SESSION_ID, sessionId)
            put(COLUMN_LATITUDE, latitude)
            put(COLUMN_LONGITUDE, longitude)
        }
        db.insert(TABLE_CHECKPOINTS, null, values)
        db.close()
    }

    @SuppressLint("Range")
    fun getCheckpointsForSession(sessionId: Long): List<Checkpoint> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CHECKPOINTS,
            arrayOf(COLUMN_CHECKPOINT_ID, COLUMN_LATITUDE, COLUMN_LONGITUDE),
            "$COLUMN_SESSION_ID = ?",
            arrayOf(sessionId.toString()),
            null, null, "$COLUMN_CHECKPOINT_ID DESC"
        )

        val checkpoints = mutableListOf<Checkpoint>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndex(COLUMN_CHECKPOINT_ID))
            val latitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_LATITUDE))
            val longitude = cursor.getDouble(cursor.getColumnIndex(COLUMN_LONGITUDE))
            checkpoints.add(Checkpoint(id, sessionId, latitude, longitude))
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

        val query = "SELECT * FROM $TABLE_NAME WHERE $COLUMN_ID = ?"
        val cursor: Cursor = db.rawQuery(query, arrayOf(sessionId.toString()))

        var session: Session? = null

        if (cursor.moveToFirst()) {
            val id = cursor.getInt(cursor.getColumnIndex(COLUMN_ID))
            val track = cursor.getString(cursor.getColumnIndex(COLUMN_TRACK))
            val distance = cursor.getFloat(cursor.getColumnIndex(COLUMN_DISTANCE))
            val time = cursor.getInt(cursor.getColumnIndex(COLUMN_TIME))
            val pace = cursor.getString(cursor.getColumnIndex(COLUMN_PACE))

            session = Session(id, track, distance, time, pace)
        }

        cursor.close()
        db.close()

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
}