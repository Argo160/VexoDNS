package com.example.vexodns.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import com.example.vexodns.MainActivity
import com.example.vexodns.R
import kotlin.concurrent.thread

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null

    companion object {
        const val TAG = "DnsVpnService"
        const val ACTION_CONNECT = "com.example.vexodns.CONNECT"
        const val ACTION_DISCONNECT = "com.example.vexodns.DISCONNECT"
    }

    private val notificationId = 1
    private val notificationChannelId = "VexoDNS_VPN_Channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val dnsIp = intent.getStringExtra("DNS_IP")
                if (dnsIp != null) {
                    startVpn(dnsIp)
                } else {
                    stopVpn()
                }
            }
            ACTION_DISCONNECT -> {
                stopVpn()
            }
        }
        return START_STICKY
    }

    private fun startVpn(dnsIp: String) {
        if (vpnThread?.isAlive == true) {
            return // VPN is already running
        }

        vpnThread = thread {
            try {
                val builder = Builder()
                builder.addAddress("10.0.0.2", 24)
                builder.addDnsServer(dnsIp)

                vpnInterface = builder.establish()
                Log.i(TAG, "VPN interface established. DNS set to: $dnsIp")

                createNotificationChannel()
                val notificationIntent = Intent(this, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Build the notification
                val notificationBuilder = NotificationCompat.Builder(this, notificationChannelId)
                    .setContentTitle("VexoDNS Connected")
                    .setContentText("DNS is set to: $dnsIp")
                    .setContentIntent(pendingIntent)
                    // Use a guaranteed system icon first to ensure it works
                    .setSmallIcon(R.drawable.ic_notification)


                startForeground(notificationId, notificationBuilder.build())

                while (Thread.interrupted().not()) {
                    Thread.sleep(1000)
                }

            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.i(TAG, "VPN thread interrupted.")
            } catch (e: Exception) {
                Log.e(TAG, "Error in VPN thread", e)
            }
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        vpnThread?.interrupt()
        vpnThread = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "VexoDNS VPN Service"
            val descriptionText = "Notification channel for VexoDNS VPN service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(notificationChannelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}