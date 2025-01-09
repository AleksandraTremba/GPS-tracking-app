package com.taltech.ee.finalproject.activities

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.taltech.ee.finalproject.R


class InfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        // Retrieve the list of messages passed from displayInfo
        val messageList = intent.getStringArrayListExtra("MESSAGE_LIST")

        // Display the messages in a ListView
        val infoListView = findViewById<ListView>(R.id.info_list_view)
        if (messageList != null && messageList.isNotEmpty()) {
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, messageList)
            infoListView.adapter = adapter
        } else {
            val emptyMessage = arrayListOf("No information available.")
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, emptyMessage)
            infoListView.adapter = adapter
        }
    }
}
