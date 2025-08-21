package io.github.thibaultbee.streampack.example.bitrate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.thibaultbee.streampack.example.R
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class BitrateForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "BitrateMonitorChannel"
        const val NOTIFICATION_ID = 1001
    }

    private var adaptiveBitrateManager: AdaptiveBitrateManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.d("BitrateService", "onCreate called")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

    // Only initialize adaptive bitrate manager, streamer is managed by Activity
    adaptiveBitrateManager = AdaptiveBitrateManager()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BitrateService", "onStartCommand called")
        // Service only starts bitrate monitoring, streaming is controlled by Activity
        serviceScope.launch {
            try {
                adaptiveBitrateManager?.start()
                Log.d("BitrateService", "Bitrate monitoring started")
            } catch (e: Exception) {
                Log.e("BitrateService", "Error starting bitrate monitoring: ${e.message}", e)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("BitrateService", "onDestroy called")
        serviceScope.launch {
            try {
                adaptiveBitrateManager?.stop()
                adaptiveBitrateManager?.release()
                Log.d("BitrateService", "Bitrate monitoring stopped and resources released")
            } catch (e: Exception) {
                Log.e("BitrateService", "Error stopping bitrate monitoring: ${e.message}", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bitrate Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bitrate Monitor Running")
            .setContentText("Streaming and monitoring in background")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }
}
