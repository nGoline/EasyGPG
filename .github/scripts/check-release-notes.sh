#!/usr/bin/env bash
# Release notes are easy to forget and fail quietly: Play simply publishes without them.
# Fail the release instead.
set -euo pipefail

DIR="distribution/whatsnew"
LIMIT=500

if [ ! -d "$DIR" ]; then
  echo "::error::$DIR does not exist. Play release notes live there, one file per locale."
  exit 1
fi

found=0
for f in "$DIR"/whatsnew-*; do
  [ -e "$f" ] || continue
  found=1
  chars=$(wc -m < "$f" | tr -d ' ')
  if [ "$chars" -gt "$LIMIT" ]; then
    echo "::error::$f is $chars characters; Play allows $LIMIT."
    exit 1
  fi
  if [ "$chars" -eq 0 ]; then
    echo "::error::$f is empty."
    exit 1
  fi
  echo "  $(basename "$f"): $chars/$LIMIT characters"
done

if [ "$found" -eq 0 ]; then
  echo "::error::No whatsnew-<locale> files in $DIR."
  exit 1
fi
