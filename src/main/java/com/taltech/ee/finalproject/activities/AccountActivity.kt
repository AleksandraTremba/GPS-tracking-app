package com.taltech.ee.finalproject.activities

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import com.android.volley.Request
import com.android.volley.Response
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import com.taltech.ee.finalproject.backend.HttpSingletonHandler
import com.taltech.ee.finalproject.R

class AccountActivity : AppCompatActivity() {
    private val BASE_URL = "https://sportmap.akaver.com/api/v1.0/"
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var httpHandler: HttpSingletonHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("SportMapPrefs", Context.MODE_PRIVATE)
        httpHandler = HttpSingletonHandler.getInstance(this)

        // Check if user is logged in
        val token = sharedPreferences.getString("token", null)
        val username = sharedPreferences.getString("username", null)

        if (token != null && username != null) {
            showLoggedInView(username)
        } else {
            showPreviewView()
        }
    }

    private fun showPreviewView() {
        setContentView(R.layout.activity_account)
        val registerButton: Button = findViewById(R.id.registerButton)
        val loginButton: Button = findViewById(R.id.loginButton)

        registerButton.setOnClickListener { showRegistrationView() }
        loginButton.setOnClickListener { showLoginView() }
    }

    private fun showLoginView() {
        setContentView(R.layout.account_log_in)

        val emailEditText: EditText = findViewById(R.id.emailEditText)
        val passwordEditText: EditText = findViewById(R.id.passwordEditText)
        val loginButton: Button = findViewById(R.id.loginButton)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginAccount(email, password)
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun showLoggedInView(username: String) {
        setContentView(R.layout.activity_logged_in)

        val helloTextView: TextView = findViewById(R.id.helloTextView)
        val logoutButton: Button = findViewById(R.id.logoutButton)
        val catImageView: ImageButton = findViewById(R.id.catImageView)

        helloTextView.text = "Hello, $username!"

        loadRandomCat(catImageView)

        logoutButton.setOnClickListener {
            sharedPreferences.edit().clear().apply()
            showPreviewView()
        }

        catImageView.setOnClickListener {
            loadRandomCat(catImageView)
        }
    }

    private fun loadRandomCat(imageButton: ImageButton) {
        val randomQuery = System.currentTimeMillis()
        val url = "https://cataas.com/cat?random=$randomQuery"

        Picasso.get()
            .load(url)
            .networkPolicy(NetworkPolicy.NO_CACHE)
            .into(imageButton, object : Callback {
                override fun onSuccess() {
                    // Successfully loaded the image
                    Log.d("CAT_LOAD_SUCCESS", "Cat image loaded successfully: $url")
                }

                override fun onError(e: Exception?) {
                    Log.e("CAT_LOAD_ERROR", "Failed to load cat from URL: $url", e)
                    Toast.makeText(
                        this@AccountActivity,
                        "Unable to load a cat image.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }





    private fun showRegistrationView() {
        setContentView(R.layout.activity_register)

        val emailEditText: EditText = findViewById(R.id.emailEditText)
        val passwordEditText: EditText = findViewById(R.id.passwordEditText)
        val firstNameEditText: EditText = findViewById(R.id.firstNameEditText)
        val lastNameEditText: EditText = findViewById(R.id.lastNameEditText)
        val registerButton: Button = findViewById(R.id.registerButton)

        registerButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            val firstName = firstNameEditText.text.toString()
            val lastName = lastNameEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty() && firstName.isNotEmpty() && lastName.isNotEmpty()) {
                createAccount(email, password, firstName, lastName)
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createAccount(email: String, password: String, firstName: String, lastName: String) {
        val url = BASE_URL + "account/register"
        val requestQueue = Volley.newRequestQueue(this)

        val jsonPayload = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("firstName", firstName)
            put("lastName", lastName)
        }

        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                val responseJson = JSONObject(response)
                val token = responseJson.getString("token")
                val username = responseJson.getString("status").substringAfter("User ").substringBefore(" created")

                sharedPreferences.edit().apply {
                    putString("token", token)
                    putString("username", username)
                    apply()
                }

                showLoggedInView(username)
            },
            Response.ErrorListener { error ->
                error.networkResponse?.let {
                    val responseData = String(it.data, Charsets.UTF_8)
                    try {
                        val jsonResponse = JSONObject(responseData)

                        if (jsonResponse.has("errors")) {
                            val errors = jsonResponse.getJSONObject("errors")
                            val firstErrorKey = errors.keys().next()
                            val firstErrorMessage = errors.getJSONArray(firstErrorKey).getString(0)

                            Toast.makeText(this, "Registration failed: $firstErrorMessage", Toast.LENGTH_LONG).show()
                        } else if (jsonResponse.has("messages")) {
                            val messages = jsonResponse.getJSONArray("messages")
                            val firstErrorMessage = messages.getString(0)

                            Toast.makeText(this, "Registration failed: $firstErrorMessage", Toast.LENGTH_LONG).show()
                        }

                    } catch (e: Exception) {
                        Toast.makeText(this, "An error occurred while processing the response", Toast.LENGTH_LONG).show()
                        Log.d("ERR", "Failed to parse error response: $e")
                    }
                }
            }
        ) {
            override fun getBody(): ByteArray {
                return jsonPayload.toString().toByteArray()
            }

            override fun getHeaders(): MutableMap<String, String> {
                return mutableMapOf("Content-Type" to "application/json")
            }
        }

        httpHandler.addToRequestQueue(stringRequest, "createAccount")

    }

    private fun loginAccount(email: String, password: String) {
        val url = BASE_URL + "account/login"
        val requestQueue = Volley.newRequestQueue(this)

        val jsonPayload = JSONObject().apply {
            put("email", email)
            put("password", password)
        }

        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { response ->
                val responseJson = JSONObject(response)
                val token = responseJson.getString("token")
                val username = responseJson.getString("status").substringAfter("User ").substringBefore(" logged")

                sharedPreferences.edit().apply {
                    putString("token", token)
                    putString("username", username)
                    apply()
                }

                showLoggedInView(username)
            },
            Response.ErrorListener { error ->
                error.networkResponse?.let {
                    val responseData = String(it.data, Charsets.UTF_8)
                    try {
                        val jsonResponse = JSONObject(responseData)

                        if (jsonResponse.has("errors")) {
                            val errors = jsonResponse.getJSONObject("errors")
                            val firstErrorKey = errors.keys().next()
                            val firstErrorMessage = errors.getJSONArray(firstErrorKey).getString(0)

                            Toast.makeText(this, "Registration failed: $firstErrorMessage", Toast.LENGTH_LONG).show()
                        } else if (jsonResponse.has("messages")) {
                            val messages = jsonResponse.getJSONArray("messages")
                            val firstErrorMessage = messages.getString(0)

                            Toast.makeText(this, "Registration failed: $firstErrorMessage", Toast.LENGTH_LONG).show()
                        }

                    } catch (e: Exception) {
                        Toast.makeText(this, "An error occurred while processing the response", Toast.LENGTH_LONG).show()
                        Log.d("ERR", "Failed to parse error response: $e")
                    }
                }
            }
        ) {
            override fun getBody(): ByteArray {
                return jsonPayload.toString().toByteArray()
            }

            override fun getHeaders(): MutableMap<String, String> {
                return mutableMapOf("Content-Type" to "application/json")
            }

        }

        httpHandler.addToRequestQueue(stringRequest, "loginAccount")
    }

}