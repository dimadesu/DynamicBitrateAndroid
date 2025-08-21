package io.github.thibaultbee.streampack.example.bitrate

import android.util.Log
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.streamers.dual.DualStreamer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Integrated bitrate manager that combines network monitoring with dynamic bitrate control
 * Applies the belacoder.c adaptive bitrate algorithm to StreamPack streamers
 */
class AdaptiveBitrateManager {
    // Cache last used video config for dynamic bitrate changes

    // Throttle bitrate changes: only allow every 5 seconds
    private var lastBitrateUpdateTime: Long = 0L
    // Cache last used video config for dynamic bitrate changes
    var lastVideoConfig: VideoConfig? = null
    
    companion object {
        private const val TAG = "AdaptiveBitrateManager"
    }
    
    // Components
    private val networkMonitor = NetworkStatsMonitor()
    private val bitrateController = DynamicBitrateController()
    
    // Streamer references
    private var singleStreamer: SingleStreamer? = null
    private var dualStreamer: DualStreamer? = null
    
    // Configuration
    private var isEnabled = false
    private var srtSocket: Any? = null
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State flows
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    // Bitrate state is now managed only by DynamicBitrateController
    private val _networkQuality = MutableStateFlow(NetworkStatsMonitor.ConnectionQuality.GOOD)
    val networkQuality: StateFlow<NetworkStatsMonitor.ConnectionQuality> = _networkQuality.asStateFlow()
    
    /**
     * Initialize the adaptive bitrate manager with a streamer
     */
    fun initialize(streamer: SingleStreamer) {
        this.singleStreamer = streamer
        this.dualStreamer = null
        // Initialize lastVideoConfig from streamer's current config if available
        val config = streamer.videoConfigFlow.value
        if (config != null) {
            lastVideoConfig = config
            Log.d(TAG, "lastVideoConfig initialized from streamer's config")
        } else {
            Log.w(TAG, "Streamer videoConfigFlow.value is null at initialization")
        }
        setupBitrateController()
        Log.d(TAG, "Initialized with SingleStreamer")
    }
    
    /**
     * Initialize the adaptive bitrate manager with a dual streamer
     */
    fun initialize(streamer: DualStreamer) {
        this.dualStreamer = streamer
        this.singleStreamer = null
        setupBitrateController()
        Log.d(TAG, "Initialized with DualStreamer")
    }
    
    /**
     * Configure the adaptive bitrate system
     */
    fun configure(
        minBitrate: Int = 300_000,    // 300 Kbps
        maxBitrate: Int = 6_000_000,  // 6 Mbps
        srtLatency: Int = 2000,       // 2 seconds
        enableSimulation: Boolean = false
    ) {
        bitrateController.updateConfiguration(minBitrate, maxBitrate, srtLatency)
        
    // ...existing code...
        
        Log.d(TAG, "Configured: min=${minBitrate/1000}kbps, max=${maxBitrate/1000}kbps, latency=${srtLatency}ms")
    }
    
    /**
     * Start adaptive bitrate control
     */
    fun start(srtSocket: Any? = null) {
        if (isEnabled) {
            Log.w(TAG, "Adaptive bitrate already started")
            return
        }

    // ...existing code...

        this.srtSocket = srtSocket
        isEnabled = true
        _isActive.value = true

        // Start network monitoring
        networkMonitor.startMonitoring(srtSocket)

        // Start bitrate controller
        bitrateController.startMonitoring()

        // Start processing network stats
        startNetworkStatsProcessing()

        Log.d(TAG, "Adaptive bitrate control started")
    }
    
    /**
     * Stop adaptive bitrate control
     */
    fun stop() {
        if (!isEnabled) {
            return
        }
        
        isEnabled = false
        _isActive.value = false
        
        networkMonitor.stopMonitoring()
        bitrateController.stopMonitoring()
        
        Log.d(TAG, "Adaptive bitrate control stopped")
    }
    
    // Simulation logic removed; updateNetworkConditions is no longer supported
    
    /**
     * Get current bitrate statistics
     */
    fun getBitrateState(): StateFlow<DynamicBitrateController.BitrateState> = bitrateController.bitrateState
    
    /**
     * Setup the bitrate controller callbacks
     */
    private fun setupBitrateController() {
        bitrateController.onBitrateChanged = { newBitrate ->
            scope.launch {
                applyBitrateToStreamer(newBitrate)
            }
        }
    }
    
    /**
     * Process network statistics and feed them to the bitrate controller
     */
    private fun startNetworkStatsProcessing() {
        scope.launch {
            networkMonitor.statsFlow.collect { srtStats ->
                // Convert SRT stats to bitrate controller format
                val networkStats = DynamicBitrateController.NetworkStats(
                    rtt = srtStats.msRTT,
                    bufferSize = srtStats.pktSndBuf,
                    throughput = srtStats.mbpsBandwidth,
                    packetLoss = srtStats.pktSndLoss.toDouble()
                )
                // If getConnectionQuality and getStatsSummary are not available, skip or replace with direct logging
                // Update network quality state (fallback: always GOOD)
                _networkQuality.value = NetworkStatsMonitor.ConnectionQuality.GOOD
                // Log network statistics periodically
                if (System.currentTimeMillis() % 1000 < 100) {
                    Log.v(TAG, "SRT Stats: RTT=${srtStats.msRTT}, Buf=${srtStats.pktSndBuf}, BW=${srtStats.mbpsBandwidth}, Loss=${srtStats.pktSndLoss}")
                }
            }
        }
    }
    
