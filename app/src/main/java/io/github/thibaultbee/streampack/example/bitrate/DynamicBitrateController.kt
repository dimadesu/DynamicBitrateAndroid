package io.github.thibaultbee.streampack.example.bitrate

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Dynamic bitrate controller inspired by belacoder.c
 * Adapts video bitrate based on network conditions for SRT streaming
 */
class DynamicBitrateController {
    
    companion object {
        private const val TAG = "DynamicBitrateController"
        
        // Bitrate constants (in bps)
        private const val MIN_BITRATE = 300_000  // 300 Kbps
        private const val DEF_BITRATE = 6_000_000  // 6 Mbps
        private const val ABS_MAX_BITRATE = 30_000_000  // 30 Mbps
        
        // Update intervals (in ms)
        private const val BITRATE_UPDATE_INTERVAL = 20L
        
        // Bitrate increment settings
        private const val BITRATE_INCR_MIN = 30_000  // 30 Kbps minimum increment
        private const val BITRATE_INCR_INTERVAL = 500L  // 500ms minimum interval
        private const val BITRATE_INCR_SCALE = 30  // Scale factor for increment
        
        // Bitrate decrement settings
        private const val BITRATE_DECR_MIN = 100_000  // 100 Kbps minimum decrement
        private const val BITRATE_DECR_INTERVAL = 200L  // Light congestion interval
        private const val BITRATE_DECR_FAST_INTERVAL = 250L  // Heavy congestion interval
        private const val BITRATE_DECR_SCALE = 10  // Scale factor for decrement
        
        // SRT settings
        private const val DEFAULT_SRT_LATENCY = 2000  // 2 seconds
        private const val SRT_PKT_SIZE = 1316  // Default SRT packet size
    }
    
    // Network statistics
    data class NetworkStats(
        val rtt: Int = 0,
        val bufferSize: Int = 0,
        val throughput: Double = 0.0,
        val packetLoss: Double = 0.0
    )
    
    // Bitrate adjustment state
    data class BitrateState(
        val currentBitrate: Int,
        val targetBitrate: Int,
        val networkStats: NetworkStats,
        val adjustmentReason: String = ""
    )
    
    // Configuration
    var minBitrate: Int = MIN_BITRATE
    var maxBitrate: Int = DEF_BITRATE
    var srtLatency: Int = DEFAULT_SRT_LATENCY
    
    // Current state
    private var currentBitrate: Int = MIN_BITRATE
    
    // Smoothed network metrics
    private var rttAvg: Double = 0.0
    private var rttAvgDelta: Double = 0.0
    private var rttMin: Double = 200.0
    private var rttJitter: Double = 0.0
    private var prevRtt: Int = 300
    
    private var bufferSizeAvg: Double = 0.0
    private var bufferSizeJitter: Double = 0.0
    private var prevBufferSize: Int = 0
    
    private var throughput: Double = 0.0
    
    // Timing control
    private var nextBitrateIncr: Long = 0
    private var nextBitrateDecr: Long = 0
    
    // State flow for observers
    private val _bitrateState = MutableStateFlow(
        BitrateState(
            currentBitrate = currentBitrate,
            targetBitrate = currentBitrate,
            networkStats = NetworkStats()
        )
    )
    val bitrateState: StateFlow<BitrateState> = _bitrateState.asStateFlow()
    
    // Coroutine scope for periodic updates
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Callback for bitrate changes
    var onBitrateChanged: ((Int) -> Unit)? = null
    
    /**
     * Start monitoring and adjusting bitrate based on network conditions
     */
    fun startMonitoring() {
        Log.d(TAG, "Starting dynamic bitrate monitoring")
        currentBitrate = minBitrate
        
        monitoringJob = scope.launch {
            while (isActive) {
                // In a real implementation, you would get actual SRT statistics here
                // For now, we simulate network conditions
                val networkStats = getCurrentNetworkStats()
                updateBitrate(networkStats)
                
                delay(BITRATE_UPDATE_INTERVAL)
            }
        }
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        Log.d(TAG, "Stopping dynamic bitrate monitoring")
        monitoringJob?.cancel()
    }
    
    /**
     * Update configuration
     */
    fun updateConfiguration(minBitrate: Int, maxBitrate: Int, srtLatency: Int) {
        this.minBitrate = minBitrate.coerceIn(MIN_BITRATE, ABS_MAX_BITRATE)
        this.maxBitrate = maxBitrate.coerceIn(this.minBitrate, ABS_MAX_BITRATE)
        this.srtLatency = srtLatency.coerceIn(100, 10000)
        
        Log.d(TAG, "Configuration updated: min=$minBitrate, max=$maxBitrate, latency=$srtLatency")
    }
    
