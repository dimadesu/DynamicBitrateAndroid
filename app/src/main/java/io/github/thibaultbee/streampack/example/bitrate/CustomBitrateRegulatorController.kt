import io.github.thibaultbee.streampack.core.configuration.BitrateRegulatorConfig
import io.github.thibaultbee.streampack.core.elements.encoders.IEncoder
import io.github.thibaultbee.streampack.core.elements.endpoints.IEndpoint
import io.github.thibaultbee.streampack.core.elements.utils.Scheduler
import io.github.thibaultbee.streampack.core.pipelines.outputs.encoding.IConfigurableAudioEncodingPipelineOutput
import io.github.thibaultbee.streampack.core.pipelines.outputs.encoding.IConfigurableVideoEncodingPipelineOutput
import io.github.thibaultbee.streampack.core.pipelines.outputs.encoding.IEncodingPipelineOutput
import io.github.thibaultbee.streampack.core.regulator.IBitrateRegulator
import io.github.thibaultbee.streampack.core.regulator.controllers.BitrateRegulatorController
import io.github.thibaultbee.streampack.ext.srt.regulator.DefaultSrtBitrateRegulator
import io.github.thibaultbee.streampack.ext.srt.regulator.SrtBitrateRegulator

class CustomBitrateRegulatorController (
    audioEncoder: IEncoder?,
    videoEncoder: IEncoder,
    endpoint: IEndpoint,
    bitrateRegulatorFactory: IBitrateRegulator.Factory,
    bitrateRegulatorConfig: BitrateRegulatorConfig = BitrateRegulatorConfig(),
    delayTimeInMs: Long = 500
) : BitrateRegulatorController(
    audioEncoder,
    videoEncoder,
    endpoint,
    bitrateRegulatorFactory,
    bitrateRegulatorConfig
) {
    /**
     * Bitrate regulator. Calls regularly by [scheduler]. Don't call it otherwise or you might break regulation.
     */
    private val bitrateRegulator = bitrateRegulatorFactory.newBitrateRegulator(
        bitrateRegulatorConfig,
        {
            videoEncoder.bitrate = it
        },
        { /* Do nothing for audio */ }
    )

    /**
     * Scheduler for bitrate regulation
     */
    private val scheduler = Scheduler(delayTimeInMs) {
        android.util.Log.d("CustomBitrateRegulatorController", "Scheduler tick: videoBitrate=${videoEncoder.bitrate}, audioBitrate=${audioEncoder?.bitrate ?: 0}")
        bitrateRegulator.update(
            endpoint.metrics,
            videoEncoder.bitrate,
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
        private val bitrateRegulatorFactory: SrtBitrateRegulator.Factory = DefaultSrtBitrateRegulator.Factory(),
        private val bitrateRegulatorConfig: BitrateRegulatorConfig = BitrateRegulatorConfig(),
        private val delayTimeInMs: Long = 500
    ) : BitrateRegulatorController.Factory() {
        override fun newBitrateRegulatorController(pipelineOutput: IEncodingPipelineOutput): CustomBitrateRegulatorController {
            require(pipelineOutput is IConfigurableVideoEncodingPipelineOutput) {
                "Pipeline output must be an video encoding output"
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
                bitrateRegulatorFactory,
                bitrateRegulatorConfig,
                delayTimeInMs
            )
        }
    }
}