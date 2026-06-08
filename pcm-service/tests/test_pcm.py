import time
from unittest.mock import MagicMock

import grpc
import numpy as np
import pytest

from app.processor import (
    MAX_DURATION_SEC,
    MIN_DURATION_SEC,
    SEGMENT_SEC,
    expected_pcm_bytes,
    iter_process_pcm_stream,
    process_pcm,
)
from app.servicer import PcmProcessorServicer
from app.pcm.v1 import pcm_pb2


def _sine_pcm(duration_sec: float, rate: int = 24000, freq: float = 440.0) -> bytes:
    n = int(duration_sec * rate)
    t = np.arange(n, dtype=np.float32) / rate
    samples = (0.3 * 32767 * np.sin(2 * np.pi * freq * t)).astype(np.int16)
    return samples.tobytes()


def _collect_segments(pcm: bytes, duration_sec: float, rtf: float = 0.0):
    return list(
        iter_process_pcm_stream(pcm, 0.0, duration_sec, rtf=rtf)
    )


def test_process_pcm_doubles_sample_count():
    pcm = _sine_pcm(10.0)
    out = process_pcm(pcm, 0.0, 10.0, rtf=0.0)
    assert len(out) == len(pcm) * 2


def test_stream_10s_single_segment():
    pcm = _sine_pcm(10.0)
    segments = _collect_segments(pcm, 10.0, rtf=0.0)
    assert len(segments) == 1
    assert segments[0].is_last
    assert segments[0].segment_duration_sec == pytest.approx(10.0)
    assert len(segments[0].pcm_s16le) == expected_pcm_bytes(10.0, 48000)


def test_stream_11s_two_segments():
    pcm = _sine_pcm(11.0)
    segments = _collect_segments(pcm, 11.0, rtf=0.0)
    assert len(segments) == 2
    assert segments[0].segment_duration_sec == pytest.approx(10.0)
    assert segments[1].segment_duration_sec == pytest.approx(1.0)
    assert segments[1].is_last


def test_stream_90s_nine_segments():
    pcm = _sine_pcm(90.0)
    segments = _collect_segments(pcm, 90.0, rtf=0.0)
    assert len(segments) == 9
    assert all(s.segment_duration_sec == pytest.approx(SEGMENT_SEC) for s in segments[:-1])
    assert segments[-1].is_last


def test_rtf_enforces_delay_once():
    pcm = _sine_pcm(10.0)
    start = time.perf_counter()
    list(iter_process_pcm_stream(pcm, 0.0, 10.0, rtf=0.6))
    assert time.perf_counter() - start >= 5.5


def test_validate_duration_bounds():
    pcm = _sine_pcm(MIN_DURATION_SEC)
    with pytest.raises(ValueError):
        list(iter_process_pcm_stream(pcm, 0.0, MIN_DURATION_SEC - 0.1, rtf=0.0))

    pcm90 = _sine_pcm(MAX_DURATION_SEC)
    with pytest.raises(ValueError):
        list(iter_process_pcm_stream(pcm90, 0.0, MAX_DURATION_SEC + 0.1, rtf=0.0))


def test_grpc_process_stream():
    servicer = PcmProcessorServicer()
    pcm = _sine_pcm(10.0)
    request = pcm_pb2.ProcessRequest(
        pcm_s16le=pcm,
        gain_db=6.0,
        duration_sec=10.0,
        input_sample_rate=24000,
        output_sample_rate=48000,
    )
    segments = list(servicer.ProcessStream(request, MagicMock()))
    assert len(segments) == 1
    assert len(segments[0].pcm_s16le) == expected_pcm_bytes(10.0, 48000)


def test_grpc_invalid_argument():
    servicer = PcmProcessorServicer()
    context = MagicMock()

    def abort(code, details):
        raise ValueError(details)

    context.abort.side_effect = abort
    request = pcm_pb2.ProcessRequest(
        pcm_s16le=b"",
        gain_db=6.0,
        duration_sec=10.0,
        input_sample_rate=24000,
        output_sample_rate=48000,
    )
    with pytest.raises(ValueError, match="Empty PCM body"):
        list(servicer.ProcessStream(request, context))
