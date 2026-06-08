#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Starting PCM Service A (gRPC) on :8090"
cd "$ROOT/pcm-service"
uv run python -m app.grpc_server &
PCM_PID=$!

echo "==> Starting Spring Orchestrator on :8080"
cd "$ROOT/orchestrator"
./gradlew compileJava bootRun &
SPRING_PID=$!

echo "==> Starting Vite dev server on :5173"
cd "$ROOT/web"
pnpm dev &
WEB_PID=$!

cleanup() {
  echo
  echo "Stopping services..."
  kill "$PCM_PID" "$SPRING_PID" "$WEB_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo
echo "Services:"
echo "  PCM:    gRPC localhost:8090"
echo "  Spring: http://localhost:8080"
echo "  Web UI: http://localhost:5173"
echo "Press Ctrl+C to stop all."

wait
