#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROTO="$ROOT/proto/pcm/v1/pcm.proto"
OUT="$ROOT/pcm-service/app"

rm -rf "$OUT/pcm"
mkdir -p "$OUT"
cd "$ROOT/pcm-service"
uv run python -m grpc_tools.protoc \
  -I"$ROOT/proto" \
  --python_out="$OUT" \
  --grpc_python_out="$OUT" \
  --pyi_out="$OUT" \
  "$PROTO"

# Package init files and fix grpc import path
mkdir -p "$OUT/pcm/v1"
touch "$OUT/pcm/__init__.py"
touch "$OUT/pcm/v1/__init__.py"
sed -i 's/from pcm\.v1 import pcm_pb2/from app.pcm.v1 import pcm_pb2/' "$OUT/pcm/v1/pcm_pb2_grpc.py"

echo "Generated Python stubs in $OUT/pcm/v1"
