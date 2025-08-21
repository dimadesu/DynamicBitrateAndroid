
package io.github.thibaultbee.streampack.example

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
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
    }
}