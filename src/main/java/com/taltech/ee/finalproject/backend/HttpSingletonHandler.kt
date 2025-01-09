package com.taltech.ee.finalproject.backend

import android.content.Context
import android.text.TextUtils
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

class HttpSingletonHandler {
    companion object {
        private val TAG = HttpSingletonHandler::class.java.simpleName
        private var context: Context? = null

        private var instance: HttpSingletonHandler? = null

        @Synchronized
        fun getInstance(context: Context): HttpSingletonHandler {
            if (instance == null) {
                instance = HttpSingletonHandler(context)
            }
            return instance!!
        }
    }

    constructor(context: Context) {
        Companion.context = context
    }

    val requestQueue: RequestQueue? = null
        get() {
            if (field == null) {
                return Volley.newRequestQueue(context)
            }
            return field;
        }


    fun <T> addToRequestQueue(request: Request<T>, tag: String) {
        // kotlin does not have this syntax
        // xx = a == null ? "null" : a
        // instead we have this:
        request.tag = if (TextUtils.isEmpty(tag)) TAG else tag
        requestQueue?.add(request);
    }

    fun <T> addToRequestQueue(request: Request<T>) {
        request.tag = TAG
        requestQueue?.add(request);
    }

    fun cancelPendingRequests(tag: Any) {
        if (requestQueue != null) {
            requestQueue!!.cancelAll(tag)
        }
    }

}
