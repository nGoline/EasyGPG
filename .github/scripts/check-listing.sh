#!/usr/bin/env bash
# Play truncates or rejects over-length listing copy, so check before pasting.
set -euo pipefail

DIR="distribution/listing"
status=0

check() { # file, limit, label
  local f="$1" limit="$2" label="$3"
  if [ ! -f "$f" ]; then
    echo "  MISSING  $f"; status=1; return
  fi
  local n; n=$(wc -m < "$f" | tr -d ' ')
  if [ "$n" -gt "$limit" ]; then
    echo "  OVER     $label: $n/$limit ($((n-limit)) too many) — $f"; status=1
  else
    echo "  ok       $label: $n/$limit — $f"
  fi
}

for locale_dir in "$DIR"/*/; do
  [ -d "$locale_dir" ] || continue
  echo "$(basename "$locale_dir"):"
  check "${locale_dir}title.txt" 30 "App name"
  check "${locale_dir}short-description.txt" 80 "Short description"
  check "${locale_dir}full-description.txt" 4000 "Full description"
done

exit $status
