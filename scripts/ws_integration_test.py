#!/usr/bin/env python3
"""End-to-end API + WebSocket test against running services."""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

try:
    import websocket  # type: ignore
except ImportError:
    print("Install: pip install websocket-client  (or uv pip install websocket-client)", file=sys.stderr)
    sys.exit(1)

API = "http://localhost:8080"
PCM_RTF_HINT = "Set PCM_RTF=0.01 on pcm-service for faster runs"


def post_json(url: str, payload: dict) -> dict:
    data = json.dumps(payload).encode()
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())


def upload_flac(path: Path) -> dict:
    boundary = "----WsAudioDemo"
    body = bytearray()
    body.extend(f"--{boundary}\r\n".encode())
    body.extend(b'Content-Disposition: form-data; name="file"; filename="' + path.name.encode() + b'"\r\n')
    body.extend(b"Content-Type: audio/flac\r\n\r\n")
    body.extend(path.read_bytes())
    body.extend(f"\r\n--{boundary}--\r\n".encode())
    req = urllib.request.Request(
        f"{API}/api/v1/audio/upload",
        data=bytes(body),
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read())


def run_ws(stream_path: str, timeout_sec: int = 120, verbose: bool = False) -> dict:
    url = f"ws://localhost:8080{stream_path}"
    complete: dict | None = None
    error: str | None = None
    binary_bytes = 0
    segment_completes = 0
    chunk_completes = 0
    ws = websocket.create_connection(url, timeout=10)
    try:
        deadline = time.time() + timeout_sec
        while time.time() < deadline:
            remaining = deadline - time.time()
            ws.settimeout(max(1.0, min(30.0, remaining)))
            try:
                raw = ws.recv()
            except websocket.WebSocketTimeoutException:
                if verbose:
                    print(f"  … waiting ({binary_bytes} binary bytes so far)", flush=True)
                continue
            if isinstance(raw, bytes):
                binary_bytes += len(raw)
                continue
            frame = json.loads(raw)
            if verbose:
                print(f"  ws text: {frame.get('type')}", flush=True)
            t = frame.get("type")
            if t == "segment_complete":
                segment_completes += 1
            if t == "chunk_complete":
                chunk_completes += 1
            if t == "complete":
                complete = frame
                break
            if t == "error":
                error = frame.get("message", "unknown error")
                break
    finally:
        ws.close()
    if error:
        raise RuntimeError(error)
    if not complete:
        raise TimeoutError(f"WS did not complete within {timeout_sec}s")
    if segment_completes < 1:
        raise RuntimeError(f"Expected segment_complete frames, got {segment_completes}")
    if chunk_completes < 1:
        raise RuntimeError(f"Expected chunk_complete frames, got {chunk_completes}")
    return complete


def download(job_id: str) -> bytes:
    with urllib.request.urlopen(f"{API}/api/v1/audio/download/{job_id}", timeout=120) as resp:
        return resp.read()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--flac",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "test-fixtures/flac/tone_10s_24k_mono.flac",
    )
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args()

    if not args.flac.is_file():
        print(f"Missing {args.flac}. Run ./test-fixtures/generate.sh", file=sys.stderr)
        return 1

    print(f"==> Health ({PCM_RTF_HINT})")
    with urllib.request.urlopen(f"{API}/api/v1/health", timeout=5) as r:
        print(" ", json.loads(r.read()))

    print(f"==> Upload {args.flac.name}")
    up = upload_flac(args.flac)
    print(" ", up)

    print("==> Process")
    proc = post_json(f"{API}/api/v1/audio/process", {"uri": up["uri"], "gainDb": 6})
    print(" ", {k: proc[k] for k in ("jobId", "totalChunks", "streamPath", "estimatedProcessingSeconds")})

    print("==> WebSocket pipeline")
    complete = run_ws(proc["streamPath"], timeout_sec=args.timeout, verbose=args.verbose)
    print(" ", complete)

    print("==> Download")
    data = download(proc["jobId"])
    print(f"  {len(data)} bytes")
    assert len(data) > 0
    print("OK")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except urllib.error.URLError as e:
        print(f"Connection failed: {e}. Start pcm-service + orchestrator first.", file=sys.stderr)
        raise SystemExit(1)
