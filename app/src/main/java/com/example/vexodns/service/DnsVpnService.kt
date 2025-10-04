package com.example.vexodns.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
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
        const val BROADCAST_VPN_STATE = "com.example.vexodns.VPN_STATE"

        @Volatile
        private var isServiceRunning = false

        fun isVpnRunning(): Boolean = isServiceRunning
    }

    private val notificationId = 1
    private val notificationChannelId = "VexoDNS_VPN_Channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_CONNECT -> {
                val dnsIp = intent.getStringExtra("DNS_IP")
                Log.d(TAG, "Connect action received with DNS IP: $dnsIp")
                if (dnsIp != null) {
                    startVpn(dnsIp)
                } else {
                    Log.e(TAG, "DNS IP is null, stopping VPN")
                    stopVpn()
                }
            }
            ACTION_DISCONNECT -> {
                Log.d(TAG, "Disconnect action received")
                stopVpn()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }
        return START_STICKY
    }

    private fun startVpn(dnsIp: String) {
        Log.d(TAG, "startVpn called with DNS: $dnsIp")

        if (vpnThread?.isAlive == true) {
            Log.i(TAG, "VPN thread is already running")
            return
        }

        vpnThread = thread {
            try {
                Log.d(TAG, "Creating VPN builder")
                val builder = Builder()
                builder.addAddress("10.0.0.2", 24)
                builder.addDnsServer(dnsIp)

                Log.d(TAG, "Establishing VPN interface")
                vpnInterface = builder.establish()

                if (vpnInterface == null) {
                    Log.e(TAG, "Failed to establish VPN interface - vpnInterface is null")
                    isServiceRunning = false
                    return@thread
                }

                Log.i(TAG, "VPN interface established successfully. DNS set to: $dnsIp")
                isServiceRunning = true

                // Create notification channel
                createNotificationChannel()

                Log.d(TAG, "Creating notification")
                val notificationIntent = Intent(this, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val stopIntent = Intent(this, DnsVpnService::class.java).apply {
                    action = ACTION_DISCONNECT
                }
                val stopPendingIntent = PendingIntent.getService(
                    this, 1, stopIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(this, notificationChannelId)
                    .setContentTitle("VexoDNS Connected")
                    .setContentText("DNS is set to: $dnsIp")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentIntent(pendingIntent)
                    .addAction(0, getString(R.string.action_stop), stopPendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

                Log.d(TAG, "Starting foreground service")
                startForeground(notificationId, notification)
                Log.i(TAG, "Foreground service started successfully")

                // Keep thread alive
                while (Thread.interrupted().not()) {
                    Thread.sleep(1000)
                }

            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.i(TAG, "VPN thread interrupted")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in VPN thread", e)
                isServiceRunning = false
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Illegal state exception in VPN thread", e)
                isServiceRunning = false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in VPN thread", e)
                isServiceRunning = false
            }
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN")

        isServiceRunning = false

        val broadcastIntent = Intent(BROADCAST_VPN_STATE)
        sendBroadcast(broadcastIntent)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }

        vpnThread?.interrupt()
        vpnThread = null

        try {
            vpnInterface?.close()
            Log.d(TAG, "VPN interface closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null

        stopSelf()
        Log.i(TAG, "VPN stopped completely")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel")
            val name = "VexoDNS VPN Service"
            val descriptionText = "Notification channel for VexoDNS VPN service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(notificationChannelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        stopVpn()
        super.onDestroy()
    }
}