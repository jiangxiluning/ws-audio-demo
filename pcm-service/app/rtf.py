import time


def enforce_rtf(duration_sec: float, elapsed_sec: float, rtf: float = 0.6) -> None:
    target = max(0.0, duration_sec * rtf - elapsed_sec)
    if target > 0:
        time.sleep(target)
