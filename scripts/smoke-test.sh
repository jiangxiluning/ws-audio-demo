#!/usr/bin/env bash
# Quick smoke check — services must already be running.
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"

echo "==> Orchestrator + PCM (gRPC via /api/v1/health)"
curl -sf "$API_URL/api/v1/health" | grep -q '"pcmService":"ok"' && echo "  ok" || { echo "  FAIL"; exit 1; }

echo "==> Web dev (optional)"
if curl -sf -o /dev/null "http://localhost:5173/"; then
  echo "  ok"
else
  echo "  skip (not running)"
fi

echo "All checks passed."
