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
import io.github.thibaultbee.streampack.core.interfaces.setCameraId
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.defaultCameraId
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
    private var streamer: SingleStreamer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.d("BitrateService", "onCreate called")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Initialize streamer and adaptive bitrate manager
        streamer = SingleStreamer(this, withAudio = true, withVideo = true)
        // Headless camera: do not bind to any preview Surface
        serviceScope.launch {
            streamer?.setCameraId(defaultCameraId)
        }
        // Do NOT call setStreamerView or bind preview
        adaptiveBitrateManager = AdaptiveBitrateManager()
        adaptiveBitrateManager?.initialize(streamer!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BitrateService", "onStartCommand called")
        val action = intent?.action
        val streamUrl = intent?.getStringExtra("STREAM_URL") ?: "srt://localhost:8890?streamid=publish:mystream"
        when (action) {
            "START_STREAM" -> {
                serviceScope.launch {
                    try {
                        streamer?.setConfig(
                            io.github.thibaultbee.streampack.core.streamers.single.AudioConfig(
                                mimeType = android.media.MediaFormat.MIMETYPE_AUDIO_AAC,
                                sampleRate = 44100,
                                channelConfig = android.media.AudioFormat.CHANNEL_IN_STEREO
                            ),
                            io.github.thibaultbee.streampack.core.streamers.single.VideoConfig(
                                mimeType = android.media.MediaFormat.MIMETYPE_VIDEO_AVC,
                                resolution = android.util.Size(1280, 720),
                                fps = 25
                            )
                        )
                        streamer?.setAudioSource(io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MicrophoneSourceFactory())
                        // Camera already set, just start stream (headless)
                        streamer?.startStream()
                        adaptiveBitrateManager?.start()
                        Log.d("BitrateService", "Streaming started in foreground service")
                    } catch (e: Exception) {
                        Log.e("BitrateService", "Error starting stream: ${e.message}", e)
                    }
                }
            }
            "STOP_STREAM" -> {
                serviceScope.launch {
                    try {
                        adaptiveBitrateManager?.stop()
                        streamer?.stopStream()
                        streamer?.release()
                        Log.d("BitrateService", "Streaming stopped in foreground service")
                    } catch (e: Exception) {
                        Log.e("BitrateService", "Error stopping stream: ${e.message}", e)
                    }
                }
            }
            else -> {
                // Default: just start bitrate monitoring if needed
                serviceScope.launch {
                    try {
                        adaptiveBitrateManager?.start()
                        Log.d("BitrateService", "Bitrate monitoring started")
                    } catch (e: Exception) {
                        Log.e("BitrateService", "Error starting bitrate monitoring: ${e.message}", e)
                    }
                }
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
                streamer?.stopStream()
                streamer?.release()
                Log.d("BitrateService", "Streaming and bitrate monitoring stopped and resources released")
            } catch (e: Exception) {
                Log.e("BitrateService", "Error stopping stream/bitrate monitoring: ${e.message}", e)
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