    /**
     * Get current network statistics (placeholder for actual SRT stats)
     * In a real implementation, this would interface with the SRT library
     */
    private fun getCurrentNetworkStats(): NetworkStats {
        // TODO: Replace with actual SRT statistics gathering
        // This would interface with the StreamPack SRT extension to get real statistics
        // For now, return simulated values for demonstration
        return NetworkStats(
            rtt = Random.nextInt(100, 300),
            bufferSize = Random.nextInt(0, 1000),
            throughput = Random.nextDouble(1.0, 10.0),
            packetLoss = Random.nextDouble(0.0, 2.0)
        )
    }
    
    /**
     * Core bitrate adjustment algorithm based on belacoder.c logic
     */
    private fun updateBitrate(stats: NetworkStats) {
        val currentTime = System.currentTimeMillis()
        
        // Update RTT statistics with exponential smoothing
        updateRttStats(stats.rtt)
        
        // Update buffer size statistics
        updateBufferSizeStats(stats.bufferSize)
        
        // Update throughput with exponential smoothing
        updateThroughputStats(stats.throughput)
        
        // Calculate thresholds based on current statistics
        val thresholds = calculateThresholds()
        
        // Determine new bitrate based on network conditions
        val newBitrate = calculateNewBitrate(stats, thresholds, currentTime)
        
        // Apply bitrate change if different
        if (newBitrate != currentBitrate) {
            val reason = getBitrateChangeReason(stats, thresholds)
            applyBitrateChange(newBitrate, reason)
        }
        
        // Update state flow
        _bitrateState.value = BitrateState(
            currentBitrate = currentBitrate,
            targetBitrate = newBitrate,
            networkStats = stats,
            adjustmentReason = getBitrateChangeReason(stats, thresholds)
        )
        
        logNetworkStats(stats, thresholds)
    }
    
    private fun updateRttStats(rtt: Int) {
        // Initialize RTT average on first measurement
        if (rttAvg == 0.0) {
            rttAvg = rtt.toDouble()
        } else {
            rttAvg = rttAvg * 0.99 + 0.01 * rtt
        }
        
        // Update RTT delta average
        val deltaRtt = (rtt - prevRtt).toDouble()
        rttAvgDelta = rttAvgDelta * 0.8 + deltaRtt * 0.2
        prevRtt = rtt
        
        // Update minimum RTT
        rttMin *= 1.001
        if (rtt != 100 && rtt < rttMin && rttAvgDelta < 1.0) {
            rttMin = rtt.toDouble()
        }
        
        // Update RTT jitter
        rttJitter *= 0.99
        if (deltaRtt > rttJitter) {
            rttJitter = deltaRtt
        }
    }
    
    private fun updateBufferSizeStats(bufferSize: Int) {
        // Rolling average for buffer size
        bufferSizeAvg = bufferSizeAvg * 0.99 + bufferSize * 0.01
        
        // Update buffer size jitter
        bufferSizeJitter *= 0.99
        val deltaBufferSize = bufferSize - prevBufferSize
        if (deltaBufferSize > bufferSizeJitter) {
            bufferSizeJitter = deltaBufferSize.toDouble()
        }
        prevBufferSize = bufferSize
    }
    
    private fun updateThroughputStats(currentThroughput: Double) {
        // Rolling average for throughput
        throughput *= 0.97
        throughput += currentThroughput * 0.03
    }
    
    private data class BitrateThresholds(
        val bufferThreshold1: Int,
        val bufferThreshold2: Int,
        val bufferThreshold3: Int,
        val rttThresholdMin: Int,
        val rttThresholdMax: Int
    )
    
    private fun calculateThresholds(): BitrateThresholds {
        val bufferTh3 = ((bufferSizeAvg + bufferSizeJitter) * 4).toInt()
        val bufferTh2 = max(50.0, bufferSizeAvg + max(bufferSizeJitter * 3.0, bufferSizeAvg))
            .toInt()
            .coerceAtMost(rttToBufferSize(srtLatency / 2))
        val bufferTh1 = max(50.0, bufferSizeAvg + bufferSizeJitter * 2.5).toInt()
        val rttThMax = (rttAvg + max(rttJitter * 4, rttAvg * 0.15)).toInt()
        val rttThMin = (rttMin + max(1.0, rttJitter * 2)).toInt()
        
        return BitrateThresholds(
            bufferThreshold1 = bufferTh1,
            bufferThreshold2 = bufferTh2,
            bufferThreshold3 = bufferTh3,
            rttThresholdMin = rttThMin,
            rttThresholdMax = rttThMax
        )
    }
    
