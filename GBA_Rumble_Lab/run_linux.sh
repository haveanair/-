#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "usage: $0 GAME.gba [frames] [input_script.csv]"
  exit 2
fi

ROM="$1"
FRAMES="${2:-3600}"
SCRIPT="${3:-GBA_Rumble_Lab/examples/input_script.csv}"
CORE_XZ=".v-rally-runtime/mgba_libretro.so.xz"
CORE=".v-rally-runtime/mgba_libretro.so"
OUT="probe-out-$(date +%Y%m%d-%H%M%S)"

if [ ! -f "$CORE" ]; then
  cp "$CORE_XZ" "$CORE.xz"
  xz -dkf "$CORE.xz"
fi

python3 GBA_Rumble_Lab/probe/rumble_probe.py \
  --core "$CORE" \
  --rom "$ROM" \
  --frames "$FRAMES" \
  --script "$SCRIPT" \
  --out "$OUT" \
  --frame-crc-every 60

echo "output: $OUT"
