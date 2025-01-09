package com.taltech.ee.finalproject.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.taltech.ee.finalproject.R
import com.taltech.ee.finalproject.database.SessionsDatabaseHelper

class SavedSessionsActivity : AppCompatActivity() {
    private lateinit var sessionsListView: ListView
    private val sessionIdList = mutableListOf<Long>()


    @SuppressLint("Range")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_sessions)

        sessionsListView = findViewById(R.id.sessions_list_view)

        val dbHelper = SessionsDatabaseHelper(this)
        val cursor = dbHelper.getAllSessions()

        val sessionList = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val sessionId = cursor.getLong(cursor.getColumnIndex(SessionsDatabaseHelper.COLUMN_ID))
            val name = cursor.getString(cursor.getColumnIndex(SessionsDatabaseHelper.COLUMN_NAME)) // Get session name
            val track = cursor.getString(cursor.getColumnIndex(SessionsDatabaseHelper.COLUMN_TRACK))
            val distance = cursor.getFloat(cursor.getColumnIndex(SessionsDatabaseHelper.COLUMN_DISTANCE))
            val time = cursor.getLong(cursor.getColumnIndex(SessionsDatabaseHelper.COLUMN_TIME))
            val pace = cursor.getString(cursor.getColumnIndex(SessionsDatabaseHelper.COLUMN_PACE))

            val hours = (time / (1000 * 60 * 60)) % 24
            val minutes = (time / (1000 * 60)) % 60
            val seconds = (time / 1000) % 60

            val displayName = if (name != "Session") name else "Session $sessionId"

            sessionList.add("$displayName \n\nDistance: ${"%.2f".format(distance)} km | Time: ${"%02d:%02d:%02d".format(hours, minutes, seconds)} | Pace: $pace\n")
            sessionIdList.add(sessionId)
        }

        sessionsListView.setOnItemClickListener { _, _, position, _ ->
            val sessionId = sessionIdList[position]
            val intent = Intent(this, SessionMapActivity::class.java).apply {
                putExtra("SESSION_ID", sessionId)
            }
            startActivity(intent)
        }

        var deleteButton = findViewById<Button>(R.id.delete_button)
        deleteButton.setOnClickListener{
            dbHelper.clearDatabase()
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, sessionList)
        sessionsListView.adapter = adapter

    }
}