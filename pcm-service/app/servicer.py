import os

import grpc

from app.pcm.v1 import pcm_pb2, pcm_pb2_grpc
from app.processor import iter_process_pcm_stream


class PcmProcessorServicer(pcm_pb2_grpc.PcmProcessorServicer):
    def ProcessStream(self, request, context):
        rtf = float(os.getenv("PCM_RTF", "0.6"))
        try:
            for segment in iter_process_pcm_stream(
                request.pcm_s16le,
                request.gain_db,
                request.duration_sec,
                request.input_sample_rate or 24000,
                request.output_sample_rate or 48000,
                rtf=rtf,
            ):
                yield pcm_pb2.ProcessSegment(
                    pcm_s16le=segment.pcm_s16le,
                    segment_index=segment.segment_index,
                    segment_duration_sec=segment.segment_duration_sec,
                    is_last=segment.is_last,
                )
        except ValueError as exc:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(exc))

    def Check(self, request, context):
        return pcm_pb2.HealthCheckResponse(status="ok")


def add_servicer(server: grpc.Server) -> None:
    pcm_pb2_grpc.add_PcmProcessorServicer_to_server(PcmProcessorServicer(), server)
