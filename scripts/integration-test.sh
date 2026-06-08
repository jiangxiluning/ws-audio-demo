#!/usr/bin/env bash
# Full-stack integration test (starts services, runs 10s FLAC pipeline).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FLAC="$ROOT/test-fixtures/flac/tone_10s_24k_mono.flac"
PCM_RTF="${PCM_RTF:-0.01}"

if [ ! -f "$FLAC" ]; then
  echo "Generating fixtures..."
  "$ROOT/test-fixtures/generate.sh"
fi

pkill -f "app.grpc_server" 2>/dev/null || true
pkill -f "OrchestratorApplication" 2>/dev/null || true
sleep 2

cleanup() {
  [ -n "${PCM_PID:-}" ] && kill "$PCM_PID" 2>/dev/null || true
  [ -n "${SPRING_PID:-}" ] && kill "$SPRING_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "==> Start pcm-service (PCM_RTF=$PCM_RTF)"
cd "$ROOT/pcm-service"
PCM_RTF="$PCM_RTF" uv run python -m app.grpc_server &
PCM_PID=$!

echo "==> Start orchestrator"
cd "$ROOT/orchestrator"
./gradlew compileJava bootRun --quiet > /tmp/ws-audio-spring.log 2>&1 &
SPRING_PID=$!

echo "==> Wait for health"
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/api/v1/health >/dev/null 2>&1; then
    pcm_ok=$(curl -sf http://localhost:8080/api/v1/health | grep -o '"pcmService":"ok"' || true)
    if [ -n "$pcm_ok" ]; then
      break
    fi
  fi
  sleep 2
done
curl -sf http://localhost:8080/api/v1/health | grep -q '"pcmService":"ok"' || { echo "Services not ready"; exit 1; }

echo "==> Run WS integration test"
cd "$ROOT/pcm-service"
PCM_RTF="$PCM_RTF" uv run python "$ROOT/scripts/ws_integration_test.py" --flac "$FLAC" --timeout 120

echo "Integration test passed."