    private fun rttToBufferSize(rtt: Int): Int {
        return ((throughput / 8) * rtt / SRT_PKT_SIZE).toInt()
    }
    
    private fun calculateNewBitrate(
        stats: NetworkStats,
        thresholds: BitrateThresholds,
        currentTime: Long
    ): Int {
        var bitrate = currentBitrate
        
        when {
            // Severe congestion: drop to minimum immediately
            bitrate > minBitrate && (stats.rtt >= (srtLatency / 3) || stats.bufferSize > thresholds.bufferThreshold3) -> {
                bitrate = minBitrate
                nextBitrateDecr = currentTime + BITRATE_DECR_INTERVAL
            }
            
            // Heavy congestion: fast decrease
            currentTime > nextBitrateDecr && (stats.rtt > (srtLatency / 5) || stats.bufferSize > thresholds.bufferThreshold2) -> {
                bitrate -= BITRATE_DECR_MIN + bitrate / BITRATE_DECR_SCALE
                nextBitrateDecr = currentTime + BITRATE_DECR_FAST_INTERVAL
            }
            
            // Light congestion: slow decrease
            currentTime > nextBitrateDecr && (stats.rtt > thresholds.rttThresholdMax || stats.bufferSize > thresholds.bufferThreshold1) -> {
                bitrate -= BITRATE_DECR_MIN
                nextBitrateDecr = currentTime + BITRATE_DECR_INTERVAL
            }
            
            // Good conditions: increase bitrate
            currentTime > nextBitrateIncr && stats.rtt < thresholds.rttThresholdMin && rttAvgDelta < 0.01 -> {
                bitrate += BITRATE_INCR_MIN + bitrate / BITRATE_INCR_SCALE
                nextBitrateIncr = currentTime + BITRATE_INCR_INTERVAL
            }
        }
        
        // Clamp to configured range
        return bitrate.coerceIn(minBitrate, maxBitrate)
    }
    
    private fun getBitrateChangeReason(stats: NetworkStats, thresholds: BitrateThresholds): String {
        return when {
            stats.rtt >= (srtLatency / 3) -> "Severe RTT congestion"
            stats.bufferSize > thresholds.bufferThreshold3 -> "Severe buffer congestion"
            stats.rtt > (srtLatency / 5) -> "Heavy RTT congestion"
            stats.bufferSize > thresholds.bufferThreshold2 -> "Heavy buffer congestion"
            stats.rtt > thresholds.rttThresholdMax -> "Light RTT congestion"
            stats.bufferSize > thresholds.bufferThreshold1 -> "Light buffer congestion"
            stats.rtt < thresholds.rttThresholdMin && rttAvgDelta < 0.01 -> "Good conditions - increasing"
            else -> "Stable"
        }
    }
    
    private fun applyBitrateChange(newBitrate: Int, reason: String) {
        val oldBitrate = currentBitrate
        currentBitrate = newBitrate
        
        // Round to nearest 100 kbps for cleaner values
        val roundedBitrate = (newBitrate / 100_000) * 100_000
        
        Log.d(TAG, "Bitrate changed: $oldBitrate -> $roundedBitrate bps ($reason)")
        
        // Notify listener (e.g., to update encoder bitrate)
        onBitrateChanged?.invoke(roundedBitrate)
    }
    
    private fun logNetworkStats(stats: NetworkStats, thresholds: BitrateThresholds) {
        Log.v(TAG, "Network stats - RTT: ${stats.rtt}ms (avg: ${rttAvg.toInt()}, min: ${rttMin.toInt()}, jitter: ${rttJitter.toInt()}), " +
                "Buffer: ${stats.bufferSize} (avg: ${bufferSizeAvg.toInt()}, jitter: ${bufferSizeJitter.toInt()}), " +
                "Throughput: ${"%.1f".format(throughput)} Mbps, " +
                "Bitrate: ${currentBitrate / 1000} kbps")
    }
    
    /**
     * Cleanup resources
     */
    fun release() {
        stopMonitoring()
        scope.cancel()
    }
}
