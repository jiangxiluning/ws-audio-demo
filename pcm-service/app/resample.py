import numpy as np
from scipy.signal import resample_poly


def resample_24k_to_48k_s16le(pcm: bytes, input_rate: int = 24000, output_rate: int = 48000) -> bytes:
    if not pcm:
        return b""
    samples = np.frombuffer(pcm, dtype=np.int16).astype(np.float32)
    if input_rate == output_rate:
        return pcm
    gcd = np.gcd(input_rate, output_rate)
    up = output_rate // gcd
    down = input_rate // gcd
    resampled = resample_poly(samples, up, down)
    resampled = np.clip(resampled, -32768, 32767).astype(np.int16)
    return resampled.tobytes()
