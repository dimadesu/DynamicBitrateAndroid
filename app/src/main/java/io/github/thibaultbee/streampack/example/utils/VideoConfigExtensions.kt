package io.github.thibaultbee.streampack.example.utils

import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig

/**
 * Extension function to create a copy of VideoConfig with updated bitrate
 * This provides compatibility until the actual StreamPack API is confirmed
 */
fun VideoConfig.copyWithBitrate(newBitrate: Int): VideoConfig {
    return VideoConfig(
        mimeType = this.mimeType,
        resolution = this.resolution,
        fps = this.fps,
        startBitrate = newBitrate
    )
}
