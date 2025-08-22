
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
    private var srtStatsReceiver: BroadcastReceiver? = null

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

        // Register receiver for bitrate stats and update UI
        bitrateStatsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                if (intent?.action == "io.github.thibaultbee.streampack.example.BITRATE_STATS") {
                    val actualBitrate = intent.getIntExtra("currentBitrate", currentBitrate)
                    currentBitrate = actualBitrate
                    bitrateValueTextView?.text = "Bitrate: $actualBitrate"
                }
            }
        }
        registerReceiver(bitrateStatsReceiver, IntentFilter("io.github.thibaultbee.streampack.example.BITRATE_STATS"))

        // Register receiver for SRT stats and display in UI, filtering out zero-value fields
        srtStatsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                if (intent?.action == "io.github.thibaultbee.streampack.example.SRT_STATS") {
                    val stats = intent.getStringExtra("srtStats")
                    android.util.Log.d("SRT_STATS_RAW", stats ?: "No SRT stats")
                    // Parse the actual stats string and render only main values
                    val rendered = stats?.let {
                        val regex = Regex("""([a-zA-Z0-9]+)=([^,)]+)""")
                        val map = regex.findAll(it).associate { m -> m.groupValues[1] to m.groupValues[2] }
                        listOf(
                            map["msRTT"]?.let { v -> "RTT: $v ms" },
                            map["mbpsSendRate"]?.let { v -> "Send Rate: $v Mbps" },
                            map["pktRecvACKTotal"]?.let { v -> "ACKs: $v" },
                            map["pktRetransTotal"]?.let { v -> "Retransmits: $v" },
                            map["pktSndBuf"]?.let { v -> "Send Buffer: $v" },
                            map["pktFlightSize"]?.let { v -> "Flight Size: $v" }
                        ).filterNotNull().joinToString("\n").ifEmpty { "No SRT stats" }
                    } ?: "No SRT stats"
                    findViewById<TextView?>(R.id.srtStatsTextView)?.text = rendered
                }
            }
        }
        registerReceiver(srtStatsReceiver, IntentFilter("io.github.thibaultbee.streampack.example.SRT_STATS"))
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
        srtStatsReceiver?.let { unregisterReceiver(it) }
    }
}