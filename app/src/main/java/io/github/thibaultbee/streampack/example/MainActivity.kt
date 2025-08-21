
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Example: Start streaming service
        findViewById<android.view.View>(R.id.startStreamButton)?.setOnClickListener {
            val intent = Intent(this, io.github.thibaultbee.streampack.example.bitrate.BitrateForegroundService::class.java)
            intent.action = "START_STREAM"
            startForegroundService(intent)
        }

        // Example: Stop streaming service
        findViewById<android.view.View>(R.id.stopStreamButton)?.setOnClickListener {
            val intent = Intent(this, io.github.thibaultbee.streampack.example.bitrate.BitrateForegroundService::class.java)
            intent.action = "STOP_STREAM"
            startForegroundService(intent)
        }

        // Register receiver for bitrate stats
        bitrateStatsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                if (intent?.action == "io.github.thibaultbee.streampack.example.BITRATE_STATS") {
                    val currentBitrate = intent.getIntExtra("currentBitrate", 0)
                    val targetBitrate = intent.getIntExtra("targetBitrate", 0)
                    val rtt = intent.getIntExtra("rtt", 0)
                    val bufferSize = intent.getIntExtra("bufferSize", 0)
                    val throughput = intent.getDoubleExtra("throughput", 0.0)
                    val packetLoss = intent.getDoubleExtra("packetLoss", 0.0)
                    val adjustmentReason = intent.getStringExtra("adjustmentReason") ?: ""

                    // Example: update a TextView with stats (replace with your BitrateMonitorView logic)
                    val statsText = "Bitrate: $currentBitrate\nTarget: $targetBitrate\nRTT: $rtt\nBuffer: $bufferSize\nThroughput: $throughput\nLoss: $packetLoss\nReason: $adjustmentReason"
                    findViewById<TextView?>(R.id.bitrateStatsTextView)?.text = statsText
                }
            }
        }
        registerReceiver(bitrateStatsReceiver, IntentFilter("io.github.thibaultbee.streampack.example.BITRATE_STATS"))
    }

    override fun onDestroy() {
        super.onDestroy()
        bitrateStatsReceiver?.let { unregisterReceiver(it) }
    }
}