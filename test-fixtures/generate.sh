#!/usr/bin/env bash
# Generate boundary test WAV/FLAC fixtures (requires ffmpeg).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WAV_DIR="$ROOT/test-fixtures/wav"
FLAC_DIR="$ROOT/test-fixtures/flac"

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg required. Install: sudo apt install ffmpeg" >&2
  exit 1
fi

mkdir -p "$WAV_DIR" "$FLAC_DIR"

echo "==> Synthetic boundary WAV"
ffmpeg -y -hide_banner -loglevel error \
  -f lavfi -i "sine=frequency=440:duration=8" \
  -ar 24000 -ac 1 -sample_fmt s16 "$WAV_DIR/tone_8s_24k_mono.wav"

ffmpeg -y -hide_banner -loglevel error \
  -f lavfi -i "sine=frequency=440:duration=301" \
  -ar 24000 -ac 1 -sample_fmt s16 "$WAV_DIR/tone_301s_24k_mono.wav"

echo "==> 10s integration test FLAC"
ffmpeg -y -hide_banner -loglevel error \
  -f lavfi -i "sine=frequency=440:duration=10" \
  -ar 24000 -ac 1 -sample_fmt s16 "$WAV_DIR/tone_10s_24k_mono.wav"

ffmpeg -y -hide_banner -loglevel error \
  -i "$WAV_DIR/tone_10s_24k_mono.wav" \
  -compression_level 8 "$FLAC_DIR/tone_10s_24k_mono.flac"

MAIN_WAV="$ROOT/褪黑素.wav"
if [ -f "$MAIN_WAV" ] && [ ! -f "$WAV_DIR/褪黑素.wav" ]; then
  cp "$MAIN_WAV" "$WAV_DIR/褪黑素.wav"
fi

if [ -f "$WAV_DIR/褪黑素.wav" ]; then
  echo "==> FLAC from 褪黑素.wav"
  ffmpeg -y -hide_banner -loglevel error \
    -i "$WAV_DIR/褪黑素.wav" \
    -sample_fmt s16 -ac 1 -ar 24000 \
    "$FLAC_DIR/褪黑素.flac"

  ffmpeg -y -hide_banner -loglevel error \
    -i "$WAV_DIR/褪黑素.wav" \
    -compression_level 8 \
    "$FLAC_DIR/褪黑素_44100_stereo.flac"
fi

echo "Done. Fixtures in test-fixtures/{wav,flac}/"
