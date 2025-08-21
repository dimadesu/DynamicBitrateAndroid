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

    // Moblin-style smoothing and jitter
    private var bufferSizeAvg: Double = 0.0
    private var bufferSizeJitter: Double = 0.0
    private var prevBufferSize: Int = 0
    private var rttAvg: Double = 0.0
    private var rttAvgDelta: Double = 0.0
    private var prevRtt: Int = 300
    private var rttMin: Double = 200.0
    private var rttJitter: Double = 0.0
    private var throughput: Double = 0.0
    
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
                // Moblin-style smoothing and jitter
                val pktSndBufDouble = (srtStats.pktSndBuf as Number).toDouble()
                bufferSizeAvg = bufferSizeAvg * 0.99 + pktSndBufDouble * 0.01
                bufferSizeJitter = bufferSizeJitter * 0.99
                val deltaBufferSize = pktSndBufDouble - prevBufferSize.toDouble()
                if (deltaBufferSize > bufferSizeJitter) bufferSizeJitter = deltaBufferSize
                prevBufferSize = pktSndBufDouble.toInt()

                val msRTTDouble = (srtStats.msRTT as Number).toDouble()
                if (rttAvg == 0.0) {
                    rttAvg = msRTTDouble
                } else {
                    rttAvg = rttAvg * 0.99 + 0.01 * msRTTDouble
                }
                val deltaRtt = srtStats.msRTT - prevRtt.toDouble()
                rttAvgDelta = rttAvgDelta * 0.8 + deltaRtt * 0.2
                prevRtt = srtStats.msRTT.toInt()
                rttMin *= 1.001
                val msRTTDoubleForMin = (srtStats.msRTT as Number).toDouble()
                if (msRTTDoubleForMin != 100.0 && msRTTDoubleForMin < rttMin && rttAvgDelta < 1.0) {
                    rttMin = msRTTDoubleForMin
                }
                rttJitter = rttJitter * 0.99
                if (deltaRtt > rttJitter) rttJitter = deltaRtt

                throughput = throughput * 0.97 + srtStats.mbpsBandwidth.toDouble() * 0.03

                // Log Moblin-style stats periodically
                if (System.currentTimeMillis() % 1000 < 100) {
                    Log.v(TAG, "Moblin SRT Stats: RTT=${srtStats.msRTT}, Buf=${srtStats.pktSndBuf}, AvgBuf=${bufferSizeAvg.toInt()}, JitterBuf=${bufferSizeJitter.toInt()}, RTTAvg=${rttAvg.toInt()}, RTTJitter=${rttJitter.toInt()}, RTTMin=${rttMin.toInt()}, BW=${throughput}")
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
                // If encoder is available and stream is running, update bitrate directly
                val encoder = streamer.videoEncoder
                if (encoder != null && streamer.isStreamingFlow.value) {
                    encoder.bitrate = bitrate
                    Log.d(TAG, "Updated encoder bitrate directly to ${bitrate/1000} kbps")
                    lastVideoConfig = lastVideoConfig?.copy(startBitrate = bitrate)
                    return
                }
                // Fallback: update config only if encoder is not available
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
                streamer.setVideoConfig(newVideoConfig)
                lastVideoConfig = newVideoConfig
                Log.d(TAG, "Updated SingleStreamer video bitrate to ${bitrate/1000} kbps (via config update)")
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
