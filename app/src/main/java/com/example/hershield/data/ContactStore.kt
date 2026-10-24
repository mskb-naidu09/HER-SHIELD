package com.example.hershield.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ContactStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("hershield", Context.MODE_PRIVATE)

    var userName: String
        get() = prefs.getString("userName", "") ?: ""
        set(value) {
            prefs.edit().putString("userName", value).apply()
        }

    fun load(): List<Contact> {
        val arr = JSONArray(prefs.getString("contacts", "[]") ?: "[]")

        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)

            Contact(
                name = o.getString("name"),
                phone = o.getString("phone"),
                latitude = o.getDouble("lat"),
                longitude = o.getDouble("lon"),
                priority = o.getInt("priority")
            )
        }
    }

    fun save(list: List<Contact>) {
        val arr = JSONArray()

        list.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("phone", it.phone)
                    .put("lat", it.latitude)
                    .put("lon", it.longitude)
                    .put("priority", it.priority)
            )
        }

        prefs.edit().putString("contacts", arr.toString()).apply()
    }
}