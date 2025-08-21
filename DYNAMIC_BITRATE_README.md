# Dynamic Bitrate Control for StreamPack Android

This implementation ports the sophisticated dynamic bitrate control algorithm from `belacoder.c` to the StreamPack Android application. It provides adaptive bitrate adjustment based on real-time network conditions for SRT streaming.

## Overview

The dynamic bitrate system consists of several components that work together to provide intelligent streaming quality adaptation:

### Core Components

1. **DynamicBitrateController**: Implements the core algorithm from belacoder.c
2. **NetworkStatsMonitor**: Monitors SRT connection statistics
3. **AdaptiveBitrateManager**: Integrates the system with StreamPack
4. **BitrateMonitorView**: Provides UI for monitoring and testing

## Algorithm Details

The algorithm is based on the original C implementation and includes:

### Network Metrics Tracking

- **RTT (Round Trip Time)**: Exponentially smoothed average with jitter detection
- **Buffer Size**: Send buffer monitoring with congestion detection
- **Throughput**: Rolling average of network throughput
- **Packet Loss**: Loss rate monitoring for quality assessment

### Adaptive Logic

The bitrate adjustment follows these rules:

#### Decrease Conditions (Congestion Detection)

1. **Severe Congestion**: Drop to minimum bitrate immediately
   - RTT ≥ latency/3 OR buffer > threshold3
2. **Heavy Congestion**: Fast decrease with larger steps
   - RTT > latency/5 OR buffer > threshold2
   - Decrease: `BITRATE_DECR_MIN + current_bitrate/BITRATE_DECR_SCALE`
3. **Light Congestion**: Gradual decrease
   - RTT > rtt_threshold_max OR buffer > threshold1
   - Decrease: `BITRATE_DECR_MIN`

#### Increase Conditions (Good Network)

- RTT < rtt_threshold_min AND stable RTT trend
- Increase: `BITRATE_INCR_MIN + current_bitrate/BITRATE_INCR_SCALE`

### Constants from belacoder.c

```kotlin
// Bitrate limits
MIN_BITRATE = 300_000      // 300 Kbps
ABS_MAX_BITRATE = 30_000_000 // 30 Mbps

// Update intervals
BITRATE_UPDATE_INTERVAL = 20L // 20ms

// Increment settings
BITRATE_INCR_MIN = 30_000     // 30 Kbps minimum step
BITRATE_INCR_INTERVAL = 500L  // 500ms minimum interval
BITRATE_INCR_SCALE = 30       // Scale factor

// Decrement settings
BITRATE_DECR_MIN = 100_000    // 100 Kbps minimum step
BITRATE_DECR_INTERVAL = 200L  // Light congestion interval
BITRATE_DECR_FAST_INTERVAL = 250L // Heavy congestion interval
BITRATE_DECR_SCALE = 10       // Scale factor
```

## Usage

### Basic Integration

```kotlin
class MainActivity : AppCompatActivity() {
    private val adaptiveBitrateManager = AdaptiveBitrateManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize with streamer
        adaptiveBitrateManager.initialize(streamer)

        // Configure parameters
        adaptiveBitrateManager.configure(
            minBitrate = 500_000,    // 500 Kbps
            maxBitrate = 4_000_000,  // 4 Mbps
            srtLatency = 2000,       // 2 seconds
            enableSimulation = true  // For testing
        )
    }

    private fun startStreaming() {
        streamer.startStream("srt://server:port")

        // Start adaptive bitrate
        adaptiveBitrateManager.start()
    }

    private fun stopStreaming() {
        adaptiveBitrateManager.stop()
        streamer.stopStream()
    }
}
```

### UI Integration

The `BitrateMonitorView` provides real-time monitoring and testing controls:

```kotlin
// Setup monitoring view
binding.bitrateMonitor.setup(adaptiveBitrateManager, this)
binding.bitrateMonitor.setShowDetails(true)
```

The UI shows:

- Current and target bitrate
- Network quality assessment
- Real-time RTT, buffer size, throughput stats
- Network simulation controls for testing
- Adjustment reasoning

### Testing Network Conditions

For development and testing, you can simulate different network conditions:

```kotlin
// Simulate network conditions
adaptiveBitrateManager.updateNetworkConditions(
    NetworkStatsMonitor.ConnectionQuality.POOR
)
```

Available quality levels:

- `EXCELLENT`: RTT 10-30ms, minimal loss
- `GOOD`: RTT 30-80ms, < 0.5% loss
- `FAIR`: RTT 80-150ms, < 1.5% loss
- `POOR`: RTT 150-300ms, < 5% loss
- `CRITICAL`: RTT 300-800ms, > 5% loss

## Implementation Notes

### Real SRT Integration

Currently the system uses simulated network statistics. To integrate with real SRT statistics:

1. **Extend NetworkStatsMonitor**: Implement `getSrtStatistics()` using SRT library calls
2. **Add JNI Interface**: Create native bindings to access SRT statistics
3. **Socket Integration**: Pass actual SRT socket to monitoring system

Example of what real integration would look like:

```kotlin
// Pseudo-code for real SRT integration
private fun getSrtStatistics(srtSocket: SRTSocket): SrtStats {
    val stats = SRT_TRACEBSTATS()
    val result = srt_bstats(srtSocket.handle, stats, true)

    return SrtStats(
        rtt = stats.msRTT.toInt(),
        bufferSize = getSendBufferSize(srtSocket),
        throughputMbps = stats.mbpsSendRate,
        packetLoss = calculatePacketLoss(stats),
        ackCount = stats.pktRecvACKTotal
    )
}
```

### Configuration Persistence

```kotlin
// Save configuration
val config = adaptiveBitrateManager.exportConfiguration()
sharedPrefs.edit().putString("bitrate_config", gson.toJson(config)).apply()

// Restore configuration
val configJson = sharedPrefs.getString("bitrate_config", null)
val config = gson.fromJson(configJson, Map::class.java)
adaptiveBitrateManager.importConfiguration(config)
```

### Performance Considerations

- The system runs monitoring loops every 20-50ms
- All calculations use efficient exponential smoothing
- Network calls are made on background threads
- UI updates are throttled to avoid excessive redraws

## Differences from belacoder.c

1. **Language**: Ported from C to Kotlin with proper coroutines
2. **Architecture**: Modular design with separate monitoring and control components
3. **UI**: Added comprehensive monitoring and testing interface
4. **Integration**: Designed specifically for StreamPack API
5. **Error Handling**: Enhanced error handling and logging
6. **Testing**: Built-in network simulation for development

## Future Enhancements

1. **Machine Learning**: Add ML-based prediction for proactive adjustments
2. **Multi-metric**: Include additional network metrics (jitter, bandwidth variation)
3. **Profile-based**: Different adaptation profiles for different use cases
4. **Analytics**: Detailed logging and analytics for optimization
5. **Real SRT**: Full integration with SRT library statistics

## Troubleshooting

### Common Issues

1. **No bitrate changes**: Check that adaptive bitrate is enabled and streaming is active
2. **Erratic behavior**: Verify network simulation is disabled for real testing
3. **Performance issues**: Ensure monitoring is stopped when not streaming
4. **UI not updating**: Check that lifecycle owner is properly connected

### Debug Logging

Enable verbose logging to see detailed algorithm behavior:

```kotlin
// In logcat, filter for tags:
// DynamicBitrateController
// NetworkStatsMonitor
// AdaptiveBitrateManager
```

The logs show detailed network statistics, threshold calculations, and bitrate adjustment decisions that match the original belacoder.c debug output.
