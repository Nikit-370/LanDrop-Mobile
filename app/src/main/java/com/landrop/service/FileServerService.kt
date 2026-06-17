package com.landrop.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.landrop.MainActivity
import com.landrop.server.FileSharingManager

class FileServerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isWakeLockAcquired = false
    private var wifiLock: WifiManager.WifiLock? = null
    private var isWifiLockAcquired = false

    companion object {
        private const val CHANNEL_ID = "landrop_sharing_channel"
        private const val NOTIFICATION_ID = 4591
        
        fun startService(context: Context) {
            val intent = Intent(context, FileServerService::class.java)
            try {
                // Since the user triggers this from the active UI, startService is 100% allowed,
                // avoids ForegroundServiceStartNotAllowedException, and operates with maximum reliability.
                context.startService(intent)
            } catch (e: Exception) {
                Log.w("FileServerService", "Standard startService was not allowed, falling back to startForegroundService", e)
                try {
                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                } catch (ex: Exception) {
                    Log.e("FileServerService", "Failed starting service entirely in all fallback modes", ex)
                }
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FileServerService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Call startForeground in onCreate safely. If the OS denies foreground promotion (e.g. background restriction),
        // we log the warning but CONTINUE executing as a standard active active background service, preventing an immediate crash.
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d("FileServerService", "Successfully promoted service to foreground.")
        } catch (e: Exception) {
            Log.w("FileServerService", "Failed to startForeground in onCreate. Continuing in regular background mode natively.", e)
        }

        // Acquire partial wake lock to keep CPU running when screen is locked
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LANDrop:FileServerServiceWakeLock").apply {
                setReferenceCounted(false)
                acquire() // Indefinite acquire while server is active
            }
            isWakeLockAcquired = true
            Log.d("FileServerService", "Power WakeLock acquired successfully.")
        } catch (e: Exception) {
            isWakeLockAcquired = false
            Log.e("FileServerService", "Failed to acquire WakeLock", e)
        }

        // Acquire WifiLock to prevent Wi-Fi from dropping to low power sleep state
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LANDrop:FileServerServiceWifiLock")
            } else {
                @Suppress("DEPRECATION")
                wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "LANDrop:FileServerServiceWifiLock")
            }.apply {
                setReferenceCounted(false)
                acquire()
            }
            isWifiLockAcquired = true
            Log.d("FileServerService", "WifiLock acquired successfully.")
        } catch (e: Exception) {
            isWifiLockAcquired = false
            Log.e("FileServerService", "Failed to acquire WifiLock", e)
        }

        // Ensure server starts up
        try {
            FileSharingManager.startServer(applicationContext)
            Log.d("FileServerService", "Server started securely on FGS launch.")
        } catch (e: Exception) {
            Log.e("FileServerService", "Failed to boot sever in service start", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        try {
            // Safely verify or confirm foreground state inside start command too
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        NOTIFICATION_ID, 
                        notification, 
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (e: Exception) {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            } else {
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
        } catch (e: Exception) {
            Log.e("FileServerService", "Failed to update notification or foreground state in onStartCommand", e)
        }

        // START_STICKY tells the system to recreate the service if it gets terminated due to memory pressure
        return START_STICKY
    }

    override fun onDestroy() {
        // Halt server execution
        try {
            FileSharingManager.stopServer()
            Log.d("FileServerService", "Server stopped on service onDestroy.")
        } catch (e: Exception) {
            Log.e("FileServerService", "Error stopping server in onDestroy", e)
        }

        // Safely release system locks
        try {
            if (isWakeLockAcquired) {
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }
            }
            wakeLock = null
            isWakeLockAcquired = false
            Log.d("FileServerService", "WakeLock released.")
        } catch (e: Exception) {
            Log.e("FileServerService", "Error releasing WakeLock", e)
        }

        try {
            if (isWifiLockAcquired) {
                wifiLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }
            }
            wifiLock = null
            isWifiLockAcquired = false
            Log.d("FileServerService", "WifiLock released.")
        } catch (e: Exception) {
            Log.e("FileServerService", "Error releasing WifiLock", e)
        }

        super.onDestroy()
        Log.d("FileServerService", "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun buildNotification(): Notification {
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Server is online at http://${FileSharingManager.getLocalIpAddress()}:${FileSharingManager.serverPort.value}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LANDrop Local File Sharing")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Local Sharing Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the local web server active for file transfers"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
