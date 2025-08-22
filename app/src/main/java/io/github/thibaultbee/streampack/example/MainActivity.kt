
package io.github.thibaultbee.streampack.example

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity


import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private var bitrateStatsReceiver: BroadcastReceiver? = null

    private var currentBitrate: Int = 2000000 // Default value, update as needed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.view.View>(R.id.startStreamButton)?.setOnClickListener {
            val intent = Intent(this, io.github.thibaultbee.streampack.example.bitrate.BitrateForegroundService::class.java)
            intent.action = "START_STREAM"
            startForegroundService(intent)
        }

        findViewById<android.view.View>(R.id.stopStreamButton)?.setOnClickListener {
            val intent = Intent(this, io.github.thibaultbee.streampack.example.bitrate.BitrateForegroundService::class.java)
            intent.action = "STOP_STREAM"
            startForegroundService(intent)
        }

        val bitrateValueTextView = findViewById<TextView>(R.id.bitrateValueTextView)
        bitrateValueTextView?.text = "Bitrate: $currentBitrate"

        findViewById<android.view.View>(R.id.bitrateUpButton)?.setOnClickListener {
            currentBitrate += 250_000 // Increase by 250kbps
            bitrateValueTextView?.text = "Bitrate: $currentBitrate"
            sendBitrateChange(currentBitrate)
        }

        findViewById<android.view.View>(R.id.bitrateDownButton)?.setOnClickListener {
            currentBitrate = (currentBitrate - 250_000).coerceAtLeast(250_000)
            bitrateValueTextView?.text = "Bitrate: $currentBitrate"
            sendBitrateChange(currentBitrate)
        }

        // Register receiver for bitrate stats (if needed for future)
        bitrateStatsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                // No longer used, but kept for future stats display
            }
        }
        registerReceiver(bitrateStatsReceiver, IntentFilter("io.github.thibaultbee.streampack.example.BITRATE_STATS"))
    }

    private fun sendBitrateChange(newBitrate: Int) {
        val intent = Intent(this, io.github.thibaultbee.streampack.example.bitrate.BitrateForegroundService::class.java)
        intent.action = "SET_BITRATE"
        intent.putExtra("BITRATE", newBitrate)
        startForegroundService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        bitrateStatsReceiver?.let { unregisterReceiver(it) }
    }
}