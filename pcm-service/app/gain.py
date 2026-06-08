import numpy as np


def apply_gain_s16le(pcm: bytes, gain_db: float) -> bytes:
    if not pcm:
        return b""
    samples = np.frombuffer(pcm, dtype=np.int16).astype(np.float32)
    factor = 10.0 ** (gain_db / 20.0)
    samples *= factor
    samples = np.clip(samples, -32768, 32767)
    return samples.astype(np.int16).tobytes()
