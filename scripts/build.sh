#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/fetch-ocr.py --download
variant="${1:-all}"
case "$variant" in
  all) python3 scripts/build-ocr.py --variant personal
       python3 scripts/build-ocr.py --variant release ;;
  personal|release) python3 scripts/build-ocr.py --variant "$variant" ;;
  *) echo 'Usage: bash scripts/build.sh [all|personal|release]' >&2; exit 2 ;;
esac
python3 scripts/verify-apks.py --variant "$variant"
