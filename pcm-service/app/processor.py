import time
from collections.abc import Iterator
from dataclasses import dataclass

from app.gain import apply_gain_s16le
from app.resample import resample_24k_to_48k_s16le
from app.rtf import enforce_rtf

INPUT_RATE = 24000
OUTPUT_RATE = 48000
SEGMENT_SEC = 10.0
MIN_DURATION_SEC = 10.0
MAX_DURATION_SEC = 90.0
BYTES_PER_SAMPLE = 2


@dataclass(frozen=True)
class PcmSegment:
    pcm_s16le: bytes
    segment_index: int
    segment_duration_sec: float
    is_last: bool


def expected_pcm_bytes(duration_sec: float, sample_rate: int = INPUT_RATE) -> int:
    return int(round(duration_sec * sample_rate * BYTES_PER_SAMPLE))


def validate_request(
    pcm: bytes,
    gain_db: float,
    duration_sec: float,
    input_rate: int,
    output_rate: int,
) -> None:
    if input_rate != INPUT_RATE:
        raise ValueError(f"Input sample rate must be {INPUT_RATE}")
    if output_rate != OUTPUT_RATE:
        raise ValueError(f"Output sample rate must be {OUTPUT_RATE}")
    if duration_sec < MIN_DURATION_SEC or duration_sec > MAX_DURATION_SEC:
        raise ValueError(f"Duration must be between {MIN_DURATION_SEC} and {MAX_DURATION_SEC} seconds")
    if gain_db < -24 or gain_db > 24:
        raise ValueError("Gain must be between -24 and 24 dB")
    if len(pcm) == 0:
        raise ValueError("Empty PCM body")
    expected = expected_pcm_bytes(duration_sec)
    if abs(len(pcm) - expected) > BYTES_PER_SAMPLE:
        raise ValueError(f"PCM byte length {len(pcm)} does not match duration {duration_sec}s")


def iter_process_pcm_stream(
    pcm: bytes,
    gain_db: float,
    total_duration_sec: float,
    input_rate: int = INPUT_RATE,
    output_rate: int = OUTPUT_RATE,
    rtf: float = 0.6,
) -> Iterator[PcmSegment]:
    """Process PCM in 10s segments, yield immediately; RTF applied once after all segments."""
    validate_request(pcm, gain_db, total_duration_sec, input_rate, output_rate)

    started = time.perf_counter()
    segment_bytes = expected_pcm_bytes(SEGMENT_SEC)
    segment_index = 0
    offset = 0

    while offset < len(pcm):
        end = min(offset + segment_bytes, len(pcm))
        chunk = pcm[offset:end]
        segment_duration = len(chunk) / (input_rate * BYTES_PER_SAMPLE)
        gained = apply_gain_s16le(chunk, gain_db)
        resampled = resample_24k_to_48k_s16le(gained, input_rate, output_rate)
        is_last = end >= len(pcm)
        yield PcmSegment(
            pcm_s16le=resampled,
            segment_index=segment_index,
            segment_duration_sec=segment_duration,
            is_last=is_last,
        )
        segment_index += 1
        offset = end

    elapsed = time.perf_counter() - started
    enforce_rtf(total_duration_sec, elapsed, rtf)


def process_pcm(
    pcm: bytes,
    gain_db: float,
    duration_sec: float,
    input_rate: int = INPUT_RATE,
    output_rate: int = OUTPUT_RATE,
    rtf: float = 0.6,
) -> bytes:
    """Concatenate all stream segments (for unit tests)."""
    parts: list[bytes] = []
    for segment in iter_process_pcm_stream(pcm, gain_db, duration_sec, input_rate, output_rate, rtf):
        parts.append(segment.pcm_s16le)
    return b"".join(parts)