    /**
     * Apply the new bitrate to the active streamer
     */
    private suspend fun applyBitrateToStreamer(bitrate: Int) {
        val now = System.currentTimeMillis()
        if (now - lastBitrateUpdateTime < 5000) {
            Log.d(TAG, "Bitrate update throttled: only allowed every 5 seconds")
            return
        }
        lastBitrateUpdateTime = now
        try {
            when {
                singleStreamer != null -> {
                    // Update video config for single streamer
                    updateSingleStreamerBitrate(bitrate)
                }
                dualStreamer != null -> {
                    // Update video config for dual streamer
                    updateDualStreamerBitrate(bitrate)
                }
                else -> {
                    Log.w(TAG, "No streamer available to update bitrate")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply bitrate $bitrate", e)
        }
    }
    
    /**
     * Update bitrate for SingleStreamer
     */
    private suspend fun updateSingleStreamerBitrate(bitrate: Int) {
        singleStreamer?.let { streamer ->
            try {
                // Ensure lastVideoConfig is initialized before updating bitrate
                if (lastVideoConfig == null) {
                    val config = streamer.videoConfigFlow.value
                    if (config != null) {
                        lastVideoConfig = config
                        Log.d(TAG, "lastVideoConfig initialized from streamer's config before bitrate update")
                    } else {
                        Log.w(TAG, "Cannot update bitrate: lastVideoConfig and streamer.videoConfigFlow.value are null")
                        return
                    }
                }
                val newVideoConfig = VideoConfig(
                    mimeType = lastVideoConfig!!.mimeType,
                    resolution = lastVideoConfig!!.resolution,
                    fps = lastVideoConfig!!.fps,
                    startBitrate = bitrate
                )
                // Stop stream if running
                var wasStreaming = false
                if (streamer.isStreamingFlow.value) {
                    Log.d(TAG, "Stopping stream before updating video config")
                    streamer.stopStream()
                    wasStreaming = true
                    Log.d(TAG, "Stream stopped, waiting before reconfiguring")
                    delay(20) // 100ms delay to allow resources to clean up
                }
                streamer.setVideoConfig(newVideoConfig)
                lastVideoConfig = newVideoConfig
                if (wasStreaming) {
                    Log.d(TAG, "Waiting before restarting stream")
                    delay(20) // 100ms delay before restarting
                    streamer.startStream()
                }
                Log.d(TAG, "Updated SingleStreamer video bitrate to ${bitrate/1000} kbps")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update SingleStreamer video bitrate", e)
            }
        }
    }
    
    /**
     * Update bitrate for DualStreamer
     * Note: DualStreamer config types are internal, so dynamic bitrate is not supported yet
     */
    private suspend fun updateDualStreamerBitrate(bitrate: Int) {
        singleStreamer?.let { streamer ->
            try {
                // Use cached video config for bitrate changes
                if (lastVideoConfig != null) {
                    val newVideoConfig = lastVideoConfig!!.copy(startBitrate = bitrate)
                    streamer.setVideoConfig(newVideoConfig)
                    lastVideoConfig = newVideoConfig
                    Log.d(TAG, "Updated SingleStreamer video bitrate to ${bitrate/1000} kbps")
                } else {
                    Log.e(TAG, "lastVideoConfig is null, cannot update bitrate")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update SingleStreamer video bitrate", e)
            }
        }
    fun getStatusSummary(): String {
        val bitrateState = bitrateController.bitrateState.value
        val quality = networkQuality.value
        val isActiveText = if (isActive.value) "Active" else "Inactive"
        return "Adaptive Bitrate: $isActiveText\n" +
            "Current: ${bitrateState.currentBitrate/1000} kbps\n" +
            "Target: ${bitrateState.targetBitrate/1000} kbps\n" +
            "Network: $quality\n" +
            "RTT: ${bitrateState.networkStats.rtt}ms\n" +
            "Reason: ${bitrateState.adjustmentReason}"
    }
    }
    
    /**
     * Export configuration for persistence
     */
    fun exportConfiguration(): Map<String, Any> {
        return mapOf(
            "minBitrate" to bitrateController.minBitrate,
            "maxBitrate" to bitrateController.maxBitrate,
            "srtLatency" to bitrateController.srtLatency,
            "isEnabled" to isEnabled
        )
    }
    
    /**
     * Import configuration from persistence
     */
    fun importConfiguration(config: Map<String, Any>) {
        val minBitrate = (config["minBitrate"] as? Int) ?: 300_000
        val maxBitrate = (config["maxBitrate"] as? Int) ?: 6_000_000
        val srtLatency = (config["srtLatency"] as? Int) ?: 2000
        
        configure(minBitrate, maxBitrate, srtLatency)
        
        Log.d(TAG, "Configuration imported")
    }
    
    /**
     * Release all resources
     */
    fun release() {
    stop()
    // If release() is not available on networkMonitor, skip
    bitrateController.release()
    scope.cancel()
    singleStreamer = null
    dualStreamer = null
    Log.d(TAG, "Adaptive bitrate manager released")
    }
}
