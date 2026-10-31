#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "$#" -ne 0 ]; then
  echo 'Usage: bash scripts/build.sh' >&2
  exit 2
fi
python3 scripts/fetch-ocr.py --download
python3 scripts/build-ocr.py
python3 scripts/verify-apks.py
