#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> pcm-service: uv sync + proto"
cd "$ROOT/pcm-service"
uv sync --extra dev
"$ROOT/scripts/generate-proto.sh"

echo "==> web: pnpm install && build"
cd "$ROOT/web"
pnpm install --frozen-lockfile 2>/dev/null || pnpm install || true
node node_modules/vite/bin/vite.js build

echo "==> Copy web dist to Spring static"
rm -rf "$ROOT/orchestrator/src/main/resources/static"
mkdir -p "$ROOT/orchestrator/src/main/resources/static"
cp -r dist/* "$ROOT/orchestrator/src/main/resources/static/"

echo "==> orchestrator: bootJar"
cd "$ROOT/orchestrator"
./gradlew bootJar

echo "Done. JAR: orchestrator/build/libs/"
