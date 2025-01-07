package com.taltech.ee.finalproject

import android.content.Context
import com.android.volley.toolbox.JsonObjectRequest
import org.json.JSONObject
import com.android.volley.toolbox.Volley
import java.time.Instant
import java.time.format.DateTimeFormatter
import android.location.Location
import android.util.Log


object BackendHandler {

    fun isUserLoggedIn(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences("SportMapPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("token", null)
        return !token.isNullOrEmpty()
    }

    fun startSession(
        context: Context,
        sessionName: String,
        sessionDescription: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://sportmap.akaver.com/api/v1.0/GpsSessions"
        val sharedPreferences = context.getSharedPreferences("SportMapPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("token", null) ?: return onError("No JWT token found")

        val payload = JSONObject().apply {
            put("name", sessionName)
            put("description", sessionDescription)
            put(  "gpsSessionTypeId", "00000000-0000-0000-0000-000000000003")
            put("recordedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
            put("minSpeed", 420)
            put("maxSpeed", 600)
        }

        val request = object : JsonObjectRequest(Method.POST, url, payload,
            { response ->
                Log.e("BCND", "Successfully created session.")
                val sessionId = response.getString("id")
                onSuccess(sessionId)
            },
            { error ->
                Log.e("BCND", "Error creating session: ${error.networkResponse?.statusCode}, ${String(error.networkResponse?.data ?: ByteArray(0))}")
                onError(error.message ?: "Error creating session")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return mutableMapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            }
        }

        Volley.newRequestQueue(context).add(request)
    }

    fun postLocation(
        context: Context,
        payload: JSONObject,
        currentSessionIdBackend: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://sportmap.akaver.com/api/v1/GpsLocations/$currentSessionIdBackend"
        val sharedPreferences = context.getSharedPreferences("SportMapPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("token", null) ?: return onError("No JWT token found")

        val request = object : JsonObjectRequest(
            Method.POST, url, payload,
            { response ->
                Log.d("BCND", "Location posted: $response")
                onSuccess()
            },
            { error ->
                Log.e("BCND", "Error posting location: ${error.networkResponse?.statusCode}, ${String(error.networkResponse?.data ?: ByteArray(0))}")
            }
        ) {
            override fun getHeaders(): Map<String, String> {
                return mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            }
        }

        Volley.newRequestQueue(context).add(request)
    }

    fun fetchLocationTypes(
        context: Context,
        onSuccess: (List<Pair<String, String>>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://sportmap.akaver.com/api/v1.0/GpsLocationTypes"
        val sharedPreferences = context.getSharedPreferences("SportMapPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("token", null) ?: return onError("No JWT token found")

        val request = object : JsonObjectRequest(
            Method.GET, url, null,
            { response ->
                val types = response.getJSONArray("results").let { array ->
                    (0 until array.length()).map { index ->
                        val obj = array.getJSONObject(index)
                        obj.getString("id") to obj.getString("name")
                    }
                }
                onSuccess(types)
            },
            { error ->
                Log.e("BCND", "Error fetching location types: ${error}")
                onError(error.message ?: "Unknown error")
            }
        ) {
            override fun getHeaders(): Map<String, String> {
                return mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            }
        }

        Volley.newRequestQueue(context).add(request)
    }

    fun fetchGpsSessionTypes(
        context: Context,
        onSuccess: (List<Map<String, Any>>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://sportmap.akaver.com/api/v1/GpsSessionTypes"
        val sharedPreferences = context.getSharedPreferences("SportMapPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("token", null) ?: return onError("No JWT token found")

        val request = object : JsonObjectRequest(
            Method.GET, url, null,
            { response ->
                try {
                    val sessionTypes = mutableListOf<Map<String, Any>>()
                    val jsonArray = response
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i.toString())
                        val sessionType = mapOf(
                            "id" to obj.getString("id"),
                            "name" to obj.getString("name"),
                            "description" to obj.getString("description"),
                            "paceMin" to obj.getInt("paceMin"),
                            "paceMax" to obj.getInt("paceMax")
                        )
                        sessionTypes.add(sessionType)
                    }
                    onSuccess(sessionTypes)
                } catch (e: Exception) {
                    Log.e("BCND", "Error parsing GPS session types: ${e.message}")
                    onError("Error parsing data")
                }
            },
            { error ->
                Log.e("BCND", "Error fetching GPS session types: ${error.message}")
                onError(error.message ?: "Unknown error")
            }
        ) {
            override fun getHeaders(): Map<String, String> {
                return mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            }
        }

        Volley.newRequestQueue(context).add(request)
    }

}