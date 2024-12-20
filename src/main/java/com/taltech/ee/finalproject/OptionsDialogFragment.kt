package com.taltech.ee.finalproject

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment

class OptionsDialogFragment : DialogFragment() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_options, container, false)

        // Initialize shared preferences
        sharedPreferences = requireActivity().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

        // Find buttons
        val orientationButton = view.findViewById<Button>(R.id.orientation_button)
        val centeredMapButton = view.findViewById<Button>(R.id.centered_map_button)
        val satelliteViewButton = view.findViewById<Button>(R.id.satellite_view_button)

        // Get saved values from preferences and set the initial text
        val isNorthUp = sharedPreferences.getBoolean("isNorthUp", true)
        val isCentered = sharedPreferences.getBoolean("isCentered", false)
        val isSatelliteViewButton = sharedPreferences.getBoolean("isSatelliteView", false)

        Log.d("DEBUG", "Loaded preferences: isNorthUp=$isNorthUp, isCentered=$isCentered")



        orientationButton.text = if (isNorthUp) "Direction-Up" else "North-Up"
        centeredMapButton.text = if (isCentered) "Stop Keeping Map Centered" else "Keep map constantly centered"
        satelliteViewButton.text = if (isSatelliteViewButton) "Normal view" else "Satellite view"

        // Set button click listeners
        orientationButton.setOnClickListener {
            val currentOrientation = sharedPreferences.getBoolean("isNorthUp", true)
            sharedPreferences.edit().putBoolean("isNorthUp", !currentOrientation).apply()
            orientationButton.text = if (currentOrientation) "North-Up" else "Direction-Up"
            // Send a broadcast or callback to MainActivity to update map orientation
            (activity as MainActivity).updateOrientation(!currentOrientation)
        }

        centeredMapButton.setOnClickListener {
            val currentCentered = sharedPreferences.getBoolean("isCentered", false)
            sharedPreferences.edit().putBoolean("isCentered", !currentCentered).apply()
            centeredMapButton.text = if (currentCentered) "Keep map constantly centered" else "Stop Keeping Map Centered"
            (activity as MainActivity).toggleMapCentering(!currentCentered)
        }

        satelliteViewButton.setOnClickListener {
            val currentSatelliteView = sharedPreferences.getBoolean("isSatelliteView", true)
            val newSatelliteView = !currentSatelliteView
            sharedPreferences.edit().putBoolean("isSatelliteView", newSatelliteView).apply()
            satelliteViewButton.text = if (newSatelliteView) "Normal view" else "Satellite view"

            (activity as MainActivity).toggleSatelliteView(newSatelliteView)
        }

        val savedSessionsButton = view.findViewById<Button>(R.id.saved_sessions_button)
        savedSessionsButton.setOnClickListener {
            val intent = Intent(requireContext(), SavedSessionsActivity::class.java)
            startActivity(intent)
        }



        return view
    }
}
