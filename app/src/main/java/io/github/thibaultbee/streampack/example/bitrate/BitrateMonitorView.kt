package io.github.thibaultbee.streampack.example.bitrate

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.thibaultbee.streampack.example.databinding.ViewBitrateMonitorBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Custom view component for monitoring and controlling adaptive bitrate
 */
class BitrateMonitorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    private val binding: ViewBitrateMonitorBinding
    private var adaptiveBitrateManager: AdaptiveBitrateManager? = null
    private var lifecycleOwner: LifecycleOwner? = null
    
    init {
        binding = ViewBitrateMonitorBinding.inflate(LayoutInflater.from(context), this, true)
        orientation = VERTICAL
        setupUI()
    }
    
    /**
     * Setup the adaptive bitrate manager and lifecycle owner
     */
    fun setup(manager: AdaptiveBitrateManager, lifecycleOwner: LifecycleOwner) {
        this.adaptiveBitrateManager = manager
        this.lifecycleOwner = lifecycleOwner
        observeState()
        setupControls()
    }
    
    /**
     * Setup UI controls
     */
    private fun setupUI() {
        // Initially hide the view until setup is called
        visibility = GONE
    }
    
    /**
     * Setup control listeners
     */
    private fun setupControls() {
        val manager = adaptiveBitrateManager ?: return
        
        // Toggle adaptive bitrate
        binding.toggleAdaptiveBitrate.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                manager.start()
            } else {
                manager.stop()
            }
        }
        
        // Network quality simulation buttons
    // Simulation controls removed; only real network quality is shown
        
        // Show the view after setup
        visibility = VISIBLE
    }
    
    /**
     * Observe adaptive bitrate manager state
     */
    private fun observeState() {
        val manager = adaptiveBitrateManager ?: return
        val owner = lifecycleOwner ?: return
        
        owner.lifecycleScope.launch {
            // Observe individual flows separately to avoid complex type inference
            manager.isActive.collect { isActive ->
                // Update UI based on active state
                updateActiveState(isActive)
            }
        }
        
        owner.lifecycleScope.launch {
            manager.getBitrateState().collect { bitrateState ->
                updateCurrentBitrate(bitrateState.currentBitrate)
                updateBitrateState(bitrateState)
            }
        }
        
        owner.lifecycleScope.launch {
            manager.networkQuality.collect { quality ->
                updateNetworkQuality(quality)
            }
        }
    }
    
    /**
     * Update UI with current state
     */
    private fun updateUI(
        isActive: Boolean,
        currentBitrate: Int,
        networkQuality: NetworkStatsMonitor.ConnectionQuality,
        bitrateState: DynamicBitrateController.BitrateState
    ) {
        updateActiveState(isActive)
        updateCurrentBitrate(currentBitrate)
        updateNetworkQuality(networkQuality)
        updateBitrateState(bitrateState)
    }
    
    private fun updateActiveState(isActive: Boolean) {
        // Update toggle state without triggering listener
        binding.toggleAdaptiveBitrate.setOnCheckedChangeListener(null)
        binding.toggleAdaptiveBitrate.isChecked = isActive
        binding.toggleAdaptiveBitrate.setOnCheckedChangeListener { _, isChecked ->
            adaptiveBitrateManager?.let { manager ->
                if (isChecked) manager.start() else manager.stop()
            }
        }
        
        // Update status text
        binding.textStatus.text = if (isActive) "Active" else "Inactive"
    }
    
    private fun updateCurrentBitrate(currentBitrate: Int) {
        binding.textCurrentBitrate.text = "${currentBitrate / 1000} kbps"
    }
    
    private fun updateNetworkQuality(networkQuality: NetworkStatsMonitor.ConnectionQuality) {
        binding.textNetworkQuality.text = networkQuality.name
        binding.textNetworkQuality.setTextColor(getQualityColor(networkQuality))
        updateQualityButtons(networkQuality)
    }
    
    private fun updateBitrateState(bitrateState: DynamicBitrateController.BitrateState) {
        // Update target bitrate
        binding.textTargetBitrate.text = "${bitrateState.targetBitrate / 1000} kbps"
        
        // Update network stats
        val stats = bitrateState.networkStats
        binding.textRtt.text = "${stats.rtt} ms"
        binding.textBufferSize.text = "${stats.bufferSize} bytes"
        binding.textThroughput.text = "${"%.1f".format(stats.throughput)} Mbps"
        binding.textPacketLoss.text = "${"%.2f".format(stats.packetLoss)}%"
        
        // Update adjustment reason
        binding.textAdjustmentReason.text = bitrateState.adjustmentReason
    }
    
    /**
     * Get color for network quality
     */
    private fun getQualityColor(quality: NetworkStatsMonitor.ConnectionQuality): Int {
        return when (quality) {
            NetworkStatsMonitor.ConnectionQuality.GOOD -> 0xFF4CAF50.toInt()      // Green
            NetworkStatsMonitor.ConnectionQuality.FAIR -> 0xFFFFC107.toInt()      // Amber
            NetworkStatsMonitor.ConnectionQuality.POOR -> 0xFFF44336.toInt()      // Red
        }
    }
    
    /**
     * Update quality control buttons to show current selection
     */
    private fun updateQualityButtons(currentQuality: NetworkStatsMonitor.ConnectionQuality) {
        // Only show buttons for GOOD, FAIR, POOR
        listOf(
            binding.buttonGood,
            binding.buttonFair,
            binding.buttonPoor
        ).forEach { button ->
            button.alpha = 0.5f
        }
        when (currentQuality) {
            NetworkStatsMonitor.ConnectionQuality.GOOD -> binding.buttonGood.alpha = 1.0f
            NetworkStatsMonitor.ConnectionQuality.FAIR -> binding.buttonFair.alpha = 1.0f
            NetworkStatsMonitor.ConnectionQuality.POOR -> binding.buttonPoor.alpha = 1.0f
        }
    }
    
    /**
     * Show or hide the detailed statistics
     */
    fun setShowDetails(show: Boolean) {
        binding.layoutDetails.visibility = if (show) VISIBLE else GONE
        binding.layoutNetworkControls.visibility = if (show) VISIBLE else GONE
    }
    
    /**
     * Export current configuration
     */
    fun exportConfiguration(): Map<String, Any>? {
        return adaptiveBitrateManager?.exportConfiguration()
    }
    
    /**
     * Import configuration
     */
    fun importConfiguration(config: Map<String, Any>) {
        adaptiveBitrateManager?.importConfiguration(config)
    }
}
