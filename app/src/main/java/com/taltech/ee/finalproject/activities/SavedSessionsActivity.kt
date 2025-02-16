package com.taltech.ee.finalproject.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.taltech.ee.finalproject.R
import com.taltech.ee.finalproject.database.SessionsDatabaseHelper

class SavedSessionsActivity : AppCompatActivity() {
    private lateinit var sessionsListView: ListView
    private val sessionIdList = mutableListOf<Long>()
    private lateinit var updateSessionButton: Button


    @SuppressLint("Range")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_sessions)

        sessionsListView = findViewById(R.id.sessions_list_view)
        updateSessionButton = findViewById(R.id.update_session_button)


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

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, sessionList)
        sessionsListView.adapter = adapter

        updateSessionButton.setOnClickListener {
            showUpdateSessionDialog(dbHelper)
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun showUpdateSessionDialog(dbHelper: SessionsDatabaseHelper) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Update Session")

        val dialogLayout = layoutInflater.inflate(R.layout.dialog_update_session, null)
        val sessionIdInput = dialogLayout.findViewById<EditText>(R.id.session_id_input)
        val distanceInput = dialogLayout.findViewById<EditText>(R.id.distance_input)
        val timeInput = dialogLayout.findViewById<EditText>(R.id.time_input)

        builder.setView(dialogLayout)
        builder.setPositiveButton("Update") { _, _ ->
            val sessionId = sessionIdInput.text.toString().toLongOrNull()
            val newDistance = distanceInput.text.toString().toFloatOrNull()
            val newTime = timeInput.text.toString().toLongOrNull()

            if (sessionId == null || newDistance == null || newTime == null) {
                Toast.makeText(this, "Invalid input!", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            if (dbHelper.getSessionById(sessionId) != null) {
                dbHelper.updateSession(sessionId, newDistance, newTime)
                Toast.makeText(this, "Session updated!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Session ID not found!", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
}