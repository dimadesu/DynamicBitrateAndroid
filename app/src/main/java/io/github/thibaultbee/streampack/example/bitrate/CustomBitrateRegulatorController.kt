package io.github.thibaultbee.streampack.example.bitrate

import io.github.thibaultbee.streampack.core.configuration.BitrateRegulatorConfig
import io.github.thibaultbee.streampack.core.elements.encoders.IEncoder
import io.github.thibaultbee.streampack.core.elements.endpoints.IEndpoint
import io.github.thibaultbee.streampack.core.pipelines.outputs.encoding.IConfigurableAudioEncodingPipelineOutput
import io.github.thibaultbee.streampack.core.pipelines.outputs.encoding.IConfigurableVideoEncodingPipelineOutput
import io.github.thibaultbee.streampack.core.pipelines.outputs.encoding.IEncodingPipelineOutput
import io.github.thibaultbee.streampack.core.regulator.IBitrateRegulator
import io.github.thibaultbee.streampack.core.regulator.controllers.BitrateRegulatorController
import io.github.thibaultbee.streampack.core.regulator.controllers.DummyBitrateRegulatorController
import io.github.thibaultbee.streampack.core.regulator.BitrateRegulator
import io.github.thibaultbee.streampack.core.elements.utils.Scheduler

class CustomBitrateRegulatorController(
    audioEncoder: IEncoder?,
    videoEncoder: IEncoder,
    endpoint: IEndpoint,
    bitrateRegulatorConfig: BitrateRegulatorConfig = BitrateRegulatorConfig(),
    delayTimeInMs: Long = 500,
    private val customFactory: IBitrateRegulator.Factory = CustomBitrateRegulator.Factory()
) : BitrateRegulatorController(
    audioEncoder,
    videoEncoder,
    endpoint,
    customFactory,
    bitrateRegulatorConfig
) {
    // You can add custom fields or override methods here if needed

    private val bitrateRegulator: IBitrateRegulator = customFactory.newBitrateRegulator(
        bitrateRegulatorConfig,
        { videoEncoder?.bitrate = it },
        { audioEncoder?.bitrate = it }
    )

    private val scheduler = Scheduler(delayTimeInMs) {
        bitrateRegulator.update(
            endpoint.metrics,
            videoEncoder?.bitrate ?: 0,
            audioEncoder?.bitrate ?: 0
        )
    }

    override fun start() {
        scheduler.start()
    }

    override fun stop() {
        scheduler.stop()
    }

    class Factory(
        private val bitrateRegulatorConfig: BitrateRegulatorConfig = BitrateRegulatorConfig(),
        private val delayTimeInMs: Long = 500
    ) : BitrateRegulatorController.Factory() {
        override fun newBitrateRegulatorController(pipelineOutput: IEncodingPipelineOutput): BitrateRegulatorController {
            require(pipelineOutput is IConfigurableVideoEncodingPipelineOutput) {
                "Pipeline output must be a video encoding output"
            }
            val videoEncoder = requireNotNull(pipelineOutput.videoEncoder) {
                "Video encoder must be set"
            }
            val audioEncoder = if (pipelineOutput is IConfigurableAudioEncodingPipelineOutput) {
                pipelineOutput.audioEncoder
            } else {
                null
            }
            return CustomBitrateRegulatorController(
                audioEncoder,
                videoEncoder,
                pipelineOutput.endpoint,
                bitrateRegulatorConfig,
                delayTimeInMs
            )
        }
    }
}

class CustomBitrateRegulator(
    config: BitrateRegulatorConfig,
    setVideoBitrate: (Int) -> Unit,
    setAudioBitrate: (Int) -> Unit
) : BitrateRegulator(config, setVideoBitrate, setAudioBitrate) {
    override fun update(stats: Any, currentVideoBitrate: Int, currentAudioBitrate: Int) {
        // TODO: Implement your custom logic here
        // Example: keep bitrate at midpoint of range
        val targetBitrate = (bitrateRegulatorConfig.videoBitrateRange.lower + bitrateRegulatorConfig.videoBitrateRange.upper) / 2
        onVideoTargetBitrateChange(targetBitrate)
    }

    class Factory : IBitrateRegulator.Factory {
        override fun newBitrateRegulator(
            config: BitrateRegulatorConfig,
            setVideoBitrate: (Int) -> Unit,
            setAudioBitrate: (Int) -> Unit
        ): BitrateRegulator {
            return CustomBitrateRegulator(config, setVideoBitrate, setAudioBitrate)
        }
    }
}
