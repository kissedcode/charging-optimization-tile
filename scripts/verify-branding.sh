#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for path in branding/icon-source.png \
  app/src/main/res/mipmap-xxxhdpi/ic_launcher.png \
  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml; do
  test -s "$path" || { echo "Missing required icon: $path" >&2; exit 1; }
done
